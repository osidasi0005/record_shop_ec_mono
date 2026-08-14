-- record-shop-ec-mybatis の比較実験用スキーマ。
-- 対象はCatalog(releases/pressings/release_genres)とInventory(listings)のみ。
-- 比較実験用のため、起動のたびに作り直す(DROP→CREATE)。データの永続化は目的外。

DROP TABLE IF EXISTS listings CASCADE;
DROP TABLE IF EXISTS release_genres CASCADE;
DROP TABLE IF EXISTS pressings CASCADE;
DROP TABLE IF EXISTS releases CASCADE;

CREATE TABLE releases (
    id                    UUID PRIMARY KEY,
    title                 VARCHAR(255) NOT NULL,
    artist_name           VARCHAR(255) NOT NULL,
    original_release_year INTEGER      NOT NULL
);

-- JPA版の@ElementCollectionに相当。ジャンルはReleaseの値の集合であり独立エンティティではない。
CREATE TABLE release_genres (
    release_id UUID        NOT NULL REFERENCES releases (id) ON DELETE CASCADE,
    genre      VARCHAR(50) NOT NULL,
    PRIMARY KEY (release_id, genre)
);

-- JPA版の@OneToMany(cascade=ALL, orphanRemoval=true)に相当。同一集約内の親子。
CREATE TABLE pressings (
    id             UUID PRIMARY KEY,
    release_id     UUID         NOT NULL REFERENCES releases (id) ON DELETE CASCADE,
    label_name     VARCHAR(255) NOT NULL,
    catalog_number VARCHAR(100) NOT NULL,
    country        VARCHAR(2)   NOT NULL,
    press_year     INTEGER      NOT NULL,
    matrix_runout  VARCHAR(255),
    reissue        BOOLEAN      NOT NULL,
    media_type     VARCHAR(20)  NOT NULL,
    speed          VARCHAR(20)  NOT NULL,
    disc_count     INTEGER      NOT NULL,
    CONSTRAINT uk_pressing_identity UNIQUE (release_id, catalog_number, country, press_year)
);

-- versionはMyBatisでは自動付与されないため、UPDATE文側で明示的にWHERE version = ?と
-- version = version + 1を書いて楽観ロックを手動実装する(JPAの@Versionに相当)。
CREATE TABLE listings (
    id              UUID           PRIMARY KEY,
    pressing_id     UUID           NOT NULL,
    condition_type  VARCHAR(10)    NOT NULL,
    price_amount    NUMERIC(12, 2) NOT NULL,
    price_currency  VARCHAR(3)     NOT NULL,
    status          VARCHAR(20)    NOT NULL,
    stock_quantity  INTEGER,
    vinyl_grade     VARCHAR(20),
    sleeve_grade    VARCHAR(20),
    seller_note     VARCHAR(1000),
    version         BIGINT         NOT NULL DEFAULT 0
);
