import http from 'k6/http';
import { Counter } from 'k6/metrics';
import { textSummary } from 'https://jslib.k6.io/k6-summary/0.0.2/index.js';

export const reserveSuccess = new Counter('reserve_success');   // 201
export const reserveSoldOut = new Counter('reserve_sold_out');  // 409 (매진, 정상 응답)
export const server503 = new Counter('server_error_503');       // 락 대기 초과 등
export const serverError = new Counter('server_error');         // 503 외 5xx + 타임아웃/연결 실패(status 0)
export const otherFail = new Counter('other_fail');             // 그 외 (401, 400, 404 등)

// 409는 기대된 응답이므로 http_req_failed 에서 제외한다. 5xx와 타임아웃(status 0)만 실패로 센다.
export const reserveExpected = http.expectedStatuses(201, 409);
export const readExpected = http.expectedStatuses({ min: 200, max: 299 });

export function countReserve(res) {
  if (res.status === 201) reserveSuccess.add(1);
  else if (res.status === 409) reserveSoldOut.add(1);
  else if (res.status === 503) server503.add(1);
  else if (res.status >= 500 || res.status === 0) serverError.add(1);
  else otherFail.add(1);
}

export function countRead(res) {
  if (res.status >= 200 && res.status < 300) return;
  if (res.status === 503) server503.add(1);
  else if (res.status >= 500 || res.status === 0) serverError.add(1);
  else otherFail.add(1);
}

// loadtest/results/${RESULT_NAME}.json 에 요약 저장. 저장소 루트에서 k6 를 실행한다고 가정한다.
export function summaryOutput(data) {
  const name = __ENV.RESULT_NAME || new Date().toISOString().replace(/[:.]/g, '-');
  return {
    [`loadtest/results/${name}.json`]: JSON.stringify(data, null, 2),
    stdout: textSummary(data, { indent: ' ', enableColors: false }),
  };
}
