# savePick 프로젝트 구조

- 문서 ID: 14
- 작성: technical-architect
- 최종 갱신: 2026-08-28
- 선행 문서: 01, 02, 03, 04, 05, 10, 11, 12, 13

## 한 줄 요약
`04-business-rules.md`의 규칙이 코드에서 **한 군데에만** 존재하도록 도메인별 4계층 구조를 정의하고, 재고 선점 로직의 위치와 6개 배치 작업의 소속을 확정한다.

---

## 0. 구조 설계 원칙

| # | 원칙 | 근거 |
|---|---|---|
| L1 | 규칙은 한 곳에만 둔다. API 경로와 배치 경로가 같은 규칙을 각자 구현하지 않는다 | BATCH-01과 API-022가 모두 선점 만료를 다룬다 (13번 §2) |
| L2 | 재고 수량을 바꾸는 코드는 한 지점을 통과한다 | 05 §0-6(수량 변화와 상태 변경은 함께), 13번 §0.3 |
| L3 | 의존 방향은 안쪽(도메인)으로만 흐른다. 도메인은 프레임워크·DB를 모른다 | 규칙을 테스트하는 데 DB가 필요하면 규칙 테스트를 안 쓰게 된다 |
| L4 | 트랜잭션 경계는 애플리케이션 계층에만 있다 | 락 순서(13번 §7)를 한 계층에서 통제하기 위함 |
| L5 | 폴더는 기술이 아니라 **도메인**으로 먼저 나눈다 | `controllers/`, `services/`로 나누면 주문 규칙을 고치려고 5개 폴더를 오간다 |
| L6 | 문서의 식별자(`FR-###`, `BR-###`, `API-###`, `BATCH-##`)를 코드에서 그대로 쓴다 | 요구사항 추적. 3개월 뒤에 이 코드가 왜 있는지 답할 수 있어야 한다 |

---

## 1. 기술 스택

### 1.1 선택

| 영역 | 선택 | 이유 |
|---|---|---|
| 언어 | Java 21 (LTS) | 레코드·패턴 매칭으로 값 객체 표현이 간결하고, 아래 프레임워크·DB 조합의 검증된 조합이다 |
| 프레임워크 | Spring Boot 3.3 | 선언적 트랜잭션(`@Transactional`), 비관적 락 지원, 스케줄러, 시큐리티 필터가 한 묶음으로 제공된다. 13번의 락·트랜잭션 설계를 프레임워크 밖에서 만들 필요가 없다 |
| 영속성 | Spring Data JPA + 일부 네이티브 쿼리 | 엔티티 매핑은 JPA로, 조건부 UPDATE와 집계 보정 쿼리는 네이티브로 쓴다 (§9.2) |
| DB | PostgreSQL 15 | 10번이 요구하는 부분 유니크 인덱스, 생성 컬럼, `SELECT ... FOR UPDATE`, `lock_timeout`을 모두 지원한다 (10번 T-A1) |
| 스키마 관리 | Flyway | 제약과 인덱스가 설계의 핵심이므로 스키마를 ORM 자동 생성에 맡기지 않는다 |
| 인증 | Spring Security + 직접 구현한 JWT 필터 | 12번의 401/403/404 구분과 `/api/admin/*` 경로 규칙을 필터 체인으로 표현한다 |
| 배치 | Spring `@Scheduled` + ShedLock | 6개 배치는 모두 단순 주기 작업이다. 잡 상태 관리가 필요 없어 Spring Batch는 과하다 |
| 빌드 | Gradle (Kotlin DSL) | |
| 테스트 | JUnit 5 + Testcontainers(PostgreSQL) | 13번의 락·제약은 실제 PostgreSQL에서만 검증된다. 인메모리 DB로는 부분 유니크 인덱스와 생성 컬럼을 재현할 수 없다 |

### 1.2 포기한 대안

| 대안 | 포기 이유 |
|---|---|
| Node.js(NestJS) + Prisma | 선언적 트랜잭션·비관적 락·스케줄러를 각각 조립해야 하고, 13번의 락 순서 규칙을 강제할 장치가 약하다 |
| 서버리스 함수 기반 백엔드 | 30초 주기 배치, 커넥션 풀, 행 락 보유 트랜잭션이 서버리스 실행 모델과 맞지 않는다 (13번 §2·§7) |
| MySQL | 부분 유니크 인덱스(`WHERE status='PENDING'`)를 직접 지원하지 않아 10번의 멱등성·유일성 제약 여러 개를 트리거나 보조 테이블로 흉내 내야 한다. 10번 T-A1의 전제가 무너진다 |
| MongoDB 등 문서 DB | 재고 정합성이 다중 문서 트랜잭션에 의존하게 되고, `CHECK` 제약으로 초과 판매를 막는 2층 방어선을 만들 수 없다 |
| Redis를 재고 정본으로 사용 | 13번 §1·§3에서 기각한 이유와 같다 (정본 이중화) |
| Spring Batch | 청크·잡 리포지토리 인프라가 6개 단순 주기 작업에 비해 과하다 |
| 마이크로서비스 분리 | 주문·재고·결제가 하나의 트랜잭션 안에서 원자적이어야 한다 (13번 §4). 서비스로 쪼개면 분산 트랜잭션 문제를 스스로 만든다 |

---

## 2. 저장소 구조

```
save-pick/
├── docs/                      # 01~14 설계 문서 (정본)
├── apps/
│   ├── api/                   # 서버 애플리케이션 (이 문서의 주 대상)
│   └── web/                   # 클라이언트 애플리케이션 (구조는 G3에서 확정, §가정 PS-U1)
├── wireframes/                # 디자인 트랙 산출물
├── assets/
└── deliverables/
```

이 문서는 `apps/api`의 구조를 정의한다. 클라이언트 구조는 06~09 디자인 문서가 확정된 뒤 G3에서 정한다.

---

## 3. 서버 계층

### 3.1 4계층과 의존 방향

```
        [ api ]            HTTP 입출력, 인증 필터, 오류 변환
           │
           ▼
     [ application ]       트랜잭션 경계, 락 순서, 여러 도메인 조합
           │
           ▼
       [ domain ]  ◀────  [ infrastructure ]
                          JPA 구현, 네이티브 쿼리, 스케줄 트리거
```

- 화살표 방향으로만 의존한다. `domain`은 어떤 계층도 참조하지 않는다.
- `infrastructure`는 `domain`이 선언한 리포지토리 인터페이스를 구현한다. 의존이 안쪽을 향한다.
- `api`가 `infrastructure`나 `domain` 엔티티를 직접 반환하지 않는다. 항상 `application`이 만든 결과 객체를 DTO로 변환한다.

### 3.2 계층별 책임

| 계층 | 하는 일 | 하지 않는 일 |
|---|---|---|
| `api` | 요청 검증(형식·필수값), 인증·권한 판정, 응답 DTO 변환, 예외 → 오류 코드 변환 | 업무 규칙 판정, 트랜잭션 시작, DB 접근 |
| `application` | 트랜잭션 경계 선언, **락 획득 순서 통제**, 도메인 서비스 호출 순서 결정, 원장·이력 기록 지시 | 수량 계산·상태 전이 가능 여부 판정, HTTP·JSON 인지 |
| `domain` | 엔티티·값 객체, 상태 전이 규칙, 수량 계산, 할인 구간 판정, 정책 객체 | DB·프레임워크·시각 직접 조회(`Clock` 주입받는다) |
| `infrastructure` | JPA 매핑, 비관적 락 쿼리, 조건부 UPDATE, 스케줄 트리거, 설정 어댑터 | 업무 규칙 판정, 여러 도메인 조합 |

**`api`의 요청 검증과 `domain`의 규칙 판정을 구분하는 기준**: "형식이 틀렸는가"는 `api`(→ `VALIDATION_ERROR`), "지금 해도 되는가"는 `domain`(→ `OUT_OF_STOCK`, `HOLD_EXPIRED` 등)이다.

---

## 4. 패키지 구조

루트 패키지는 `kr.savepick`이다.

```
apps/api/src/main/java/kr/savepick/
├── common/
│   ├── config/            SecurityConfig, JpaConfig, SchedulerConfig, ClockConfig
│   ├── error/             ErrorCode(11번 카탈로그와 1:1), BusinessException, GlobalExceptionHandler
│   ├── response/          PageResponse, ErrorResponse(code/message/serverTime/details)
│   ├── time/              ServerClock — 모든 시각 판정의 단일 출처 (BR-028)
│   ├── audit/             ActorType(CUSTOMER/ADMIN/SYSTEM), 현재 요청 주체 확보
│   └── batch/             DataRetentionJob (BATCH-06)
│
├── account/               회원·인증·노쇼 제한
│   ├── api/               AuthController(API-001~004), MeController(API-005~007),
│   │                      AdminAuthController(API-101)
│   ├── application/       SignUpService, LoginService, TokenRefreshService,
│   │                      MemberProfileService, OrderRestrictionService
│   ├── domain/            Member, AuthSession, LoginAttempt, MemberRestriction,
│   │                      LoginBlockPolicy, Role, OrderPermission
│   └── infrastructure/    MemberJpaRepository, AuthSessionJpaRepository,
│                          BcryptPasswordHasher, JwtAccessTokenIssuer, RefreshTokenHasher
│
├── store/                 매장 정보·영업시간·휴무일
│   ├── api/               StoreController(API-009), AdminStoreSettingController(API-120~121)
│   ├── application/       StoreQueryService, StoreSettingService
│   ├── domain/            Store, StoreHoliday, BusinessHours
│   └── infrastructure/    StoreJpaRepository, StoreHolidayJpaRepository
│
├── product/               상품 정보·판매 상태·할인 구간
│   ├── api/               ProductController(API-010~011),
│   │                      AdminProductController(API-102~108)
│   ├── application/       ProductQueryService, ProductRegisterService,
│   │                      ProductUpdateService, ProductStatusService
│   ├── domain/            Product, ProductStatus, ProductChangeLog,
│   │                      DiscountRatePolicy(BR-004), ClosingTimePolicy(BR-003)
│   ├── infrastructure/    ProductJpaRepository, ProductSearchQuery
│   └── batch/             ProductClosingJob (BATCH-02)
│
├── stock/                 재고 수량·임시 선점·재고 원장   ← 13번 문서의 구현 위치
│   ├── api/               AdminStockController(API-109~111)
│   ├── application/       StockAdjustService(API-109), StockQueryService(API-110~111),
│   │                      InventoryHoldService(선점 생성·해제·확정),
│   │                      ExpiredHoldReclaimer(지연 정리, 13번 §2.2),
│   │                      StockLedgerRecorder(모든 수량 변경의 단일 통과점)
│   ├── domain/            ProductStock, StockQuantities(값 객체), InventoryHold, HoldStatus,
│   │                      StockLedger, StockChangeReason, StockHoldPolicy,
│   │                      StockReductionPolicy(BR-025), StockRestorePolicy(BR-019)
│   ├── infrastructure/    ProductStockJpaRepository(비관적 락 조회),
│   │                      InventoryHoldJpaRepository, StockLedgerJpaRepository
│   └── batch/             StockConsistencyCheckJob (BATCH-04)
│
├── cart/                  장바구니 (재고를 건드리지 않는다, BR-010)
│   ├── api/               CartController(API-012~016)
│   ├── application/       CartService, CartValidationService(API-012 재검증), CartMergeService
│   ├── domain/            Cart, CartItem, CartLimitPolicy(BR-009)
│   └── infrastructure/    CartJpaRepository, CartItemJpaRepository
│
├── pickup/                픽업 시간대·정원·픽업 번호
│   ├── api/               AdminPickupSlotController(API-118~119)
│   ├── application/       PickupSlotQueryService, PickupSlotReserveService(정원 점유·반납),
│   │                      PickupNumberIssuer(BR-026)
│   ├── domain/            PickupSlot, SlotCapacity, PickupNumberSeq,
│   │                      SlotSelectablePolicy(BR-013·015·016·017)
│   ├── infrastructure/    PickupSlotJpaRepository, PickupNumberSeqJpaRepository
│   └── batch/             PickupSlotProvisionJob (BATCH-05)
│
└── order/                 주문·주문서·결제 시도·주문 상태
    ├── api/               OrderController(API-017~025), AdminOrderController(API-112~117)
    ├── application/       OrderDraftService(API-017 주문서 생성),
    │                      OrderHoldQueryService(API-018), OrderAbandonService(API-019),
    │                      PickupSlotAssignService(API-021),
    │                      OrderCancelService(API-025·117), OrderFulfillService(API-115~116),
    │                      OrderQueryService
    ├── domain/            Order, OrderStatus(8개), OrderItem, OrderStatusHistory,
    │                      OrderNumberGenerator, CancelDeadlinePolicy(BR-018),
    │                      OrderTransitionRule(05 §2.2·2.3)
    ├── payment/           PaymentAttemptService(API-022), PaymentAttempt, PaymentResult,
    │                      VirtualPaymentGateway, PaymentRetryPolicy(BR-012)
    ├── infrastructure/    OrderJpaRepository, OrderItemJpaRepository,
    │                      PaymentAttemptJpaRepository
    └── batch/             HoldExpiryReclaimJob (BATCH-01), NoShowDetectionJob (BATCH-03)
```

### 4.1 도메인 간 의존 방향

```
order ──► stock      (선점·확정·복구)
order ──► pickup     (정원 점유·픽업 번호)
order ──► product    (읽기: 마감 시각·상태·수량 한도)
order ──► account    (읽기: 주문 제한 상태·연락처 스냅샷)
cart  ──► product    (읽기)
stock ──► product    (읽기)
pickup ─► store      (읽기: 영업시간·휴무일)
product ► store      (읽기: 영업 종료 시각)
account ─ (의존 없음)
```

- 순환 의존이 없다. `stock`은 `order`를 모른다. 선점 요청은 `order`가 `stock`의 애플리케이션 서비스를 호출해 시작한다.
- `payment`를 별도 도메인으로 두지 않고 `order` 하위에 둔 이유: `payment_attempts`는 `orders` 없이 존재할 수 없고(FK NOT NULL), 결제 성공이 곧 주문 확정이다. 분리하면 `payment → order` 역방향 호출이 생겨 순환이 된다.
- 도메인 간 호출은 **애플리케이션 서비스 인터페이스**를 통해서만 한다. 다른 도메인의 엔티티나 JPA 리포지토리를 직접 참조하지 않는다.

---

## 5. 재고 선점 로직의 위치

13번 문서의 설계가 코드 어디에 있는지 명시한다. 이 표를 벗어난 곳에 재고 로직을 두지 않는다.

| 13번 설계 요소 | 위치 | 계층 |
|---|---|---|
| 락 획득과 순서(`orders` → `product_stocks` → …) | `order/application/OrderDraftService`, `PaymentAttemptService`, `OrderCancelService` | application |
| 비관적 락 조회 (`FOR UPDATE`) | `stock/infrastructure/ProductStockJpaRepository#findForUpdate` | infrastructure |
| 판매 가능 수량 계산·부족 판정 | `stock/domain/StockQuantities`, `StockHoldPolicy` | domain |
| 선점 생성·해제·확정 실행 | `stock/application/InventoryHoldService` | application |
| 만료 선점 지연 정리 (13번 §2.2) | `stock/application/ExpiredHoldReclaimer` | application |
| 만료 선점 읽기 보정 (13번 §2.1) | `stock/application/StockQueryService` + `stock/infrastructure`의 집계 쿼리 | application / infrastructure |
| 수량 변경과 원장 기록의 동시 수행 (L2) | `stock/application/StockLedgerRecorder` | application |
| 축소 하한선 판정 (BR-025) | `stock/domain/StockReductionPolicy` | domain |
| 복구·폐기 판정 (BR-019) | `stock/domain/StockRestorePolicy` | domain |
| 만료 회수 배치 | `order/batch/HoldExpiryReclaimJob` (트리거) → `stock`·`order` 서비스 호출 | infrastructure 트리거 + application 로직 |

**강제 규칙**

1. `product_stocks`를 UPDATE 하는 코드는 `StockLedgerRecorder`를 반드시 통과한다. 원장 없이 수량만 바뀌는 경로를 만들지 않는다 (05 C4).
2. `InventoryHold`의 상태 전이는 항상 조건부(`WHERE status = 'HELD'`)로 실행한다. 이 조건이 없는 UPDATE를 작성하지 않는다.
3. 컨트롤러(`api`)에 재고 수량 비교나 만료 시각 비교를 두지 않는다.
4. `stock` 도메인의 정책 객체는 `Clock`을 주입받는다. `LocalDateTime.now()`를 직접 호출하는 코드를 도메인에 두지 않는다 (BR-028).

---

## 6. 배치 작업 배치 위치

### 6.1 소속과 근거

| 배치 | 클래스 위치 | 주기 | 그 도메인에 둔 이유 |
|---|---|---|---|
| BATCH-01 선점 만료 회수 | `order/batch/HoldExpiryReclaimJob` | 30초 | 최종 결과가 **주문 종결**(PENDING → EXPIRED)이다. 선점 회수는 `stock` 서비스를 호출해 수행한다 |
| BATCH-02 상품 마감 상태 전환 | `product/batch/ProductClosingJob` | 30초 | `products.status` 전이만 다룬다 |
| BATCH-03 노쇼 전환·제재 | `order/batch/NoShowDetectionJob` | 3분 | 주문 상태 전이가 시작점이다. 제재 생성은 `account` 서비스를 호출한다 |
| BATCH-04 재고 정합성 점검 | `stock/batch/StockConsistencyCheckJob` | 매일 03:00 | 재고 집계와 원장만 비교한다 |
| BATCH-05 픽업 시간대 생성 | `pickup/batch/PickupSlotProvisionJob` | 매일 00:05 + 기동 시 | 슬롯 생성 규칙이 `pickup` 도메인에 있다 |
| BATCH-06 만료 데이터 정리 | `common/batch/DataRetentionJob` | 매일 04:00 | 세션·로그인 기록·게스트 장바구니 등 여러 도메인에 걸친 위생 작업이다. 각 도메인의 정리 서비스를 호출한다 |

### 6.2 배치 클래스가 하는 일과 하지 않는 일

```
@Component
class HoldExpiryReclaimJob {
    @Scheduled(fixedDelayString = "${savepick.batch.hold-expiry.interval}")
    @SchedulerLock(name = "BATCH-01-hold-expiry")
    fun run() { ... 대상 조회 후 건별로 애플리케이션 서비스 호출 ... }
}
```

| 한다 | 하지 않는다 |
|---|---|
| 실행 시각 트리거, 분산 락 획득, 대상 조회, **건별로** 애플리케이션 서비스 호출, 결과 로깅 | 상태 전이 판정, 수량 계산, 원장 기록 형식 결정 |

- 배치 1회 실행 안에서 **건당 1트랜잭션**으로 처리한다. 100건을 한 트랜잭션에 묶지 않는다 (13번 §7.2).
- 배치가 호출하는 서비스는 API 경로가 호출하는 서비스와 **같은 클래스**다. 규칙이 두 벌로 갈라지지 않는다 (L1).
- 배치를 HTTP로 노출하지 않는다 (12번 §4.6).
- `@Scheduled` 트리거를 각 도메인의 `batch/` 아래에 두고 별도 `batch` 최상위 모듈을 만들지 않은 이유: 배치를 한곳에 모으면 그 안에서 도메인 규칙을 다시 구현하려는 압력이 생긴다. 트리거를 규칙 옆에 두면 기존 서비스를 호출하는 것이 자연스러운 선택이 된다.

---

## 7. 명명 규칙

### 7.1 클래스

| 종류 | 규칙 | 예 |
|---|---|---|
| 컨트롤러 | `<대상>Controller`, 관리자용은 `Admin` 접두 | `OrderController`, `AdminStockController` |
| 요청 DTO | `<동작>Request` | `CreateOrderRequest`, `AdjustStockRequest` |
| 응답 DTO | `<대상>Response` | `OrderDraftResponse`, `HoldStatusResponse` |
| 애플리케이션 서비스 | `<동작 대상><동작>Service` | `OrderCancelService`, `StockAdjustService` |
| 조회 전용 서비스 | `<대상>QueryService` | `ProductQueryService` |
| 도메인 정책 | `<규칙 대상>Policy` | `DiscountRatePolicy`, `CancelDeadlinePolicy` |
| 도메인 엔티티 | 단수 명사, 접미사 없음 | `Order`, `ProductStock`, `InventoryHold` |
| 값 객체 | 의미 중심 명사 | `StockQuantities`, `BusinessHours`, `SlotCapacity` |
| 리포지토리 인터페이스 | `<엔티티>Repository` (domain) | `ProductStockRepository` |
| JPA 구현 | `<엔티티>JpaRepository` (infrastructure) | `ProductStockJpaRepository` |
| 배치 | `<작업>Job` | `HoldExpiryReclaimJob` |
| 열거형 | 05번 문서의 상태값과 **문자 그대로 일치** | `OrderStatus.PENDING`, `HoldStatus.HELD` |

### 7.2 메서드

| 종류 | 규칙 | 예 |
|---|---|---|
| 조회 (없으면 예외) | `get<대상>` | `getOrder(orderId)` |
| 조회 (없으면 빈 값) | `find<대상>` | `findActiveRestriction(memberId)` |
| 판정 | `is~` / `can~` / `has~` | `canCancel(now)`, `hasEnoughStock(quantity)` |
| 상태 전이 | 도메인 사건의 이름 | `confirm()`, `release()`, `expire()`, `consume()` |
| 락 조회 | `~ForUpdate` | `findByProductIdForUpdate(productId)` |

`setStatus(...)` 같은 세터로 상태를 바꾸지 않는다. 전이 규칙(05 §2.3)이 통과할 자리가 사라진다.

### 7.3 데이터베이스

| 대상 | 규칙 | 예 |
|---|---|---|
| 테이블 | 스네이크 복수형 | `orders`, `inventory_holds`, `stock_ledgers` |
| 컬럼 | 스네이크 단수형 | `hold_expires_at`, `held_quantity` |
| 기본키 | `id` (`pickup_number_seqs`는 `business_date`) | |
| 외래키 컬럼 | `<참조 테이블 단수>_id` | `product_id`, `order_id` |
| 시각 컬럼 | `<사건>_at` | `confirmed_at`, `canceled_at`, `expires_at` |
| 수량 컬럼 | `<의미>_quantity` | `total_quantity`, `discarded_quantity` |
| 제약 | `CHK_<테이블>_<의미>` / `UQ_<테이블>_<컬럼들>` | `CHK_stock_non_negative_available` |
| 인덱스 | `IX_<테이블>_<컬럼들>` | `IX_orders_hold_expires_at_pending` |
| 마이그레이션 | `V<번호>__<내용>.sql` | `V1__init_schema.sql`, `V2__add_stock_indexes.sql` |

컬럼명은 10번 문서에 적힌 이름을 그대로 쓴다. 코드에서 이름을 바꾸지 않는다.

### 7.4 API·오류 코드

| 대상 | 규칙 |
|---|---|
| 고객 경로 | `/api/<복수 명사>` (11번 §0.1) |
| 관리자 경로 | `/api/admin/<복수 명사>` |
| 하위 행위 | `POST /api/orders/{orderId}/cancel` 처럼 명사 뒤에 동사 1개 |
| JSON 필드 | 카멜 케이스 (`holdExpiresAt`) |
| 오류 코드 | 대문자 스네이크. `common/error/ErrorCode` 열거형이 11번 §0.5 카탈로그와 **1:1**로 대응한다 |

`ErrorCode`에 카탈로그에 없는 값을 추가하면 11번 문서를 먼저 고친다. 코드가 문서를 앞서지 않는다.

### 7.5 설정 키와 테스트

| 대상 | 규칙 | 예 |
|---|---|---|
| 설정 키 | `savepick.<영역>.<항목>` | `savepick.hold.ttl` |
| 단위 테스트 | `<대상>Test` | `StockHoldPolicyTest` |
| 통합 테스트 | `<대상>IntegrationTest` | `OrderDraftServiceIntegrationTest` |
| 동시성 테스트 | `<대상>ConcurrencyTest` | `InventoryHoldConcurrencyTest` |
| 테스트 메서드 | `<상황>_<기대결과>` (한국어 `@DisplayName` 병기) | `마지막_1개_동시주문_한_건만_성공한다` |

---

## 8. 설정값 외부화

G1 확정값을 코드에 상수로 박지 않고 설정으로 뺀다. 값이 바뀔 때 고칠 자리가 하나여야 한다.

```yaml
savepick:
  time-zone: Asia/Seoul                # BR-028
  hold:
    ttl: PT10M                         # BR-007 선점 10분, 연장 없음
    expiring-soon-threshold: PT60S     # 03 U1 (임시 채택)
  payment:
    max-attempts: 3                    # BR-012
  order:
    customer-cancel-deadline: PT1H     # BR-018 픽업 시작 1시간 전
  pickup:
    open-time: "10:00"                 # BR-014
    close-time: "22:00"
    slot-unit: PT30M                   # 30분 단위 24슬롯
    default-slot-capacity: 20          # BR-016
    reservation-close-before: PT30M    # BR-015
    selectable-days: 2                 # BR-013 D+0~D+1
    no-show-grace: PT30M               # BR-021
  restriction:
    no-show-window-days: 30            # BR-023
    no-show-threshold: 3
    restriction-days: 7
  auth:
    access-token-ttl: PT30M            # 12번 §2.1
    refresh-session-ttl: P30D          # FR-002
    login-fail-threshold: 5            # 03 A3
    login-block-duration: PT10M
  stock:
    lock-timeout: PT3S                 # 13번 §3
  batch:
    hold-expiry.interval: PT30S        # BATCH-01 (BR-008 1분 이내)
    product-closing.interval: PT30S    # BATCH-02
    no-show.interval: PT3M             # BATCH-03 (BR-021 5분 이내)
```

각 값 옆의 `BR-###`는 주석이 아니라 추적 표시다. 값을 바꾸려면 해당 규칙 문서를 먼저 확인한다.

`stores` 테이블에도 영업시간·기본 정원이 있다(10번 §3.1). 설정 파일의 값은 **최초 시드와 기본값**이고, 운영 중 정본은 DB다 (API-121로 변경). 두 곳이 다르면 DB를 따른다.

---

## 9. 횡단 관심사

### 9.1 시각

- `common/time/ServerClock`이 유일한 시각 출처다. `Clock` 빈을 주입해 테스트에서 시각을 고정할 수 있게 한다.
- 도메인·애플리케이션 코드에서 `LocalDateTime.now()`, `Instant.now()`를 직접 호출하지 않는다.
- DB에서 판정하는 시각(`now()`)과 애플리케이션 시각이 섞이면 만료 판정이 어긋난다. **선점 만료·취소 마감처럼 단일 트랜잭션 안에서 비교하는 판정은 DB의 `now()`를 기준으로 한다.** 표시용 잔여 시간은 응답의 `serverTime`으로 내려 클라이언트가 보정한다 (FR-005).

### 9.2 트랜잭션과 락

- `@Transactional`은 `application` 계층에만 붙인다. `api`·`domain`에는 붙이지 않는다.
- 트랜잭션 진입 시 필요한 행 락을 13번 §7.1의 순서대로 획득한다. 이 순서를 서비스마다 다시 정하지 않도록 `common`에 순서를 문서화한 상수·주석을 둔다.
- 조건부 UPDATE(상태 전이)는 JPA 더티 체킹이 아니라 명시적 쿼리로 작성한다. 영향 행 수를 확인해야 하기 때문이다.
- 조회 전용 서비스에는 `@Transactional(readOnly = true)`를 붙인다.

### 9.3 예외와 오류 응답

```
domain / application  →  BusinessException(ErrorCode, details)
        ↓
api / GlobalExceptionHandler  →  ErrorResponse { code, message, serverTime, details }
```

- 모든 오류 응답에 `serverTime`을 포함한다 (11번 §0.3).
- `ErrorCode`가 HTTP 상태를 함께 들고 있어 컨트롤러가 상태 코드를 판단하지 않는다.
- 예상하지 못한 예외는 `INTERNAL_ERROR`(500)로 변환하고 스택 트레이스는 로그에만 남긴다 (12번 §5 P5).
- DB 제약 위반(유니크·CHECK)은 잡아서 의미 있는 `ErrorCode`로 번역한다. 예: `orders`의 PENDING 부분 유니크 위반 → `PENDING_ORDER_EXISTS`, `stock_ledgers` 유니크 위반 → 중복 처리이므로 롤백 후 현재 상태에 맞는 코드로 응답.

### 9.4 인증 필터

```
JwtAuthenticationFilter   액세스 토큰 검증 → 주체(sub, role, sid) 확보
        ↓
AdminPathAuthorizationRule   /api/admin/* 경로에 role = ADMIN 요구 (12번 §3.1)
        ↓
컨트롤러 메서드 권한 선언   @RequireRole(CUSTOMER) 등
        ↓
서비스의 소유권 검사       orders.member_id = sub (아니면 404)
```

소유권 검사를 컨트롤러에 흩지 않고 조회 조건 자체에 넣는다. `findByIdAndMemberId(...)`가 비면 404다.

### 9.5 로깅

| 항목 | 규칙 |
|---|---|
| 요청 추적 | 요청마다 `traceId`를 발급해 MDC에 넣고 모든 로그에 남긴다 |
| 마스킹 | `password`, `accessToken`, `Set-Cookie`는 마스킹한다 (12번 §5 P4) |
| 재고 변경 | 애플리케이션 로그가 아니라 `stock_ledgers`가 정본이다. 로그는 보조 |
| 배치 | 1회 실행마다 처리 건수·소요 시간·실패 건수를 남긴다 (13번 §9.2) |

---

## 10. 테스트 구조

```
apps/api/src/test/java/kr/savepick/
├── domain/          정책·상태 전이 단위 테스트 (DB 없음, 빠름)
├── application/     Testcontainers 기반 통합 테스트 (제약·락 검증)
├── concurrency/     동시성 시나리오 테스트
└── api/             컨트롤러 슬라이스 테스트 (권한·오류 코드 매핑)
```

반드시 있어야 하는 동시성 테스트 (13번 §10 항목과 1:1)

| 테스트 | 검증 대상 |
|---|---|
| 마지막 1개를 두 스레드가 동시에 주문 | 한 건만 201, 다른 건은 `OUT_OF_STOCK`. `held_quantity`가 1을 넘지 않는다 |
| 선점 만료 직후 다른 고객 주문 | 지연 정리로 즉시 구매 가능해진다 |
| BATCH-01과 결제 성공이 동시에 진행 | 조건부 UPDATE로 한쪽만 성공. 원장이 중복되지 않는다 |
| 같은 취소 요청을 두 번 전송 | 재고가 한 번만 변한다. 두 번째는 409 |
| 고객 취소와 관리자 취소 동시 실행 | 한쪽만 성공, `stock_ledgers` 유니크 위반이 발생하지 않는다 |
| 관리자 축소와 주문서 생성 동시 실행 | 순서와 무관하게 `total >= held + confirmed`가 유지된다 |
| 같은 `Idempotency-Key`로 결제 2회 전송 | 시도 횟수가 1만 증가한다 |
| 슬롯 마지막 1자리 동시 결제 | 한 건만 CONFIRMED, 다른 건은 `SLOT_FULL`이며 선점은 유지된다 |

단위 테스트는 인메모리로 돌리되, **제약·락·부분 유니크 인덱스를 다루는 테스트는 반드시 실제 PostgreSQL(Testcontainers)에서 돌린다.** 이 설계의 방어선 2·3층이 DB 기능에 있기 때문이다 (13번 §0.3).

---

## 11. 문서 ↔ 코드 추적

| 문서 식별자 | 코드에서의 위치 |
|---|---|
| `FR-###` | 컨트롤러 메서드 주석 1줄, 통합 테스트 `@DisplayName` |
| `BR-###` | 도메인 정책 클래스 주석, 설정 키 옆 표시 |
| `API-###` | 컨트롤러 메서드 주석 |
| `BATCH-##` | Job 클래스명 주석과 `@SchedulerLock(name = "BATCH-01-...")` |
| 05번 상태값 | 열거형 상수명과 문자 그대로 일치 |
| 10번 컬럼명 | 엔티티 필드의 `@Column(name = ...)` |
| 11번 오류 코드 | `ErrorCode` 열거형 상수명 |

새 규칙을 코드로 먼저 만들지 않는다. 문서(04·05)를 고치고 식별자를 받은 뒤 구현한다.

---

## 가정 / 미확정

### 가정 (확인 필요)

| # | 가정한 내용 | 근거 | 틀릴 경우 영향 |
|---|---|---|---|
| PS-A1 | 백엔드는 Java 21 + Spring Boot 3.3 단일 애플리케이션이다 | 선언적 트랜잭션·비관적 락·스케줄러가 13번 설계에 그대로 필요하다 | 다른 스택이면 §4 패키지 구조와 §9 횡단 관심사 구현 방식이 바뀐다. 계층 분리와 도메인 경계는 유지된다 |
| PS-A2 | DB는 PostgreSQL 15다 | 10번 T-A1 | MySQL이면 10번의 부분 유니크 인덱스 여러 개를 대체 설계해야 한다 |
| PS-A3 | 단일 애플리케이션(모놀리식) 안에 고객 API·관리자 API·배치가 모두 있다 | 하나의 트랜잭션 안에서 주문·재고·픽업이 원자적이어야 한다 | 분리하면 분산 트랜잭션이 필요해진다 |
| PS-A4 | 도메인으로 먼저 나누고 그 안에서 계층으로 나눈다 | L5 | 계층 우선 구조로 바꾸면 한 규칙 수정에 여러 최상위 폴더를 오간다 |
| PS-A5 | `payment`는 `order`의 하위 패키지다 | `payment_attempts`가 `orders`에 종속되고 역방향 호출이 순환을 만든다 | 실제 결제 연동이 생기면 별도 도메인으로 승격이 필요하다 |
| PS-A6 | 선점(`inventory_holds`)은 `stock` 도메인 소유다 | 선점은 주문 상태가 아니라 재고 자원의 상태다 | `order` 소유로 옮기면 `stock`이 `order`를 알아야 해 의존이 순환한다 |
| PS-A7 | 배치 트리거를 각 도메인의 `batch/` 아래에 둔다 | L1. 규칙 옆에 두면 기존 서비스를 호출하게 된다 | 별도 배치 애플리케이션으로 분리하면 서비스 계층을 공유 모듈로 빼야 한다 |
| PS-A8 | 인프라(배포·CI)는 이 문서 범위 밖이다 | 설계 문서의 범위는 코드 구조까지다 | 배포 형태(컨테이너·인스턴스 수)가 정해지면 배치 분산 락 설정이 달라진다 |

### 미확정 (결정 대기)

| # | 결정이 필요한 사항 | 선택지 | 막히는 작업 |
|---|---|---|---|
| PS-U1 | 클라이언트 애플리케이션 구조 | G3에서 확정 (임시 보류) / 고객·관리자 단일 앱 / 분리 | 06~09 디자인 문서 확정 후 결정 |
| PS-U2 | 저장소 형태 | 단일 저장소에 `apps/api`·`apps/web` (임시 채택) / 저장소 분리 | 배포 파이프라인 구성 |
| PS-U3 | 조회 전용 쿼리 작성 방식 | Spring Data + 네이티브 쿼리 (임시 채택) / QueryDSL 도입 | 관리자 목록의 복합 필터(API-112) 구현 방식 |
| PS-U4 | 도메인 엔티티와 JPA 엔티티 분리 여부 | 하나로 사용 (임시 채택) / 분리해 매핑 계층 추가 | 규모가 작아 분리 이득이 작다. 도메인 규칙이 늘면 재검토 |
| PS-U5 | 배치 분산 락 도입 시점 | 처음부터 ShedLock (임시 채택) / 단일 인스턴스면 생략 | 배포 인스턴스 수 확정 여부 (PS-A8) |
| PS-U6 | API 문서 자동화 | 미정 / SpringDoc(OpenAPI) 생성 | 11번 문서와 자동 생성 문서의 정본 관계 정리 필요 |

### 향후 검토 (첫 버전 범위 밖)

| # | 내용 | 사유 |
|---|---|---|
| PS-F1 | 읽기 전용 복제본 분리와 조회 트래픽 분산 | 현재 규모에서 불필요 (13번 IC-F2) |
| PS-F2 | 도메인별 서비스 분리(마이크로서비스) | 트랜잭션 원자성 요구와 충돌 |
| PS-F3 | 이벤트 발행 기반 도메인 간 통신 | 현재는 동기 호출로 충분하고 순환도 없다 |
| PS-F4 | 멀티 테넌시(매장별 분기) 모듈 구조 | 제외 범위(다중 매장) |
| PS-F5 | 알림 발송 모듈 | 03 F6 |
