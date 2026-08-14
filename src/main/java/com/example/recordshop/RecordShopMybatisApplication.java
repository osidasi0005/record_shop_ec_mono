package com.example.recordshop;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("com.example.recordshop.infrastructure.mybatis")
public class RecordShopMybatisApplication {

    public static void main(String[] args) {
        SpringApplication.run(RecordShopMybatisApplication.class, args);
    }
}
