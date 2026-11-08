// 시나리오 C: 여러 공연(loadtest-spread-*)에 예매가 분산되는 경우. quantity=1 고정.
// 행 락 경합이 적은 기준선으로, reserve-hot 과 락 경합을 비교하는 용도.
// 실행 전 03_reset.sql, 실행 후 04_verify.sql 을 돌린다.
import http from 'k6/http';
import { BASE_URL } from './lib/config.js';
import { loginAll, authHeaders } from './lib/auth.js';
import { reserveExpected, countReserve, summaryOutput } from './lib/metrics.js';

const VUS = parseInt(__ENV.VUS || '50', 10);
const DURATION = __ENV.DURATION || '30s';
const TITLE_PREFIX = 'loadtest-spread-';

export const options = {
  setupTimeout: '300s', // 회원 수 × BCrypt 로그인
  scenarios: {
    reserve_spread: {
      executor: 'constant-vus',
      vus: VUS,
      duration: DURATION,
    },
  },
  thresholds: {
    // 409(매진)는 expectedStatuses 로 제외되므로 5xx/타임아웃만 실패로 집계된다.
    http_req_failed: ['rate<0.01'],
  },
};

export function setup() {
  const tokens = loginAll();

  const res = http.get(`${BASE_URL}/api/tickets`);
  if (res.status !== 200) throw new Error(`공연 목록 조회 실패 status=${res.status}`);
  const ticketIds = res
    .json()
    .filter((t) => t.title.startsWith(TITLE_PREFIX))
    .map((t) => t.id);
  if (ticketIds.length === 0) {
    throw new Error(`title 이 ${TITLE_PREFIX} 로 시작하는 공연이 없습니다. 02_seed_tickets.sql 을 실행하세요.`);
  }

  return { tokens, ticketIds };
}

export default function (data) {
  // VU마다 토큰을 나눠 쓴다 (VU 수가 회원 수보다 많으면 순환).
  const token = data.tokens[(__VU - 1) % data.tokens.length];
  const ticketId = data.ticketIds[Math.floor(Math.random() * data.ticketIds.length)];
  const res = http.post(
    `${BASE_URL}/api/reservations`,
    JSON.stringify({ ticketId, quantity: 1 }),
    {
      headers: authHeaders(token),
      tags: { name: 'reserve' },
      responseCallback: reserveExpected,
    },
  );
  countReserve(res);
}

export function handleSummary(data) {
  return summaryOutput(data);
}
