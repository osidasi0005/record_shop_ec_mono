package com.example.recordshop.infrastructure.mybatis;

import com.example.recordshop.domain.catalog.Format;
import com.example.recordshop.domain.catalog.MediaType;
import com.example.recordshop.domain.catalog.Pressing;
import com.example.recordshop.domain.catalog.PressingId;
import com.example.recordshop.domain.catalog.Release;
import com.example.recordshop.domain.catalog.ReleaseId;
import com.example.recordshop.domain.catalog.ReleaseRepository;
import com.example.recordshop.domain.catalog.Speed;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * {@link ReleaseRepository}(ドメイン層のポート)のMyBatisアダプタ実装。
 *
 * <p>JPA版({@code JpaReleaseRepository})との一番の違いはここ。JPAは
 * {@code @OneToMany}/{@code @ElementCollection}の設定さえすれば、集約全体の組み立てを
 * フレームワークが自動でやってくれる。MyBatisにはその仕組みが無いため、「本体」「genres」
 * 「pressings」を別々のSELECTで取得し、{@code toDomain}系のメソッドで手動で組み立てている。
 *
 * <p>{@link #findAll()} は、Release件数分ループして{@link #findById(ReleaseId)}を呼ぶ
 * 素朴な実装にすると、JPA版で見つかったのと全く同じN+1問題がMyBatisでも再現する。
 * それを避けるため、genres/pressingsは常に「全件を1回のSELECTで取得してJava側でグルーピングする」
 * という書き方を徹底している。つまりMyBatisはN+1を自動では防いでくれず、
 * 「N+1を避けた書き方をする責任」がJPA以上にはっきりと開発者側にある。
 */
@Repository
public class MyBatisReleaseRepository implements ReleaseRepository {

    private final ReleaseMapper mapper;

    public MyBatisReleaseRepository(ReleaseMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    @Transactional
    public void save(Release release) {
        UUID releaseId = release.releaseId().value();
        ReleaseRow row = new ReleaseRow(releaseId, release.title(), release.artistName(),
                release.originalReleaseYear(), release.artworkUrl());

        // JPAならdirty checkingが自動でやってくれる「新規か更新か」の判定を、ここでは
        // 明示的なSELECTで自分の手で行う必要がある。addPressing()等で集約が変化した後の
        // 再saveがこの分岐を通り、子コレクション(genres/pressings)は全delete→re-insertで
        // 最新の状態に揃える(MyBatisにはJPAの@OneToMany cascadeに相当する自動反映が無いため)。
        boolean isNew = mapper.selectReleaseById(releaseId) == null;
        if (isNew) {
            mapper.insertRelease(row);
        } else {
            mapper.updateRelease(row);
            mapper.deleteGenresByReleaseId(releaseId);
            mapper.deletePressingsByReleaseId(releaseId);
        }

        for (String genre : release.genres()) {
            mapper.insertGenre(releaseId, genre);
        }
        for (Pressing pressing : release.pressings()) {
            mapper.insertPressing(toPressingRow(pressing, releaseId));
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Release> findById(ReleaseId releaseId) {
        ReleaseRow row = mapper.selectReleaseById(releaseId.value());
        if (row == null) {
            return Optional.empty();
        }
        Set<String> genres = mapper.selectGenresByReleaseId(releaseId.value()).stream()
                .map(GenreRow::genre)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        List<Pressing> pressings = mapper.selectPressingsByReleaseId(releaseId.value()).stream()
                .map(this::toDomainPressing)
                .toList();
        return Optional.of(toDomain(row, genres, pressings));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Release> findByPressingId(PressingId pressingId) {
        UUID releaseId = mapper.selectReleaseIdByPressingId(pressingId.value());
        if (releaseId == null) {
            return Optional.empty();
        }
        return findById(new ReleaseId(releaseId));
    }

    @Override
    @Transactional(readOnly = true)
    public List<Release> findAll() {
        List<ReleaseRow> releaseRows = mapper.selectAllReleases();

        Map<UUID, Set<String>> genresByRelease = new HashMap<>();
        for (GenreRow g : mapper.selectAllGenres()) {
            genresByRelease.computeIfAbsent(g.releaseId(), k -> new LinkedHashSet<>()).add(g.genre());
        }
        Map<UUID, List<Pressing>> pressingsByRelease = new HashMap<>();
        for (PressingRow p : mapper.selectAllPressings()) {
            pressingsByRelease.computeIfAbsent(p.releaseId(), k -> new ArrayList<>()).add(toDomainPressing(p));
        }

        List<Release> releases = new ArrayList<>();
        for (ReleaseRow row : releaseRows) {
            Set<String> genres = genresByRelease.getOrDefault(row.id(), Set.of());
            List<Pressing> pressings = pressingsByRelease.getOrDefault(row.id(), List.of());
            releases.add(toDomain(row, genres, pressings));
        }
        return releases;
    }

    private Release toDomain(ReleaseRow row, Set<String> genres, List<Pressing> pressings) {
        return Release.reconstitute(new ReleaseId(row.id()), row.title(), row.artistName(),
                genres, row.originalReleaseYear(), row.artworkUrl(), pressings);
    }

    private Pressing toDomainPressing(PressingRow row) {
        return Pressing.reconstitute(new PressingId(row.id()), row.labelName(), row.catalogNumber(),
                row.country(), row.pressYear(), row.matrixRunout(), row.reissue(),
                new Format(MediaType.valueOf(row.mediaType()), Speed.valueOf(row.speed()), row.discCount()),
                row.artworkUrl());
    }

    private PressingRow toPressingRow(Pressing pressing, UUID releaseId) {
        return new PressingRow(pressing.pressingId().value(), releaseId, pressing.labelName(),
                pressing.catalogNumber(), pressing.country(), pressing.pressYear(), pressing.matrixRunout(),
                pressing.isReissue(), pressing.format().mediaType().name(), pressing.format().speed().name(),
                pressing.format().discCount(), pressing.artworkUrl());
    }
}
