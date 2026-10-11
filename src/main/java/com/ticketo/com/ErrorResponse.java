package com.ticketo.com;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 모든 오류 응답의 공통 형식. Spring 기본 오류 응답의 필드(timestamp, status, error, path)를 유지하고
 * 사용자에게 보여줄 message와, 검증 실패 시 필드별 사유(fieldErrors)를 더한다.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(
        LocalDateTime timestamp,
        int status,
        String error,
        String message,
        String path,
        List<FieldError> fieldErrors
) {

    public record FieldError(String field, String message) {
    }
}
