package com.example.recordshop.domain.ordering;

import com.example.recordshop.domain.catalog.Pressing;
import com.example.recordshop.domain.catalog.Release;
import com.example.recordshop.domain.catalog.ReleaseRepository;
import com.example.recordshop.domain.inventory.Listing;
import com.example.recordshop.domain.inventory.ListingRepository;
import com.example.recordshop.domain.shared.Address;
import com.example.recordshop.domain.shared.InvariantViolationException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * 注文確定のドメインサービス。
 *
 * <p>Cart / Listing / Release は別々の集約であり、それぞれが自分の不変条件しか守れない。
 * 「カートの中身を注文に変換し、在庫を予約し、Pressing 情報をスナップショット化する」という
 * 複数集約にまたがる一連の手続きは、この domain service が調停する。
 *
 * <p>途中の Listing でエラーが起きた場合、それまでに予約済みの Listing は解放してロールバックする
 * (実際の運用ではリポジトリのトランザクション境界と合わせて設計するが、ここではドメイン層だけで
 * 補償動作を表現している)。
 */
public final class OrderPlacementService {

    private final ListingRepository listingRepository;
    private final ReleaseRepository releaseRepository;
    private final OrderRepository orderRepository;

    public OrderPlacementService(ListingRepository listingRepository,
                                  ReleaseRepository releaseRepository,
                                  OrderRepository orderRepository) {
        this.listingRepository = listingRepository;
        this.releaseRepository = releaseRepository;
        this.orderRepository = orderRepository;
    }

    public Order placeOrder(Cart cart, Address shippingAddress, Address billingAddress, Instant now) {
        if (cart.isEmpty()) {
            throw new InvariantViolationException("空のカートから注文は作成できません");
        }

        List<Listing> reserved = new ArrayList<>();
        List<Integer> reservedQuantities = new ArrayList<>();
        List<OrderLine> orderLines = new ArrayList<>();

        try {
            for (CartLine cartLine : cart.lines()) {
                Listing listing = listingRepository.findById(cartLine.listingId())
                        .orElseThrow(() -> new InvariantViolationException(
                                "Listing が見つかりません: " + cartLine.listingId()));

                listing.reserve(cartLine.quantity());
                reserved.add(listing);
                reservedQuantities.add(cartLine.quantity());

                Release release = releaseRepository.findByPressingId(listing.pressingId())
                        .orElseThrow(() -> new InvariantViolationException(
                                "Pressing を含む Release が見つかりません: " + listing.pressingId()));
                Pressing pressing = release.findPressing(listing.pressingId())
                        .orElseThrow(() -> new InvariantViolationException(
                                "Pressing が見つかりません: " + listing.pressingId()));

                PressingSnapshot snapshot = new PressingSnapshot(
                        release.title(),
                        release.artistName(),
                        pressing.labelName(),
                        pressing.catalogNumber(),
                        pressing.country(),
                        pressing.pressYear(),
                        pressing.format(),
                        listing.conditionType(),
                        listing.vinylGrade(),
                        listing.sleeveGrade()
                );

                orderLines.add(new OrderLine(listing.listingId(), snapshot, listing.price(), cartLine.quantity()));
            }
        } catch (RuntimeException e) {
            // 途中まで予約した分をロールバックしてから例外を伝播する。
            for (int i = 0; i < reserved.size(); i++) {
                reserved.get(i).cancelReservation(reservedQuantities.get(i));
                listingRepository.save(reserved.get(i));
            }
            throw e;
        }

        Order order = Order.place(OrderId.generate(), cart.customerId(), orderLines,
                shippingAddress, billingAddress, now);

        for (Listing listing : reserved) {
            listingRepository.save(listing);
        }
        orderRepository.save(order);

        return order;
    }
}
