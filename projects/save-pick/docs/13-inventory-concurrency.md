# savePick 재고 동시성 설계

- 문서 ID: 13
- 작성: technical-architect
- 최종 갱신: 2026-08-28
- 선행 문서: 01, 02, 03, 04, 05, 10, 11

## 한 줄 요약
재고 임시 선점 10분을 `inventory_holds` 행과 `product_stocks` 집계와 `stock_ledgers` 원장 3중 구조로 기록하고, `product_stocks` 단일 행 락으로 모든 재고 경합을 직렬화하며, 만료·해제·복구의 중복 실행을 원장의 유니크 제약으로 물리적으로 차단한다.

---

## 0. 전제와 용어

### 0.1 변경 불가 전제 (G1 확정값)

| # | 값 | 근거 |
|---|---|---|
| G1 | 선점 유효 시간 **10분**, 연장 없음 | BR-007 |
| G2 | 만료 정리 주기 **1분 이내** | BR-008 |
| G3 | 장바구니는 재고를 선점하지 않는다 | BR-010 |
| G4 | 결제 실패 1~2회는 선점 유지, 3회째 실패 시 FAILED + 즉시 해제 | BR-012-1, BR-012-3 |
| G5 | 재시도 횟수가 남아도 선점 만료가 먼저면 결제 불가, 주문은 EXPIRED | BR-012-4 |
| G6 | 무응답은 실패로 간주한다 | BR-011 |
| G7 | 부분 선점을 만들지 않는다 (전부 또는 전무) | BR-027 |
| G8 | 상품 마감 이전 취소는 판매 가능 재고로 복구, 이후 취소는 복구 없이 총 재고 차감 + 폐기 기록 | BR-019, 05 S7·S8 |
| G9 | 총 재고를 `선점 중 + 확정 판매` 미만으로 줄일 수 없다 | BR-025 |
| G10 | 픽업 시간대 정원 점유는 결제 성공 시점 | BR-016, 05 §8 |

### 0.2 용어와 대응 컬럼

| 개념 | 컬럼 | 비고 |
|---|---|---|
| 총 재고 | `product_stocks.total_quantity` | 관리자가 등록한 실물 수량 |
| 선점 중 | `product_stocks.held_quantity` | 유효 HELD 선점 합계 |
| 확정 판매 | `product_stocks.confirmed_quantity` | CONFIRMED·READY·COMPLETED·NO_SHOW 합계 |
| 판매 가능 | `product_stocks.available_quantity` | 생성 컬럼 `total - held - confirmed` |
| 폐기 | `product_stocks.discarded_quantity` | 마감 후 취소 누적 (S8) |

### 0.3 이 문서의 방어선 3층

| 층 | 수단 | 막는 것 |
|---|---|---|
| 1층 | `product_stocks` 행 비관적 락 | 동시 요청의 교차 실행 |
| 2층 | `CHK_stock_non_negative_available` (`total - held - confirmed >= 0`) | 코드 버그로 인한 초과 판매 |
| 3층 | `stock_ledgers`의 `UNIQUE (order_id, product_id, reason)` | 해제·복구의 중복 실행 |

1층이 뚫려도 2층이 커밋을 거부하고, 2층을 통과한 중복 처리는 3층이 거부한다. 어느 한 층도 애플리케이션 코드의 성실함에 의존하지 않는다.

---

## 1. 임시 선점의 저장 위치와 구조

**문제**
주문서 생성 시점부터 결제 완료까지 10분간 다른 고객이 그 수량을 사지 못하게 잡아 둬야 한다. 이 상태를 어디에 어떤 형태로 기록할 것인가.

**선택한 방식**

세 곳에 같은 트랜잭션으로 함께 쓴다.

| 대상 | 테이블·컬럼 | 역할 |
|---|---|---|
| 개별 선점 | `inventory_holds` 1행 (주문 × 품목) | 누가 무엇을 몇 개 언제까지 잡았는지의 정본 |
| 집계 | `product_stocks.held_quantity` (+ 생성 컬럼 `available_quantity`) | 조회·판정에 쓰는 O(1) 합계, 락 지점 |
| 이력 | `stock_ledgers` 1행 (`reason = 'HOLD'`) | 감사 추적과 멱등성 보증 |
| 만료 스캔 사본 | `orders.hold_expires_at` | 주문 단위 만료 스캔용 비정규화 사본 |

`inventory_holds` 1행이 담는 값

| 컬럼 | 값 |
|---|---|
| `order_id`, `product_id` | `UNIQUE (order_id, product_id)` — 주문 × 품목당 선점 1건 |
| `quantity` | 선점 수량 (`>= 1`) |
| `status` | `HELD` → `CONSUMED` / `RELEASED` / `EXPIRED` (05 §3.1) |
| `expires_at` | `created_at + 10분`. UPDATE로 늘리지 않는다 (G1) |
| `settled_at` | HELD를 벗어난 시각 |

한 주문의 모든 선점은 **같은 `expires_at`** 을 갖는다 (05 §3.4-4). PostgreSQL의 `now()`가 트랜잭션 시작 시각으로 고정되므로, 트랜잭션 안에서 `now() + interval '10 minutes'`를 각 행에 써도 값이 자동으로 같아진다. 계산값을 애플리케이션에서 미리 만들어 넘기는 방식과 결과가 같으면서 코드 실수의 여지가 없다.

`orders.hold_expires_at`은 같은 값의 사본이다. 만료 회수 배치가 주문 단위로 처리해야 하는데, `inventory_holds`부터 스캔하면 주문 단위로 다시 묶어야 한다. `(hold_expires_at) WHERE status = 'PENDING'` 부분 인덱스로 배치가 필요한 주문만 곧바로 찾는다.

**왜 이 방식인가**

- 집계 컬럼만 두면 "누가 무엇을 잡았는지"를 알 수 없어 결제 성공 시 확정으로 전환할 대상, 만료 시 되돌릴 대상을 특정할 수 없다.
- 행만 두면 판매 가능 수량 조회마다 `SUM`이 필요하다. 상품 목록 조회(API-010)는 가장 빈번한 요청이고, 여기에 집계 쿼리가 붙으면 선점 행이 쌓일수록 느려진다.
- 원장을 함께 두면 재고 이력 조회(API-111, FR-047)를 별도 설계 없이 얻고, 무엇보다 유니크 제약으로 멱등성을 강제할 자리가 생긴다 (§5).

**포기한 대안과 이유**

| 대안 | 포기 이유 |
|---|---|
| Redis 키에 TTL 10분으로 선점 저장 | 재고 정본이 DB와 Redis 둘로 갈라진다. Redis가 죽으면 선점이 통째로 사라져 초과 판매가 나고, 복구 근거가 없다. 만료가 자동인 대신 만료 이력이 남지 않아 FR-047을 만족하지 못한다 |
| `orders` 상태만으로 선점 표현 (PENDING 주문 수량을 매번 합산) | 판매 가능 수량 조회가 주문·품목 조인 집계가 된다. 상품 목록 조회에서 가장 비싼 쿼리가 된다 |
| `products` 테이블에 재고 컬럼을 직접 두기 | 상품 정보 수정(이름·가격)과 재고 변경이 같은 행을 다투게 된다. 관리자의 상품 수정이 고객 주문을 막는다. 재고를 `product_stocks`로 분리한 이유다 |
| 선점을 만들지 않고 결제 성공 시에만 차감 | 결제 단계에서 품절이 나 고객이 결제까지 마친 뒤 실패한다. P3(헛걸음 방지)·V2와 정면으로 어긋난다 |

**실패 시 사용자에게 보이는 것**
선점 생성 자체가 실패하는 경우는 재고 부족뿐이며 §3에서 다룬다.

---

## 2. 선점 TTL 10분과 만료 회수 방식

**문제**
`expires_at`이 지난 선점은 판매 가능 재고로 돌아가야 한다. 그런데 시각이 지나는 순간 DB가 스스로 값을 바꿔 주지는 않는다. 회수 전 구간에서는 `held_quantity`가 실제보다 크고, 생성 컬럼인 `available_quantity`가 실제보다 작다 (05 C5 위반 구간).

**선택한 방식 — 세 경로를 함께 쓴다**

| 경로 | 시점 | 하는 일 | 목적 |
|---|---|---|---|
| (a) 읽기 보정 | 재고를 **보여 주는** 조회 (API-010, API-011, API-110) | 쓰기 없이 표시값만 보정 | 만료된 선점 때문에 품절로 보이지 않게 한다 |
| (b) 쓰기 지연 정리 | 재고를 **바꾸는** 트랜잭션 (API-017 주문서 생성, API-109 재고 조정) | 잠근 상품의 만료 HELD를 실제로 회수 | 판정 근거를 실제 값으로 만든다 |
| (c) 주기 배치 | BATCH-01, **30초** 주기 | 남은 HELD 회수 + 주문을 EXPIRED로 종결 | 아무도 그 상품을 건드리지 않아도 정리된다 |

추가로 (d) 결제 요청 시점 검사(API-022)와 (e) 선점 잔여 시간 조회(API-018)에서 **자기 주문**에 한해 만료를 종결한다. BR-008이 요구하는 "결제 요청 시점과 주기적 정리 두 경로"를 (d)와 (c)가 만족하고, (a)(b)(e)는 그 사이 구간의 표시·판정 정확도를 위한 보강이다.

### 2.1 (a) 읽기 보정 — 쓰기를 하지 않는다

```
표시용 판매 가능 수량
  = product_stocks.available_quantity + 만료된_선점_수량

만료된_선점_수량
  = COALESCE(SUM(quantity), 0)
    FROM inventory_holds
   WHERE product_id = :productId
     AND status = 'HELD'
     AND expires_at <= now()
```

`(expires_at) WHERE status = 'HELD'` 부분 인덱스로 스캔 범위가 만료된 행으로 제한된다. 목록 조회에서는 상품 묶음에 대해 한 번의 `GROUP BY product_id`로 처리한다.

관리자 재고 현황(API-110)의 `heldQuantity`에도 같은 보정을 적용한다. FR-046이 "만료된 선점은 포함하지 않는다"를 요구하기 때문이다.

**왜 읽기 경로에서 실제 회수를 하지 않는가**
상품 목록 조회는 인증 없이 누구나 호출하는 가장 빈번한 요청이다. 여기서 쓰기를 하면 (1) 읽기 요청이 행 락을 잡아 주문 트랜잭션과 경합하고, (2) 조회가 몰릴 때 같은 정리를 여러 요청이 동시에 시도하며, (3) 읽기 전용 복제본으로 분리할 수 없게 된다. 표시값은 보정으로 충분하고, 실제 값이 필요한 곳은 재고를 잡는 트랜잭션뿐이다.

### 2.2 (b) 쓰기 지연 정리 — 잠근 상품만, 선점 행만

주문서 생성·재고 조정 트랜잭션이 `product_stocks` 행을 `FOR UPDATE`로 잠근 **직후**에 실행한다.

```
1) UPDATE inventory_holds
      SET status = 'EXPIRED', settled_at = now()
    WHERE product_id = :productId
      AND status = 'HELD'
      AND expires_at <= now()
   RETURNING id, order_id, quantity;

2) 회수 합계만큼 product_stocks.held_quantity 를 차감한다.

3) 회수된 선점마다 stock_ledgers 에 reason = 'HOLD_EXPIRE' 1행을 남긴다.
   (order_id, product_id, 'HOLD_EXPIRE') 유니크 제약이 중복 기록을 막는다.
```

**여기서 `orders`는 건드리지 않는다.** 그 주문이 다른 상품의 선점도 갖고 있다면 그 상품의 `product_stocks` 행도 잠가야 하는데, 그러면 락 획득 순서가 깨져 교착이 생긴다 (§7). 주문 상태 전환은 주문 전체를 다룰 수 있는 (c) BATCH-01과 (d)(e)에 맡긴다.

그 결과 **주문은 PENDING인데 선점은 이미 EXPIRED**인 중간 상태가 최대 30초간 존재한다. 이 상태에서 고객이 할 수 있는 일은 없다. 결제(API-022)와 시간대 지정(API-021)은 `orders.hold_expires_at <= now()`를 먼저 검사해 `HOLD_EXPIRED`로 끝나고, 조회(API-018)는 `status: "EXPIRED"`, 잔여 0으로 응답한다 (11번 API-018). 즉 중간 상태가 고객에게 잘못된 가능성을 보여 주지 않는다.

BATCH-01이 나중에 같은 선점을 다시 회수하려 해도 `WHERE status = 'HELD'` 조건에 걸려 영향 행이 0이므로 집계 차감도 원장 기록도 건너뛴다. 이중 회수가 구조적으로 불가능하다.

### 2.3 (c) BATCH-01 — 주기 30초

- 대상: `orders`에서 `status = 'PENDING' AND hold_expires_at <= now()` (부분 인덱스 사용), 한 번에 100건.
- 주문 1건 = 트랜잭션 1개. 락 순서는 `orders` → `product_stocks`(product_id 오름차순) 이다 (§7).
- 처리: `orders`를 조건부로 `EXPIRED` 전환 → 남은 HELD를 `EXPIRED`로 → `held_quantity` 차감 → `stock_ledgers`에 `HOLD_EXPIRE` → `order_status_histories` 기록.
- 조건부 UPDATE(`WHERE id = ? AND status = 'PENDING'`)의 영향 행이 0이면(그 사이 결제 성공·주문서 포기) 아무것도 하지 않고 다음 건으로 넘어간다.
- 다중 인스턴스로 띄워도 같은 주문을 두 번 처리하지 않도록 배치 진입에 분산 락(ShedLock)을 건다. 락이 없어도 조건부 UPDATE와 유니크 제약이 결과를 보호하지만, 무의미한 락 경합을 줄인다.
- 실패해도 다음 주기에 다시 처리된다. 배치가 몇 주기 죽어 있어도 (a)(b)가 표시와 판정을 정확하게 유지하므로 초과 판매나 잘못된 품절이 생기지 않는다.

**왜 30초인가**: BR-008이 요구하는 상한은 1분이다. 30초는 그 절반이라 한 주기를 통째로 건너뛰어도 규칙을 지킨다.

**왜 이 방식인가 — 스케줄러와 지연 정리를 둘 다 쓰는 이유**

| 방식 | 단독으로 쓸 때의 문제 |
|---|---|
| 스케줄러만 | 주기 사이 최대 30초 동안 만료 선점이 재고를 잡고 있는 것처럼 보인다. 마감 직전 마지막 1개에서는 이 30초가 판매 기회 그 자체다. 또 배치 프로세스가 죽으면 재고가 무기한 잠긴다 |
| 지연 정리만 | 그 상품에 아무 요청도 오지 않으면 영원히 정리되지 않는다. 재고 현황(FR-046)과 지표가 계속 어긋나고, 주문이 PENDING으로 남아 고객의 "유효한 주문서 1건" 슬롯(`UNIQUE (member_id) WHERE status = 'PENDING'`)을 막아 새 주문서를 만들 수 없게 된다. 이게 결정적이다 |

**포기한 대안과 이유**

| 대안 | 포기 이유 |
|---|---|
| Redis 키 만료 이벤트(keyspace notification)로 회수 | 이벤트 전달이 보장되지 않는다(구독자가 끊긴 동안의 만료는 유실). 재고 회수를 유실 가능한 채널에 맡길 수 없다 |
| DB 트리거·이벤트 스케줄러로 회수 | 재고 규칙이 애플리케이션과 DB로 갈라진다. 테스트·추적이 어렵고, 원장 기록 같은 부수 작업을 트리거 안에서 하면 실패 처리가 불투명해진다 |
| 만료 선점 행을 DELETE | 이력이 사라져 FR-047(재고 변경 이력)을 만족하지 못하고, 멱등성 판단 근거도 없어진다. 상태로만 종결한다 (10번 §8) |
| TTL을 지키기 위해 주문 생성 시 지연 작업 예약(딜레이 큐) | 큐 인프라가 하나 더 늘고, 예약 취소·중복 실행 처리가 결국 지금과 같은 유니크 제약을 요구한다 |

**실패 시 사용자에게 보이는 것**
- 결제 중 만료: `HOLD_EXPIRED`(409). 주문은 EXPIRED로 종결되고 "다시 주문해 주세요" 안내로 이어진다.
- 조회 중 만료: API-018이 `status: "EXPIRED"`, `holdRemainingSeconds: 0`을 오류 없이 반환한다.
- 다른 고객 입장: 만료 즉시(늦어도 30초 안에) 해당 수량이 판매 가능으로 되돌아온다.

---

## 3. 마지막 재고 1개를 동시에 주문한 경우 (락 전략)

**문제**
잔여 1개인 상품을 고객 A와 B가 같은 순간에 주문서로 만든다. 둘 다 "판매 가능 1개"를 읽고 둘 다 선점하면 초과 판매가 된다. 게다가 주문서는 여러 품목을 담을 수 있고, 한 품목이라도 부족하면 **어떤 품목도 선점하면 안 된다** (G7).

**선택한 방식 — `product_stocks` 행 비관적 락 + CHECK 제약**

주문서 생성(API-017)의 트랜잭션 절차다.

```
BEGIN;
SET LOCAL lock_timeout = '3s';

-- 1. 주문 대상 상품을 product_id 오름차순으로 정렬한 뒤, 하나씩 잠근다
FOR each productId IN ASC ORDER:
    SELECT total_quantity, held_quantity, confirmed_quantity
      FROM product_stocks
     WHERE product_id = :productId
       FOR UPDATE;

-- 2. 잠근 상품의 만료 선점을 지연 회수한다 (§2.2)

-- 3. 품목별로 (total - held - confirmed) >= 요청수량 을 검사한다
--    하나라도 부족하면 부족 목록을 모아 ROLLBACK

-- 4. 선점 반영
UPDATE product_stocks
   SET held_quantity = held_quantity + :n, updated_at = now()
 WHERE product_id = :productId;

INSERT INTO inventory_holds (order_id, product_id, quantity, status, expires_at, created_at)
VALUES (:orderId, :productId, :n, 'HELD', now() + interval '10 minutes', now());

INSERT INTO stock_ledgers (..., reason = 'HOLD', delta_held = +:n, after_* = 갱신값, actor_type = 'CUSTOMER');

-- 5. 주문서 생성
INSERT INTO orders (..., status = 'PENDING', hold_expires_at = now() + interval '10 minutes');

COMMIT;
```

**이 절차의 각 장치가 막는 것**

| 장치 | 막는 것 |
|---|---|
| `FOR UPDATE` (1단계) | A가 잠근 동안 B의 `SELECT ... FOR UPDATE`가 대기한다. B는 A가 커밋한 **뒤의** 값(판매 가능 0)을 읽는다. 읽고-계산하고-쓰는 구간이 원자적이 된다 |
| `product_id` 오름차순 고정 | 다품목 주문 두 건이 서로 다른 순서로 잠가 생기는 교착을 막는다. 정렬은 애플리케이션이 하고, 한 문장에 `IN` + `ORDER BY`로 넣지 않는다. `ORDER BY`와 `FOR UPDATE`의 잠금 순서는 실행 계획에 따라 달라질 수 있어 보장 수단으로 쓰지 않는다 |
| 3단계 전량 검증 후 4단계 일괄 반영 | 부분 선점을 만들지 않는다 (G7, BR-027) |
| `CHK_stock_non_negative_available` | 위 절차에 버그가 있어도 `held + confirmed > total`이 되는 커밋을 DB가 거부한다 (2층 방어) |
| `UNIQUE (member_id) WHERE status = 'PENDING'` | 같은 고객이 동시에 두 개의 주문서를 만들어 재고를 이중으로 잡는 것을 막는다. 두 번째는 유니크 위반 → `PENDING_ORDER_EXISTS`(409) |
| `lock_timeout = 3s` | 락 보유 구간은 외부 호출이 없어 수 밀리초다. 3초를 넘게 기다렸다면 정상 경합이 아니라 이상 상황이므로 무한 대기 대신 실패시킨다 |

**동시 요청의 실제 진행**

| 시각 | 고객 A | 고객 B | 상품 12의 값 |
|---|---|---|---|
| t0 | `FOR UPDATE` 획득 | — | total 1, held 0, confirmed 0 |
| t1 | 판매 가능 1 ≥ 1 → 선점 | `FOR UPDATE` **대기** | |
| t2 | COMMIT | 대기 해제, 잠금 획득 | total 1, held 1, confirmed 0 |
| t3 | — | 판매 가능 0 < 1 → ROLLBACK | 변화 없음 |
| t4 | 201 주문서 생성 | 409 `OUT_OF_STOCK` | |

**왜 이 방식인가**

1. **부족 수량을 응답에 담아야 한다.** 11번 API-017은 `details.shortages[{ productId, requested, available }]`를 규정한다. 조건부 UPDATE만 쓰면 "영향 행 0"만 알 수 있어 몇 개가 부족한지 말할 수 없다. 값을 읽은 상태에서 판정해야 이 응답을 만들 수 있다.
2. **전부-또는-전무가 필요하다.** 다품목 주문을 낙관적으로 처리하면 일부 성공 후 충돌이 났을 때 앞선 성공을 되돌리는 보상 처리가 필요하다. 트랜잭션과 선락(先鎖)으로 처리하면 롤백 한 번으로 끝난다.
3. **경합 대상이 극히 좁다.** 락 지점은 상품당 정확히 1행이고, 서로 다른 상품 주문은 전혀 대기하지 않는다. 마감 임박 인기 상품 몇 개에서만 짧은 직렬화가 일어난다.
4. **락 보유 구간에 외부 호출이 없다.** 가상 결제 판정조차 즉시 반환하도록 정의했다 (§4). 락을 오래 쥘 코드 경로가 존재하지 않는다.

**포기한 대안과 이유**

| 대안 | 포기 이유 |
|---|---|
| 낙관적 락 (`product_stocks.version` + 충돌 시 재시도) | 품목이 N개면 충돌 확률이 N배로 늘고, 마감 직전 인기 상품에서는 재시도가 재시도를 부른다. 재시도 중 다른 품목의 재고가 바뀌면 처음부터 다시 읽어야 해 결국 직렬화와 같아지면서 실패 응답만 불안정해진다 |
| 조건부 UPDATE 단독 (`UPDATE ... SET held = held + :n WHERE total - held - confirmed >= :n`) | 가장 짧은 락 구간을 얻지만 부족 수량을 알 수 없어 `shortages` 응답을 만들 수 없다. 만료 선점 지연 정리를 끼워 넣을 지점도 없다. 이 방식이 하는 일은 이미 `CHK_stock_non_negative_available`가 2층에서 수행한다 |
| `SERIALIZABLE` 격리 수준 | 직렬화 실패(`40001`)를 애플리케이션이 전부 재시도해야 하고, 재고 부족과 직렬화 실패를 사용자 응답으로 구분해 번역하기 어렵다. 필요한 직렬화 범위가 행 하나로 좁은데 격리 수준을 전역으로 올릴 이유가 없다 |
| Redis 분산 락 또는 Redis 재고 카운터 (`DECRBY`) | 재고 정본이 두 곳이 된다. Redis와 DB가 어긋났을 때 어느 쪽이 옳은지 판정할 근거가 없고, 원장 기록과 원자적으로 묶을 수 없다 |
| 상품별 단일 워커 큐(직렬 처리) | 큐 인프라가 늘고, 동기 응답(주문서 생성 결과)을 큐 왕복으로 만들면 지연이 커진다. DB 행 락이 이미 상품 단위 직렬화를 공짜로 제공한다 |
| 테이블 락 / 애플리케이션 전역 뮤텍스 | 서로 무관한 상품의 주문까지 직렬화된다. 인스턴스를 늘려도 처리량이 늘지 않는다 |

**실패 시 사용자에게 보이는 것**

| 상황 | 응답 | 이어지는 안내 |
|---|---|---|
| 재고 부족 | `OUT_OF_STOCK`(409) + `details.shortages[]` | 부족 품목과 남은 수량을 알리고 수량을 줄이거나 품목을 빼도록 유도한다. 이후 조회에서 그 상품은 품절로 표시된다 (FR-015) |
| 이미 PENDING 주문서 보유 | `PENDING_ORDER_EXISTS`(409) + `details.orderId`, `details.holdExpiresAt` | 진행 중인 주문서로 이동하거나 포기(API-019) 후 다시 시도 |
| 락 대기 3초 초과 | `INTERNAL_ERROR`(500) | 트랜잭션 전체가 롤백되어 재고와 주문이 변하지 않는다. 재시도로 안전하게 복구된다 |

---

## 4. 가상 결제 실패 시 선점 해제

**문제**
결제는 성공·실패 두 결과만 있고 무응답도 실패다 (G6). 실패마다 재고를 풀면 카드 오입력 한 번으로 재고를 놓치고, 무제한 재시도를 허용하면 재고가 계속 묶인다. 해제 시점을 언제로 잡을 것인가.

**선택한 방식 — 시도 횟수와 만료 시각 중 먼저 오는 쪽에서 해제 (BR-012-5)**

| 사건 | 선점 상태 | 재고 변화 | 주문 상태 | 원장 `reason` |
|---|---|---|---|---|
| 1회째 실패 | `HELD` 유지 | 없음 | `PENDING` 유지 | 기록 없음 |
| 2회째 실패 | `HELD` 유지 | 없음 | `PENDING` 유지 | 기록 없음 |
| 3회째 실패 | `RELEASED` | 선점 −N, 판매 가능 +N (S5) | `FAILED` | `HOLD_RELEASE` |
| 선점 만료가 먼저 | `EXPIRED` | 선점 −N, 판매 가능 +N (S6) | `EXPIRED` | `HOLD_EXPIRE` |
| 결제 성공 | `CONSUMED` | 선점 −N, 확정 판매 +N (S4) | `CONFIRMED` | `CONFIRM` |
| 주문서 포기 (API-019) | `RELEASED` | 선점 −N, 판매 가능 +N | `EXPIRED` | `HOLD_RELEASE` |

1~2회 실패에서 재고를 건드리지 않으므로 `product_stocks` 행 락도 잡지 않는다. 실패 경로가 다른 고객의 주문을 방해하지 않는다.

**결제 트랜잭션의 순서 (11번 API-022의 절차를 락 관점으로 다시 쓴 것)**

```
BEGIN;
SET LOCAL lock_timeout = '3s';

1) SELECT * FROM orders WHERE id = :orderId AND member_id = :memberId FOR UPDATE;
     없으면 404 NOT_FOUND / status <> 'PENDING' 이면 409 INVALID_ORDER_STATUS

2) hold_expires_at <= now() 이면
     → 선점을 EXPIRED로, held_quantity 차감, 원장 HOLD_EXPIRE, 주문 EXPIRED 로 종결
     → COMMIT 후 409 HOLD_EXPIRED
     → payment_attempts 행을 만들지 않는다 (시도 횟수를 소모시키지 않는다)

3) amount <> orders.total_amount 이면 409 AMOUNT_MISMATCH (시도 기록 없음, BR-029)

4) pickup_slot_id IS NULL 이면 409 SLOT_NOT_SELECTED
   슬롯 예약 마감(now() > start_at - 30분)이면 409 SLOT_CLOSED

5) INSERT payment_attempts (attempt_no = payment_attempt_count + 1, status='REQUESTED', idempotency_key)
     attempt_no > 3 → CHECK 위반 전에 애플리케이션이 409 PAYMENT_ATTEMPT_EXCEEDED
     idempotency_key 중복 → 기존 시도의 결과를 그대로 반환하고 종료
   UPDATE orders SET payment_attempt_count = payment_attempt_count + 1

6) 가상 결제 판정 (외부 호출 없음, 지연 없이 즉시 결과 반환)

7-성공)
     product_stocks 를 product_id 오름차순으로 FOR UPDATE
     inventory_holds: HELD → CONSUMED (조건부)
     product_stocks:  held −N, confirmed +N   → 원장 CONFIRM
     pickup_slots:    FOR UPDATE 후 reserved_count < capacity 확인, +1  (아니면 409 SLOT_FULL)
     pickup_number_seqs: UPDATE ... last_number + 1 RETURNING
     orders: CONFIRMED, pickup_number, cancelable_until, no_show_due_at, confirmed_at
     order_status_histories: PENDING → CONFIRMED

7-실패, 시도 1~2회) payment_attempts 를 FAILED 로 확정하고 종료 (재고 무변경)

7-실패, 시도 3회)
     product_stocks 를 product_id 오름차순으로 FOR UPDATE
     inventory_holds: HELD → RELEASED (조건부)
     product_stocks:  held −N                → 원장 HOLD_RELEASE
     orders: FAILED, failed_at, stock_settled_at
     order_status_histories: PENDING → FAILED

COMMIT;
```

**무응답(TIMEOUT)의 처리**
6단계의 가상 결제 판정기는 **대기하지 않는다**. `TIMEOUT`은 "기다렸다가 응답이 없었다"가 아니라 판정기가 즉시 돌려주는 실패 사유값이다 (`payment_attempts.failure_reason = 'TIMEOUT'`). 이렇게 정의해야 결제 전체를 하나의 트랜잭션으로 유지하면서도 재고 행 락 보유 시간이 밀리초 단위로 유지된다. 실제 결제 대행사 연동은 제외 범위이며, 연동이 생기면 외부 호출을 트랜잭션 밖으로 빼는 2단계 구조가 필요해진다 (§가정 IC-F1).

**중복 요청 처리**
`Idempotency-Key`는 `payment_attempts.idempotency_key`의 UNIQUE 제약으로 강제한다. 같은 키의 재전송은 앞선 시도 결과를 그대로 반환하고 시도 횟수를 늘리지 않으며 재고를 다시 건드리지 않는다. 두 요청이 동시에 들어오면 한쪽이 유니크 위반으로 실패하고, 애플리케이션은 이를 잡아 기존 행을 다시 읽어 같은 응답을 만든다. `UNIQUE (order_id) WHERE status = 'SUCCEEDED'`가 성공 기록이 두 개 생기는 경우를 추가로 차단하며, 이때의 응답은 `ALREADY_PAID`(409)다.

**왜 이 방식인가**

- 실패 1~2회에 재고를 풀면, 푼 순간 다른 고객이 채가서 재시도가 반드시 실패한다. 재시도 3회 규칙(BR-012-2)이 무의미해진다.
- 반대로 3회 실패 후에도 만료까지 잡고 있으면 더 이상 결제할 수 없는 주문이 최대 10분간 재고를 묶는다. 결제 불가가 확정된 순간이 해제의 자연스러운 시점이다.
- 만료가 먼저 오면 남은 횟수와 무관하게 결제를 막는다(G5). 두 조건 중 먼저 오는 쪽에서 해제하므로 "언제 풀리는가"에 대한 답이 항상 하나다.
- 2단계에서 시도 기록을 만들지 않는 이유: 만료로 결제가 불가능해진 것을 고객의 시도 소모로 계산하면, 고객이 통제하지 못한 사유로 횟수를 잃는다.

**포기한 대안과 이유**

| 대안 | 포기 이유 |
|---|---|
| 실패할 때마다 선점 해제 후 재시도 시 재선점 | 재시도가 사실상 새 주문서가 된다. 그 사이 품절이면 재시도가 불가능해지고, 금액도 다시 계산돼야 해 BR-005(금액 확정)와 충돌한다 |
| 3회 실패 후 선점을 만료까지 유지 | 결제가 확정적으로 불가능한 재고를 최대 10분 더 묶는다. 마감 임박 상품에서 손실이 크다 |
| 실패 시 남은 시간을 연장해 주기 | BR-007이 연장을 금지한다. 연장을 허용하면 실패를 반복해 재고를 무한정 잡는 경로가 생긴다 |
| 결제 실패를 4xx로 반환 | 실패는 정상 흐름의 갈래이고 잔여 시간·잔여 횟수를 함께 전달해야 한다 (11번 A-A5) |
| 결제 판정을 트랜잭션 밖에 두고 결과만 나중에 반영 | 판정과 반영 사이에 선점이 만료돼 "성공했는데 재고가 없는" 상태가 생긴다. 가상 결제에는 이 복잡도를 감수할 이유가 없다 |

**실패 시 사용자에게 보이는 것**

| 상황 | 응답 |
|---|---|
| 1~2회째 실패 | HTTP 200, `result: "FAILED"`, `paymentAttemptRemaining`, `holdRemainingSeconds` — 남은 시간 안에 재시도 가능 |
| 3회째 실패 | HTTP 200, `result: "FAILED"`, `status: "FAILED"`, `holdReleased: true` — 새 주문서를 만들도록 안내 |
| 선점 만료 후 시도 | `HOLD_EXPIRED`(409). 주문은 EXPIRED로 종결 |
| FAILED·EXPIRED 주문에 재시도 | `INVALID_ORDER_STATUS`(409) |
| 결제 직전 슬롯 정원 소진 | `SLOT_FULL`(409). 선점은 유지되므로 다른 시간대를 골라 다시 시도할 수 있다 |

---

## 5. 주문 취소 시 재고 복구와 중복 복구 방지 (멱등성)

**문제**
취소는 확정된 재고를 되돌린다. 같은 취소가 두 번 반영되면 존재하지 않는 재고가 판매 가능으로 표시돼 초과 판매가 된다. 네트워크 재전송, 고객과 관리자의 동시 취소, 배치 재실행이 모두 이 위험을 만든다. 게다가 복구 여부가 품목별로 다르다 (G8).

**선택한 방식 — 3중 가드, 정본은 원장의 유니크 제약**

| # | 가드 | 위치 | 막는 것 |
|---|---|---|---|
| 1 | 조건부 상태 전이 | `UPDATE orders SET status='CANCELED' WHERE id=? AND status IN ('CONFIRMED','READY')` | 두 번째 취소 요청은 영향 행 0. 재고 처리 자체에 도달하지 못한다 |
| 2 | **원장 유니크 제약** | `stock_ledgers` `UNIQUE (order_id, product_id, reason) WHERE order_id IS NOT NULL` | 어떤 경로로 들어와도 `(주문, 품목, CANCEL_RESTORE)` 또는 `(주문, 품목, CANCEL_DISCARD)`는 두 번 기록될 수 없다. 위반 시 트랜잭션 전체가 롤백된다 |
| 3 | 종결 표시 | `orders.stock_settled_at` | 재고 종결이 끝났음을 한 컬럼으로 확인한다. 조회·점검용 보조 신호 |

취소 트랜잭션

```
BEGIN;
SET LOCAL lock_timeout = '3s';

1) SELECT * FROM orders WHERE id = :orderId FOR UPDATE;
     고객 취소면 member_id 일치 확인 (아니면 404)
     status NOT IN ('CONFIRMED','READY') → 409 CANCEL_NOT_ALLOWED (PENDING이면 INVALID_ORDER_STATUS)
     고객 취소이고 now() > cancelable_until → 409 CANCEL_DEADLINE_PASSED   (관리자는 이 검사를 건너뛴다, BR-020)
     관리자 취소이고 cancel_reason 없음 → 400 CANCEL_REASON_REQUIRED

2) UPDATE orders SET status='CANCELED', canceled_by=?, cancel_reason=?, canceled_at=now()
    WHERE id=:orderId AND status IN ('CONFIRMED','READY');
     영향 행 0 → 아무것도 하지 않고 409

3) 품목을 product_id 오름차순으로 정렬해 product_stocks 를 FOR UPDATE

4) 품목별 판정: 현행 products.closing_at 과 now() 비교      -- 스냅샷이 아니라 현행값 (10번 T-A7)
     마감 전 → CANCEL_RESTORE : confirmed −N            (total 불변, 판매 가능 +N)   [S7]
     마감 후 → CANCEL_DISCARD : confirmed −N, total −N, discarded +N (판매 가능 불변) [S8]
   각각 stock_ledgers 1행 (유니크 제약이 중복을 차단)

5) UPDATE pickup_slots SET reserved_count = GREATEST(reserved_count - 1, 0) WHERE id = :slotId;
     취소 시점과 무관하게 항상 1건 반납 (BR-019, 05 §8)

6) UPDATE orders SET stock_settled_at = now();
   INSERT order_status_histories (CONFIRMED|READY → CANCELED)
COMMIT;
```

**왜 이 방식인가 — 유니크 제약을 정본 가드로 두는 이유**

애플리케이션 플래그(`stock_settled_at`)만으로 막으면, "읽고 → 판단하고 → 쓰는" 사이에 다른 트랜잭션이 끼어들 수 있고, 새 코드 경로(관리자 취소, 배치 보정, 운영 스크립트)가 추가될 때마다 플래그 검사를 잊을 수 있다. 유니크 제약은 코드 경로가 몇 개든, 누가 쓰든 예외 없이 적용된다. 중복 복구는 "막아야 하는 실수"가 아니라 "물리적으로 불가능한 일"이어야 한다.

**왜 `CANCEL_DISCARD`가 안전한가**
`confirmed −N`과 `total −N`을 함께 적용하므로 `total - held - confirmed`가 변하지 않는다. `CHK_stock_non_negative_available`를 위반할 수 없고, 팔 수 없게 된 수량이 판매 가능으로 되살아나지도 않는다 (BR-019 근거와 일치).

**동시 취소 두 건 (고객 + 관리자)**
1단계의 `orders` 행 락이 두 트랜잭션을 직렬화한다. 먼저 잠근 쪽이 2단계에서 상태를 바꾸고 커밋하면, 나중 트랜잭션은 다시 읽었을 때 `CANCELED`를 보고 `CANCEL_NOT_ALLOWED`(409)로 끝난다. 재고 처리는 한 번만 실행된다.

**재전송된 취소 요청**
이미 CANCELED인 주문에 대한 두 번째 요청은 `CANCEL_NOT_ALLOWED`(409)를 받는다 (11번 API-025 오류표). 성공 응답을 다시 주는 방식(완전 멱등)이 클라이언트에는 더 편하지만, 취소는 되돌릴 수 없는 조작이라 "이미 처리됨"을 사용자에게 분명히 알리는 편이 낫다고 판단했다. 어느 쪽이든 **재고는 한 번만 변한다**는 점은 유니크 제약이 보장한다.

**부분 취소가 없어서 단순해지는 것**
BR-024가 부분 취소를 금지하므로 품목 단위 재시도·부분 복구 상태가 존재하지 않는다. 멱등 키의 단위가 (주문, 품목, 사유)로 고정되고, 중간 상태를 표현할 컬럼이 필요 없다.

**노쇼는 복구하지 않는다**
`NO_SHOW` 전환은 수량을 바꾸지 않고 `stock_ledgers` 행도 만들지 않는다 (S10, BR-022). 확정 판매 수량으로 남는다. BATCH-03이 여러 번 돌아도 `UNIQUE (order_id, to_status)`가 이력 중복을 막고, 조건부 UPDATE가 상태 중복 전환을 막는다.

**포기한 대안과 이유**

| 대안 | 포기 이유 |
|---|---|
| 취소 API에 `Idempotency-Key` 헤더 요구 | 클라이언트가 키를 제대로 만들어 보내야 성립한다. 관리자 취소·배치 보정 등 키가 없는 경로를 보호하지 못한다. 결제와 달리 취소는 자연 키(주문·품목·사유)가 이미 있다 |
| `orders.stock_settled_at` 단독 검사 | 검사와 쓰기 사이의 경합에 취약하고, 새 코드 경로가 검사를 빠뜨릴 수 있다. 보조 신호로만 남긴다 |
| 분산 락(Redis)으로 주문 단위 직렬화 | 이미 `orders` 행 락이 같은 일을 하고, 인프라 장애 시 보증이 사라진다 |
| 재고 복구를 비동기 큐로 분리 | 취소 응답에 복구·폐기 결과(`stockResults`)를 담아야 한다 (11번 API-025). 비동기로 빼면 응답 시점에 결과를 모른다 |
| 취소 시 `stock_ledgers`를 보고 "이미 복구했는지" 조회 후 분기 | 조회-판단-쓰기 사이 경합이 남는다. 같은 정보를 제약으로 강제하면 조회가 필요 없다 |

**실패 시 사용자에게 보이는 것**

| 상황 | 응답 |
|---|---|
| 정상 취소 | 200 + `stockResults[]`에 품목별 `restored: true/false`와 사유(`CANCEL_RESTORE` / `CANCEL_DISCARD`) |
| 취소 마감 경과 (고객) | `CANCEL_DEADLINE_PASSED`(409) + `details.cancelableUntil` — 매장 문의 안내로 이어진다 |
| 이미 취소·완료·노쇼된 주문 | `CANCEL_NOT_ALLOWED`(409) |
| PENDING 주문 취소 시도 | `INVALID_ORDER_STATUS`(409) — 주문서 포기(API-019)를 안내한다 |

---

## 6. 관리자가 재고를 수정하는 동안 들어온 주문

**문제**
관리자가 실물을 세어 총 재고를 20 → 15로 조정하는 사이, 고객이 그 상품을 주문한다. 관리자가 본 화면의 값은 이미 낡았을 수 있고, 축소가 이미 만들어진 선점·확정 주문을 무효화하면 안 된다 (G9).

**선택한 방식 — 같은 `product_stocks` 행 락을 공유해 직렬화하고, 축소는 하한선으로 거부한다**

관리자 재고 조정(API-109)은 증감량이 아니라 **총 재고의 목표값**을 받는다 (11번 API-109). 트랜잭션은 다음과 같다.

```
BEGIN;
SET LOCAL lock_timeout = '3s';

1) SELECT total_quantity, held_quantity, confirmed_quantity
     FROM product_stocks WHERE product_id = :productId FOR UPDATE;

2) 만료 선점 지연 회수 (§2.2)          -- held_quantity 를 실제 값으로 만든다

3) minimumSettableQuantity = held_quantity + confirmed_quantity
   목표값 < minimumSettableQuantity  →  409 STOCK_BELOW_COMMITTED
                                        details.minimumSettableQuantity 동봉, ROLLBACK

4) UPDATE product_stocks SET total_quantity = :target, updated_at = now();
   INSERT stock_ledgers (reason='ADMIN_ADJUST', delta_total = :target - :before,
                         actor_type='ADMIN', actor_id=:adminId, note=:note);
COMMIT;
```

주문서 생성(§3)과 재고 조정은 **같은 행을 같은 방식으로 잠근다**. 그래서 둘은 절대로 교차 실행되지 않고, 항상 둘 중 하나가 먼저 완결된다.

**두 순서의 결과**

| 순서 | 진행 | 결과 |
|---|---|---|
| 관리자 먼저 | 관리자가 락 획득 → 20을 15로 축소 → 커밋 → 고객 트랜잭션이 대기 해제 | 고객은 **축소된 값**(판매 가능 15 − held − confirmed)으로 판정받는다. 부족하면 `OUT_OF_STOCK`(409) |
| 고객 먼저 | 고객이 락 획득 → 3개 선점 → 커밋 → 관리자 트랜잭션이 대기 해제 | 관리자는 **선점이 반영된 값**으로 최소 설정값을 계산한다. 목표값이 그보다 작으면 `STOCK_BELOW_COMMITTED`(409)이고 응답의 `minimumSettableQuantity`가 방금 늘어난 값이다 |

어느 경우에도 이미 만들어진 선점과 확정 주문은 취소되지 않는다 (BR-025, 05 원칙 2).

**왜 이 방식인가**

- 재고 조정과 주문서 생성이 결국 **같은 자원(`product_stocks` 1행)** 을 바꾼다. 두 흐름에 서로 다른 동시성 장치를 쓰면 둘 사이의 경합만 보호되지 않는다. 같은 락을 공유하는 것이 추가 장치 없이 두 흐름을 함께 보호하는 유일한 방법이다.
- 축소를 거부하되 이미 만들어진 선점·확정을 건드리지 않는 방향은 BR-025가 명시한 우선순위(이미 결제한 고객의 주문을 관리자 조작으로 무효화하지 않는다)와 일치한다.
- 하한선(`held + confirmed`)은 판정 시점의 실제 값에서 계산되므로, 관리자가 낡은 화면을 보고 있어도 **거부 결정 자체는 항상 최신 값 기준**이다.

**2단계 지연 정리를 관리자 경로에도 넣는 이유**
만료됐지만 아직 회수되지 않은 선점이 `held_quantity`에 남아 있으면 최소 설정값이 실제보다 커진다. 관리자는 정당한 축소를 거부당하고, 응답에 표시되는 `minimumSettableQuantity`가 실제보다 큰 값이 되어 관리자가 잘못된 판단을 하게 된다. 조정 직전에 그 상품의 만료 선점을 회수하면 항상 실제 값으로 판정한다.

**낡은 화면 값 문제 (lost update)**
관리자가 본 `before` 값과 실제 값이 다를 수 있다. 절대값 방식이므로 마지막 요청이 이긴다. 이를 다음으로 완화한다.

- 응답에 `before`와 `after`를 모두 담아 관리자가 자신이 본 값과 실제 반영 전 값이 같았는지 확인할 수 있게 한다 (11번 API-109).
- 축소가 위험한 방향이며, 그 방향은 하한선(3단계)이 막는다. 증가는 항상 안전하다.
- 모든 변경이 `stock_ledgers`에 `ADMIN_ADJUST`로 남아 사후 추적이 가능하다 (FR-047).
- 더 강한 보증(요청에 기대 현재값을 함께 보내 다르면 거부)은 11번 API-109의 요청 스키마 변경이 필요해 §미확정 IC-U2에 제안으로만 남긴다.

**즉시 판매를 멈춰야 할 때**
재고 축소로는 이미 잡힌 선점을 없앨 수 없다. 판매를 당장 멈춰야 하면 상품 상태를 `HIDDEN`으로 전환한다 (API-106). `HIDDEN`은 목록·상세 노출과 신규 주문만 차단하고 기존 선점·확정 주문은 그대로 둔다 (BR-025 위반 시 처리, 05 §5).

**증가 조정과 진행 중 주문**
총 재고를 늘리는 조정은 하한선 검사를 항상 통과한다. 대기 중이던 고객 트랜잭션은 늘어난 값을 보고 선점에 성공한다. 별도 알림이나 예약 대기열은 두지 않는다 (첫 버전 범위 밖).

**상품 마감 시각 수정과의 상호작용**
관리자가 `closing_at`을 변경하면 취소 시 복구·폐기 판정 기준이 바뀐다. 판정은 주문 시점 스냅샷(`order_items.product_closing_at`)이 아니라 **현행 `products.closing_at`** 으로 한다 (10번 T-A7). 마감을 늦춘 상품은 다시 팔 수 있으므로 복구하는 것이 맞고, 마감을 당긴 상품은 팔 수 없으므로 폐기가 맞다.

**포기한 대안과 이유**

| 대안 | 포기 이유 |
|---|---|
| 증감량(`delta`) 방식으로 조정 | 요청 재전송이 이중 반영된다. 실물을 세어 넣는 조작에는 절대값이 자연스럽다 (11번 API-109) |
| 축소 시 최근 선점부터 강제 해제 | 결제 직전인 고객의 주문이 아무 예고 없이 사라진다. BR-025가 금지하는 방향이다 |
| 관리자 조정을 큐에 넣어 나중에 반영 | 관리자가 방금 입력한 값이 화면에 반영되지 않아 같은 조작을 반복하게 된다 |
| 조정 중 해당 상품 주문을 잠시 차단(플래그) | 락으로 이미 직렬화되는 문제에 상태 플래그를 하나 더 만드는 것이다. 플래그를 되돌리지 못하고 트랜잭션이 죽으면 상품이 영구 차단된다 |
| 재고 조정에 낙관적 락(`version`) 사용 | 관리자 조작은 초당 수 건 수준이라 경합 자체가 드물다. 하한선 검사가 위험한 방향을 이미 막는다 |

**실패 시 사용자에게 보이는 것**

| 대상 | 상황 | 응답 |
|---|---|---|
| 관리자 | 선점·확정 미만으로 축소 시도 | `STOCK_BELOW_COMMITTED`(409) + `details.minimumSettableQuantity`. "지금 설정 가능한 최소값"을 함께 알린다 |
| 관리자 | 음수·비정수 입력 | `VALIDATION_ERROR`(400) |
| 고객 | 축소 직후 주문 시도 | `OUT_OF_STOCK`(409) + `details.shortages[]`. 조회에서는 줄어든 잔여 수량이 60초 안에 반영된다 (03 A7) |

---

## 7. 전역 락 순서와 트랜잭션 경계

### 7.1 락 획득 순서 (모든 트랜잭션이 지킨다)

```
1. orders                (주문 1건)
2. product_stocks        (product_id 오름차순)
3. inventory_holds       (2에서 잠근 상품에 속한 것만)
4. pickup_slots
5. pickup_number_seqs
```

역순으로 잠그는 코드 경로를 만들지 않는다. 이 순서 하나로 재고·픽업 영역의 교착이 사라진다.

§2.2의 쓰기 지연 정리가 이 순서를 지키는 방식이 중요하다. 지연 정리는 2번(`product_stocks`)을 잡은 뒤 3번(`inventory_holds`)만 갱신하고, **그 선점이 속한 다른 주문의 `orders` 행(1번)을 거슬러 올라가 잡지 않는다**. 그래서 순서가 뒤집히지 않는다. `stock_ledgers`에 `order_id` 외래키를 넣을 때 PostgreSQL이 참조 대상 주문 행에 거는 잠금은 `FOR KEY SHARE`이며, 주문 상태 변경(`FOR NO KEY UPDATE`)과 충돌하지 않으므로 이 경로에서도 대기가 생기지 않는다.

### 7.2 트랜잭션 경계

| 흐름 | 트랜잭션 범위 | 락 보유 구간 |
|---|---|---|
| 주문서 생성 (API-017) | 검증 → 선점 → 원장 → 주문 INSERT | 상품 행: 수 ms |
| 결제 (API-022) | 검증 → 시도 기록 → 판정 → 확정/해제 | 주문 행 전체, 상품·슬롯 행은 7단계에서만 |
| 취소 (API-025, API-117) | 상태 전이 → 품목별 복구·폐기 → 슬롯 반납 | 주문 행 전체, 상품 행은 4단계 |
| 재고 조정 (API-109) | 잠금 → 지연 정리 → 하한 검사 → 반영 | 상품 행: 수 ms |
| BATCH-01 | 주문 1건당 1트랜잭션 (100건을 한 트랜잭션에 묶지 않는다) | 건당 수 ms |

배치를 건 단위 트랜잭션으로 쪼개는 이유는, 100건을 한 트랜잭션으로 묶으면 그동안 100개 상품의 행 락을 동시에 쥐어 고객 주문이 대기하기 때문이다. 중간에 실패해도 처리된 건은 유지되고 나머지는 다음 주기에 처리된다.

**트랜잭션 안에서 하지 않는 것**: 외부 HTTP 호출, 파일 I/O, 알림 발송, 사용자 입력 대기. 현재 설계에는 이런 경로가 없다 (가상 결제 판정도 즉시 반환, §4).

---

## 8. 픽업 시간대 정원 경합 (재고와 같은 원리)

정원도 유한 자원이므로 같은 방식으로 다룬다.

| 항목 | 처리 |
|---|---|
| 점유 시점 | 결제 성공 시점에만 (G10, 05 §8). PENDING은 점유하지 않는다 |
| 락 | `pickup_slots` 행 `FOR UPDATE` (락 순서 4번) |
| 판정 | `reserved_count < capacity` 이면 `+1`, 아니면 `SLOT_FULL`(409) |
| 반납 | 취소·노쇼 시 `GREATEST(reserved_count - 1, 0)` |
| 정원 축소 | `reserved_count <= capacity` 제약을 걸지 않는다. 관리자가 정원을 줄여 기존 예약이 정원을 넘는 상태가 정상이다 (10번 §3.3, BR-016) |
| 마지막 1자리 동시 결제 | 먼저 잠근 쪽만 CONFIRMED. 나중 쪽은 `SLOT_FULL`(409)을 받고 **선점은 유지**되므로 다른 시간대를 골라 다시 결제할 수 있다 |

시간대 지정(API-021)에서 정원을 점유하지 않는 이유는, 지정 후 결제하지 않고 이탈하는 고객이 정원을 묶기 때문이다. 대신 API-020·API-021에서 정원을 미리 검사해 결제 직전 실패 확률을 낮춘다.

---

## 9. 정합성 점검과 관측

### 9.1 BATCH-04 (매일 03:00)

상품별로 아래를 비교한다.

| 비교 | 기대 |
|---|---|
| `product_stocks.held_quantity` | `SUM(inventory_holds.quantity) WHERE status='HELD' AND expires_at > now()` |
| `product_stocks.confirmed_quantity` | 확정 판매 주문(CONFIRMED·READY·COMPLETED·NO_SHOW)의 품목 수량 합 |
| `product_stocks.total_quantity` | `SUM(stock_ledgers.delta_total)` 누적 |
| `available_quantity` | `total - held - confirmed` (생성 컬럼이므로 항상 성립) |

**불일치를 자동 보정하지 않는다.** 보정은 원인을 지워 버린다. 경보만 남기고 원장으로 원인을 추적한 뒤 사람이 결정한다.

### 9.2 관측 지표

| 지표 | 용도 |
|---|---|
| `OUT_OF_STOCK` 발생률 (상품별) | 인기 상품의 경합 강도. 재고 등록량 조정 근거 |
| 선점 만료 비율 (`HOLD_EXPIRE` / `HOLD`) | 10분이 충분한지 판단하는 근거. 만료가 많으면 흐름 어딘가가 느리다 |
| 결제 3회 실패율 | 가상 결제 판정기 설정 검증 |
| 행 락 대기 시간 p99 / `INTERNAL_ERROR`(락 타임아웃) 건수 | 락 전략의 한계 도달 여부 |
| BATCH-01 1회 처리 건수와 소요 시간 | 30초 주기가 유지되는지 |
| `stock_ledgers` 유니크 위반 발생 건수 | 0이어야 한다. 0이 아니면 중복 처리 경로가 생겼다는 신호 |

---

## 10. 요구 항목 대조표

| # | 요구 항목 | 절 | 핵심 수단 | 실패 응답 |
|---|---|---|---|---|
| 1 | 임시 선점을 어디에 기록하는가 | §1 | `inventory_holds` 행 + `product_stocks.held_quantity` + `stock_ledgers(HOLD)` + `orders.hold_expires_at` | — |
| 2 | TTL 10분과 만료 회수 | §2 | 읽기 보정 + 쓰기 지연 정리 + BATCH-01(30초) + 결제 시점 검사 | `HOLD_EXPIRED`(409) |
| 3 | 마지막 1개 동시 주문 | §3 | `product_stocks` 행 `FOR UPDATE`(product_id 오름차순) + `CHK_stock_non_negative_available` | `OUT_OF_STOCK`(409) + `shortages[]` |
| 4 | 결제 실패 시 선점 해제 | §4 | 1~2회 유지, 3회째 `RELEASED`+`HOLD_RELEASE`, 만료가 먼저면 `EXPIRED`+`HOLD_EXPIRE` | 200 `result: FAILED` / `HOLD_EXPIRED`(409) |
| 5 | 취소 복구와 멱등성 | §5 | 조건부 상태 전이 + `UNIQUE (order_id, product_id, reason)` + `stock_settled_at` | `CANCEL_NOT_ALLOWED`·`CANCEL_DEADLINE_PASSED`(409) |
| 6 | 관리자 재고 수정 중 주문 | §6 | 동일 행 락 공유 + 지연 정리 후 하한선 검사 | `STOCK_BELOW_COMMITTED`(409) / `OUT_OF_STOCK`(409) |

### 05 §6.2 수량 변화표와의 대응

| 이벤트 | 트리거 | 원장 `reason` | 락 |
|---|---|---|---|
| S1 재고 증가 | API-109 | `ADMIN_ADJUST` | `product_stocks` |
| S2 재고 축소 | API-109 | `ADMIN_ADJUST` | `product_stocks` |
| S3 주문서 생성 | API-017 | `HOLD` | `product_stocks` |
| S4 결제 성공 | API-022 | `CONFIRM` | `orders` → `product_stocks` → `pickup_slots` → `pickup_number_seqs` |
| S5 결제 3회 실패 | API-022 | `HOLD_RELEASE` | `orders` → `product_stocks` |
| S6 선점 만료 | BATCH-01 / 지연 정리 / API-018·022 | `HOLD_EXPIRE` | `orders` → `product_stocks` (지연 정리는 `product_stocks`만) |
| — 주문서 포기 | API-019 | `HOLD_RELEASE` | `orders` → `product_stocks` |
| S7 취소 (마감 전) | API-025 / API-117 | `CANCEL_RESTORE` | `orders` → `product_stocks` → `pickup_slots` |
| S8 취소 (마감 후) | API-025 / API-117 | `CANCEL_DISCARD` | `orders` → `product_stocks` → `pickup_slots` |
| S9 픽업 완료 | API-116 | 없음 | `orders` |
| S10 노쇼 전환 | BATCH-03 | 없음 | `orders` → `pickup_slots` |

---

## 가정 / 미확정

### 가정 (확인 필요)

| # | 가정한 내용 | 근거 | 틀릴 경우 영향 |
|---|---|---|---|
| IC-A1 | RDBMS는 PostgreSQL이며 `SELECT ... FOR UPDATE`와 `SET LOCAL lock_timeout`을 쓴다 | 10번 T-A1 | MySQL이면 `FOR UPDATE`는 같지만 `innodb_lock_wait_timeout`이 초 단위 세션 설정이라 세밀한 제어가 어렵다 |
| IC-A2 | 트랜잭션 격리 수준은 기본값 READ COMMITTED다 | 필요한 직렬화 범위가 행 하나로 좁다. 행 락으로 충분하다 | REPEATABLE READ로 올리면 §3의 재조회 값이 트랜잭션 시작 시점 스냅샷이 되어 락 획득 후 다시 읽는 절차를 바꿔야 한다 |
| IC-A3 | 만료 회수의 지연 정리는 `inventory_holds`와 집계까지만 처리하고 `orders` 상태는 배치에 맡긴다 | 락 순서를 지키기 위함 (§7) | 지연 정리에서 주문까지 종결하려면 주문 단위 락을 먼저 잡아야 해 조회 경로의 대기가 늘어난다 |
| IC-A4 | 가상 결제 판정기는 지연 없이 즉시 결과를 반환한다(무응답도 즉시 `TIMEOUT` 반환) | 결제 전체를 한 트랜잭션으로 두라는 11번 API-022 절차와 짧은 락 보유를 동시에 만족시키는 유일한 방법 | 판정기가 실제로 대기하면 트랜잭션을 2단계로 나눠야 하고 중간 실패 복구 설계가 추가된다 |
| IC-A5 | 락 대기 타임아웃은 3초이고 초과 시 `INTERNAL_ERROR`(500)로 롤백한다 | 락 보유 구간이 수 ms이므로 3초 대기는 이상 상황이다 | 값이 짧으면 정상 경합에서도 실패가 나고, 길면 장애 시 요청이 쌓인다 |
| IC-A6 | BATCH-01 주기는 30초, 1회 100건이다 | BR-008의 1분 상한에 대한 여유값 | 주문량이 늘면 배치 1회가 30초를 넘길 수 있어 건수·주기 조정이 필요하다 |
| IC-A7 | 재고 복구 판정은 현행 `products.closing_at` 기준이다 | 10번 T-A7, BR-019 | 스냅샷 기준으로 바꾸면 마감을 늦춘 상품의 취소분이 복구되지 않는다 |
| IC-A8 | 다중 인스턴스 배치 중복 실행은 분산 락(ShedLock)으로 막는다 | 조건부 UPDATE만으로도 결과는 안전하지만 무의미한 경합을 줄인다 | 단일 인스턴스로 운영하면 필요 없다 |
| IC-A9 | 취소 재전송은 멱등 성공(200)이 아니라 `CANCEL_NOT_ALLOWED`(409)를 반환한다 | 11번 API-025 오류표 | 완전 멱등 응답이 필요하면 11번 오류표를 바꿔야 한다. 재고가 한 번만 변한다는 보증은 어느 쪽이든 동일하다 |

### 미확정 (결정 대기)

| # | 결정이 필요한 사항 | 선택지 | 막히는 작업 |
|---|---|---|---|
| IC-U1 | 상품 목록 조회의 만료 선점 보정 비용 | 매 조회 시 집계 (임시 채택) / 짧은 캐시(수 초) | 상품 수가 늘고 만료 선점이 쌓일 때의 조회 지연 |
| IC-U2 | 관리자 재고 조정의 낡은 값 방어 | 없음, 절대값 마지막 요청 우선 (임시 채택) / 요청에 기대 현재값을 넣어 다르면 거부 | 11번 API-109 요청 스키마 변경 여부 |
| IC-U3 | 락 대기 타임아웃 초과 시 서버 자동 재시도 | 재시도 없음 (임시 채택) / 1회 즉시 재시도 후 실패 | 재시도는 사용자 대기 시간을 늘리고, 재시도 없음은 500 노출을 늘린다 |
| IC-U4 | BATCH-04 불일치 발견 시 대응 | 경보만 (임시 채택) / 자동 보정 후 경보 | 자동 보정은 원인을 감춘다. 운영 인력 여건에 따라 달라진다 |
| IC-U5 | 만료된 선점 행의 장기 보관 | 삭제하지 않음 (임시 채택, 10번 §8) / 1년 후 아카이브 | `inventory_holds` 증가 속도와 `(expires_at) WHERE status='HELD'` 부분 인덱스 크기 |
| IC-U6 | 인기 상품의 대기열 안내 | 없음 (임시 채택) / 대기 순번 표시 | `OUT_OF_STOCK` 발생률이 높게 나올 때의 대응 |

### 향후 검토 (첫 버전 범위 밖)

| # | 내용 | 사유 |
|---|---|---|
| IC-F1 | 결제 대행사 연동 시 외부 호출을 트랜잭션 밖으로 빼는 2단계 구조와 미결(pending) 결제 조정 배치 | 제외 범위(실제 결제) |
| IC-F2 | 재고 조회용 캐시·읽기 전용 복제본 분리 | 현재 규모에서 단일 DB로 충분하다 |
| IC-F3 | 상품 단위 샤딩·파티셔닝 | 단일 매장 규모에서 불필요 |
| IC-F4 | 매장 간 재고 이동 시의 분산 트랜잭션 | 제외 범위(다중 매장) |
| IC-F5 | 재고 변경 이벤트 발행(이벤트 소싱·CDC) | 현재는 원장 테이블 조회로 충분하다 |
