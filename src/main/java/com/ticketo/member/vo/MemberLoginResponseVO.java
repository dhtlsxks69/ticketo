package com.ticketo.member.vo;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 로그인 응답 VO. 액세스 토큰과 비밀번호를 제외한 회원 정보를 담는다.
 * 클라이언트는 이후 요청의 {@code Authorization: Bearer <accessToken>} 헤더로 토큰을 보낸다.
 */
@Getter
@AllArgsConstructor
public class MemberLoginResponseVO {

    private final String accessToken;

    private final String tokenType;

    /** 액세스 토큰 유효 시간(초) */
    private final long expiresIn;

    private final MemberVO member;
}
