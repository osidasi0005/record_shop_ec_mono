package com.example.recordshop.infrastructure.mybatis;

import com.example.recordshop.domain.catalog.PressingId;
import com.example.recordshop.domain.inventory.GoldmineGrade;
import com.example.recordshop.domain.inventory.Listing;
import com.example.recordshop.domain.inventory.ListingId;
import com.example.recordshop.domain.inventory.ListingStatus;
import com.example.recordshop.domain.shared.Money;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * record-shop-ec-domain(JPA版)の {@code ListingOptimisticLockingTest} と全く同じシナリオを、
 * MyBatis版({@link MyBatisListingRepository})で検証する。
 *
 * <p>JPA版との一番の違いは、versionの管理を{@link MyBatisListingRepository}内の
 * {@code ThreadLocal}で手動再現している点。この仕組みが正しく機能していれば、
 * JPA版と同じく「片方だけ成功し、もう片方は楽観ロック競合で失敗する」という結果になるはずである。
 *
 * <p>クラスに{@code @Transactional}を付けない点はJPA版と同じ理由(別トランザクションでの
 * 競合を再現するため、{@link TransactionTemplate}で明示的にトランザクションを分ける)。
 */
@SpringBootTest
class MyBatisListingOptimisticLockingTest {

    @Autowired
    private MyBatisListingRepository listingRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    void 同時に同じUsedListingを予約すると片方だけ成功しもう片方は楽観ロック競合で失敗する() throws Exception {
        ListingId listingId = ListingId.generate();
        Listing seed = Listing.usedCopy(listingId, PressingId.generate(), Money.jpy(28000),
                GoldmineGrade.VERY_GOOD_PLUS, GoldmineGrade.VERY_GOOD, null);
        seed.publish(Instant.now());
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> listingRepository.save(seed));

        // 両スレッドが「読み込み+in-memoryでreserve()」を終えるまで待ち合わせてから、
        // ほぼ同時にsave()(実際のUPDATE)へ進ませることで競合を確実に発生させる。
        CyclicBarrier bothReservedBarrier = new CyclicBarrier(2);
        List<Throwable> failures = Collections.synchronizedList(new ArrayList<>());
        AtomicInteger successCount = new AtomicInteger();

        Runnable reserveInOwnTransaction = () -> {
            try {
                new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                    Listing listing = listingRepository.findById(listingId).orElseThrow();
                    listing.reserve(1);
                    awaitBarrier(bothReservedBarrier);
                    listingRepository.save(listing);
                });
                successCount.incrementAndGet();
            } catch (Throwable e) {
                failures.add(e);
            }
        };

        // 別々のThreadインスタンスなので、MyBatisListingRepositoryのThreadLocalは
        // 互いに独立している(スレッドプールの使い回しによる混線は起きない)。
        Thread t1 = new Thread(reserveInOwnTransaction);
        Thread t2 = new Thread(reserveInOwnTransaction);
        t1.start();
        t2.start();
        t1.join(5000);
        t2.join(5000);

        assertThat(successCount.get()).isEqualTo(1);
        assertThat(failures).hasSize(1);
        assertThat(failures.get(0)).isInstanceOf(OptimisticLockingFailureException.class);

        // 二重販売が起きていない(=RESERVEDへの遷移が1回だけ反映されている)ことをDBの最終状態で確認
        Listing reloaded = new TransactionTemplate(transactionManager)
                .execute(status -> listingRepository.findById(listingId).orElseThrow());
        assertThat(reloaded.status()).isEqualTo(ListingStatus.RESERVED);
    }

    private void awaitBarrier(CyclicBarrier barrier) {
        try {
            barrier.await(5, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
