package com.example.recordshop.web.customer;

/**
 * GET/POST /register のフォームバックオブジェクト。
 * Thymeleafのth:fieldでの双方向バインディングのため、record ではなく可変クラスにしている。
 */
public class RegisterForm {

    private String email = "";
    private String password = "";
    private String displayName = "";

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }
}
