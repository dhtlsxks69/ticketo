package com.ticketo.member.vo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 로그인 요청 VO. 가입 시의 형식 규칙(비밀번호 길이 등)은 적용하지 않고 값 존재 여부만 검증한다.
 */
@Getter
@Setter
@NoArgsConstructor
public class MemberLoginVO {

    @NotBlank
    @Size(max = 255)
    private String email;

    @NotBlank
    @Size(max = 64)
    private String password;
}
