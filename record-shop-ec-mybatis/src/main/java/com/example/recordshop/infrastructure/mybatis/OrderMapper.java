package com.example.recordshop.infrastructure.mybatis;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.UUID;

@Mapper
public interface OrderMapper {

    void insertOrder(OrderRow row);

    void insertOrderLine(OrderLineRow row);

    void updateOrder(OrderRow row);

    OrderRow selectOrderById(@Param("id") UUID id);

    List<OrderLineRow> selectOrderLinesByOrderId(@Param("orderId") UUID orderId);

    List<OrderRow> selectOrdersByCustomerId(@Param("customerId") UUID customerId);

    /** 管理画面の注文一覧用。全顧客の注文を新しい順に返す。 */
    List<OrderRow> selectAllOrders();

    /**
     * findByCustomerId()をN+1にしないための一括取得用メソッド。
     * 注文件数分ループしてlinesを取得する実装にすると、Release/Listingと同じN+1問題が再現するため、
     * 該当する注文IDをまとめてIN句で1回のSELECTにする。
     */
    List<OrderLineRow> selectOrderLinesByOrderIds(@Param("orderIds") List<UUID> orderIds);
}
