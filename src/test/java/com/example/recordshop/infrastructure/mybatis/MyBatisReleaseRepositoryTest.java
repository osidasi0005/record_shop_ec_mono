package com.example.recordshop.infrastructure.mybatis;

import com.example.recordshop.domain.catalog.Format;
import com.example.recordshop.domain.catalog.MediaType;
import com.example.recordshop.domain.catalog.Pressing;
import com.example.recordshop.domain.catalog.Release;
import com.example.recordshop.domain.catalog.ReleaseId;
import com.example.recordshop.domain.catalog.Speed;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * MyBatisアダプタの結合テスト。JPA版({@code JpaReleaseRepositoryTest})と同じ観点を検証しつつ、
 * {@link ReleaseMapper}が genres/pressings を正しく組み立てられているかを重点的に確認する。
 */
@SpringBootTest
@Transactional
class MyBatisReleaseRepositoryTest {

    @Autowired
    private MyBatisReleaseRepository repository;

    private Release kindOfBlue() {
        Release release = Release.register(ReleaseId.generate(), "Kind of Blue", "Miles Davis",
                Set.of("Jazz", "Modal Jazz"), 1959);
        release.addPressing("Columbia", "CL 1355", "US", 1959, "XSM", false,
                Format.vinyl(MediaType.LP, Speed.RPM_33, 1));
        return release;
    }

    @Test
    void save_findById_で保存した集約が同じ内容で復元できる() {
        Release release = kindOfBlue();

        repository.save(release);
        Release found = repository.findById(release.releaseId()).orElseThrow();

        assertThat(found.title()).isEqualTo("Kind of Blue");
        assertThat(found.genres()).containsExactlyInAnyOrder("Jazz", "Modal Jazz");
        assertThat(found.pressings()).hasSize(1);
        Pressing pressing = found.pressings().get(0);
        assertThat(pressing.catalogNumber()).isEqualTo("CL 1355");
        assertThat(pressing.format().mediaType()).isEqualTo(MediaType.LP);
    }

    @Test
    void findByPressingId_でPressingを含むReleaseを検索できる() {
        Release release = kindOfBlue();
        repository.save(release);
        var pressingId = release.pressings().get(0).pressingId();

        Release found = repository.findByPressingId(pressingId).orElseThrow();

        assertThat(found.releaseId()).isEqualTo(release.releaseId());
    }

    @Test
    void findAll_で複数Releaseのgenres_pressingsが取り違えなく組み立てられる() {
        Release release1 = kindOfBlue();
        Release release2 = Release.register(ReleaseId.generate(), "A Love Supreme", "John Coltrane",
                Set.of("Jazz", "Spiritual Jazz"), 1965);
        release2.addPressing("Impulse!", "A-77", "US", 1965, null, false,
                Format.vinyl(MediaType.LP, Speed.RPM_33, 1));
        repository.save(release1);
        repository.save(release2);

        var all = repository.findAll();

        assertThat(all).hasSize(2);
        Release foundRelease2 = all.stream()
                .filter(r -> r.releaseId().equals(release2.releaseId()))
                .findFirst().orElseThrow();
        // release1のgenresがrelease2に混ざっていない(グルーピングが正しい)ことを確認
        assertThat(foundRelease2.genres()).containsExactlyInAnyOrder("Jazz", "Spiritual Jazz");
        assertThat(foundRelease2.pressings()).hasSize(1);
        assertThat(foundRelease2.pressings().get(0).catalogNumber()).isEqualTo("A-77");
    }
}
