package com.example.recordshop.domain.catalog;

import com.example.recordshop.domain.shared.InvariantViolationException;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReleaseTest {

    private Release kindOfBlue() {
        return Release.register(ReleaseId.generate(), "Kind of Blue", "Miles Davis",
                Set.of("Jazz"), 1959, null);
    }

    private Format lpFormat() {
        return Format.vinyl(MediaType.LP, Speed.RPM_33, 1);
    }

    @Test
    void addPressing_複数プレス版を追加できる() {
        Release release = kindOfBlue();

        Pressing original = release.addPressing("Columbia", "CL 1355", "US", 1959, "XSM", false, lpFormat(), null);
        Pressing reissue = release.addPressing("Music On Vinyl", "MOVLP1183", "EU", 2020, null, true, lpFormat(), null);

        assertEquals(2, release.pressings().size());
        assertTrue(release.findPressing(original.pressingId()).isPresent());
        assertTrue(release.findPressing(reissue.pressingId()).isPresent());
    }

    @Test
    void addPressing_品番と製造国と製造年が同じPressingは拒否される() {
        Release release = kindOfBlue();
        release.addPressing("Columbia", "CL 1355", "US", 1959, "XSM-A", false, lpFormat(), null);

        assertThrows(InvariantViolationException.class, () ->
                release.addPressing("Columbia", "CL 1355", "US", 1959, "XSM-B", false, lpFormat(), null));
    }

    @Test
    void addPressing_製造年が違えば同じ品番でも登録できる() {
        Release release = kindOfBlue();
        release.addPressing("Columbia", "CL 1355", "US", 1959, "XSM-A", false, lpFormat(), null);

        Pressing laterPressing = release.addPressing("Columbia", "CL 1355", "US", 1963, "XSM-C", false, lpFormat(), null);

        assertEquals(2, release.pressings().size());
        assertEquals(1963, laterPressing.pressYear());
    }

    @Test
    void register_artworkUrlを指定して登録できる() {
        Release release = Release.register(ReleaseId.generate(), "Kind of Blue", "Miles Davis",
                Set.of("Jazz"), 1959, "https://example.com/kob.jpg");

        assertEquals("https://example.com/kob.jpg", release.artworkUrl());
    }

    @Test
    void register_artworkUrlが空文字の場合はnullとして扱われる() {
        Release release = Release.register(ReleaseId.generate(), "Kind of Blue", "Miles Davis",
                Set.of("Jazz"), 1959, "  ");

        assertEquals(null, release.artworkUrl());
    }

    @Test
    void changeArtworkUrl_登録後にアートワークを設定_変更できる() {
        Release release = kindOfBlue();
        assertEquals(null, release.artworkUrl());

        release.changeArtworkUrl("https://example.com/kob.jpg");
        assertEquals("https://example.com/kob.jpg", release.artworkUrl());

        release.changeArtworkUrl("https://example.com/kob-v2.jpg");
        assertEquals("https://example.com/kob-v2.jpg", release.artworkUrl());
    }

    @Test
    void changePressingArtworkUrl_指定したPressingのアートワークだけを変更できる() {
        Release release = kindOfBlue();
        Pressing pressing = release.addPressing("Columbia", "CL 1355", "US", 1959, "XSM", false, lpFormat(), null);

        release.changePressingArtworkUrl(pressing.pressingId(), "https://example.com/pressing.jpg");

        assertEquals("https://example.com/pressing.jpg",
                release.findPressing(pressing.pressingId()).orElseThrow().artworkUrl());
    }

    @Test
    void changePressingArtworkUrl_存在しないPressingIdを指定すると例外を投げる() {
        Release release = kindOfBlue();

        assertThrows(IllegalArgumentException.class, () ->
                release.changePressingArtworkUrl(PressingId.generate(), "https://example.com/x.jpg"));
    }
}
