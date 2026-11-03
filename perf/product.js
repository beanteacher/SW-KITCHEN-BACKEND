// 제품 목록·검색·필터·상세 API 를 동시 100명으로 1분 동안 부른다. 95% 가 0.5초를 넘으면 실패로 끝난다
// 데이터는 perf/seed.sql 로 넣는다. 실행: k6 run -e BASE_URL=http://localhost:8081 perf/product.js
import http from 'k6/http';
import { check, sleep } from 'k6';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const PRODUCT_COUNT = 3000;
const KEYWORDS = ['냉장고', '오븐', '대성', 'RFAA', '작업대 12'];
const SCOPES = ['NEW', 'STOCK'];
const ORDERS = ['POPULAR', 'NEWEST', 'PRICE_ASC', 'PRICE_DESC'];
const MATERIALS = ['STAINLESS', 'ALUMINUM', 'STEEL', 'PLASTIC'];

export const options = {
    vus: 100,
    duration: '1m',
    thresholds: {
        http_req_duration: ['p(95)<500'],
        // API 마다 따로 결과에 보이게 한다
        'http_req_duration{name:list}': ['p(95)<500'],
        'http_req_duration{name:filter}': ['p(95)<500'],
        'http_req_duration{name:detail}': ['p(95)<500'],
        http_req_failed: ['rate<0.01'],
    },
};

const pick = (list) => list[Math.floor(Math.random() * list.length)];
const between = (min, max) => min + Math.floor(Math.random() * (max - min + 1));

// 고객 화면에서 고를 수 있는 조건을 무작위로 섞는다
function searchQuery() {
    const params = [`scope=${pick(SCOPES)}`, `orderBy=${pick(ORDERS)}`, `page=${between(1, 5)}`];
    if (Math.random() < 0.5) params.push(`categoryId=${between(1, 24)}`);
    if (Math.random() < 0.3) params.push(`materials=${pick(MATERIALS)}`);
    if (Math.random() < 0.3) params.push(`keyword=${encodeURIComponent(pick(KEYWORDS))}`);
    return params.join('&');
}

// 목록 화면은 목록과 필터를 함께 부르고, 그중 한 제품의 상세로 들어간다
export default function () {
    const query = searchQuery();
    const list = http.get(`${BASE_URL}/api/v1/product?${query}`, { tags: { name: 'list' } });
    const filter = http.get(`${BASE_URL}/api/v1/product/filter?${query}`, { tags: { name: 'filter' } });
    const detail = http.get(`${BASE_URL}/api/v1/product/${between(1, PRODUCT_COUNT)}`, { tags: { name: 'detail' } });

    check(list, { '목록 200': (r) => r.status === 200 });
    check(filter, { '필터 200': (r) => r.status === 200 });
    check(detail, { '상세 200': (r) => r.status === 200 });
    sleep(1);
}
