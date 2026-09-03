# savePick API 명세

- 문서 ID: 11
- 작성: technical-architect
- 최종 갱신: 2026-08-28
- 선행 문서: 01, 02, 03, 04, 05, 10

## 한 줄 요약
고객용 25개, 관리자용 21개 엔드포인트와 6개 배치 작업의 요청·응답·오류 코드·권한을 정의하고, 49개 FR 전부를 매핑한다.

---

## 0. 공통 규약

### 0.1 경로와 권한

| 구분 | 경로 접두어 | 권한 |
|---|---|---|
| 고객용 | `/api/...` | 비로그인 또는 고객 (엔드포인트별 명시) |
| 관리자용 | `/api/admin/...` | 관리자 전용. 고객 토큰으로 접근하면 403 |

관리자 계정이라도 `/api/orders` 같은 고객 주문 엔드포인트는 사용하지 않는다. 관리자 흐름과 고객 흐름은 경로에서 분리한다 (02 §3, 02 U4).

### 0.2 공통 헤더

| 헤더 | 사용처 | 설명 |
|---|---|---|
| `Authorization: Bearer <accessToken>` | 인증 필요한 모든 요청 | 12번 문서 참조 |
| `X-Guest-Token: <uuid>` | 미로그인 장바구니 | 비로그인 장바구니 식별 (10번 CART) |
| `Idempotency-Key: <string>` | `POST /api/orders/{orderId}/payments` | 중복 결제 요청 차단 |

### 0.3 공통 응답 형식

성공 응답은 리소스 객체를 그대로 반환한다. 오류 응답은 아래 형식으로 통일한다.

```json
{
  "code": "OUT_OF_STOCK",
  "message": "요청한 수량만큼 재고가 남아 있지 않습니다.",
  "serverTime": "2026-08-28T19:04:12+09:00",
  "details": { "shortages": [{ "productId": 12, "requested": 2, "available": 1 }] }
}
```

- 모든 오류 응답에 `serverTime`을 포함한다. 클라이언트가 표시 중인 남은 시간과 서버 판정을 맞추기 위함이다 (BR-028, FR-005).
- `details`는 오류 코드별로 정의된 경우에만 존재한다.

### 0.4 시각·금액 표기

- 모든 시각은 ISO 8601 + KST 오프셋(`+09:00`)으로 주고받는다 (BR-028).
- 모든 금액은 원 단위 정수다.
- 목록 응답은 `{ "items": [...], "page": { "number": 0, "size": 20, "totalElements": 37 } }` 형태를 쓴다.

### 0.5 오류 코드 카탈로그

| 코드 | HTTP | 의미 | 규칙 |
|---|---|---|---|
| `VALIDATION_ERROR` | 400 | 입력 형식·필수값 위반 | FR별 완료 조건 |
| `UNAUTHENTICATED` | 401 | 토큰 없음·만료·폐기 | BR-002 |
| `FORBIDDEN` | 403 | 권한 부족(고객이 관리자 기능 접근 등) | BR-002 |
| `NOT_FOUND` | 404 | 리소스 없음 | — |
| `EMAIL_DUPLICATED` | 409 | 이미 가입된 이메일 | BR-002 |
| `INVALID_CREDENTIALS` | 401 | 이메일·비밀번호 불일치(무엇이 틀렸는지 구분하지 않음) | BR-002 |
| `LOGIN_BLOCKED` | 429 | 연속 실패 5회로 10분 차단 | BR-002 |
| `PRODUCT_NOT_ON_SALE` | 409 | ON_SALE이 아닌 상품에 대한 담기·주문 | BR-030 |
| `PRODUCT_CLOSED` | 409 | 마감 시각이 지난 상품 | BR-030 |
| `MAX_QUANTITY_EXCEEDED` | 409 | 1회 주문 최대 수량 초과 | BR-009 |
| `CART_ITEM_LIMIT_EXCEEDED` | 409 | 장바구니 품목 10개 초과 | BR-009 |
| `CART_EMPTY` | 409 | 빈 장바구니로 주문 시도 | BR-010 |
| `CART_HAS_UNAVAILABLE_ITEM` | 409 | 구매 불가 품목이 남아 있음 | BR-005, BR-030 |
| `OUT_OF_STOCK` | 409 | 요청 수량 > 판매 가능 수량 | BR-006, BR-027 |
| `PENDING_ORDER_EXISTS` | 409 | 유효한 주문서를 이미 보유 | BR-007 |
| `ORDER_RESTRICTED` | 403 | 노쇼 누적으로 주문 제한 상태 | BR-023 |
| `HOLD_EXPIRED` | 409 | 선점 유효 시간 경과 | BR-007, BR-008 |
| `INVALID_ORDER_STATUS` | 409 | 현재 상태에서 허용되지 않는 전이 | 05 §2.3 |
| `SLOT_NOT_FOUND` | 404 | 존재하지 않는 시간대 | BR-014 |
| `SLOT_CLOSED` | 409 | 예약 마감(시작 30분 전 경과) 또는 관리자 차단 | BR-015, BR-016 |
| `SLOT_FULL` | 409 | 정원 도달 | BR-016 |
| `SLOT_AFTER_PRODUCT_CLOSING` | 409 | 시간대 시작이 품목 최이른 마감 시각보다 늦음 | BR-017 |
| `SLOT_DATE_OUT_OF_RANGE` | 409 | D+0~D+1 범위 밖 또는 휴무일 | BR-013 |
| `SLOT_NOT_SELECTED` | 409 | 시간대 미지정 상태로 결제 시도 | BR-016 |
| `AMOUNT_MISMATCH` | 409 | 결제 요청 금액 ≠ 주문서 확정 금액 | BR-029 |
| `PAYMENT_ATTEMPT_EXCEEDED` | 409 | 결제 시도 4회째 | BR-012 |
| `PAYMENT_FAILED` | 200 | 결제 실패(오류가 아니라 정상 응답 본문으로 반환) | BR-011, BR-012 |
| `ALREADY_PAID` | 409 | 성공 기록이 이미 존재 | BR-011 |
| `CANCEL_DEADLINE_PASSED` | 409 | 고객 취소 마감 경과 | BR-018 |
| `CANCEL_NOT_ALLOWED` | 409 | COMPLETED·NO_SHOW·CANCELED 취소 시도 | BR-020, BR-024 |
| `CANCEL_REASON_REQUIRED` | 400 | 관리자 취소 사유 누락 | BR-020 |
| `STOCK_BELOW_COMMITTED` | 409 | 총 재고를 (선점 중 + 확정 판매) 미만으로 축소 시도 | BR-025 |
| `PRODUCT_STATUS_TRANSITION_DENIED` | 409 | 허용되지 않는 상품 상태 전이 | 05 §5.3 |
| `CLOSING_TIME_INVALID` | 400 | 마감 시각이 과거이거나 영업 종료 시각 초과 | BR-003 |
| `BUSINESS_HOUR_INVALID` | 400 | 종료 시각 ≤ 시작 시각, 30분 단위 아님 | BR-014 |
| `PICKUP_NUMBER_EXHAUSTED` | 409 | 영업일 픽업 번호 999 소진 | BR-026 |
| `INTERNAL_ERROR` | 500 | 처리 중 시스템 오류(재고 행 락 대기 타임아웃 포함). 트랜잭션 전체가 롤백되어 재고·주문 상태는 변하지 않는다 | — (규칙 위반이 아니라 시스템 오류. 13번 §3) |

`PAYMENT_FAILED`만 HTTP 200으로 반환한다. 가상 결제의 실패는 시스템 오류가 아니라 정상 흐름의 한 갈래이며, 남은 재시도 횟수와 선점 잔여 시간을 함께 돌려줘야 하기 때문이다 (BR-012, 02 CS-03).

---

## 1. 고객 API — 인증과 계정

### API-001 · 회원가입

- `POST /api/auth/signup`
- 권한: 비로그인
- 관련 요구사항: FR-001
- 관련 규칙: BR-002

**요청**
```json
{ "email": "minsu@example.com", "password": "savepick123", "name": "민수", "phone": "01012345678", "guestToken": "0f0a2b6e-1a3c-4c2e-9f1a-8c1d2e3f4a5b" }
```

**응답 (201)**
```json
{
  "memberId": 42,
  "email": "minsu@example.com",
  "name": "민수",
  "role": "CUSTOMER",
  "accessToken": "eyJhbGciOi...",
  "accessTokenExpiresAt": "2026-08-28T19:34:12+09:00",
  "cartMerged": true
}
```

리프레시 토큰은 응답 본문에 넣지 않고 `HttpOnly` 쿠키로 내려준다 (12번 문서 §2). `guestToken`이 있으면 그 장바구니를 회원 장바구니로 병합한다 (02 CS-01 7단계).

**오류**
| 코드 | HTTP | 조건 | 규칙 |
|---|---|---|---|
| `VALIDATION_ERROR` | 400 | 이메일 형식 아님 / 비밀번호 8자 미만 / 필수값 누락 | FR-001 |
| `EMAIL_DUPLICATED` | 409 | 이미 가입된 이메일 | BR-002 |

`role`은 요청으로 받지 않는다. 가입으로 관리자 권한이 부여되지 않는다 (FR-001, FR-004 예외).

### API-002 · 로그인

- `POST /api/auth/login`
- 권한: 비로그인
- 관련 요구사항: FR-002, FR-004
- 관련 규칙: BR-002

**요청**
```json
{ "email": "jihyun@example.com", "password": "savepick123", "guestToken": "0f0a2b6e-..." }
```

**응답 (200)**
```json
{
  "memberId": 17,
  "name": "지현",
  "role": "CUSTOMER",
  "accessToken": "eyJhbGciOi...",
  "accessTokenExpiresAt": "2026-08-28T19:34:12+09:00",
  "orderPermission": "ALLOWED"
}
```

**오류**
| 코드 | HTTP | 조건 | 규칙 |
|---|---|---|---|
| `INVALID_CREDENTIALS` | 401 | 미가입 이메일 또는 비밀번호 불일치. 두 경우의 응답이 동일하다 | BR-002 |
| `LOGIN_BLOCKED` | 429 | 연속 실패 5회 후 10분 이내 재시도. `details.retryAfterAt` 포함 | BR-002 |
| `FORBIDDEN` | 403 | `role = 'ADMIN'` 계정이 고객 로그인 경로로 접근 | BR-002 |

노쇼 제한 계정도 로그인은 성공한다. `orderPermission: "RESTRICTED"`로 알린다 (BR-023, FR-002 예외).

### API-003 · 액세스 토큰 재발급

- `POST /api/auth/token/refresh`
- 권한: 리프레시 쿠키 보유자
- 관련 요구사항: FR-002
- 관련 규칙: BR-002

**요청** — 본문 없음. `refreshToken` 쿠키를 사용한다.

**응답 (200)**
```json
{ "accessToken": "eyJhbGciOi...", "accessTokenExpiresAt": "2026-08-28T20:04:12+09:00" }
```

**오류**
| 코드 | HTTP | 조건 | 규칙 |
|---|---|---|---|
| `UNAUTHENTICATED` | 401 | 쿠키 없음 / 세션 폐기됨 / 마지막 활동 후 30일 경과 | BR-002 |

### API-004 · 로그아웃

- `POST /api/auth/logout`
- 권한: 고객, 관리자
- 관련 요구사항: FR-002
- 관련 규칙: BR-002

**응답 (204)** — 본문 없음. 해당 세션의 `auth_sessions.revoked_at`을 채우고 쿠키를 만료시킨다.

**오류**
| 코드 | HTTP | 조건 | 규칙 |
|---|---|---|---|
| `UNAUTHENTICATED` | 401 | 유효한 세션 없음 | BR-002 |

### API-005 · 내 정보 조회

- `GET /api/me`
- 권한: 고객
- 관련 요구사항: FR-003
- 관련 규칙: BR-002

**응답 (200)**
```json
{ "memberId": 17, "email": "jihyun@example.com", "name": "지현", "phone": "01098765432", "orderPermission": "ALLOWED" }
```

**오류**
| 코드 | HTTP | 조건 | 규칙 |
|---|---|---|---|
| `UNAUTHENTICATED` | 401 | 미인증 | BR-002 |

### API-006 · 내 정보 수정

- `PATCH /api/me`
- 권한: 고객
- 관련 요구사항: FR-003
- 관련 규칙: BR-002

**요청**
```json
{ "name": "김지현", "phone": "01098765432" }
```

**응답 (200)**
```json
{ "memberId": 17, "email": "jihyun@example.com", "name": "김지현", "phone": "01098765432" }
```

이메일은 요청에 포함해도 무시한다. 이미 확정된 주문의 `contactName`·`contactPhone` 스냅샷은 바뀌지 않는다 (FR-003).

**오류**
| 코드 | HTTP | 조건 | 규칙 |
|---|---|---|---|
| `VALIDATION_ERROR` | 400 | 휴대폰 번호 형식 위반 / 이름 빈 값 | FR-003 |
| `UNAUTHENTICATED` | 401 | 미인증 | BR-002 |

### API-007 · 노쇼·주문 제한 상태 조회

- `GET /api/me/no-show-status`
- 권한: 고객
- 관련 요구사항: FR-031, FR-032
- 관련 규칙: BR-023

**응답 (200)**
```json
{
  "recentNoShowCount": 3,
  "windowDays": 30,
  "orderPermission": "RESTRICTED",
  "restrictedUntil": "2026-09-04T21:00:00+09:00",
  "noShowOrders": [{ "orderNo": "ORD-20260821-000048", "noShowAt": "2026-08-21T21:00:00+09:00" }]
}
```

**오류**
| 코드 | HTTP | 조건 | 규칙 |
|---|---|---|---|
| `UNAUTHENTICATED` | 401 | 미인증 | BR-002 |

### API-008 · 서버 시각 조회

- `GET /api/system/time`
- 권한: 비로그인
- 관련 요구사항: FR-005
- 관련 규칙: BR-028

**응답 (200)**
```json
{ "serverTime": "2026-08-28T19:04:12.352+09:00", "timezone": "Asia/Seoul" }
```

클라이언트는 이 값과 자기 시각의 차이를 보정해 남은 시간을 표시한다. 판정은 언제나 서버가 한다 (FR-005 "차이가 5초를 넘지 않는다").

**오류** — 없음.

### API-009 · 매장 정보 조회

- `GET /api/store`
- 권한: 비로그인
- 관련 요구사항: FR-033
- 관련 규칙: BR-001, BR-014

**응답 (200)**
```json
{
  "name": "savePick 신선마켓",
  "address": "서울특별시 ○○구 ○○로 12",
  "phone": "0212345678",
  "openTime": "10:00",
  "closeTime": "22:00",
  "slotUnitMinutes": 30
}
```

좌표·지도 정보는 제공하지 않는다 (제외 범위).

**오류** — 없음.

---

## 2. 고객 API — 상품

### API-010 · 상품 목록 조회 (검색·정렬·필터 포함)

- `GET /api/products?keyword=삼겹살&sort=CLOSING_SOON&hideSoldOut=true&page=0&size=20`
- 권한: 비로그인
- 관련 요구사항: FR-010, FR-011, FR-012, FR-014, FR-015, FR-034
- 관련 규칙: BR-004, BR-006, BR-030

**요청 파라미터**
| 이름 | 필수 | 기본값 | 설명 |
|---|---|---|---|
| `keyword` | N | 없음 | 상품명 부분 일치. 대소문자·앞뒤 공백 무시. 빈 값이면 전체 목록과 동일 (FR-011) |
| `sort` | N | `CLOSING_SOON` | `CLOSING_SOON` / `DISCOUNT_DESC` / `PRICE_ASC` (FR-012) |
| `hideSoldOut` | N | `false` | true면 `availableQuantity = 0` 제외 (FR-012) |
| `page`, `size` | N | 0, 20 | |

**응답 (200)**
```json
{
  "serverTime": "2026-08-28T19:04:12+09:00",
  "items": [
    {
      "productId": 12,
      "name": "국내산 삼겹살 300g",
      "saleUnit": "300g",
      "originalPrice": 12000,
      "discountRate": 50,
      "discountPrice": 6000,
      "availableQuantity": 3,
      "lowStock": true,
      "soldOut": false,
      "closingAt": "2026-08-28T21:00:00+09:00",
      "nextDiscountAt": "2026-08-28T19:00:00+09:00"
    }
  ],
  "page": { "number": 0, "size": 20, "totalElements": 7 }
}
```

- `status = 'ON_SALE'`인 상품만 반환한다. DRAFT·HIDDEN·CLOSED는 어떤 조건으로도 나오지 않는다 (FR-010, FR-011).
- `discountRate`·`discountPrice`는 응답 생성 시각의 서버 시각으로 계산한다. 저장된 값을 읽지 않는다 (BR-004).
- `availableQuantity`는 만료됐지만 아직 회수되지 않은 선점을 제외한 값이다 (05 C5, 13번 §2).
- `lowStock`은 `availableQuantity <= 5`일 때 true다 (FR-015).

**오류**
| 코드 | HTTP | 조건 | 규칙 |
|---|---|---|---|
| `VALIDATION_ERROR` | 400 | `sort` 허용값 아님 / `size > 100` | FR-012 |

일치하는 상품이 없으면 오류가 아니라 빈 `items` 배열을 반환한다 (FR-010, FR-011 예외).

### API-011 · 상품 상세 조회

- `GET /api/products/{productId}`
- 권한: 비로그인
- 관련 요구사항: FR-013, FR-014, FR-015, FR-034
- 관련 규칙: BR-004, BR-006, BR-009, BR-030

**응답 (200)**
```json
{
  "serverTime": "2026-08-28T19:04:12+09:00",
  "productId": 12,
  "name": "국내산 삼겹살 300g",
  "description": "오늘 손질한 국내산 삼겹살입니다.",
  "saleUnit": "300g",
  "originalPrice": 12000,
  "discountRate": 50,
  "discountPrice": 6000,
  "availableQuantity": 3,
  "lowStock": true,
  "soldOut": false,
  "maxOrderQuantity": 5,
  "closingAt": "2026-08-28T21:00:00+09:00",
  "purchasable": true
}
```

`purchasable`은 `status = 'ON_SALE'` 이면서 `closingAt > serverTime` 이고 `availableQuantity > 0`일 때만 true다.

**오류**
| 코드 | HTTP | 조건 | 규칙 |
|---|---|---|---|
| `NOT_FOUND` | 404 | 없는 상품 / DRAFT 상품 | FR-013 |
| `PRODUCT_CLOSED` | 409 | 마감 시각 경과(CLOSED). 판매 종료 안내를 위해 상품 이름·마감 시각을 `details`로 함께 반환 | BR-030, FR-034 |
| `PRODUCT_NOT_ON_SALE` | 409 | HIDDEN 상태 | BR-030, FR-015 예외 |

---

## 3. 고객 API — 장바구니

### API-012 · 장바구니 조회 (유효성 재검증 포함)

- `GET /api/cart`
- 권한: 비로그인(게스트 토큰) 또는 고객
- 관련 요구사항: FR-017, FR-018
- 관련 규칙: BR-005, BR-006, BR-009, BR-030

**응답 (200)**
```json
{
  "serverTime": "2026-08-28T19:04:12+09:00",
  "guestToken": "0f0a2b6e-...",
  "items": [
    {
      "cartItemId": 91,
      "productId": 12,
      "name": "국내산 삼겹살 300g",
      "quantity": 2,
      "addedPrice": 8400,
      "currentPrice": 6000,
      "priceChanged": true,
      "lineAmount": 12000,
      "availableQuantity": 3,
      "shortage": 0,
      "purchasable": true,
      "unavailableReason": null
    },
    {
      "cartItemId": 92,
      "productId": 30,
      "name": "유기농 시금치",
      "quantity": 1,
      "addedPrice": 1500,
      "currentPrice": 1500,
      "priceChanged": false,
      "lineAmount": 1500,
      "availableQuantity": 0,
      "shortage": 1,
      "purchasable": false,
      "unavailableReason": "OUT_OF_STOCK"
    }
  ],
  "totalAmount": 13500,
  "orderable": false
}
```

- 장바구니를 열 때마다 현재 할인가로 금액을 다시 계산한다 (FR-018).
- `unavailableReason`은 `OUT_OF_STOCK` / `PRODUCT_CLOSED` / `PRODUCT_NOT_ON_SALE` 중 하나다.
- `orderable`이 false이면 주문서를 만들 수 없다 (FR-018).

**오류**
| 코드 | HTTP | 조건 | 규칙 |
|---|---|---|---|
| `VALIDATION_ERROR` | 400 | 게스트 토큰과 인증 토큰이 모두 없음 | BR-010 |

### API-013 · 장바구니 담기

- `POST /api/cart/items`
- 권한: 비로그인(게스트 토큰) 또는 고객
- 관련 요구사항: FR-016, FR-034
- 관련 규칙: BR-009, BR-010, BR-030

**요청**
```json
{ "productId": 12, "quantity": 2 }
```

**응답 (201)**
```json
{ "cartItemId": 91, "productId": 12, "quantity": 2, "currentPrice": 6000, "cartItemCount": 2 }
```

같은 상품을 다시 담으면 수량을 합산한다. 담기는 재고를 선점하지 않고 다른 고객의 `availableQuantity`를 줄이지 않는다 (BR-010).

**오류**
| 코드 | HTTP | 조건 | 규칙 |
|---|---|---|---|
| `VALIDATION_ERROR` | 400 | `quantity < 1` | BR-009 |
| `NOT_FOUND` | 404 | 없는 상품 | FR-016 |
| `PRODUCT_NOT_ON_SALE` | 409 | HIDDEN·DRAFT 상품 | BR-030 |
| `PRODUCT_CLOSED` | 409 | 마감 시각 경과 | BR-030, FR-034 |
| `MAX_QUANTITY_EXCEEDED` | 409 | 합산 수량 > `maxOrderQuantity`. `details.maxOrderQuantity` 포함 | BR-009 |
| `OUT_OF_STOCK` | 409 | 합산 수량 > 현재 판매 가능 수량. `details.available` 포함 | BR-006, FR-016 예외 |
| `CART_ITEM_LIMIT_EXCEEDED` | 409 | 품목 수 10개 초과 | BR-009 |

### API-014 · 장바구니 수량 변경

- `PATCH /api/cart/items/{cartItemId}`
- 권한: 비로그인(게스트 토큰) 또는 고객
- 관련 요구사항: FR-017
- 관련 규칙: BR-009

**요청**
```json
{ "quantity": 3 }
```

**응답 (200)**
```json
{ "cartItemId": 91, "quantity": 3, "lineAmount": 18000, "totalAmount": 19500 }
```

`quantity = 0`이면 해당 품목을 삭제하고 `204`를 반환한다 (FR-017).

**오류**
| 코드 | HTTP | 조건 | 규칙 |
|---|---|---|---|
| `VALIDATION_ERROR` | 400 | 음수 수량 | BR-009 |
| `NOT_FOUND` | 404 | 내 장바구니의 품목이 아님 | BR-002 |
| `MAX_QUANTITY_EXCEEDED` | 409 | `maxOrderQuantity` 초과 | BR-009 |
| `OUT_OF_STOCK` | 409 | 판매 가능 수량 초과 | BR-006 |

### API-015 · 장바구니 품목 삭제

- `DELETE /api/cart/items/{cartItemId}`
- 권한: 비로그인(게스트 토큰) 또는 고객
- 관련 요구사항: FR-017
- 관련 규칙: BR-009

**응답 (204)** — 본문 없음.

**오류**
| 코드 | HTTP | 조건 | 규칙 |
|---|---|---|---|
| `NOT_FOUND` | 404 | 내 장바구니의 품목이 아님 | BR-002 |

### API-016 · 구매 불가 품목 일괄 삭제

- `DELETE /api/cart/items/unavailable`
- 권한: 비로그인(게스트 토큰) 또는 고객
- 관련 요구사항: FR-018
- 관련 규칙: BR-005, BR-006, BR-030

**응답 (200)**
```json
{ "removedCartItemIds": [92], "remainingItemCount": 1, "totalAmount": 12000, "orderable": true }
```

**오류**
| 코드 | HTTP | 조건 | 규칙 |
|---|---|---|---|
| `NOT_FOUND` | 404 | 장바구니 없음 | — |

---

## 4. 고객 API — 주문서와 재고 선점

### API-017 · 주문서 생성 (재고 임시 선점)

- `POST /api/orders`
- 권한: 고객 (로그인 필수)
- 관련 요구사항: FR-019, FR-021, FR-032, FR-034
- 관련 규칙: BR-005, BR-007, BR-008, BR-009, BR-010, BR-023, BR-027, BR-029

**요청**
```json
{ "cartItemIds": [91, 93] }
```

장바구니에 담긴 품목 중 주문할 품목을 지정한다. 지정하지 않으면 장바구니 전 품목을 대상으로 한다. 수량은 클라이언트가 보내지 않고 서버가 장바구니 값을 읽는다. 클라이언트가 보낸 수량을 신뢰하면 한도 검증을 우회할 수 있기 때문이다.

**응답 (201)**
```json
{
  "orderId": 1001,
  "orderNo": "ORD-20260828-000123",
  "status": "PENDING",
  "serverTime": "2026-08-28T19:04:12+09:00",
  "holdExpiresAt": "2026-08-28T19:14:12+09:00",
  "holdRemainingSeconds": 600,
  "paymentAttemptRemaining": 3,
  "totalAmount": 13500,
  "items": [
    { "productId": 12, "name": "국내산 삼겹살 300g", "quantity": 2, "originalUnitPrice": 12000, "discountRate": 50, "unitPrice": 6000, "lineAmount": 12000, "productClosingAt": "2026-08-28T21:00:00+09:00" },
    { "productId": 30, "name": "유기농 시금치", "quantity": 1, "originalUnitPrice": 3000, "discountRate": 50, "unitPrice": 1500, "lineAmount": 1500, "productClosingAt": "2026-08-28T22:00:00+09:00" }
  ],
  "earliestClosingAt": "2026-08-28T21:00:00+09:00"
}
```

- 이 시점에 전 품목의 재고가 선점되고 금액이 고정된다 (BR-005, BR-007).
- 한 품목이라도 부족하면 어떤 품목도 선점하지 않는다. 부분 선점을 만들지 않는다 (BR-027).
- 픽업 시간대는 아직 지정하지 않은 상태다. `earliestClosingAt`은 선택 가능한 시간대 상한 판정 기준이다 (BR-017).

**오류**
| 코드 | HTTP | 조건 | 규칙 |
|---|---|---|---|
| `UNAUTHENTICATED` | 401 | 미인증 상태에서 주문 시도 | BR-002, FR-004 |
| `ORDER_RESTRICTED` | 403 | 노쇼 누적 제한 상태. `details.restrictedUntil` 포함 | BR-023, FR-032 |
| `PENDING_ORDER_EXISTS` | 409 | 유효한 PENDING 주문서 보유. `details.orderId`, `details.holdExpiresAt` 포함 | BR-007, FR-019 |
| `CART_EMPTY` | 409 | 대상 품목 없음 | BR-010, FR-017 |
| `CART_HAS_UNAVAILABLE_ITEM` | 409 | 마감·숨김 품목 포함. `details.items[]`에 사유별로 나열 | BR-030, FR-018 |
| `OUT_OF_STOCK` | 409 | 한 품목이라도 수량 부족. `details.shortages[]`에 품목별 부족 수량 | BR-006, BR-027, FR-019 |
| `MAX_QUANTITY_EXCEEDED` | 409 | 장바구니 수량이 그 사이 낮아진 `maxOrderQuantity`를 초과 | BR-009 |
| `PRODUCT_CLOSED` | 409 | 대상 품목이 마감됨 | BR-030, FR-034 |

동시에 마지막 1개를 요청한 두 고객 중 실패한 쪽은 `OUT_OF_STOCK`을 받는다 (02 CS-04, 13번 §3).

### API-018 · 선점 잔여 시간 조회

- `GET /api/orders/{orderId}/hold`
- 권한: 고객 (본인 주문)
- 관련 요구사항: FR-020, FR-005
- 관련 규칙: BR-007, BR-008, BR-028

**응답 (200)**
```json
{
  "orderId": 1001,
  "status": "PENDING",
  "serverTime": "2026-08-28T19:13:20+09:00",
  "holdExpiresAt": "2026-08-28T19:14:12+09:00",
  "holdRemainingSeconds": 52,
  "expiringSoon": true,
  "paymentAttemptRemaining": 2
}
```

`expiringSoon`은 잔여 60초 이하일 때 true다 (03 U1 임시 채택값 1분). 이미 만료된 경우 `status`는 `EXPIRED`, `holdRemainingSeconds`는 0으로 반환하고 오류를 내지 않는다. 조회 시점에 지연 정리가 실행된다 (13번 §2).

**오류**
| 코드 | HTTP | 조건 | 규칙 |
|---|---|---|---|
| `NOT_FOUND` | 404 | 본인 주문이 아님 | BR-002 |

### API-019 · 주문서 포기

- `DELETE /api/orders/{orderId}`
- 권한: 고객 (본인 주문)
- 관련 요구사항: FR-020
- 관련 규칙: BR-008

**응답 (200)**
```json
{ "orderId": 1001, "status": "EXPIRED", "releasedAt": "2026-08-28T19:06:40+09:00" }
```

결제 전 주문서 포기는 CANCELED가 아니라 EXPIRED로 처리하고 선점을 즉시 해제한다 (05 §2.2, 05 A3).

**오류**
| 코드 | HTTP | 조건 | 규칙 |
|---|---|---|---|
| `NOT_FOUND` | 404 | 본인 주문이 아님 | BR-002 |
| `INVALID_ORDER_STATUS` | 409 | PENDING이 아닌 주문. `details.currentStatus` 포함 | 05 §2.3 |

### API-020 · 선택 가능한 픽업 시간대 조회

- `GET /api/orders/{orderId}/pickup-slots?date=2026-08-28`
- 권한: 고객 (본인 주문)
- 관련 요구사항: FR-022, FR-023
- 관련 규칙: BR-013, BR-014, BR-015, BR-016, BR-017

**요청 파라미터**
| 이름 | 필수 | 설명 |
|---|---|---|
| `date` | N | 생략하면 선택 가능한 날짜별로 모두 반환한다 |

**응답 (200)**
```json
{
  "serverTime": "2026-08-28T19:04:12+09:00",
  "selectableDates": [
    { "date": "2026-08-28", "label": "D+0", "selectable": true },
    { "date": "2026-08-29", "label": "D+1", "selectable": false, "unselectableReason": "AFTER_PRODUCT_CLOSING" }
  ],
  "slots": [
    { "slotId": 341, "date": "2026-08-28", "startAt": "2026-08-28T19:30:00+09:00", "endAt": "2026-08-28T20:00:00+09:00", "capacity": 20, "reservedCount": 18, "selectable": true },
    { "slotId": 342, "date": "2026-08-28", "startAt": "2026-08-28T20:00:00+09:00", "endAt": "2026-08-28T20:30:00+09:00", "capacity": 20, "reservedCount": 20, "selectable": false, "unselectableReason": "SLOT_FULL" },
    { "slotId": 340, "date": "2026-08-28", "startAt": "2026-08-28T19:00:00+09:00", "endAt": "2026-08-28T19:30:00+09:00", "capacity": 20, "reservedCount": 5, "selectable": false, "unselectableReason": "RESERVATION_CLOSED" },
    { "slotId": 345, "date": "2026-08-28", "startAt": "2026-08-28T21:30:00+09:00", "endAt": "2026-08-28T22:00:00+09:00", "capacity": 20, "reservedCount": 2, "selectable": false, "unselectableReason": "AFTER_PRODUCT_CLOSING" }
  ]
}
```

`unselectableReason`은 `RESERVATION_CLOSED`(시작 30분 전 경과) / `SLOT_FULL`(정원 도달) / `AFTER_PRODUCT_CLOSING`(품목 최이른 마감 시각 초과) / `BLOCKED`(관리자 차단) / `HOLIDAY`(휴무일) 중 하나다. 사유를 구분해 내려줘야 FR-023 예외를 만족한다.

**오류**
| 코드 | HTTP | 조건 | 규칙 |
|---|---|---|---|
| `NOT_FOUND` | 404 | 본인 주문이 아님 | BR-002 |
| `INVALID_ORDER_STATUS` | 409 | PENDING이 아닌 주문 | 05 §2.3 |
| `HOLD_EXPIRED` | 409 | 선점 만료 | BR-007 |
| `SLOT_DATE_OUT_OF_RANGE` | 409 | `date`가 D+0~D+1 밖 | BR-013 |

선택 가능한 시간대가 하나도 없어도 오류가 아니라 빈 목록 + 사유가 담긴 응답을 반환한다 (FR-022 예외).

### API-021 · 픽업 시간대 지정

- `PATCH /api/orders/{orderId}/pickup-slot`
- 권한: 고객 (본인 주문)
- 관련 요구사항: FR-022, FR-023
- 관련 규칙: BR-013, BR-015, BR-016, BR-017

**요청**
```json
{ "slotId": 341 }
```

**응답 (200)**
```json
{
  "orderId": 1001,
  "pickupSlotId": 341,
  "pickupStartAt": "2026-08-28T19:30:00+09:00",
  "pickupEndAt": "2026-08-28T20:00:00+09:00",
  "holdRemainingSeconds": 480
}
```

이 시점에는 정원을 점유하지 않는다. 정원 점유는 결제 성공 시점에 한다 (05 §8, 05 A10).

**오류**
| 코드 | HTTP | 조건 | 규칙 |
|---|---|---|---|
| `NOT_FOUND` | 404 | 본인 주문이 아님 | BR-002 |
| `SLOT_NOT_FOUND` | 404 | 없는 시간대 | BR-014 |
| `INVALID_ORDER_STATUS` | 409 | PENDING이 아닌 주문 | 05 §2.3 |
| `HOLD_EXPIRED` | 409 | 선점 만료 | BR-007, BR-008 |
| `SLOT_CLOSED` | 409 | 예약 마감 또는 관리자 차단 | BR-015, BR-016 |
| `SLOT_FULL` | 409 | 정원 도달 | BR-016 |
| `SLOT_AFTER_PRODUCT_CLOSING` | 409 | 시작 시각 > 품목 최이른 마감 시각 | BR-017 |
| `SLOT_DATE_OUT_OF_RANGE` | 409 | D+2 이후 또는 휴무일 | BR-013 |

---

## 5. 고객 API — 결제와 주문 확정

### API-022 · 가상 결제 요청

- `POST /api/orders/{orderId}/payments`
- 권한: 고객 (본인 주문)
- 관련 요구사항: FR-021, FR-024, FR-025, FR-026
- 관련 규칙: BR-011, BR-012, BR-015, BR-016, BR-026, BR-029

**요청 헤더** — `Idempotency-Key: 6f2e1a...` (필수). 같은 키로 재전송하면 앞선 시도의 결과를 그대로 반환하고 시도 횟수를 늘리지 않는다.

**요청**
```json
{ "amount": 13500 }
```

**응답 (200) — 성공**
```json
{
  "result": "SUCCEEDED",
  "orderId": 1001,
  "orderNo": "ORD-20260828-000123",
  "status": "CONFIRMED",
  "pickupNumber": "017",
  "pickupBusinessDate": "2026-08-28",
  "pickupStartAt": "2026-08-28T19:30:00+09:00",
  "pickupEndAt": "2026-08-28T20:00:00+09:00",
  "paidAmount": 13500,
  "cancelableUntil": "2026-08-28T18:30:00+09:00",
  "noShowDueAt": "2026-08-28T20:30:00+09:00",
  "confirmedAt": "2026-08-28T19:06:03+09:00"
}
```

**응답 (200) — 실패 (1~2회째)**
```json
{
  "result": "FAILED",
  "code": "PAYMENT_FAILED",
  "orderId": 1001,
  "status": "PENDING",
  "attemptNo": 1,
  "paymentAttemptRemaining": 2,
  "holdExpiresAt": "2026-08-28T19:14:12+09:00",
  "holdRemainingSeconds": 489,
  "failureReason": "DECLINED",
  "message": "결제가 실패했습니다. 남은 시간 안에 다시 시도할 수 있습니다."
}
```

선점은 유지되고 재고는 복구하지 않는다 (BR-012-1, 02 CS-03).

**응답 (200) — 실패 (3회째)**
```json
{
  "result": "FAILED",
  "code": "PAYMENT_FAILED",
  "orderId": 1001,
  "status": "FAILED",
  "attemptNo": 3,
  "paymentAttemptRemaining": 0,
  "holdReleased": true,
  "failureReason": "TIMEOUT",
  "message": "결제가 3회 실패해 주문이 종료됐습니다. 다시 주문해 주세요."
}
```

3회째 실패 시 주문은 FAILED, 선점은 RELEASED가 되고 재고가 즉시 복구된다 (BR-012-3). 픽업 번호는 발급하지 않는다 (BR-026, FR-025).

**결제 처리 순서** (모두 하나의 트랜잭션)
1. 주문 상태가 PENDING이고 본인 주문인지 확인한다.
2. `holdExpiresAt > 서버 시각`인지 확인한다. 지났으면 EXPIRED로 종결하고 `HOLD_EXPIRED`를 반환한다 (BR-012-4).
3. `amount = orders.total_amount`인지 확인한다. 다르면 시도 기록을 만들지 않고 `AMOUNT_MISMATCH` (BR-029, 05 §4.3-6).
4. `pickup_slot_id`가 지정됐는지, 예약 마감 전인지, 정원이 남았는지 재확인한다 (BR-015, BR-016).
5. PAYMENT_ATTEMPT를 `attempt_no = payment_attempt_count + 1`로 만든다. 4회째면 `PAYMENT_ATTEMPT_EXCEEDED`.
6. 가상 결제 결과를 판정한다. 무응답·예외는 `TIMEOUT` 실패로 기록한다 (BR-011).
7. 성공이면 선점을 CONSUMED로, 재고를 확정 차감으로 전환하고, 슬롯 `reserved_count`를 +1 하고, 픽업 번호를 발급한다.
8. 실패이고 시도 수가 3이면 선점을 RELEASED로 바꾸고 재고를 복구한다.

**오류**
| 코드 | HTTP | 조건 | 규칙 |
|---|---|---|---|
| `NOT_FOUND` | 404 | 본인 주문이 아님 | BR-002 |
| `VALIDATION_ERROR` | 400 | `Idempotency-Key` 누락 / `amount` 누락 | BR-029 |
| `INVALID_ORDER_STATUS` | 409 | PENDING이 아닌 주문(EXPIRED·FAILED·CONFIRMED 등) | 05 §2.3, BR-012 |
| `HOLD_EXPIRED` | 409 | 선점 만료. 주문을 EXPIRED로 종결하고 반환 | BR-007, BR-012-4 |
| `AMOUNT_MISMATCH` | 409 | 요청 금액 ≠ 확정 금액. `details.expectedAmount` 포함 | BR-029 |
| `SLOT_NOT_SELECTED` | 409 | 시간대 미지정 | BR-016, FR-023 |
| `SLOT_CLOSED` | 409 | 결제 직전 예약 마감 경과 | BR-015 |
| `SLOT_FULL` | 409 | 결제 직전 정원 소진 | BR-016 |
| `PAYMENT_ATTEMPT_EXCEEDED` | 409 | 4회째 시도 | BR-012-2 |
| `ALREADY_PAID` | 409 | 성공 기록이 이미 존재 | BR-011, 05 §4.3-4 |
| `PICKUP_NUMBER_EXHAUSTED` | 409 | 영업일 픽업 번호 999 소진 | BR-026 |

### API-023 · 주문 내역 조회

- `GET /api/orders?status=IN_PROGRESS&page=0&size=20`
- 권한: 고객 (본인 주문)
- 관련 요구사항: FR-027
- 관련 규칙: BR-002

**요청 파라미터**
| 이름 | 필수 | 기본값 | 설명 |
|---|---|---|---|
| `status` | N | 없음 | `IN_PROGRESS`(CONFIRMED·READY) / `COMPLETED` / `CANCELED` / `NO_SHOW` |
| `includeExpired` | N | `false` | true일 때만 EXPIRED·FAILED 주문을 포함한다 (FR-027) |

**응답 (200)**
```json
{
  "items": [
    { "orderId": 1001, "orderNo": "ORD-20260828-000123", "orderedAt": "2026-08-28T19:04:12+09:00", "status": "CONFIRMED", "pickupStartAt": "2026-08-28T19:30:00+09:00", "pickupEndAt": "2026-08-28T20:00:00+09:00", "pickupNumber": "017", "totalAmount": 13500, "itemSummary": "국내산 삼겹살 300g 외 1건" }
  ],
  "page": { "number": 0, "size": 20, "totalElements": 1 }
}
```

다른 고객의 주문은 어떤 파라미터로도 조회되지 않는다. 조회 조건에 항상 `member_id = 인증 주체`를 강제한다 (BR-002, FR-027).

**오류**
| 코드 | HTTP | 조건 | 규칙 |
|---|---|---|---|
| `UNAUTHENTICATED` | 401 | 미인증 | BR-002, FR-004 |
| `VALIDATION_ERROR` | 400 | `status` 허용값 아님 | FR-027 |

### API-024 · 주문 상세 조회

- `GET /api/orders/{orderId}`
- 권한: 고객 (본인 주문)
- 관련 요구사항: FR-028, FR-030, FR-031, FR-033
- 관련 규칙: BR-018, BR-021, BR-022, BR-026

**응답 (200)**
```json
{
  "serverTime": "2026-08-28T19:20:00+09:00",
  "orderId": 1001,
  "orderNo": "ORD-20260828-000123",
  "status": "CONFIRMED",
  "orderedAt": "2026-08-28T19:04:12+09:00",
  "items": [
    { "productId": 12, "name": "국내산 삼겹살 300g", "quantity": 2, "unitPrice": 6000, "lineAmount": 12000 },
    { "productId": 30, "name": "유기농 시금치", "quantity": 1, "unitPrice": 1500, "lineAmount": 1500 }
  ],
  "totalAmount": 13500,
  "pickupNumber": "017",
  "pickupStartAt": "2026-08-28T19:30:00+09:00",
  "pickupEndAt": "2026-08-28T20:00:00+09:00",
  "noShowDueAt": "2026-08-28T20:30:00+09:00",
  "cancelable": false,
  "cancelableUntil": "2026-08-28T18:30:00+09:00",
  "cancelUnavailableReason": "CANCEL_DEADLINE_PASSED",
  "canceledBy": null,
  "cancelReason": null,
  "store": { "name": "savePick 신선마켓", "address": "서울특별시 ○○구 ○○로 12", "phone": "0212345678" },
  "statusHistory": [
    { "toStatus": "PENDING", "actorType": "CUSTOMER", "occurredAt": "2026-08-28T19:04:12+09:00" },
    { "toStatus": "CONFIRMED", "actorType": "CUSTOMER", "occurredAt": "2026-08-28T19:06:03+09:00" }
  ]
}
```

- `cancelUnavailableReason`은 `CANCEL_DEADLINE_PASSED` / `ALREADY_COMPLETED` / `ALREADY_CANCELED` / `NO_SHOW` 중 하나다 (FR-030).
- NO_SHOW 주문이면 `noShowAt`과 `refunded: false`를 함께 반환한다 (BR-022, FR-031).
- 관리자 취소 건은 `canceledBy: "ADMIN"`과 `cancelReason`이 채워진다 (FR-028, BR-020).

**오류**
| 코드 | HTTP | 조건 | 규칙 |
|---|---|---|---|
| `NOT_FOUND` | 404 | 본인 주문이 아니거나 없음. 타인 주문임을 알리지 않는다 | BR-002, FR-028 예외 |
| `UNAUTHENTICATED` | 401 | 미인증 | BR-002 |

### API-025 · 주문 취소 (고객)

- `POST /api/orders/{orderId}/cancel`
- 권한: 고객 (본인 주문)
- 관련 요구사항: FR-029, FR-030
- 관련 규칙: BR-018, BR-019, BR-024

**요청**
```json
{ "confirmed": true }
```

`confirmed`가 true가 아니면 실행하지 않는다. 전체 취소임을 확인받는 단계다 (FR-030, BR-024).

**응답 (200)**
```json
{
  "orderId": 1001,
  "status": "CANCELED",
  "canceledAt": "2026-08-28T18:31:00+09:00",
  "canceledBy": "CUSTOMER",
  "slotReleased": true,
  "stockResults": [
    { "productId": 12, "quantity": 2, "restored": true, "reason": "CANCEL_RESTORE" },
    { "productId": 30, "quantity": 1, "restored": false, "reason": "CANCEL_DISCARD", "note": "상품 마감 시각 경과" }
  ]
}
```

- 품목별로 마감 시각 도달 여부를 따져 복구·폐기를 결정한다 (BR-019 "일부만 마감 시각이 지난 경우 마감 전 품목만 복구").
- 픽업 시간대 정원은 취소 시점과 무관하게 항상 1건 반납한다 (BR-019, 05 §8).
- 부분 취소는 지원하지 않는다 (BR-024).

**오류**
| 코드 | HTTP | 조건 | 규칙 |
|---|---|---|---|
| `NOT_FOUND` | 404 | 본인 주문이 아님 | BR-002 |
| `VALIDATION_ERROR` | 400 | `confirmed`가 true가 아님 | FR-030 |
| `CANCEL_DEADLINE_PASSED` | 409 | 픽업 시작 1시간 전 경과. `details.cancelableUntil` 포함 | BR-018 |
| `CANCEL_NOT_ALLOWED` | 409 | COMPLETED·NO_SHOW·CANCELED 주문 | BR-020, FR-029 |
| `INVALID_ORDER_STATUS` | 409 | PENDING 주문 취소 시도. EXPIRED 처리(API-019)를 안내한다 | 05 §2.3 |

---

## 6. 관리자 API — 인증

### API-101 · 관리자 로그인

- `POST /api/admin/auth/login`
- 권한: 비로그인 (관리자 계정만 성공)
- 관련 요구사항: FR-002, FR-004
- 관련 규칙: BR-002

**요청**
```json
{ "email": "owner@savepick.store", "password": "..." }
```

**응답 (200)**
```json
{ "memberId": 1, "name": "상현", "role": "ADMIN", "accessToken": "eyJhbGciOi...", "accessTokenExpiresAt": "2026-08-28T09:30:00+09:00" }
```

관리자 진입 경로를 고객과 분리한다 (02 U4). 고객 계정으로 이 경로에 로그인하면 성공시키지 않는다.

**오류**
| 코드 | HTTP | 조건 | 규칙 |
|---|---|---|---|
| `INVALID_CREDENTIALS` | 401 | 이메일·비밀번호 불일치 | BR-002 |
| `FORBIDDEN` | 403 | `role = 'CUSTOMER'` 계정. 어떤 데이터도 반환하지 않는다 | BR-002, FR-004 |
| `LOGIN_BLOCKED` | 429 | 연속 실패 5회 후 10분 이내 | BR-002 |

---

## 7. 관리자 API — 상품

### API-102 · 상품 목록 조회 (관리자)

- `GET /api/admin/products?status=ON_SALE&page=0&size=20`
- 권한: 관리자
- 관련 요구사항: FR-042, FR-044
- 관련 규칙: BR-004, BR-030

**응답 (200)**
```json
{
  "serverTime": "2026-08-28T09:35:00+09:00",
  "items": [
    { "productId": 12, "name": "국내산 삼겹살 300g", "status": "ON_SALE", "originalPrice": 12000, "currentDiscountRate": 30, "currentPrice": 8400, "nextDiscountRate": 50, "nextDiscountAt": "2026-08-28T15:00:00+09:00", "closingAt": "2026-08-28T21:00:00+09:00", "totalQuantity": 20, "availableQuantity": 20 }
  ],
  "page": { "number": 0, "size": 20, "totalElements": 12 }
}
```

고객 목록과 달리 DRAFT·HIDDEN·CLOSED를 모두 조회할 수 있다.

**오류**
| 코드 | HTTP | 조건 | 규칙 |
|---|---|---|---|
| `UNAUTHENTICATED` | 401 | 미인증 | BR-002 |
| `FORBIDDEN` | 403 | 고객 권한 | BR-002, FR-004 |

### API-103 · 상품 등록

- `POST /api/admin/products`
- 권한: 관리자
- 관련 요구사항: FR-040, FR-043
- 관련 규칙: BR-003, BR-009

**요청**
```json
{ "name": "국내산 삼겹살 300g", "description": "오늘 손질한 국내산 삼겹살입니다.", "saleUnit": "300g", "originalPrice": 12000, "closingAt": "2026-08-28T21:00:00+09:00", "maxOrderQuantity": 5 }
```

**응답 (201)**
```json
{ "productId": 12, "status": "DRAFT", "name": "국내산 삼겹살 300g", "originalPrice": 12000, "closingAt": "2026-08-28T21:00:00+09:00", "maxOrderQuantity": 5 }
```

등록 직후 상태는 DRAFT이며 고객에게 노출되지 않는다 (FR-040, 05 §5.2).

**오류**
| 코드 | HTTP | 조건 | 규칙 |
|---|---|---|---|
| `VALIDATION_ERROR` | 400 | 필수값 누락 / `originalPrice < 100` / `maxOrderQuantity < 1` | BR-004, BR-009, FR-040 |
| `CLOSING_TIME_INVALID` | 400 | 마감 시각이 과거이거나 영업 종료 시각(22:00) 초과 | BR-003, FR-043 |
| `FORBIDDEN` | 403 | 고객 권한 | BR-002 |

### API-104 · 상품 상세 조회 (관리자)

- `GET /api/admin/products/{productId}`
- 권한: 관리자
- 관련 요구사항: FR-041, FR-044, FR-046
- 관련 규칙: BR-004, BR-006

**응답 (200)**
```json
{
  "productId": 12,
  "name": "국내산 삼겹살 300g",
  "description": "오늘 손질한 국내산 삼겹살입니다.",
  "saleUnit": "300g",
  "originalPrice": 12000,
  "closingAt": "2026-08-28T21:00:00+09:00",
  "maxOrderQuantity": 5,
  "status": "ON_SALE",
  "currentDiscountRate": 30,
  "currentPrice": 8400,
  "nextDiscountRate": 50,
  "nextDiscountAt": "2026-08-28T15:00:00+09:00",
  "stock": { "totalQuantity": 20, "availableQuantity": 12, "heldQuantity": 2, "confirmedQuantity": 6, "discardedQuantity": 0 }
}
```

**오류**
| 코드 | HTTP | 조건 | 규칙 |
|---|---|---|---|
| `NOT_FOUND` | 404 | 없는 상품 | — |
| `FORBIDDEN` | 403 | 고객 권한 | BR-002 |

### API-105 · 상품 수정

- `PATCH /api/admin/products/{productId}`
- 권한: 관리자
- 관련 요구사항: FR-041, FR-043
- 관련 규칙: BR-003, BR-005

**요청**
```json
{ "originalPrice": 11000, "closingAt": "2026-08-28T20:00:00+09:00", "maxOrderQuantity": 3, "confirmEarlierClosing": true }
```

**응답 (200)**
```json
{
  "productId": 12,
  "originalPrice": 11000,
  "closingAt": "2026-08-28T20:00:00+09:00",
  "maxOrderQuantity": 3,
  "changedFields": ["originalPrice", "closingAt", "maxOrderQuantity"],
  "affectedConfirmedOrderCount": 2,
  "updatedAt": "2026-08-28T14:02:11+09:00"
}
```

- 이미 확정된 주문의 단가·결제 금액은 바뀌지 않는다. `order_items` 스냅샷을 수정하지 않는다 (FR-041, BR-005).
- 마감 시각을 앞당겨 이미 확정된 주문의 픽업 시간대보다 빨라지면 `confirmEarlierClosing: true` 없이는 실행하지 않고 `VALIDATION_ERROR`와 함께 영향 주문 수를 반환한다 (FR-041 예외).
- 변경된 항목은 PRODUCT_CHANGE_LOG에 기록한다 (FR-041).

**오류**
| 코드 | HTTP | 조건 | 규칙 |
|---|---|---|---|
| `NOT_FOUND` | 404 | 없는 상품 | — |
| `VALIDATION_ERROR` | 400 | 값 범위 위반 / 확정 주문 영향 확인 미동의(`details.affectedConfirmedOrderCount` 포함) | FR-041 |
| `CLOSING_TIME_INVALID` | 400 | 마감 시각이 과거이거나 영업 종료 초과 | BR-003 |
| `PRODUCT_STATUS_TRANSITION_DENIED` | 409 | CLOSED 상품의 마감 시각 수정 시도 | 05 §5.3, FR-043 예외 |
| `FORBIDDEN` | 403 | 고객 권한 | BR-002 |

### API-106 · 상품 판매 상태 전환

- `PATCH /api/admin/products/{productId}/status`
- 권한: 관리자
- 관련 요구사항: FR-042
- 관련 규칙: BR-025, BR-030

**요청**
```json
{ "status": "ON_SALE" }
```

**응답 (200)**
```json
{ "productId": 12, "status": "ON_SALE", "changedAt": "2026-08-28T09:40:00+09:00", "keptHoldCount": 0, "keptConfirmedOrderCount": 0 }
```

HIDDEN 전환은 기존 HELD 선점과 확정 주문을 취소하지 않고 신규 주문서 생성만 막는다 (FR-042, 05 §5.2).

**오류**
| 코드 | HTTP | 조건 | 규칙 |
|---|---|---|---|
| `NOT_FOUND` | 404 | 없는 상품 | — |
| `VALIDATION_ERROR` | 400 | `status` 허용값 아님 | 05 §5.1 |
| `PRODUCT_STATUS_TRANSITION_DENIED` | 409 | CLOSED에서의 전환 / `ON_SALE → DRAFT` / 재고 미등록 상태의 `DRAFT → ON_SALE` | 05 §5.3, FR-042 |
| `FORBIDDEN` | 403 | 고객 권한 | BR-002 |

### API-107 · 상품 수정 이력 조회

- `GET /api/admin/products/{productId}/change-logs?page=0&size=20`
- 권한: 관리자
- 관련 요구사항: FR-041
- 관련 규칙: BR-003

**응답 (200)**
```json
{
  "items": [
    { "changedField": "originalPrice", "beforeValue": "12000", "afterValue": "11000", "actorName": "상현", "changedAt": "2026-08-28T14:02:11+09:00" }
  ],
  "page": { "number": 0, "size": 20, "totalElements": 3 }
}
```

**오류**
| 코드 | HTTP | 조건 | 규칙 |
|---|---|---|---|
| `NOT_FOUND` | 404 | 없는 상품 | — |
| `FORBIDDEN` | 403 | 고객 권한 | BR-002 |

### API-108 · 할인 구간 정책 조회

- `GET /api/admin/discount-policy`
- 권한: 관리자
- 관련 요구사항: FR-044
- 관련 규칙: BR-004

**응답 (200)**
```json
{
  "tiers": [
    { "code": "D0", "remainingFrom": null, "remainingToHours": null, "condition": "24시간 초과", "discountRate": 0 },
    { "code": "D1", "condition": "24시간 이하 ~ 6시간 초과", "discountRate": 30 },
    { "code": "D2", "condition": "6시간 이하 ~ 2시간 초과", "discountRate": 50 },
    { "code": "D3", "condition": "2시간 이하 ~ 마감 이전", "discountRate": 70 }
  ],
  "rounding": "10원 단위 내림",
  "minimumPrice": 100,
  "boundaryRule": "경계값은 더 큰 할인율 구간에 속한다",
  "editable": false
}
```

`editable: false`는 관리자가 개별 주문·상품의 할인율을 임의로 바꿀 수 없음을 뜻한다 (BR-004, FR-044).

**오류**
| 코드 | HTTP | 조건 | 규칙 |
|---|---|---|---|
| `FORBIDDEN` | 403 | 고객 권한 | BR-002 |

---

## 8. 관리자 API — 재고

### API-109 · 재고 등록·조정

- `PUT /api/admin/products/{productId}/stock`
- 권한: 관리자
- 관련 요구사항: FR-045
- 관련 규칙: BR-006, BR-025

**요청**
```json
{ "totalQuantity": 17, "note": "실물 진열 수량 확인" }
```

총 재고의 **목표값**을 보낸다. 증감량이 아니라 절대값을 받는 이유는 관리자가 실물을 세어 입력하는 조작이기 때문이며, 증감량 방식은 요청 재전송 시 이중 반영 위험이 있다.

**응답 (200)**
```json
{
  "productId": 12,
  "before": { "totalQuantity": 20, "availableQuantity": 12, "heldQuantity": 2, "confirmedQuantity": 6 },
  "after": { "totalQuantity": 17, "availableQuantity": 9, "heldQuantity": 2, "confirmedQuantity": 6 },
  "minimumSettableQuantity": 8,
  "changedAt": "2026-08-28T14:10:00+09:00"
}
```

**오류**
| 코드 | HTTP | 조건 | 규칙 |
|---|---|---|---|
| `NOT_FOUND` | 404 | 없는 상품 | — |
| `VALIDATION_ERROR` | 400 | 음수 또는 정수가 아닌 수량 | BR-006, FR-045 예외 |
| `STOCK_BELOW_COMMITTED` | 409 | `totalQuantity < heldQuantity + confirmedQuantity`. `details.minimumSettableQuantity` 포함 | BR-025, FR-045 |
| `FORBIDDEN` | 403 | 고객 권한 | BR-002 |

조정 중 들어온 주문과의 충돌 처리는 13번 문서 §6에서 정의한다.

### API-110 · 재고 현황 조회

- `GET /api/admin/stocks?onlyUnavailable=false&page=0&size=20`
- 권한: 관리자
- 관련 요구사항: FR-046
- 관련 규칙: BR-006

**응답 (200)**
```json
{
  "serverTime": "2026-08-28T14:11:00+09:00",
  "items": [
    { "productId": 12, "name": "국내산 삼겹살 300g", "status": "ON_SALE", "totalQuantity": 17, "availableQuantity": 9, "heldQuantity": 2, "confirmedQuantity": 6, "discardedQuantity": 0, "consistent": true }
  ],
  "page": { "number": 0, "size": 20, "totalElements": 12 }
}
```

- `heldQuantity`에는 만료된 선점이 포함되지 않는다 (FR-046, 05 C5).
- `consistent`는 `totalQuantity = availableQuantity + heldQuantity + confirmedQuantity` 성립 여부다 (FR-046).
- `onlyUnavailable=true`이면 `availableQuantity = 0`인 상품만 반환한다 (FR-046).

**오류**
| 코드 | HTTP | 조건 | 규칙 |
|---|---|---|---|
| `FORBIDDEN` | 403 | 고객 권한 | BR-002 |

### API-111 · 재고 변경 이력 조회

- `GET /api/admin/stocks/{productId}/ledger?page=0&size=50`
- 권한: 관리자
- 관련 요구사항: FR-047
- 관련 규칙: BR-006, BR-019

**응답 (200)**
```json
{
  "items": [
    { "ledgerId": 8821, "reason": "CANCEL_DISCARD", "orderId": 1001, "orderNo": "ORD-20260828-000123", "deltaTotal": -1, "deltaConfirmed": -1, "afterTotal": 16, "afterAvailable": 9, "actorType": "CUSTOMER", "note": "상품 마감 시각 경과로 복구하지 않음", "occurredAt": "2026-08-28T21:10:00+09:00" },
    { "ledgerId": 8790, "reason": "HOLD_EXPIRE", "orderId": 1000, "orderNo": "ORD-20260828-000120", "deltaHeld": -2, "afterAvailable": 11, "actorType": "SYSTEM", "occurredAt": "2026-08-28T19:14:30+09:00" }
  ],
  "page": { "number": 0, "size": 50, "totalElements": 24 }
}
```

`reason`은 `ADMIN_ADJUST` / `HOLD` / `HOLD_RELEASE` / `HOLD_EXPIRE` / `CONFIRM` / `CANCEL_RESTORE` / `CANCEL_DISCARD`로 구분된다 (FR-047). 이력은 수정·삭제할 수 없다.

**오류**
| 코드 | HTTP | 조건 | 규칙 |
|---|---|---|---|
| `NOT_FOUND` | 404 | 없는 상품 | — |
| `FORBIDDEN` | 403 | 고객 권한 | BR-002 |

---

## 9. 관리자 API — 주문

### API-112 · 주문 목록 조회

- `GET /api/admin/orders?pickupDate=2026-08-28&slotId=341&status=CONFIRMED&page=0&size=20`
- 권한: 관리자
- 관련 요구사항: FR-048, FR-053
- 관련 규칙: BR-002, BR-021

**요청 파라미터**
| 이름 | 필수 | 기본값 | 설명 |
|---|---|---|---|
| `pickupDate` | N | 오늘·내일 | 픽업 날짜 (FR-048) |
| `slotId` | N | 없음 | 픽업 시간대 |
| `status` | N | PENDING·EXPIRED 제외 전체 | 주문 상태. `NO_SHOW` 지정 시 노쇼 목록이 된다 (FR-053) |

**응답 (200)**
```json
{
  "items": [
    { "orderId": 1001, "orderNo": "ORD-20260828-000123", "pickupNumber": "017", "customerName": "지현", "status": "CONFIRMED", "pickupStartAt": "2026-08-28T19:30:00+09:00", "pickupEndAt": "2026-08-28T20:00:00+09:00", "noShowDueAt": "2026-08-28T20:30:00+09:00", "totalAmount": 13500, "itemCount": 2 }
  ],
  "page": { "number": 0, "size": 20, "totalElements": 14 }
}
```

PENDING·EXPIRED는 `status`로 명시하지 않는 한 반환하지 않는다 (FR-048). 노쇼 전환 예정 시각(`noShowDueAt`)을 함께 내려 유예 중인 주문을 구분한다 (02 AS-04 2단계).

**오류**
| 코드 | HTTP | 조건 | 규칙 |
|---|---|---|---|
| `VALIDATION_ERROR` | 400 | 날짜 형식 오류 / `status` 허용값 아님 | FR-048 |
| `FORBIDDEN` | 403 | 고객 권한 | BR-002 |

### API-113 · 픽업 번호로 주문 조회

- `GET /api/admin/orders/by-pickup-number?businessDate=2026-08-28&pickupNumber=017`
- 권한: 관리자
- 관련 요구사항: FR-049
- 관련 규칙: BR-026

**응답 (200)** — API-114와 동일한 주문 상세 객체를 반환한다.

`businessDate`를 생략하면 오늘 영업일로 조회한다. 다른 영업일의 같은 번호는 함께 반환되지 않는다 (FR-049).

**오류**
| 코드 | HTTP | 조건 | 규칙 |
|---|---|---|---|
| `NOT_FOUND` | 404 | 해당 영업일에 없는 픽업 번호 | BR-026, FR-049 |
| `VALIDATION_ERROR` | 400 | 번호가 1~999 범위 밖 | BR-026 |
| `FORBIDDEN` | 403 | 고객 권한 | BR-002 |

### API-114 · 주문 상세 조회 (관리자)

- `GET /api/admin/orders/{orderId}`
- 권한: 관리자
- 관련 요구사항: FR-050
- 관련 규칙: BR-002, BR-011

**응답 (200)**
```json
{
  "orderId": 1001,
  "orderNo": "ORD-20260828-000123",
  "status": "CONFIRMED",
  "pickupNumber": "017",
  "pickupStartAt": "2026-08-28T19:30:00+09:00",
  "pickupEndAt": "2026-08-28T20:00:00+09:00",
  "customer": { "name": "지현", "phone": "01098765432" },
  "items": [
    { "productId": 12, "name": "국내산 삼겹살 300g", "quantity": 2, "unitPrice": 6000, "lineAmount": 12000 }
  ],
  "totalAmount": 13500,
  "paymentAttempts": [
    { "attemptNo": 1, "status": "FAILED", "failureReason": "DECLINED", "requestedAt": "2026-08-28T19:05:10+09:00", "resolvedAt": "2026-08-28T19:05:11+09:00" },
    { "attemptNo": 2, "status": "SUCCEEDED", "requestedAt": "2026-08-28T19:06:02+09:00", "resolvedAt": "2026-08-28T19:06:03+09:00" }
  ],
  "statusHistory": [
    { "fromStatus": null, "toStatus": "PENDING", "actorType": "CUSTOMER", "occurredAt": "2026-08-28T19:04:12+09:00" },
    { "fromStatus": "PENDING", "toStatus": "CONFIRMED", "actorType": "CUSTOMER", "occurredAt": "2026-08-28T19:06:03+09:00" }
  ],
  "availableActions": ["READY", "COMPLETE", "CANCEL"]
}
```

고객 연락처는 픽업 응대 목적으로만 반환한다 (FR-050).

`pickupStartAt`·`pickupEndAt`은 06번 SC-109·SC-110의 "픽업 날짜·시간대" 표시에 쓴다. 시각은 지정된 픽업 시간대에 있으므로 아직 시간대를 고르지 않은 주문에서는 `null`이다 (API-112의 같은 필드와 같은 규칙).

**오류**
| 코드 | HTTP | 조건 | 규칙 |
|---|---|---|---|
| `NOT_FOUND` | 404 | 없는 주문 | — |
| `FORBIDDEN` | 403 | 고객 권한 | BR-002 |

### API-115 · 픽업 준비 완료 처리

- `POST /api/admin/orders/{orderId}/ready`
- 권한: 관리자
- 관련 요구사항: FR-051
- 관련 규칙: BR-020

**응답 (200)**
```json
{ "orderId": 1001, "status": "READY", "readyAt": "2026-08-28T19:52:00+09:00", "stockChanged": false }
```

READY 전환은 재고를 바꾸지 않는다 (05 §6.2 기재 없음 = 변화 없음, FR-051).

**오류**
| 코드 | HTTP | 조건 | 규칙 |
|---|---|---|---|
| `NOT_FOUND` | 404 | 없는 주문 | — |
| `INVALID_ORDER_STATUS` | 409 | CONFIRMED가 아닌 주문. `details.currentStatus` 포함 | 05 §2.3, FR-051 |
| `FORBIDDEN` | 403 | 고객 권한 | BR-002 |

### API-116 · 픽업 완료 처리

- `POST /api/admin/orders/{orderId}/complete`
- 권한: 관리자
- 관련 요구사항: FR-052
- 관련 규칙: BR-020, BR-021

**응답 (200)**
```json
{ "orderId": 1001, "status": "COMPLETED", "completedAt": "2026-08-28T20:11:00+09:00", "stockChanged": false }
```

- CONFIRMED와 READY 모두에서 실행할 수 있다. READY는 선택 경유 상태다 (FR-051 예외, FR-052).
- 노쇼 유예가 끝나기 전이면 픽업 시간대가 지난 주문도 완료 처리할 수 있다 (FR-052, 02 AS-04 3단계).
- 상태 전이를 `WHERE status IN ('CONFIRMED','READY')` 조건부 UPDATE로 실행하므로 같은 주문을 두 번 완료 처리할 수 없다 (FR-052).

**오류**
| 코드 | HTTP | 조건 | 규칙 |
|---|---|---|---|
| `NOT_FOUND` | 404 | 없는 주문 | — |
| `INVALID_ORDER_STATUS` | 409 | 이미 COMPLETED / NO_SHOW 전환됨 / CANCELED | 05 §2.3, FR-052 예외 |
| `FORBIDDEN` | 403 | 고객 권한 | BR-002 |

### API-117 · 관리자 주문 취소

- `POST /api/admin/orders/{orderId}/cancel`
- 권한: 관리자
- 관련 요구사항: FR-054
- 관련 규칙: BR-019, BR-020, BR-024

**요청**
```json
{ "reason": "상품 이상 발견으로 판매 불가" }
```

**응답 (200)**
```json
{
  "orderId": 1001,
  "status": "CANCELED",
  "canceledBy": "ADMIN",
  "cancelReason": "상품 이상 발견으로 판매 불가",
  "canceledAt": "2026-08-28T19:45:00+09:00",
  "slotReleased": true,
  "stockResults": [
    { "productId": 12, "quantity": 2, "restored": true, "reason": "CANCEL_RESTORE" }
  ]
}
```

고객 취소 마감 시각과 무관하게 실행된다 (BR-020, FR-054).

**오류**
| 코드 | HTTP | 조건 | 규칙 |
|---|---|---|---|
| `NOT_FOUND` | 404 | 없는 주문 | — |
| `CANCEL_REASON_REQUIRED` | 400 | 사유 누락 또는 공백 | BR-020, FR-054 |
| `CANCEL_NOT_ALLOWED` | 409 | COMPLETED·NO_SHOW·CANCELED 주문 | BR-020, FR-054 |
| `INVALID_ORDER_STATUS` | 409 | PENDING 주문 | 05 §2.3 |
| `FORBIDDEN` | 403 | 고객 권한 | BR-002 |

---

## 10. 관리자 API — 픽업 운영

### API-118 · 시간대별 픽업 현황 조회

- `GET /api/admin/pickup-slots?date=2026-08-28`
- 권한: 관리자
- 관련 요구사항: FR-055, FR-057, FR-058
- 관련 규칙: BR-014, BR-016

**응답 (200)**
```json
{
  "date": "2026-08-28",
  "isHoliday": false,
  "slots": [
    {
      "slotId": 341,
      "startAt": "2026-08-28T19:30:00+09:00",
      "endAt": "2026-08-28T20:00:00+09:00",
      "capacity": 20,
      "reservedCount": 18,
      "full": false,
      "blocked": false,
      "reservationClosed": false,
      "itemTotals": [{ "productId": 12, "name": "국내산 삼겹살 300g", "quantity": 24 }]
    }
  ]
}
```

`reservedCount`에는 CONFIRMED·READY·COMPLETED만 포함한다. CANCELED·NO_SHOW는 제외한다 (FR-055, BR-016).

**오류**
| 코드 | HTTP | 조건 | 규칙 |
|---|---|---|---|
| `VALIDATION_ERROR` | 400 | 날짜 형식 오류 | — |
| `FORBIDDEN` | 403 | 고객 권한 | BR-002 |

### API-119 · 개별 시간대 정원 변경·차단

- `PATCH /api/admin/pickup-slots/{slotId}`
- 권한: 관리자
- 관련 요구사항: FR-057, FR-058
- 관련 규칙: BR-016

**요청**
```json
{ "capacity": 12, "blocked": true }
```

**응답 (200)**
```json
{ "slotId": 342, "capacity": 12, "reservedCount": 20, "blocked": true, "overCapacity": true, "keptOrderCount": 20 }
```

- 정원을 줄여 기존 예약이 정원을 넘어도 기존 주문은 취소하지 않는다. `overCapacity: true`로 상태만 알린다 (BR-016, FR-057).
- 차단해도 이미 확정된 주문은 유지한다 (FR-058).
- 차단을 해제하면 정원이 남은 경우 다시 선택 가능해진다 (FR-058).

**오류**
| 코드 | HTTP | 조건 | 규칙 |
|---|---|---|---|
| `SLOT_NOT_FOUND` | 404 | 없는 시간대 | BR-014 |
| `VALIDATION_ERROR` | 400 | `capacity < 1` | BR-016, FR-057 |
| `FORBIDDEN` | 403 | 고객 권한 | BR-002 |

### API-120 · 매장 운영 설정 조회

- `GET /api/admin/store-settings`
- 권한: 관리자
- 관련 요구사항: FR-056, FR-057
- 관련 규칙: BR-014, BR-016

**응답 (200)**
```json
{
  "name": "savePick 신선마켓",
  "address": "서울특별시 ○○구 ○○로 12",
  "phone": "0212345678",
  "openTime": "10:00",
  "closeTime": "22:00",
  "slotUnitMinutes": 30,
  "defaultSlotCapacity": 20,
  "holidays": ["2026-09-01"]
}
```

**오류**
| 코드 | HTTP | 조건 | 규칙 |
|---|---|---|---|
| `FORBIDDEN` | 403 | 고객 권한 | BR-002 |

### API-121 · 매장 운영 설정 변경

- `PUT /api/admin/store-settings`
- 권한: 관리자
- 관련 요구사항: FR-056, FR-057
- 관련 규칙: BR-003, BR-014, BR-016

**요청**
```json
{ "openTime": "10:00", "closeTime": "21:00", "defaultSlotCapacity": 12, "holidays": ["2026-09-01"] }
```

**응답 (200)**
```json
{
  "openTime": "10:00",
  "closeTime": "21:00",
  "defaultSlotCapacity": 12,
  "holidays": ["2026-09-01"],
  "excludedFutureSlotCount": 2,
  "keptConfirmedOrderCount": 3,
  "appliedFrom": "2026-08-28T09:50:00+09:00"
}
```

- 영업 종료를 앞당기면 그 이후 시작하는 **아직 확정 주문이 없는** 시간대만 신규 선택에서 제외한다. 이미 확정된 주문의 픽업 시간대는 바꾸거나 취소하지 않는다 (FR-056, 02 AS-06).
- `defaultSlotCapacity` 변경은 이후 생성되는 슬롯의 기본값과 아직 예약이 시작되지 않은 미래 슬롯에만 적용한다. 이미 생성된 슬롯의 `capacity`를 일괄 축소하지 않는다 (FR-057 "변경 이후 생성되는 주문부터 적용").
- 마감 시각 상한(`closeTime`)이 바뀌어도 이미 등록된 상품의 `closingAt`은 바꾸지 않는다.

**오류**
| 코드 | HTTP | 조건 | 규칙 |
|---|---|---|---|
| `BUSINESS_HOUR_INVALID` | 400 | 종료 ≤ 시작 / 30분 단위 아님 | BR-014, FR-056 |
| `VALIDATION_ERROR` | 400 | `defaultSlotCapacity < 1` / 휴무일 형식 오류 | BR-016, FR-057 |
| `FORBIDDEN` | 403 | 고객 권한 | BR-002 |

---

## 11. 배치 작업

API가 아니라 시스템이 주기적으로 수행하는 작업이다. 실행 주체는 항상 `SYSTEM`이며 이력의 `actorType`도 `SYSTEM`으로 남는다 (05 §0-4).

### BATCH-01 · 선점 만료 회수

- 주기: **30초** (BR-008이 요구하는 1분 이내를 만족하는 여유값)
- 관련 요구사항: FR-015, FR-019, FR-020
- 관련 규칙: BR-007, BR-008
- 동작
  1. `orders`에서 `status = 'PENDING' AND hold_expires_at <= now()`인 주문을 100건씩 가져온다.
  2. 각 주문에 대해 조건부 UPDATE로 `EXPIRED` 전환, INVENTORY_HOLD를 `EXPIRED`로 전환, `product_stocks.held_quantity` 차감, STOCK_LEDGER에 `HOLD_EXPIRE` 기록을 하나의 트랜잭션으로 수행한다.
  3. 영향 행 수가 0이면(이미 결제·포기로 종결) 아무것도 하지 않고 넘어간다.
- 실패 시: 다음 주기에 다시 처리된다. 지연 정리(13번 §2)가 있어 재고 정확성은 배치 실패와 무관하게 유지된다.

### BATCH-02 · 상품 마감 상태 전환

- 주기: **30초**
- 관련 요구사항: FR-034, FR-042, FR-010, FR-011
- 관련 규칙: BR-030
- 동작: `products`에서 `status IN ('DRAFT','ON_SALE','HIDDEN') AND closing_at <= now()`인 상품을 `CLOSED`로 전환하고 `closed_at`을 기록한다. 기존 HELD 선점과 확정 주문은 건드리지 않는다 (BR-030).
- 보완: 고객 조회 API는 배치를 기다리지 않고 `closing_at <= now()` 조건을 쿼리에 함께 넣어 마감 상품을 즉시 제외한다. 배치는 상태값을 실제로 맞추는 역할만 한다 (FR-014 "60초 안에 반영" 충족).

### BATCH-03 · 노쇼 자동 전환과 누적 제재

- 주기: **3분** (BR-021이 요구하는 5분 이내)
- 관련 요구사항: FR-031, FR-032, FR-053
- 관련 규칙: BR-021, BR-022, BR-023
- 동작
  1. `orders`에서 `status IN ('CONFIRMED','READY') AND no_show_due_at <= now()`인 주문을 조건부 UPDATE로 `NO_SHOW` 전환하고 `no_show_at`을 기록한다. 재고는 복구하지 않으며 STOCK_LEDGER 행도 만들지 않는다 (BR-022, S10).
  2. 전환된 주문의 회원에 대해 최근 30일 `NO_SHOW` 건수를 세고, 3건에 도달했고 활성 제한이 없으면 MEMBER_RESTRICTION을 `ends_at = now() + 7일`로 생성한다 (BR-023).
  3. `UNIQUE (order_id, to_status)`와 `UNIQUE (member_id, trigger_order_id)`가 중복 전환·중복 제재를 막는다 (FR-053).
- 제한 해제는 별도 배치를 두지 않는다. 판정 시 `ends_at > now()`를 확인하므로 시간이 지나면 자동으로 풀린다 (BR-023).

### BATCH-04 · 재고 정합성 점검

- 주기: **매일 03:00**
- 관련 요구사항: FR-046
- 관련 규칙: BR-006
- 동작: 상품별로 `product_stocks.held_quantity`와 유효 HELD 합계, `confirmed_quantity`와 확정 판매 주문 수량 합계를 비교한다. 불일치가 있으면 값을 보정하지 않고 경보만 남긴다. 자동 보정은 원인을 숨기므로 하지 않는다.

### BATCH-05 · 픽업 시간대 사전 생성

- 주기: **매일 00:05 + 애플리케이션 기동 시 1회**
- 관련 요구사항: FR-022, FR-023, FR-056
- 관련 규칙: BR-013, BR-014, BR-016
- 동작: D+0, D+1 날짜에 대해 영업시간을 30분으로 나눈 슬롯을 생성한다. 휴무일은 생성하지 않는다. `UNIQUE (store_id, start_at)` 덕분에 중복 실행해도 안전하다. `capacity`는 생성 시점 `stores.default_slot_capacity`를 복사한다.

### BATCH-06 · 만료 데이터 정리

- 주기: **매일 04:00**
- 관련 요구사항: FR-002 (인증 위생)
- 관련 규칙: BR-002
- 동작: 만료 세션, 90일 지난 로그인 시도 기록, 7일 지난 게스트 장바구니를 삭제한다 (10번 §8). 주문·재고 원장은 삭제하지 않는다.

---

## 12. FR ↔ API 매핑표

| FR | 요구사항 | 매핑 대상 | 권한 |
|---|---|---|---|
| FR-001 | 회원가입 | API-001 | 비로그인 |
| FR-002 | 로그인/로그아웃 | API-002, API-003, API-004, API-101 | 비로그인·고객·관리자 |
| FR-003 | 회원 정보 조회·수정 | API-005, API-006 | 고객 |
| FR-004 | 고객·관리자 권한 분리 | API-101, API-002(403), 12번 문서 권한 매트릭스, `/api/admin/*` 전체 | 공통 |
| FR-005 | 시각 기준 통일 | API-008 + 모든 응답의 `serverTime` | 공통 |
| FR-010 | 상품 목록 조회 | API-010 | 비로그인 |
| FR-011 | 상품 검색 | API-010 (`keyword`) | 비로그인 |
| FR-012 | 정렬·필터 | API-010 (`sort`, `hideSoldOut`) | 비로그인 |
| FR-013 | 상품 상세 조회 | API-011 | 비로그인 |
| FR-014 | 마감 할인가 표시 | API-010, API-011, API-108 | 비로그인·관리자 |
| FR-015 | 잔여 수량 표시 | API-010, API-011, BATCH-01 | 비로그인 |
| FR-016 | 장바구니 담기 | API-013 | 비로그인·고객 |
| FR-017 | 장바구니 조회·수정·삭제 | API-012, API-014, API-015 | 비로그인·고객 |
| FR-018 | 장바구니 유효성 재검증 | API-012, API-016 | 비로그인·고객 |
| FR-019 | 주문서 생성·재고 선점 | API-017 | 고객 |
| FR-020 | 선점 잔여 시간·만료 | API-018, API-019, BATCH-01 | 고객·시스템 |
| FR-021 | 주문 금액 확정 | API-017, API-022 | 고객 |
| FR-022 | 픽업 날짜 선택 | API-020, API-021, BATCH-05 | 고객 |
| FR-023 | 픽업 시간대 선택 | API-020, API-021, BATCH-05 | 고객 |
| FR-024 | 가상 결제 요청 | API-022 | 고객 |
| FR-025 | 결제 실패·재시도 | API-022 | 고객 |
| FR-026 | 주문 확정·픽업 번호 | API-022 | 고객 |
| FR-027 | 주문 내역 조회 | API-023 | 고객 |
| FR-028 | 주문 상세 조회 | API-024 | 고객 |
| FR-029 | 주문 취소 | API-025 | 고객 |
| FR-030 | 취소 가능 여부 안내 | API-024, API-025 | 고객 |
| FR-031 | 노쇼 결과 확인 | API-007, API-024, BATCH-03 | 고객·시스템 |
| FR-032 | 주문 제한 상태 안내 | API-007, API-017(`ORDER_RESTRICTED`) | 고객 |
| FR-033 | 픽업 안내 정보 | API-009, API-024 | 비로그인·고객 |
| FR-034 | 마감 도달 구매 차단 | API-010, API-011, API-013, API-017, BATCH-02 | 비로그인·고객·시스템 |
| FR-040 | 상품 등록 | API-103 | 관리자 |
| FR-041 | 상품 수정 | API-105, API-107 | 관리자 |
| FR-042 | 판매 상태 전환 | API-106, BATCH-02 | 관리자·시스템 |
| FR-043 | 마감 시각 설정 | API-103, API-105 | 관리자 |
| FR-044 | 할인 구간 정책 확인 | API-108, API-102 | 관리자 |
| FR-045 | 재고 등록·조정 | API-109 | 관리자 |
| FR-046 | 재고 현황 조회 | API-110, API-104, BATCH-04 | 관리자·시스템 |
| FR-047 | 재고 변경 이력 조회 | API-111 | 관리자 |
| FR-048 | 주문 목록 조회·필터 | API-112 | 관리자 |
| FR-049 | 픽업 번호로 주문 조회 | API-113 | 관리자 |
| FR-050 | 주문 상세 조회 (관리자) | API-114 | 관리자 |
| FR-051 | 픽업 준비 완료 처리 | API-115 | 관리자 |
| FR-052 | 픽업 완료 처리 | API-116 | 관리자 |
| FR-053 | 노쇼 처리 결과 확인 | API-112 (`status=NO_SHOW`), BATCH-03 | 관리자·시스템 |
| FR-054 | 관리자 주문 취소 | API-117 | 관리자 |
| FR-055 | 시간대별 픽업 현황 | API-118 | 관리자 |
| FR-056 | 영업시간 설정 | API-120, API-121, BATCH-05 | 관리자 |
| FR-057 | 시간대 정원 설정 | API-121, API-119 | 관리자 |
| FR-058 | 개별 시간대 차단 | API-119 | 관리자 |

**매핑 결과: 49개 FR 중 49개 매핑 완료. 미매핑 0개.**

### 12.1 엔드포인트 집계

| 구분 | 그룹 | 개수 |
|---|---|---|
| 고객 | 인증·계정·공통 (API-001~009) | 9 |
| 고객 | 상품 (API-010~011) | 2 |
| 고객 | 장바구니 (API-012~016) | 5 |
| 고객 | 주문서·선점·픽업 (API-017~021) | 5 |
| 고객 | 결제·주문 관리 (API-022~025) | 4 |
| 고객 소계 | | **25** |
| 관리자 | 인증 (API-101) | 1 |
| 관리자 | 상품 (API-102~108) | 7 |
| 관리자 | 재고 (API-109~111) | 3 |
| 관리자 | 주문 (API-112~117) | 6 |
| 관리자 | 픽업 운영 (API-118~121) | 4 |
| 관리자 소계 | | **21** |
| 합계 | | **46** |
| 배치 작업 | BATCH-01~06 | 6 |

### 12.2 BR ↔ 오류 코드 역매핑 점검

`04-business-rules.md`의 30개 규칙 중 오류 응답을 만들어야 하는 규칙과 연결 코드다.

| BR | 연결된 오류 코드 |
|---|---|
| BR-002 | `UNAUTHENTICATED`, `FORBIDDEN`, `INVALID_CREDENTIALS`, `LOGIN_BLOCKED`, `EMAIL_DUPLICATED`, `NOT_FOUND`(타인 주문) |
| BR-003 | `CLOSING_TIME_INVALID` |
| BR-004 | `VALIDATION_ERROR`(정가 100원 미만) |
| BR-005 | `CART_HAS_UNAVAILABLE_ITEM`, `AMOUNT_MISMATCH` |
| BR-006 | `OUT_OF_STOCK`, `STOCK_BELOW_COMMITTED` |
| BR-007 | `HOLD_EXPIRED`, `PENDING_ORDER_EXISTS` |
| BR-008 | `HOLD_EXPIRED` |
| BR-009 | `MAX_QUANTITY_EXCEEDED`, `CART_ITEM_LIMIT_EXCEEDED` |
| BR-010 | `CART_EMPTY` |
| BR-011 | `PAYMENT_FAILED`, `ALREADY_PAID` |
| BR-012 | `PAYMENT_ATTEMPT_EXCEEDED`, `PAYMENT_FAILED`, `HOLD_EXPIRED` |
| BR-013 | `SLOT_DATE_OUT_OF_RANGE` |
| BR-014 | `SLOT_NOT_FOUND`, `BUSINESS_HOUR_INVALID` |
| BR-015 | `SLOT_CLOSED` |
| BR-016 | `SLOT_FULL`, `SLOT_NOT_SELECTED` |
| BR-017 | `SLOT_AFTER_PRODUCT_CLOSING` |
| BR-018 | `CANCEL_DEADLINE_PASSED` |
| BR-019 | (오류 아님 — 응답 `stockResults`로 복구·폐기 구분) |
| BR-020 | `CANCEL_REASON_REQUIRED`, `CANCEL_NOT_ALLOWED` |
| BR-021 | (오류 아님 — BATCH-03 자동 전환) |
| BR-022 | `CANCEL_NOT_ALLOWED`(NO_SHOW 취소 시도) |
| BR-023 | `ORDER_RESTRICTED` |
| BR-024 | `CANCEL_NOT_ALLOWED` (부분 취소 파라미터 자체를 두지 않는다) |
| BR-025 | `STOCK_BELOW_COMMITTED` |
| BR-026 | `PICKUP_NUMBER_EXHAUSTED`, `NOT_FOUND`(픽업 번호 조회) |
| BR-027 | `OUT_OF_STOCK` |
| BR-028 | (오류 아님 — 모든 응답에 `serverTime`) |
| BR-029 | `AMOUNT_MISMATCH` |
| BR-030 | `PRODUCT_CLOSED`, `PRODUCT_NOT_ON_SALE`, `PRODUCT_STATUS_TRANSITION_DENIED` |

BR-001은 데이터 구조로만 강제하므로 오류 코드가 없다 (10번 §7).

---

## 가정 / 미확정

### 가정 (확인 필요)

| # | 가정한 내용 | 근거 | 틀릴 경우 영향 |
|---|---|---|---|
| A-A1 | 상품 목록과 검색을 하나의 엔드포인트(`GET /api/products`)로 합친다 | 검색은 목록의 필터 조건이며 응답 구조가 동일하다 (FR-010, FR-011) | 별도 엔드포인트가 필요하면 `GET /api/products/search`가 추가된다 |
| A-A2 | 주문서 생성 시 수량을 클라이언트가 보내지 않고 서버가 장바구니에서 읽는다 | 클라이언트 수량을 신뢰하면 `maxOrderQuantity` 검증을 우회할 수 있다 | 장바구니를 거치지 않는 즉시 주문 흐름이 필요하면 요청 스키마가 바뀐다 |
| A-A3 | 픽업 시간대 지정을 결제와 분리해 `PATCH`로 둔다 | 시간대 선택 단계에서 정원·마감을 미리 검증해야 결제 직전 실패를 줄인다 (02 CS-01 9~11단계) | 한 번에 처리하면 API-021이 없어지고 API-022 요청에 `slotId`가 들어간다 |
| A-A4 | 결제 요청에 `Idempotency-Key`를 필수로 받는다 | 네트워크 재전송이 재시도 횟수를 소모하면 고객이 이유 없이 3회를 잃는다 (BR-012) | 헤더를 선택으로 바꾸면 중복 요청이 시도 횟수를 차감한다 |
| A-A5 | 결제 실패를 HTTP 200 + `result: "FAILED"`로 반환한다 | 실패는 정상 흐름이며 잔여 시간·잔여 횟수를 함께 전달해야 한다 (02 CS-03) | 4xx로 바꾸면 클라이언트가 오류 처리와 흐름 처리를 구분하기 어려워진다 |
| A-A6 | 노쇼 목록을 별도 엔드포인트로 두지 않고 `GET /api/admin/orders?status=NO_SHOW`로 처리한다 | 필터 조건만 다르고 응답이 같다 (FR-053) | 노쇼 전용 집계 항목이 필요하면 엔드포인트가 추가된다 |
| A-A7 | 관리자 수동 노쇼 처리 API를 만들지 않는다 | 자동 전환이 정본이고, 유예 후 수동 처리는 이미 자동 전환된 상태를 재확인하는 것뿐이다 (02 AS-04 4단계, FR-053) | 수동 처리 요구가 확정되면 `POST /api/admin/orders/{id}/no-show`가 추가된다 |
| A-A8 | 관리자 계정 생성 API를 두지 않는다 | 운영자가 직접 부여한다 (FR-004 예외, 03 A11) | 관리자 계정 관리 화면이 필요하면 관리자 CRUD 엔드포인트가 추가된다 |
| A-A9 | 페이지네이션은 `page`/`size` 방식을 쓴다 | 데이터 규모가 작고 관리자 목록에서 총 건수 표시가 필요하다 | 커서 방식으로 바꾸면 응답의 `page` 객체 구조가 바뀐다 |
| A-A10 | 리프레시 토큰은 응답 본문이 아니라 `HttpOnly` 쿠키로 전달한다 | 토큰 탈취 위험을 줄인다 | 모바일 앱 클라이언트가 추가되면 본문 반환 방식이 필요해진다 |

### 미확정 (결정 대기)

| # | 결정이 필요한 사항 | 선택지 | 막히는 작업 |
|---|---|---|---|
| A-U1 | 선점 만료 임박 안내 시점(`expiringSoon` 기준) | 60초 (임시 채택, 03 U1) / 180초 / 안내 없음 | API-018 응답 필드 의미 |
| A-U2 | 픽업 번호 응답 형식 | 문자열 `"017"` 3자리 제로 패딩 (임시 채택) / 정수 `17` | API-022, API-113 스키마 |
| A-U3 | 실시간 잔여 수량 갱신 방식 | 클라이언트 폴링 (임시 채택, 60초 허용치 내) / SSE / WebSocket | FR-015, FR-014의 60초 반영 조건 충족 방법 |
| A-U4 | 상품 목록 응답의 이미지 필드 | 없음 (임시 채택) / `imageUrl` 추가 | 06~09 디자인 트랙 확정 후 G3에서 결정 |
| A-U5 | `GET /api/orders`의 `IN_PROGRESS` 정의 | CONFIRMED·READY (임시 채택) / PENDING 포함 | FR-027 필터 동작 |
| A-U6 | 관리자 목록 API의 CSV 내보내기 | 제공하지 않음 (임시 채택) / 제공 | 관리자 정산·집계 작업 |

### 향후 검토 (첫 버전 범위 밖)

| # | 내용 | 사유 |
|---|---|---|
| A-F1 | 결제 대행사 콜백(webhook) 수신 엔드포인트 | 제외 범위(실제 결제) |
| A-F2 | 알림 발송·조회 API | 03 F6 |
| A-F3 | 매장 위치·경로 API | 제외 범위(지도·위치) |
| A-F4 | 추천 상품 API | 제외 범위(AI 추천) |
| A-F5 | 매장 단위 필터가 붙은 상품·슬롯 API | 제외 범위(다중 매장) |
