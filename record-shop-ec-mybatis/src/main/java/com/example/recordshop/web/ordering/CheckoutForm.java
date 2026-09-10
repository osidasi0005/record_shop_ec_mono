package com.example.recordshop.web.ordering;

/**
 * GET/POST /checkout のフォームバックオブジェクト。
 *
 * <p>「歩く骨格」フェーズの簡易実装として、配送先と請求先を分けず単一の住所フォームにしている
 * (Orderドメイン自体は shippingAddress/billingAddress を独立して持てる設計だが、画面側で両方に
 * 同じ値を渡す)。
 */
public class CheckoutForm {

    private String recipientName = "";
    private String postalCode = "";
    private String prefecture = "";
    private String city = "";
    private String addressLine = "";
    private String country = "JP";

    public String getRecipientName() {
        return recipientName;
    }

    public void setRecipientName(String recipientName) {
        this.recipientName = recipientName;
    }

    public String getPostalCode() {
        return postalCode;
    }

    public void setPostalCode(String postalCode) {
        this.postalCode = postalCode;
    }

    public String getPrefecture() {
        return prefecture;
    }

    public void setPrefecture(String prefecture) {
        this.prefecture = prefecture;
    }

    public String getCity() {
        return city;
    }

    public void setCity(String city) {
        this.city = city;
    }

    public String getAddressLine() {
        return addressLine;
    }

    public void setAddressLine(String addressLine) {
        this.addressLine = addressLine;
    }

    public String getCountry() {
        return country;
    }

    public void setCountry(String country) {
        this.country = country;
    }
}
