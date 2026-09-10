package com.example.recordshop.domain.customer;

/**
 * パスワードのハッシュ化・照合を行うポート(インターフェースのみ)。
 *
 * <p>ドメイン層を暗号ライブラリ(Spring Security の {@code PasswordEncoder} 等)に
 * 依存させないための抽象化。実装はインフラ層(security パッケージ)に置く。
 *
 * <p>本番実装は{@code BCryptPasswordHasher}(Spring Security依存)。テストでは
 * フェイク実装で代用する。
 */
public interface PasswordHasher {

    String hash(String rawPassword);

    boolean matches(String rawPassword, String hash);
}
