package com.example.recordshop.domain.catalog;

import com.example.recordshop.domain.shared.InvariantViolationException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * 作品(Release)。Catalog コンテキストの集約ルート。
 * {@link Pressing} を子エンティティとして保持し、プレス版の追加とその一意性を一手に引き受ける。
 *
 * <p><b>不変条件</b>
 * <ul>
 *   <li>同一 Release 内で Pressing は「品番 + 製造国 + 製造年」の組で一意</li>
 *   <li>Pressing は Release を経由してのみ追加できる(直接 new はできない)</li>
 * </ul>
 */
public final class Release {

    private final ReleaseId releaseId;
    private final String title;
    private final String artistName;
    private final Set<String> genres;
    private final int originalReleaseYear;
    private final List<Pressing> pressings = new ArrayList<>();

    private Release(ReleaseId releaseId, String title, String artistName,
                     Set<String> genres, int originalReleaseYear) {
        this.releaseId = Objects.requireNonNull(releaseId, "releaseId must not be null");
        this.title = requireNonBlank(title, "title");
        this.artistName = requireNonBlank(artistName, "artistName");
        this.genres = Collections.unmodifiableSet(new LinkedHashSet<>(genres));
        this.originalReleaseYear = originalReleaseYear;
    }

    public static Release register(ReleaseId releaseId, String title, String artistName,
                                    Set<String> genres, int originalReleaseYear) {
        return new Release(releaseId, title, artistName, genres, originalReleaseYear);
    }

    /**
     * 永続化層からの再構築用ファクトリ。{@link #addPressing} の一意性チェックや ID 新規発行を経由せず、
     * 既に正しいと分かっている(=DBに保存済みの)Pressing 一覧をそのまま復元する。
     * リポジトリ実装(インフラ層)から呼ばれる想定。
     */
    public static Release reconstitute(ReleaseId releaseId, String title, String artistName,
                                        Set<String> genres, int originalReleaseYear,
                                        List<Pressing> existingPressings) {
        Release release = new Release(releaseId, title, artistName, genres, originalReleaseYear);
        release.pressings.addAll(existingPressings);
        return release;
    }

    private static String requireNonBlank(String value, String field) {
        Objects.requireNonNull(value, field + " must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }

    /**
     * プレス版を追加する。品番 + 製造国 + 製造年が既存のいずれかと重複する場合は拒否する。
     */
    public Pressing addPressing(String labelName, String catalogNumber, String country,
                                 int pressYear, String matrixRunout, boolean reissue, Format format) {
        Pressing candidate = new Pressing(PressingId.generate(), labelName, catalogNumber, country,
                pressYear, matrixRunout, reissue, format);
        boolean duplicate = pressings.stream()
                .anyMatch(p -> p.identityKey().equals(candidate.identityKey()));
        if (duplicate) {
            throw new InvariantViolationException(
                    "同一 Release 内に品番・製造国・製造年が重複する Pressing は登録できません: "
                            + candidate.catalogNumber() + " / " + candidate.country() + " / " + candidate.pressYear());
        }
        pressings.add(candidate);
        return candidate;
    }

    public Optional<Pressing> findPressing(PressingId pressingId) {
        return pressings.stream().filter(p -> p.pressingId().equals(pressingId)).findFirst();
    }

    public List<Pressing> pressings() {
        return Collections.unmodifiableList(pressings);
    }

    public ReleaseId releaseId() {
        return releaseId;
    }

    public String title() {
        return title;
    }

    public String artistName() {
        return artistName;
    }

    public Set<String> genres() {
        return genres;
    }

    public int originalReleaseYear() {
        return originalReleaseYear;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Release other)) return false;
        return releaseId.equals(other.releaseId);
    }

    @Override
    public int hashCode() {
        return releaseId.hashCode();
    }

    @Override
    public String toString() {
        return "Release{%s - %s, pressings=%d}".formatted(artistName, title, pressings.size());
    }
}
