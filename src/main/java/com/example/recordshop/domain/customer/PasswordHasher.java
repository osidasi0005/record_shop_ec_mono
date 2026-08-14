package com.example.recordshop.domain.customer;

/**
 * パスワードのハッシュ化・照合を行うポート(インターフェースのみ)。
 *
 * <p>ドメイン層を暗号ライブラリ(Spring Security の {@code PasswordEncoder} 等)に
 * 依存させないための抽象化。実装はインフラ層(security パッケージ)に置く。
 *
 * <p>この比較実験プロジェクトでは、永続化技術(JPA/MyBatis)の比較が目的のため、
 * 実際のBCrypt実装(Spring Security依存)は移植していない。テストでは
 * record-shop-ec-domain と同様、フェイク実装で代用する。
 */
public interface PasswordHasher {

    String hash(String rawPassword);

    boolean matches(String rawPassword, String hash);
}
