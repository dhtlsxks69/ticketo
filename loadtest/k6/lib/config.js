// 부하 테스트 공통 설정. 값은 모두 환경변수(k6 -e 또는 PowerShell $env: → __ENV)로 받는다.
export const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
export const MEMBER_COUNT = parseInt(__ENV.MEMBER_COUNT || '100', 10);

// 비밀번호는 기본값을 두지 않는다. 로그인이 필요한 시나리오(auth.js)가 시작할 때 호출하며, 없으면 즉시 실패한다.
// (조회 시나리오 read.js 는 로그인이 없어 비밀번호가 필요 없다)
export function requirePassword() {
  if (!__ENV.LOADTEST_PASSWORD) {
    throw new Error('LOADTEST_PASSWORD 환경변수가 필요합니다. (비밀번호는 코드/문서에 쓰지 않는다)');
  }
  return __ENV.LOADTEST_PASSWORD;
}

// 01_seed_members.sql 이 만드는 이메일 형식: loadtest-00001@example.com ~
export function memberEmail(n) {
  return `loadtest-${String(n).padStart(5, '0')}@example.com`;
}

export const JSON_HEADERS = { 'Content-Type': 'application/json' };
