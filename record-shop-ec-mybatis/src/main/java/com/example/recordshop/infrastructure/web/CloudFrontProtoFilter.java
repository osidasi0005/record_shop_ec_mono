package com.example.recordshop.infrastructure.web;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletResponseWrapper;

import java.io.IOException;

/**
 * CloudFront(HTTPS) → ALB(HTTP) → Fargate という構成では、標準の {@code X-Forwarded-Proto}
 * ヘッダーを信頼できない。ALBは自分自身への接続プロトコル(常にHTTP、CloudFrontとの間はHTTPのまま
 * 運用しているため)でこのヘッダーを毎回上書きしてしまい、CloudFrontが元々付与した値は握りつぶされる。
 * <p>
 * 代わりに、ALBが関知しないカスタムヘッダー {@value #HEADER}(CloudFront Functionで
 * viewer-request時に付与)を見て、リクエストの scheme/isSecure と、リダイレクト時の
 * Locationヘッダーを直接 https:// で組み立てる。
 * <p>
 * 注意: {@link HttpServletRequestWrapper#getScheme()} を上書きするだけでは
 * {@link HttpServletResponse#sendRedirect(String)} のLocationヘッダーは直らない。
 * Tomcatは相対パス→絶対URL変換時にコンテナ内部が保持する生のRequestオブジェクト(常にhttp)を
 * 参照するため、フィルタチェーンに渡したラッパーは見ない。そのためResponse側もラップし、
 * sendRedirect自体を上書きしてLocationヘッダーを直接組み立てる。
 */
public class CloudFrontProtoFilter implements Filter {

    static final String HEADER = "X-Forwarded-Proto-Cf";

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        if ("https".equalsIgnoreCase(httpRequest.getHeader(HEADER))) {
            HttpServletRequest wrappedRequest = new HttpServletRequestWrapper(httpRequest) {
                @Override
                public String getScheme() {
                    return "https";
                }

                @Override
                public boolean isSecure() {
                    return true;
                }

                @Override
                public int getServerPort() {
                    return 443;
                }
            };
            HttpServletResponse wrappedResponse = new HttpsRedirectResponseWrapper(httpResponse, wrappedRequest);
            chain.doFilter(wrappedRequest, wrappedResponse);
        } else {
            chain.doFilter(request, response);
        }
    }

    private static final class HttpsRedirectResponseWrapper extends HttpServletResponseWrapper {
        private final HttpServletRequest request;

        HttpsRedirectResponseWrapper(HttpServletResponse response, HttpServletRequest request) {
            super(response);
            this.request = request;
        }

        @Override
        public void sendRedirect(String location) throws IOException {
            super.sendRedirect(toHttpsAbsoluteUrl(location));
        }

        private String toHttpsAbsoluteUrl(String location) {
            if (location.startsWith("https://")) {
                return location;
            }
            if (location.startsWith("http://")) {
                return "https://" + location.substring("http://".length());
            }
            String host = request.getHeader("Host");
            if (host == null) {
                host = request.getServerName();
            }
            String path = location.startsWith("/") ? location : request.getContextPath() + "/" + location;
            return "https://" + host + path;
        }
    }
}
