package com.example.recordshop.domain.customer;

/**
 * メール送信に失敗した場合の例外({@link EmailSender} ポートの契約)。
 *
 * <p>アダプタ実装(SES等)固有の例外をそのまま伝播させると、ドメイン層・Web層が
 * 特定のSDKに依存してしまうため、このドメイン例外に包み直す。
 */
public class EmailDeliveryException extends RuntimeException {

    public EmailDeliveryException(String message, Throwable cause) {
        super(message, cause);
    }
}
