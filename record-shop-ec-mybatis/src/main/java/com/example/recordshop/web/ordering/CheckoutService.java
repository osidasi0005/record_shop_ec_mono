package com.example.recordshop.web.ordering;

import com.example.recordshop.domain.ordering.Cart;
import com.example.recordshop.domain.ordering.Order;
import com.example.recordshop.domain.ordering.OrderPlacementService;
import com.example.recordshop.domain.payment.PaymentCaptureService;
import com.example.recordshop.domain.payment.PaymentMethod;
import com.example.recordshop.domain.shared.Address;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;

/**
 * 「注文確定 + 決済Capture」を1トランザクションで実行し、在庫の楽観ロック競合時にはリトライする。
 *
 * <p><b>なぜコントローラから分離したか</b>: 以前は {@code CheckoutController#submit} 自身に
 * {@code @Transactional} を付け、そのメソッド内で {@link OptimisticLockingFailureException} を
 * 捕まえてリダイレクトを返していた。しかし例外を投げた時点で内側のトランザクションは
 * rollback-only にマークされているため、正常終了扱いでコミットしようとした瞬間に
 * {@code UnexpectedRollbackException} が発生し、利用者には500エラーしか見えなかった。
 * 例外の捕捉はトランザクション境界の<b>外側</b>で行う必要がある。
 *
 * <p><b>なぜ {@link TransactionTemplate} か</b>: リトライの各回を必ず新しいトランザクションで
 * 開始する必要がある。{@code @Transactional} メソッドを自クラスから呼ぶとプロキシを経由せず
 * 境界が効かないため、境界を明示的に開始できるTransactionTemplateを使う。
 *
 * <p><b>なぜリトライするか</b>: 在庫の楽観ロックは {@code listings.version} の行単位であり、
 * 在庫数に余裕があっても同一Listingへの同時購入は必ず競合する。1回の競合で購入失敗を返すと
 * 「在庫はあるのに買えない」状態になるため、数回だけ引き直す。本当に売り切れている場合は
 * リトライしても在庫不足で失敗するので、competing update と売り切れは自然に区別される。
 */
@Service
public class CheckoutService {

    private static final Logger LOGGER = LoggerFactory.getLogger(CheckoutService.class);

    /** 競合は「他の誰かが同じ商品を同時に買った」ケースなので、数回引き直せばまず収束する。 */
    private static final int MAX_ATTEMPTS = 3;

    private final OrderPlacementService orderPlacementService;
    private final PaymentCaptureService paymentCaptureService;
    private final TransactionTemplate transactionTemplate;

    public CheckoutService(OrderPlacementService orderPlacementService,
                            PaymentCaptureService paymentCaptureService,
                            PlatformTransactionManager transactionManager) {
        this.orderPlacementService = orderPlacementService;
        this.paymentCaptureService = paymentCaptureService;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    /**
     * @throws OptimisticLockingFailureException {@value #MAX_ATTEMPTS} 回リトライしても競合が解消しなかった場合
     */
    public Order checkout(Cart cart, Address shippingAddress, Address billingAddress) {
        OptimisticLockingFailureException lastFailure = null;

        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                return placeOrderAndCapture(cart, shippingAddress, billingAddress);
            } catch (OptimisticLockingFailureException e) {
                // このトランザクションは完全にロールバック済み。在庫も注文も決済も残っていないので、
                // 最新の在庫を読み直してそのまま引き直せる。
                lastFailure = e;
                LOGGER.info("在庫の楽観ロック競合を検知したため注文処理をリトライします(試行 {}/{})",
                        attempt, MAX_ATTEMPTS);
            }
        }

        throw lastFailure;
    }

    private Order placeOrderAndCapture(Cart cart, Address shippingAddress, Address billingAddress) {
        return transactionTemplate.execute(status -> {
            Instant now = Instant.now();
            Order order = orderPlacementService.placeOrder(cart, shippingAddress, billingAddress, now);
            paymentCaptureService.capturePayment(order.orderId(), PaymentMethod.CREDIT_CARD, now);
            return order;
        });
    }
}
