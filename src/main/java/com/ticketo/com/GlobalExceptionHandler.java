package com.ticketo.com;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.TypeMismatchException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 전역 예외 처리. 모든 오류를 {@link ErrorResponse} 형식으로 통일한다.
 *
 * Spring MVC가 던지는 표준 예외(400/404/405/415 등)와 {@code ResponseStatusException}은
 * {@link ResponseEntityExceptionHandler}가 모아서 {@link #handleExceptionInternal}로 넘겨준다.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    /** {@code @Valid} 검증 실패. 필드별 사유를 함께 내려준다. */
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        List<ErrorResponse.FieldError> fieldErrors = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> new ErrorResponse.FieldError(e.getField(), e.getDefaultMessage()))
                .toList();
        return build(status, "입력값이 올바르지 않습니다.", fieldErrors, request, headers);
    }

    /** JSON 문법 오류, 타입 불일치 등으로 요청 본문을 읽지 못한 경우 */
    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(HttpMessageNotReadableException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        return build(status, "요청 본문을 읽을 수 없습니다. JSON 형식과 값의 타입을 확인해주세요.", null, request, headers);
    }

    @Override
    protected ResponseEntity<Object> handleMissingServletRequestParameter(MissingServletRequestParameterException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        return build(status, "필수 파라미터가 없습니다. " + ex.getParameterName(), null, request, headers);
    }

    /** 경로 변수/파라미터 타입 변환 실패. 예) /api/tickets/abc */
    @Override
    protected ResponseEntity<Object> handleTypeMismatch(TypeMismatchException ex, HttpHeaders headers,
            HttpStatusCode status, WebRequest request) {
        String name = ex instanceof MethodArgumentTypeMismatchException mismatch
                ? mismatch.getName()
                : ex.getPropertyName();
        return build(status, "값의 형식이 올바르지 않습니다. " + name, null, request, headers);
    }

    /** 서비스에서 던진 ResponseStatusException(404, 409 등). 지정한 사유를 그대로 메시지로 사용한다. */
    @Override
    protected ResponseEntity<Object> handleErrorResponseException(ErrorResponseException ex, HttpHeaders headers,
            HttpStatusCode status, WebRequest request) {
        String message = ex instanceof ResponseStatusException statusException && statusException.getReason() != null
                ? statusException.getReason()
                : reasonPhrase(status);
        return build(status, message, null, request, headers);
    }

    /** 위에서 따로 처리하지 않은 Spring MVC 표준 예외 */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception ex, Object body, HttpHeaders headers,
            HttpStatusCode statusCode, WebRequest request) {
        String message = body instanceof ProblemDetail problem && problem.getDetail() != null
                ? problem.getDetail()
                : reasonPhrase(statusCode);
        return build(statusCode, message, null, request, headers);
    }

    /**
     * 데드락/락 대기 초과로 DB가 트랜잭션을 롤백한 경우. 서버 오류(500)가 아니라
     * 일시적인 충돌이므로 503으로 응답해 재시도를 안내한다. (이미 롤백되어 데이터는 변경되지 않았다)
     */
    @ExceptionHandler(PessimisticLockingFailureException.class)
    public ResponseEntity<Object> handleLockingFailure(PessimisticLockingFailureException e, WebRequest request) {
        log.warn("락 충돌로 요청 실패: {}", e.getMessage());
        return build(HttpStatus.SERVICE_UNAVAILABLE, "요청이 몰려 처리하지 못했습니다. 잠시 후 다시 시도해주세요.",
                null, request, null);
    }

    /** 예상하지 못한 예외. 내부 정보는 노출하지 않고 로그에만 남긴다. */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Object> handleUnexpected(Exception e, WebRequest request) {
        log.error("처리되지 않은 예외", e);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "서버 오류가 발생했습니다.", null, request, null);
    }

    private ResponseEntity<Object> build(HttpStatusCode status, String message,
            List<ErrorResponse.FieldError> fieldErrors, WebRequest request, HttpHeaders headers) {
        String path = request instanceof ServletWebRequest servletRequest
                ? servletRequest.getRequest().getRequestURI()
                : null;
        ErrorResponse body = new ErrorResponse(LocalDateTime.now(), status.value(), reasonPhrase(status),
                message, path, fieldErrors);
        return ResponseEntity.status(status).headers(headers).body(body);
    }

    private String reasonPhrase(HttpStatusCode status) {
        HttpStatus resolved = HttpStatus.resolve(status.value());
        return resolved != null ? resolved.getReasonPhrase() : String.valueOf(status.value());
    }
}
