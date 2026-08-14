package com.example.recordshop.infrastructure.web;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

/**
 * 学習・デモ用途のサイトを検索エンジンにインデックスさせないためのフィルタ。
 * 全レスポンスに {@code X-Robots-Tag: noindex, nofollow} を付与する。
 * {@code robots.txt}(静的ファイル)だけでは行儀の良いクローラーにしか効かないため、
 * より確実なレスポンスヘッダーでの拒否も併用する。
 */
public class NoIndexHeaderFilter implements Filter {

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        ((HttpServletResponse) response).setHeader("X-Robots-Tag", "noindex, nofollow");
        chain.doFilter(request, response);
    }
}
