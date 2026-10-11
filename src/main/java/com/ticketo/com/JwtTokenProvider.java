package com.ticketo.com;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Date;

/**
 * JWT 발급/검증. HS256 서명을 사용하며, 토큰의 subject에 회원 id를 담는다.
 *
 * 서명 비밀키는 환경변수 {@code JWT_SECRET}으로 주입한다. 기본값이 없으므로 지정하지 않으면 앱이 기동되지 않고,
 * HS256 요구사항에 따라 32바이트(영문 기준 32자) 이상이어야 한다.
 */
@Component
public class JwtTokenProvider {

    private final SecretKey key;
    private final Duration accessTokenValidity;

    public JwtTokenProvider(
            @Value("${JWT_SECRET}") String secret,
            @Value("${jwt.access-token-expiration-minutes:60}") long accessTokenExpirationMinutes) {
        // 32바이트 미만이면 WeakKeyException으로 기동 시점에 바로 실패한다.
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.accessTokenValidity = Duration.ofMinutes(accessTokenExpirationMinutes);
    }

    public String createAccessToken(Long memberId) {
        Date now = new Date();
        return Jwts.builder()
                .subject(String.valueOf(memberId))
                .issuedAt(now)
                .expiration(new Date(now.getTime() + accessTokenValidity.toMillis()))
                .signWith(key)
                .compact();
    }

    public long getAccessTokenExpiresInSeconds() {
        return accessTokenValidity.toSeconds();
    }

    /**
     * 토큰의 서명과 만료를 검증하고 회원 id를 꺼낸다.
     *
     * @throws io.jsonwebtoken.JwtException 서명 불일치, 만료, 형식 오류 등 유효하지 않은 토큰
     * @throws IllegalArgumentException 토큰이 비어 있는 경우
     */
    public Long getMemberId(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
        return Long.valueOf(claims.getSubject());
    }
}
