package com.example.recordshop.web.admin;

/**
 * POST /admin/releases/{releaseId}/artwork および
 * POST /admin/releases/{releaseId}/pressings/{pressingId}/artwork のフォームバックオブジェクト。
 * Release・Pressing双方のアートワーク設定・変更で共用する。
 */
public class ArtworkForm {

    private String artworkUrl = "";

    public String getArtworkUrl() {
        return artworkUrl;
    }

    public void setArtworkUrl(String artworkUrl) {
        this.artworkUrl = artworkUrl;
    }
}
