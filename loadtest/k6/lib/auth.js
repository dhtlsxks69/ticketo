import http from 'k6/http';
import { BASE_URL, MEMBER_COUNT, requirePassword, memberEmail, JSON_HEADERS } from './config.js';

// setup()에서 한 번만 호출한다. 회원 전원이 딱 한 번씩 로그인해 토큰 배열을 반환한다.
// (BCrypt 검증 비용이 본 부하 측정에 섞이지 않게 하기 위함)
export function loginAll() {
  const password = requirePassword();
  const tokens = [];
  for (let n = 1; n <= MEMBER_COUNT; n++) {
    const res = http.post(
      `${BASE_URL}/api/members/login`,
      JSON.stringify({ email: memberEmail(n), password }),
      { headers: JSON_HEADERS, tags: { name: 'setup_login' } },
    );
    if (res.status !== 200) {
      throw new Error(`로그인 실패: ${memberEmail(n)} status=${res.status} (시드 회원/비밀번호 확인)`);
    }
    tokens.push(res.json('accessToken'));
  }
  return tokens;
}

// 로그인 응답의 토큰은 expiresIn(기본 3600초) 동안 유효하다. 그보다 긴 실행은 지원하지 않는다.
export function authHeaders(token) {
  return { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` };
}
