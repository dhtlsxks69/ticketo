package com.ticketo.com;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * {@code Authorization: Bearer <token>} 헤더의 JWT를 검증해 인증 정보를 SecurityContext에 넣는다.
 *
 * 토큰이 없거나 유효하지 않으면 인증 정보를 넣지 않고 그대로 다음 필터로 넘긴다.
 * 인증이 필요한 API라면 이후 단계에서 {@link JsonAuthErrorHandler}가 401로 응답한다.
 * 인증 정보의 principal은 회원 id({@link Long})이며 컨트롤러에서 {@code @AuthenticationPrincipal Long}으로 받는다.
 */
@Slf4j
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtTokenProvider jwtTokenProvider;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith(BEARER_PREFIX)) {
            try {
                Long memberId = jwtTokenProvider.getMemberId(header.substring(BEARER_PREFIX.length()).trim());
                SecurityContextHolder.getContext()
                        .setAuthentication(new UsernamePasswordAuthenticationToken(memberId, null, List.of()));
            } catch (RuntimeException e) {
                // 만료/위조/형식 오류. 인증 정보를 넣지 않으면 보호된 API에서 401이 된다.
                log.debug("유효하지 않은 JWT: {}", e.getMessage());
            }
        }
        chain.doFilter(request, response);
    }
}
