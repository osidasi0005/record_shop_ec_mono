package com.example.recordshop.infrastructure.mybatis;

import com.example.recordshop.domain.catalog.PressingId;
import com.example.recordshop.domain.inventory.ConditionType;
import com.example.recordshop.domain.inventory.GoldmineGrade;
import com.example.recordshop.domain.inventory.Listing;
import com.example.recordshop.domain.inventory.ListingId;
import com.example.recordshop.domain.inventory.ListingRepository;
import com.example.recordshop.domain.inventory.ListingStatus;
import com.example.recordshop.domain.shared.Money;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Currency;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * {@link ListingRepository} のMyBatisアダプタ実装。
 *
 * <p>楽観ロックには「読み込んだ時点のversionを覚えておき、保存時のUPDATE文に
 * WHERE version = ?として使う」仕組みが必要だが、MyBatisにはこれを自動でやってくれる
 * 永続化コンテキストに相当するものが無い。
 *
 * <p>ここでは{@link ThreadLocal}で「このスレッド(=通常は1リクエストの処理スレッド)が
 * findByIdで読み込んだversionの一時記録」を手動で再現している。
 *
 * <p><b>既知の簡略化</b>: このThreadLocalは、リクエストの完了時に自動でクリアされる
 * 保証が無い(Servletコンテナのスレッドプールで使い回されるため)。本番運用するなら
 * Servlet FilterやSpring の {@code HandlerInterceptor#afterCompletion} で
 * リクエスト終了時に確実にクリアする実装が必要。この比較実験ではそこまでは踏み込まない。
 */
@Repository
public class MyBatisListingRepository implements ListingRepository {

    private final ThreadLocal<Map<ListingId, Long>> loadedVersions = ThreadLocal.withInitial(HashMap::new);

    private final ListingMapper mapper;

    public MyBatisListingRepository(ListingMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    @Transactional
    public void save(Listing listing) {
        Long knownVersion = loadedVersions.get().get(listing.listingId());

        if (knownVersion == null) {
            // findByIdを経由していない = まだDBに存在しない新規Listingとみなし、version 0で挿入する。
            mapper.insert(toRow(listing, 0L));
            loadedVersions.get().put(listing.listingId(), 0L);
            return;
        }

        int updated = mapper.update(toRow(listing, knownVersion));
        if (updated == 0) {
            throw new OptimisticLockingFailureException(
                    "Listing " + listing.listingId() + " は他のトランザクションによって更新されています(楽観ロック競合)");
        }
        // 同一スレッド内で連続してsave()される場合に備え、既知versionを更新後の値へ進めておく。
        loadedVersions.get().put(listing.listingId(), knownVersion + 1);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Listing> findById(ListingId listingId) {
        ListingRow row = mapper.selectById(listingId.value());
        if (row == null) {
            return Optional.empty();
        }
        loadedVersions.get().put(listingId, row.version());
        return Optional.of(toDomain(row));
    }

    @Override
    @Transactional(readOnly = true)
    public List<Listing> findByPressingId(PressingId pressingId) {
        List<ListingRow> rows = mapper.selectByPressingId(pressingId.value());
        for (ListingRow row : rows) {
            loadedVersions.get().put(new ListingId(row.id()), row.version());
        }
        return rows.stream().map(this::toDomain).toList();
    }

    private Listing toDomain(ListingRow row) {
        Money price = new Money(row.priceAmount(), Currency.getInstance(row.priceCurrency()));
        return Listing.reconstitute(
                new ListingId(row.id()),
                new PressingId(row.pressingId()),
                ConditionType.valueOf(row.conditionType()),
                price,
                ListingStatus.valueOf(row.status()),
                row.stockQuantity(),
                row.vinylGrade() == null ? null : GoldmineGrade.valueOf(row.vinylGrade()),
                row.sleeveGrade() == null ? null : GoldmineGrade.valueOf(row.sleeveGrade()),
                row.sellerNote()
        );
    }

    private ListingRow toRow(Listing listing, long version) {
        return new ListingRow(
                listing.listingId().value(),
                listing.pressingId().value(),
                listing.conditionType().name(),
                listing.price().amount(),
                listing.price().currency().getCurrencyCode(),
                listing.status().name(),
                listing.stockQuantity(),
                listing.vinylGrade() == null ? null : listing.vinylGrade().name(),
                listing.sleeveGrade() == null ? null : listing.sleeveGrade().name(),
                listing.sellerNote(),
                version
        );
    }
}
