package com.ticketo.reservation.vo;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 예매 VO. 요청/응답 데이터 전달과 DB 조회 결과 매핑에 함께 사용한다.
 */
@Getter
@Setter
@NoArgsConstructor
public class ReservationVO {

    public static final String STATUS_RESERVED = "RESERVED";
    public static final String STATUS_CANCELED = "CANCELED";

    private Long id;

    /** 예매한 회원. JWT 도입 전까지는 요청 값으로 받는다. */
    @NotNull
    private Long memberId;

    /** 예매 대상 공연(ticket.id) */
    @NotNull
    private Long ticketId;

    /** 예매 수량 */
    @NotNull
    @Min(1)
    private Integer quantity;

    /** RESERVED / CANCELED. 서버가 관리하므로 요청 값은 무시하고 응답에만 포함한다. */
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private String status;

    private LocalDateTime createdAt;

    /** 취소 일시. 취소 전에는 null. */
    private LocalDateTime canceledAt;
}
