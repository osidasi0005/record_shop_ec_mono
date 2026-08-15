package com.example.recordshop.infrastructure.mybatis;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface EmailVerificationMapper {

    void insert(EmailVerificationRow row);

    void updateByEmail(EmailVerificationRow row);

    EmailVerificationRow selectByEmail(@Param("email") String email);

    int countByEmail(@Param("email") String email);

    void deleteByEmail(@Param("email") String email);
}
