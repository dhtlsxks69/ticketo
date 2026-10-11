package com.ticketo.member.vo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 회원 수정 요청 VO. 이름만 수정할 수 있다.
 * 이메일은 로그인 ID이고, 비밀번호 변경은 본인 확인(JWT 도입 후)이 필요하므로 제외한다.
 */
@Getter
@Setter
@NoArgsConstructor
public class MemberUpdateVO {

    @NotBlank
    @Size(max = 100)
    private String name;
}
