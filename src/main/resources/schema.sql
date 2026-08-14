-- record-shop-ec-mybatis の比較実験用スキーマ。
-- 対象はCatalog(releases/pressings/release_genres)とInventory(listings)のみ。
-- 比較実験用のため、起動のたびに作り直す(DROP→CREATE)。データの永続化は目的外。

DROP TABLE IF EXISTS payments CASCADE;
DROP TABLE IF EXISTS order_lines CASCADE;
DROP TABLE IF EXISTS orders CASCADE;
DROP TABLE IF EXISTS listings CASCADE;
DROP TABLE IF EXISTS release_genres CASCADE;
DROP TABLE IF EXISTS pressings CASCADE;
DROP TABLE IF EXISTS releases CASCADE;
DROP TABLE IF EXISTS customers CASCADE;

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

-- customer_idはCustomer(未移植の別集約)への参照のため、listingsのpressing_idと同様
-- ただのUUID列として持たせる(FK・JOINは張らない)。住所はJPA版の@Embeddedと同じく列展開する。
CREATE TABLE orders (
    id                     UUID           PRIMARY KEY,
    customer_id            UUID           NOT NULL,
    status                 VARCHAR(20)    NOT NULL,
    placed_at              TIMESTAMP      NOT NULL,
    ship_recipient_name    VARCHAR(255)   NOT NULL,
    ship_postal_code       VARCHAR(20)    NOT NULL,
    ship_prefecture        VARCHAR(50)    NOT NULL,
    ship_city              VARCHAR(100)   NOT NULL,
    ship_address_line      VARCHAR(255)   NOT NULL,
    ship_country           VARCHAR(2)     NOT NULL,
    bill_recipient_name    VARCHAR(255)   NOT NULL,
    bill_postal_code       VARCHAR(20)    NOT NULL,
    bill_prefecture        VARCHAR(50)    NOT NULL,
    bill_city              VARCHAR(100)   NOT NULL,
    bill_address_line      VARCHAR(255)   NOT NULL,
    bill_country           VARCHAR(2)     NOT NULL
);

-- JPA版の@ElementCollection(order_lines)に相当。PressingSnapshotは確定時点の複製であり
-- releases/pressingsとは無関係な独立した非正規化データなので、列をそのまま展開する。
CREATE TABLE order_lines (
    order_id             UUID           NOT NULL REFERENCES orders (id) ON DELETE CASCADE,
    listing_id           UUID           NOT NULL,
    release_title        VARCHAR(255)   NOT NULL,
    artist_name          VARCHAR(255)   NOT NULL,
    label_name           VARCHAR(255)   NOT NULL,
    catalog_number       VARCHAR(100)   NOT NULL,
    pressing_country     VARCHAR(2)     NOT NULL,
    press_year           INTEGER        NOT NULL,
    media_type           VARCHAR(20)    NOT NULL,
    speed                VARCHAR(20)    NOT NULL,
    disc_count           INTEGER        NOT NULL,
    condition_type       VARCHAR(10)    NOT NULL,
    vinyl_grade          VARCHAR(20),
    sleeve_grade         VARCHAR(20),
    unit_price_amount    NUMERIC(12, 2) NOT NULL,
    unit_price_currency  VARCHAR(3)     NOT NULL,
    quantity             INTEGER        NOT NULL
);

-- order_idはOrder(別集約)への参照のため、listings.pressing_idと同様ただのUUID列として持たせる。
CREATE TABLE payments (
    id               UUID           PRIMARY KEY,
    order_id         UUID           NOT NULL,
    amount_amount    NUMERIC(12, 2) NOT NULL,
    amount_currency  VARCHAR(3)     NOT NULL,
    method           VARCHAR(20)    NOT NULL,
    status           VARCHAR(20)    NOT NULL,
    captured_at      TIMESTAMP
);

-- emailはCustomerRegistrationServiceが守るべき「システム全体で一意」という不変条件を、
-- DB制約としても二重に保証しておく。
CREATE TABLE customers (
    id             UUID         PRIMARY KEY,
    email          VARCHAR(255) NOT NULL UNIQUE,
    password_hash  VARCHAR(255) NOT NULL,
    display_name   VARCHAR(255) NOT NULL,
    role           VARCHAR(20)  NOT NULL,
    registered_at  TIMESTAMP    NOT NULL
);
