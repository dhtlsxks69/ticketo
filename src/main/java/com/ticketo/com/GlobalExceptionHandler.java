package com.ticketo.com;

import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 전역 예외 처리. 컨트롤러에서 처리되지 않은 예외를 적절한 상태 코드로 변환한다.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 데드락/락 대기 초과로 DB가 트랜잭션을 롤백한 경우. 서버 오류(500)가 아니라
     * 일시적인 충돌이므로 503으로 응답해 재시도를 안내한다. (이미 롤백되어 데이터는 변경되지 않았다)
     */
    @ExceptionHandler(PessimisticLockingFailureException.class)
    public ProblemDetail handleLockingFailure(PessimisticLockingFailureException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE,
                "요청이 몰려 처리하지 못했습니다. 잠시 후 다시 시도해주세요.");
    }
}
