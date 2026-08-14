package com.example.recordshop.web.admin;

import com.example.recordshop.domain.catalog.MediaType;
import com.example.recordshop.domain.catalog.Speed;

/** POST /admin/releases/{releaseId}/pressings のフォームバックオブジェクト。 */
public class PressingForm {

    private String labelName = "";
    private String catalogNumber = "";
    private String country = "";
    private int pressYear;
    private String matrixRunout = "";
    private boolean reissue;
    private MediaType mediaType = MediaType.LP;
    private Speed speed = Speed.RPM_33;
    private int discCount = 1;

    public String getLabelName() {
        return labelName;
    }

    public void setLabelName(String labelName) {
        this.labelName = labelName;
    }

    public String getCatalogNumber() {
        return catalogNumber;
    }

    public void setCatalogNumber(String catalogNumber) {
        this.catalogNumber = catalogNumber;
    }

    public String getCountry() {
        return country;
    }

    public void setCountry(String country) {
        this.country = country;
    }

    public int getPressYear() {
        return pressYear;
    }

    public void setPressYear(int pressYear) {
        this.pressYear = pressYear;
    }

    public String getMatrixRunout() {
        return matrixRunout;
    }

    public void setMatrixRunout(String matrixRunout) {
        this.matrixRunout = matrixRunout;
    }

    public boolean isReissue() {
        return reissue;
    }

    public void setReissue(boolean reissue) {
        this.reissue = reissue;
    }

    public MediaType getMediaType() {
        return mediaType;
    }

    public void setMediaType(MediaType mediaType) {
        this.mediaType = mediaType;
    }

    public Speed getSpeed() {
        return speed;
    }

    public void setSpeed(Speed speed) {
        this.speed = speed;
    }

    public int getDiscCount() {
        return discCount;
    }

    public void setDiscCount(int discCount) {
        this.discCount = discCount;
    }
}
