package com.example.recordshop.web.admin;

/** GET/POST /admin/releases/new のフォームバックオブジェクト。 */
public class ReleaseForm {

    private String title = "";
    private String artistName = "";
    /** カンマ区切りで入力してもらい、コントローラー側でSetに変換する。 */
    private String genres = "";
    private int originalReleaseYear;
    private String artworkUrl = "";

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getArtistName() {
        return artistName;
    }

    public void setArtistName(String artistName) {
        this.artistName = artistName;
    }

    public String getGenres() {
        return genres;
    }

    public void setGenres(String genres) {
        this.genres = genres;
    }

    public int getOriginalReleaseYear() {
        return originalReleaseYear;
    }

    public void setOriginalReleaseYear(int originalReleaseYear) {
        this.originalReleaseYear = originalReleaseYear;
    }

    public String getArtworkUrl() {
        return artworkUrl;
    }

    public void setArtworkUrl(String artworkUrl) {
        this.artworkUrl = artworkUrl;
    }
}
