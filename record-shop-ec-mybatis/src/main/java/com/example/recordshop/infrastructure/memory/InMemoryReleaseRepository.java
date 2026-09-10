package com.example.recordshop.infrastructure.memory;

import com.example.recordshop.domain.catalog.PressingId;
import com.example.recordshop.domain.catalog.Release;
import com.example.recordshop.domain.catalog.ReleaseId;
import com.example.recordshop.domain.catalog.ReleaseRepository;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * {@link ReleaseRepository} のインメモリ実装。
 * 本番の永続化(MyBatis など)を用意する前に、ドメイン層を単体テスト・デモで動かすためのフェイク。
 */
public final class InMemoryReleaseRepository implements ReleaseRepository {

    private final Map<ReleaseId, Release> store = new LinkedHashMap<>();

    @Override
    public void save(Release release) {
        store.put(release.releaseId(), release);
    }

    @Override
    public Optional<Release> findById(ReleaseId releaseId) {
        return Optional.ofNullable(store.get(releaseId));
    }

    @Override
    public Optional<Release> findByPressingId(PressingId pressingId) {
        return store.values().stream()
                .filter(release -> release.findPressing(pressingId).isPresent())
                .findFirst();
    }

    @Override
    public List<Release> findAll() {
        return List.copyOf(store.values());
    }
}
