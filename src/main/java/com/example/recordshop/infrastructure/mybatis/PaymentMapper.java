package com.example.recordshop.infrastructure.mybatis;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.UUID;

@Mapper
public interface PaymentMapper {

    void insert(PaymentRow row);

    PaymentRow selectById(@Param("id") UUID id);

    List<PaymentRow> selectByOrderId(@Param("orderId") UUID orderId);
}
