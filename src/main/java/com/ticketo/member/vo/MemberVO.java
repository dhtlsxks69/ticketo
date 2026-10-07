package com.ticketo.member.vo;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 회원 VO. 요청/응답 데이터 전달과 DB 조회 결과 매핑에 함께 사용한다.
 */
@Getter
@Setter
@NoArgsConstructor
public class MemberVO {

    private Long id;

    @NotBlank
    @Email
    @Size(max = 255)
    private String email;

    /** 요청에서만 받고 응답 JSON에는 절대 포함하지 않는다. DB에는 BCrypt 해시로 저장된다. */
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    @NotBlank
    @Size(min = 8, max = 64)
    private String password;

    @NotBlank
    @Size(max = 100)
    private String name;

    private LocalDateTime createdAt;
}
