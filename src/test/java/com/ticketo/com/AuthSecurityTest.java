package com.ticketo.com;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.sql.Statement;
import java.util.Date;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 인증/인가 동작 테스트. SecurityConfig와 JwtAuthenticationFilter가 실제로 적용된 상태에서
 * MockMvc로 요청을 보내 401(인증 실패), 403/404(소유자 검사), 공개 경로를 확인한다.
 *
 * 실제 DB(application.yml 접속 정보)를 사용한다. 테스트 데이터는 이메일 접두어 {@code auth-test-},
 * 공연 제목 {@code auth-test}로 구분하며, 시작 전과 끝난 뒤에 모두 정리한다. (중간에 실패해 남은 데이터 대비)
 */
@SpringBootTest
@AutoConfigureMockMvc
class AuthSecurityTest {

    private static final String EMAIL_PREFIX = "auth-test-";
    private static final String TICKET_TITLE = "auth-test";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Value("${JWT_SECRET}")
    private String jwtSecret;

    private Long memberAId;
    private Long memberBId;
    private Long ticketId;
    /** 회원 B가 예매한 내역 */
    private Long reservationOfBId;

    private String tokenA;
    private String tokenB;

    @BeforeEach
    void setUp() {
        cleanUp();

        memberAId = insertAndGetId("INSERT INTO member (email, password, name) VALUES ('"
                + uniqueEmail() + "', 'x', 'member-a')");
        memberBId = insertAndGetId("INSERT INTO member (email, password, name) VALUES ('"
                + uniqueEmail() + "', 'x', 'member-b')");
        ticketId = insertAndGetId("INSERT INTO ticket (title, venue, event_at, price, total_quantity, remaining_quantity) "
                + "VALUES ('" + TICKET_TITLE + "', 'hall', '2099-01-01 19:00:00', 1000, 10, 9)");
        reservationOfBId = insertAndGetId("INSERT INTO reservation (member_id, ticket_id, quantity) VALUES ("
                + memberBId + ", " + ticketId + ", 1)");

        tokenA = jwtTokenProvider.createAccessToken(memberAId);
        tokenB = jwtTokenProvider.createAccessToken(memberBId);
    }

    @AfterEach
    void tearDown() {
        cleanUp();
    }

    // ---------------------------------------------------------------- 401: 토큰 없음 / 위조 / 만료

    @Test
    void 토큰_없이_예매하면_401() throws Exception {
        mockMvc.perform(reserveRequest(null, ticketId, 1))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void 토큰_없이_예매_취소하면_401() throws Exception {
        mockMvc.perform(delete("/api/reservations/{id}", reservationOfBId))
                .andExpect(status().isUnauthorized());

        assertThat(reservationStatus(reservationOfBId)).isEqualTo("RESERVED");
    }

    @Test
    void 토큰_없이_내_예매_내역을_조회하면_401() throws Exception {
        mockMvc.perform(get("/api/reservations"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void 위조_토큰으로_예매하면_401() throws Exception {
        String forged = tokenSignedWith("another-secret-key-for-forged-token-0123456789", memberAId, 60_000);

        mockMvc.perform(reserveRequest(forged, ticketId, 1))
                .andExpect(status().isUnauthorized());

        assertThat(remainingQuantity()).isEqualTo(9);
    }

    @Test
    void 위조_토큰으로_내_예매_내역을_조회하면_401() throws Exception {
        String forged = tokenSignedWith("another-secret-key-for-forged-token-0123456789", memberAId, 60_000);

        mockMvc.perform(get("/api/reservations").header(HttpHeaders.AUTHORIZATION, "Bearer " + forged))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void 만료_토큰으로_예매하면_401() throws Exception {
        String expired = tokenSignedWith(jwtSecret, memberAId, -60_000);

        mockMvc.perform(reserveRequest(expired, ticketId, 1))
                .andExpect(status().isUnauthorized());

        assertThat(remainingQuantity()).isEqualTo(9);
    }

    @Test
    void 만료_토큰으로_예매_취소하면_401() throws Exception {
        String expired = tokenSignedWith(jwtSecret, memberBId, -60_000);

        mockMvc.perform(delete("/api/reservations/{id}", reservationOfBId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + expired))
                .andExpect(status().isUnauthorized());

        assertThat(reservationStatus(reservationOfBId)).isEqualTo("RESERVED");
    }

    @Test
    void 만료_토큰으로_내_예매_내역을_조회하면_401() throws Exception {
        String expired = tokenSignedWith(jwtSecret, memberAId, -60_000);

        mockMvc.perform(get("/api/reservations").header(HttpHeaders.AUTHORIZATION, "Bearer " + expired))
                .andExpect(status().isUnauthorized());
    }

    // ---------------------------------------------------------------- 403: 다른 회원의 회원 정보

    @Test
    void 본인_id로_회원_조회하면_200() throws Exception {
        mockMvc.perform(get("/api/members/{id}", memberAId).header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(memberAId))
                .andExpect(jsonPath("$.password").doesNotExist());
    }

    @Test
    void 다른_회원_id로_회원_조회하면_403() throws Exception {
        mockMvc.perform(get("/api/members/{id}", memberBId).header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA))
                .andExpect(status().isForbidden());
    }

    @Test
    void 다른_회원_id로_회원_수정하면_403() throws Exception {
        mockMvc.perform(put("/api/members/{id}", memberBId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"hacked\"}"))
                .andExpect(status().isForbidden());

        assertThat(memberName(memberBId)).isEqualTo("member-b");
    }

    // ---------------------------------------------------------------- 404: 다른 회원의 예매 취소

    @Test
    void 다른_회원의_예매를_취소하면_404() throws Exception {
        mockMvc.perform(delete("/api/reservations/{id}", reservationOfBId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA))
                .andExpect(status().isNotFound());

        // 남의 예매는 그대로 유지되고 잔여 수량도 복구되지 않는다.
        assertThat(reservationStatus(reservationOfBId)).isEqualTo("RESERVED");
        assertThat(remainingQuantity()).isEqualTo(9);
    }

    @Test
    void 본인의_예매는_취소할_수_있다() throws Exception {
        mockMvc.perform(delete("/api/reservations/{id}", reservationOfBId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELED"));

        assertThat(remainingQuantity()).isEqualTo(10);
    }

    // ---------------------------------------------------------------- 요청 본문의 memberId 무시

    @Test
    void 요청_본문에_다른_memberId를_넣어도_토큰의_회원으로_예매된다() throws Exception {
        // 회원 A의 토큰으로, 본문에는 회원 B의 id를 넣는다.
        mockMvc.perform(post("/api/reservations")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"memberId\":" + memberBId + ",\"ticketId\":" + ticketId + ",\"quantity\":2}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.memberId").value(memberAId));

        Integer reservedByA = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM reservation WHERE ticket_id = ? AND member_id = ? AND quantity = 2",
                Integer.class, ticketId, memberAId);
        Integer reservedByB = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM reservation WHERE ticket_id = ? AND member_id = ?",
                Integer.class, ticketId, memberBId);

        assertThat(reservedByA).isEqualTo(1);
        assertThat(reservedByB).isEqualTo(1); // 처음부터 있던 B의 예매 1건뿐
        assertThat(remainingQuantity()).isEqualTo(7);
    }

    // ---------------------------------------------------------------- 토큰 없이 호출 가능한 공개 API

    @Test
    void 토큰_없이_회원가입할_수_있다() throws Exception {
        mockMvc.perform(post("/api/members")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(memberJson(uniqueEmail(), "password1234", "newbie")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.password").doesNotExist());
    }

    @Test
    void 토큰_없이_로그인할_수_있고_토큰이_발급된다() throws Exception {
        String email = uniqueEmail();
        mockMvc.perform(post("/api/members")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(memberJson(email, "password1234", "login-user")))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/members/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"password1234\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.tokenType").value("Bearer"));
    }

    @Test
    void 토큰_없이_공연_목록을_조회할_수_있다() throws Exception {
        mockMvc.perform(get("/api/tickets"))
                .andExpect(status().isOk());
    }

    @Test
    void 토큰_없이_공연_상세를_조회할_수_있다() throws Exception {
        mockMvc.perform(get("/api/tickets/{id}", ticketId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(ticketId));
    }

    // ---------------------------------------------------------------- 헬퍼

    private MockHttpServletRequestBuilder reserveRequest(String token, Long targetTicketId, int quantity) {
        MockHttpServletRequestBuilder request = post("/api/reservations")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"ticketId\":" + targetTicketId + ",\"quantity\":" + quantity + "}");
        if (token != null) {
            request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        }
        return request;
    }

    /** 지정한 비밀키로 서명한 토큰을 만든다. validityMillis가 음수면 이미 만료된 토큰이 된다. */
    private String tokenSignedWith(String secret, Long memberId, long validityMillis) {
        SecretKey key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        long now = System.currentTimeMillis();
        return Jwts.builder()
                .subject(String.valueOf(memberId))
                .issuedAt(new Date(now - 120_000))
                .expiration(new Date(now + validityMillis))
                .signWith(key)
                .compact();
    }

    private String memberJson(String email, String password, String name) {
        return "{\"email\":\"" + email + "\",\"password\":\"" + password + "\",\"name\":\"" + name + "\"}";
    }

    private String uniqueEmail() {
        return EMAIL_PREFIX + UUID.randomUUID() + "@example.com";
    }

    private String reservationStatus(Long id) {
        return jdbcTemplate.queryForObject("SELECT status FROM reservation WHERE id = ?", String.class, id);
    }

    private String memberName(Long id) {
        return jdbcTemplate.queryForObject("SELECT name FROM member WHERE id = ?", String.class, id);
    }

    private Integer remainingQuantity() {
        return jdbcTemplate.queryForObject(
                "SELECT remaining_quantity FROM ticket WHERE id = ?", Integer.class, ticketId);
    }

    /** 접두어로 구분되는 테스트 데이터를 FK 순서(reservation, ticket, member)대로 삭제한다. */
    private void cleanUp() {
        jdbcTemplate.update("DELETE FROM reservation WHERE member_id IN "
                + "(SELECT id FROM (SELECT id FROM member WHERE email LIKE ?) m) "
                + "OR ticket_id IN (SELECT id FROM (SELECT id FROM ticket WHERE title = ?) t)",
                EMAIL_PREFIX + "%", TICKET_TITLE);
        jdbcTemplate.update("DELETE FROM ticket WHERE title = ?", TICKET_TITLE);
        jdbcTemplate.update("DELETE FROM member WHERE email LIKE ?", EMAIL_PREFIX + "%");
    }

    private Long insertAndGetId(String sql) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(con -> con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS), keyHolder);
        return keyHolder.getKey().longValue();
    }
}
