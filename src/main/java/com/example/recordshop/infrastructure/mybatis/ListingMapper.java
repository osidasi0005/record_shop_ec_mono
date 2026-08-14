package com.example.recordshop.infrastructure.mybatis;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.UUID;

@Mapper
public interface ListingMapper {

    void insert(ListingRow row);

    ListingRow selectById(@Param("id") UUID id);

    List<ListingRow> selectByPressingId(@Param("pressingId") UUID pressingId);

    /**
     * 楽観ロック付きUPDATE。WHERE句にversionを含め、影響行数で競合を検知する
     * (JPAの{@code @Version}が自動でやっていることを、ここでは自前で実装している)。
     *
     * @return 更新できた行数。0なら楽観ロック競合(他のトランザクションが先に更新済み)。
     */
    int update(ListingRow row);
}
