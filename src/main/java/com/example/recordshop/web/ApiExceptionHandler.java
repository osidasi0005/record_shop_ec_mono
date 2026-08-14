package com.example.recordshop.web;

import com.example.recordshop.domain.shared.IllegalStateTransitionException;
import com.example.recordshop.domain.shared.InvariantViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * ドメイン層の例外を適切な HTTP ステータスへ変換する。
 *
 * <p>ドメインクラス自体は HTTP を一切知らない(Spring非依存)ので、
 * その橋渡しをこのクラスに閉じ込める。
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    /** 不変条件違反・不正な引数 → 400 Bad Request */
    @ExceptionHandler({InvariantViolationException.class, IllegalArgumentException.class})
    public ResponseEntity<ErrorResponse> handleBadRequest(RuntimeException e) {
        return ResponseEntity.badRequest().body(new ErrorResponse(e.getMessage()));
    }

    /** 許可されていない状態遷移 → 409 Conflict */
    @ExceptionHandler(IllegalStateTransitionException.class)
    public ResponseEntity<ErrorResponse> handleConflict(IllegalStateTransitionException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new ErrorResponse(e.getMessage()));
    }

    /**
     * 楽観ロック競合(同じ Listing 等を複数リクエストが同時に更新しようとした) → 409 Conflict。
     * クライアント側は「売り切れました。もう一度お試しください」等の案内をして再試行を促す想定。
     *
     * <p>JPA版は{@code ObjectOptimisticLockingFailureException}(Hibernate固有のサブクラス)を
     * 捕まえるが、{@link com.example.recordshop.infrastructure.mybatis.MyBatisListingRepository}は
     * その基底クラスである{@link OptimisticLockingFailureException}を直接投げる実装のため、
     * こちらを捕まえるよう変更している。基底クラスを使う分、実はこちらの方が特定の永続化技術に
     * 依存しない書き方になっている。
     */
    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<ErrorResponse> handleOptimisticLock(OptimisticLockingFailureException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ErrorResponse("他の注文と同時に処理されたため確定できませんでした。もう一度お試しください。"));
    }
}
