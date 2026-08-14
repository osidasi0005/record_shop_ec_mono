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
                Set.of("Jazz"), 1959);
    }

    private Format lpFormat() {
        return Format.vinyl(MediaType.LP, Speed.RPM_33, 1);
    }

    @Test
    void addPressing_複数プレス版を追加できる() {
        Release release = kindOfBlue();

        Pressing original = release.addPressing("Columbia", "CL 1355", "US", 1959, "XSM", false, lpFormat());
        Pressing reissue = release.addPressing("Music On Vinyl", "MOVLP1183", "EU", 2020, null, true, lpFormat());

        assertEquals(2, release.pressings().size());
        assertTrue(release.findPressing(original.pressingId()).isPresent());
        assertTrue(release.findPressing(reissue.pressingId()).isPresent());
    }

    @Test
    void addPressing_品番と製造国と製造年が同じPressingは拒否される() {
        Release release = kindOfBlue();
        release.addPressing("Columbia", "CL 1355", "US", 1959, "XSM-A", false, lpFormat());

        assertThrows(InvariantViolationException.class, () ->
                release.addPressing("Columbia", "CL 1355", "US", 1959, "XSM-B", false, lpFormat()));
    }

    @Test
    void addPressing_製造年が違えば同じ品番でも登録できる() {
        Release release = kindOfBlue();
        release.addPressing("Columbia", "CL 1355", "US", 1959, "XSM-A", false, lpFormat());

        Pressing laterPressing = release.addPressing("Columbia", "CL 1355", "US", 1963, "XSM-C", false, lpFormat());

        assertEquals(2, release.pressings().size());
        assertEquals(1963, laterPressing.pressYear());
    }
}
