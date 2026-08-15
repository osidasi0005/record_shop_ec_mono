package com.example.recordshop.web.customer;

/**
 * GET/POST /register/confirm のフォームバックオブジェクト。
 * Thymeleafのth:fieldでの双方向バインディングのため、record ではなく可変クラスにしている。
 */
public class EmailVerificationForm {

    private String email = "";
    private String code = "";

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }
}
