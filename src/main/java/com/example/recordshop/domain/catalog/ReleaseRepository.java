package com.example.recordshop.domain.catalog;

import java.util.List;
import java.util.Optional;

/**
 * Release 集約の永続化ポート(インターフェースのみ)。
 * 実装(JPA/JDBC など)はインフラ層で提供する。
 */
public interface ReleaseRepository {

    void save(Release release);

    Optional<Release> findById(ReleaseId releaseId);

    /** カタログ一覧画面用。「歩く骨格」フェーズではページングをせず全件返す。 */
    List<Release> findAll();

    /**
     * 指定した PressingId を含む Release を検索する。
     * Pressing は Release の子エンティティであり単独では永続化されないため、
     * この検索メソッドを通じて「Pressing を含む Release」を引き当てる。
     */
    Optional<Release> findByPressingId(PressingId pressingId);
}
