package com.example.recordshop.web.admin;

import com.example.recordshop.domain.inventory.ConditionType;
import com.example.recordshop.domain.inventory.GoldmineGrade;

import java.math.BigDecimal;

/** POST /admin/listings のフォームバックオブジェクト。 */
public class ListingForm {

    private String pressingId = "";
    private ConditionType conditionType = ConditionType.NEW;
    private BigDecimal priceAmount;
    private String priceCurrency = "JPY";
    private Integer initialStock = 1;
    private GoldmineGrade vinylGrade;
    private GoldmineGrade sleeveGrade;
    private String sellerNote = "";

    public String getPressingId() {
        return pressingId;
    }

    public void setPressingId(String pressingId) {
        this.pressingId = pressingId;
    }

    public ConditionType getConditionType() {
        return conditionType;
    }

    public void setConditionType(ConditionType conditionType) {
        this.conditionType = conditionType;
    }

    public BigDecimal getPriceAmount() {
        return priceAmount;
    }

    public void setPriceAmount(BigDecimal priceAmount) {
        this.priceAmount = priceAmount;
    }

    public String getPriceCurrency() {
        return priceCurrency;
    }

    public void setPriceCurrency(String priceCurrency) {
        this.priceCurrency = priceCurrency;
    }

    public Integer getInitialStock() {
        return initialStock;
    }

    public void setInitialStock(Integer initialStock) {
        this.initialStock = initialStock;
    }

    public GoldmineGrade getVinylGrade() {
        return vinylGrade;
    }

    public void setVinylGrade(GoldmineGrade vinylGrade) {
        this.vinylGrade = vinylGrade;
    }

    public GoldmineGrade getSleeveGrade() {
        return sleeveGrade;
    }

    public void setSleeveGrade(GoldmineGrade sleeveGrade) {
        this.sleeveGrade = sleeveGrade;
    }

    public String getSellerNote() {
        return sellerNote;
    }

    public void setSellerNote(String sellerNote) {
        this.sellerNote = sellerNote;
    }
}
