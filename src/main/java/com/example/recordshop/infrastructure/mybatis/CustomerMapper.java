package com.example.recordshop.infrastructure.mybatis;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.UUID;

@Mapper
public interface CustomerMapper {

    void insert(CustomerRow row);

    CustomerRow selectById(@Param("id") UUID id);

    CustomerRow selectByEmail(@Param("email") String email);

    int countByEmail(@Param("email") String email);
}
