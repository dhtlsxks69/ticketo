package com.ticketo.ticket.vo;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 공연(티켓) VO. 요청/응답 데이터 전달과 DB 조회 결과 매핑에 함께 사용한다.
 */
@Getter
@Setter
@NoArgsConstructor
public class TicketVO {

    private Long id;

    /** 공연명 */
    @NotBlank
    @Size(max = 200)
    private String title;

    /** 공연장 */
    @NotBlank
    @Size(max = 200)
    private String venue;

    /** 공연 일시 */
    @NotNull
    private LocalDateTime eventAt;

    /** 티켓 1매 가격(원) */
    @NotNull
    @Min(0)
    private Integer price;

    /** 총 발행 수량 */
    @NotNull
    @Min(1)
    private Integer totalQuantity;

    /** 잔여 수량. 서버가 관리하므로 요청 값은 무시하고 응답에만 포함한다. */
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private Integer remainingQuantity;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
