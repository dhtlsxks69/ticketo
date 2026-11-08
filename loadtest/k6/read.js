// 시나리오 A: 조회 (목록 + 상세 혼합, 인증 불필요). VU를 단계적으로 올린다.
import http from 'k6/http';
import { check } from 'k6';
import { BASE_URL } from './lib/config.js';
import { readExpected, countRead, summaryOutput } from './lib/metrics.js';

export const options = {
  scenarios: {
    read: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: '30s', target: 10 },
        { duration: '30s', target: 50 },
        { duration: '30s', target: 100 },
        { duration: '60s', target: 100 },
        { duration: '15s', target: 0 },
      ],
      gracefulRampDown: '10s',
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.01'],
    http_req_duration: ['p(95)<1000'],
  },
};

const LIST_RATIO = parseFloat(__ENV.LIST_RATIO || '0.2'); // 목록 호출 비율 (나머지는 상세)
const SAMPLE_IDS = 200;

export function setup() {
  const res = http.get(`${BASE_URL}/api/tickets`);
  if (res.status !== 200) throw new Error(`공연 목록 조회 실패 status=${res.status}`);
  const ids = res.json().map((t) => t.id);
  if (ids.length === 0) throw new Error('공연이 없습니다. 02_seed_tickets.sql 을 먼저 실행하세요.');
  // 목록이 매우 크면(filler) 일정 간격으로 샘플링한다.
  const step = Math.max(1, Math.floor(ids.length / SAMPLE_IDS));
  const sample = [];
  for (let i = 0; i < ids.length; i += step) sample.push(ids[i]);
  return { ids: sample };
}

export default function (data) {
  const isList = Math.random() < LIST_RATIO;
  const url = isList
    ? `${BASE_URL}/api/tickets`
    : `${BASE_URL}/api/tickets/${data.ids[Math.floor(Math.random() * data.ids.length)]}`;
  const res = http.get(url, {
    tags: { name: isList ? 'ticket_list' : 'ticket_detail' },
    responseCallback: readExpected,
  });
  countRead(res);
  check(res, { 'status 200': (r) => r.status === 200 });
}

export function handleSummary(data) {
  return summaryOutput(data);
}
