-- docs/10-erd.md 기준 초기 스키마. 19개 엔티티, 규칙→제약 매핑은 10번 문서 §7 참조.
-- 명명 규칙은 docs/14-project-structure.md §7.3을 따른다.

CREATE EXTENSION IF NOT EXISTS pgcrypto;   -- gen_random_uuid()
CREATE EXTENSION IF NOT EXISTS pg_trgm;    -- 상품명 부분 일치 검색 (T-U1)

-- =========================================================================
-- 1. 매장·운영 영역
-- =========================================================================

CREATE TABLE stores (
    id                      SMALLINT PRIMARY KEY,
    name                    VARCHAR(100) NOT NULL,
    address                 VARCHAR(255) NOT NULL,
    phone                   VARCHAR(20) NOT NULL,
    open_time               TIME NOT NULL DEFAULT '10:00',
    close_time              TIME NOT NULL DEFAULT '22:00',
    slot_unit_minutes       SMALLINT NOT NULL,
    default_slot_capacity   SMALLINT NOT NULL DEFAULT 20,
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT CHK_stores_single_row CHECK (id = 1),
    CONSTRAINT CHK_stores_slot_unit CHECK (slot_unit_minutes = 30),
    CONSTRAINT CHK_stores_capacity_positive CHECK (default_slot_capacity >= 1),
    CONSTRAINT CHK_stores_open_before_close CHECK (open_time < close_time),
    CONSTRAINT CHK_stores_open_time_half_hour CHECK (EXTRACT(MINUTE FROM open_time)::int IN (0, 30)),
    CONSTRAINT CHK_stores_close_time_half_hour CHECK (EXTRACT(MINUTE FROM close_time)::int IN (0, 30))
);

CREATE TABLE store_holidays (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    store_id       SMALLINT NOT NULL REFERENCES stores (id),
    holiday_date   DATE NOT NULL,
    memo           VARCHAR(100),
    CONSTRAINT UQ_store_holidays_store_date UNIQUE (store_id, holiday_date)
);

-- =========================================================================
-- 2. 계정 영역
-- =========================================================================

CREATE TABLE members (
    id                 BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    email              VARCHAR(255) NOT NULL,
    password_hash      VARCHAR(255) NOT NULL,
    name               VARCHAR(50) NOT NULL,
    phone              VARCHAR(20) NOT NULL,
    role               VARCHAR(10) NOT NULL DEFAULT 'CUSTOMER',
    order_permission   VARCHAR(10) NOT NULL DEFAULT 'ALLOWED',
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT UQ_members_email UNIQUE (email),
    CONSTRAINT CHK_members_role CHECK (role IN ('CUSTOMER', 'ADMIN')),
    CONSTRAINT CHK_members_order_permission CHECK (order_permission IN ('ALLOWED', 'RESTRICTED'))
);

CREATE TABLE auth_sessions (
    id                   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    member_id            BIGINT NOT NULL REFERENCES members (id) ON DELETE CASCADE,
    refresh_token_hash   CHAR(64) NOT NULL,
    issued_at            TIMESTAMPTZ NOT NULL,
    last_used_at         TIMESTAMPTZ NOT NULL,
    expires_at           TIMESTAMPTZ NOT NULL,
    revoked_at           TIMESTAMPTZ,
    user_agent           VARCHAR(255),
    CONSTRAINT UQ_auth_sessions_refresh_token_hash UNIQUE (refresh_token_hash),
    CONSTRAINT CHK_auth_sessions_expires_after_issued CHECK (expires_at > issued_at)
);
CREATE INDEX IX_auth_sessions_member_revoked ON auth_sessions (member_id, revoked_at);
CREATE INDEX IX_auth_sessions_expires_at ON auth_sessions (expires_at);

CREATE TABLE login_attempts (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    email          VARCHAR(255) NOT NULL,
    member_id      BIGINT REFERENCES members (id),
    succeeded      BOOLEAN NOT NULL,
    attempted_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    client_ip      INET
);
CREATE INDEX IX_login_attempts_email_attempted_at ON login_attempts (email, attempted_at DESC);

-- =========================================================================
-- 3. 상품·재고 영역
-- =========================================================================

CREATE TABLE products (
    id                   BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    store_id             SMALLINT NOT NULL REFERENCES stores (id),
    name                 VARCHAR(100) NOT NULL,
    description          TEXT,
    sale_unit            VARCHAR(20) NOT NULL,
    original_price       INT NOT NULL,
    closing_at           TIMESTAMPTZ NOT NULL,
    max_order_quantity   SMALLINT NOT NULL DEFAULT 5,
    status               VARCHAR(10) NOT NULL DEFAULT 'DRAFT',
    closed_at            TIMESTAMPTZ,
    created_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT CHK_products_original_price CHECK (original_price >= 100),
    CONSTRAINT CHK_products_max_order_quantity CHECK (max_order_quantity >= 1),
    CONSTRAINT CHK_products_status CHECK (status IN ('DRAFT', 'ON_SALE', 'HIDDEN', 'CLOSED'))
);
CREATE INDEX IX_products_status_closing_at ON products (status, closing_at);
CREATE INDEX IX_products_closing_at_open ON products (closing_at) WHERE status <> 'CLOSED';
CREATE INDEX IX_products_name_trgm ON products USING GIN (lower(name) gin_trgm_ops);

CREATE TABLE product_stocks (
    product_id           BIGINT PRIMARY KEY REFERENCES products (id),
    total_quantity       INT NOT NULL,
    held_quantity        INT NOT NULL DEFAULT 0,
    confirmed_quantity   INT NOT NULL DEFAULT 0,
    discarded_quantity   INT NOT NULL DEFAULT 0,
    available_quantity   INT GENERATED ALWAYS AS (total_quantity - held_quantity - confirmed_quantity) STORED,
    updated_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT CHK_product_stocks_total_non_negative CHECK (total_quantity >= 0),
    CONSTRAINT CHK_product_stocks_held_non_negative CHECK (held_quantity >= 0),
    CONSTRAINT CHK_product_stocks_confirmed_non_negative CHECK (confirmed_quantity >= 0),
    CONSTRAINT CHK_product_stocks_discarded_non_negative CHECK (discarded_quantity >= 0),
    -- 초과 판매를 막는 최후 방어선. 어떤 코드 경로에서도 우회할 수 없다 (10번 §4.2).
    CONSTRAINT CHK_stock_non_negative_available CHECK (total_quantity - held_quantity - confirmed_quantity >= 0)
);
CREATE INDEX IX_product_stocks_available_quantity ON product_stocks (available_quantity);

CREATE TABLE pickup_slots (
    id               BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    store_id         SMALLINT NOT NULL REFERENCES stores (id),
    slot_date        DATE NOT NULL,
    start_at         TIMESTAMPTZ NOT NULL,
    end_at           TIMESTAMPTZ NOT NULL,
    capacity         SMALLINT NOT NULL,
    reserved_count   SMALLINT NOT NULL DEFAULT 0,
    blocked          BOOLEAN NOT NULL DEFAULT false,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT CHK_pickup_slots_capacity_positive CHECK (capacity >= 1),
    CONSTRAINT CHK_pickup_slots_reserved_non_negative CHECK (reserved_count >= 0),
    CONSTRAINT CHK_pickup_slots_end_after_start CHECK (end_at = start_at + INTERVAL '30 minutes'),
    CONSTRAINT UQ_pickup_slots_store_start UNIQUE (store_id, start_at)
);
CREATE INDEX IX_pickup_slots_date_start ON pickup_slots (slot_date, start_at);

CREATE TABLE pickup_number_seqs (
    business_date   DATE PRIMARY KEY,
    last_number     SMALLINT NOT NULL DEFAULT 0,
    CONSTRAINT CHK_pickup_number_seqs_range CHECK (last_number BETWEEN 0 AND 999)
);

-- =========================================================================
-- 4. 장바구니 영역
-- =========================================================================

CREATE TABLE carts (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    member_id    BIGINT REFERENCES members (id),
    guest_token  UUID,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT CHK_carts_owner_present CHECK (member_id IS NOT NULL OR guest_token IS NOT NULL)
);
CREATE UNIQUE INDEX UQ_carts_member_id ON carts (member_id) WHERE member_id IS NOT NULL;
CREATE UNIQUE INDEX UQ_carts_guest_token ON carts (guest_token) WHERE guest_token IS NOT NULL;

CREATE TABLE cart_items (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    cart_id       BIGINT NOT NULL REFERENCES carts (id) ON DELETE CASCADE,
    product_id    BIGINT NOT NULL REFERENCES products (id),
    quantity      SMALLINT NOT NULL,
    added_price   INT NOT NULL,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT CHK_cart_items_quantity_positive CHECK (quantity >= 1),
    CONSTRAINT UQ_cart_items_cart_product UNIQUE (cart_id, product_id)
);
CREATE INDEX IX_cart_items_cart_id ON cart_items (cart_id);

-- =========================================================================
-- 5. 주문 영역
-- =========================================================================

CREATE TABLE orders (
    id                       BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    order_no                 VARCHAR(20) NOT NULL,
    member_id                BIGINT NOT NULL REFERENCES members (id),
    status                   VARCHAR(12) NOT NULL,
    total_amount             INT NOT NULL,
    contact_name             VARCHAR(50) NOT NULL,
    contact_phone            VARCHAR(20) NOT NULL,
    pickup_slot_id           BIGINT REFERENCES pickup_slots (id),
    pickup_business_date     DATE,
    pickup_number            SMALLINT,
    hold_expires_at          TIMESTAMPTZ,
    payment_attempt_count    SMALLINT NOT NULL DEFAULT 0,
    cancelable_until         TIMESTAMPTZ,
    no_show_due_at           TIMESTAMPTZ,
    stock_settled_at         TIMESTAMPTZ,
    canceled_by              VARCHAR(10),
    cancel_reason            VARCHAR(200),
    confirmed_at             TIMESTAMPTZ,
    ready_at                 TIMESTAMPTZ,
    completed_at             TIMESTAMPTZ,
    canceled_at              TIMESTAMPTZ,
    no_show_at               TIMESTAMPTZ,
    expired_at               TIMESTAMPTZ,
    failed_at                TIMESTAMPTZ,
    created_at               TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at               TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT UQ_orders_order_no UNIQUE (order_no),
    CONSTRAINT CHK_orders_status CHECK (status IN
        ('PENDING', 'CONFIRMED', 'READY', 'COMPLETED', 'CANCELED', 'EXPIRED', 'FAILED', 'NO_SHOW')),
    CONSTRAINT CHK_orders_total_amount_non_negative CHECK (total_amount >= 0),
    CONSTRAINT CHK_orders_payment_attempt_count_range CHECK (payment_attempt_count BETWEEN 0 AND 3),
    CONSTRAINT CHK_orders_pickup_number_range CHECK (pickup_number IS NULL OR pickup_number BETWEEN 1 AND 999),
    CONSTRAINT CHK_orders_canceled_by CHECK (canceled_by IS NULL OR canceled_by IN ('CUSTOMER', 'ADMIN')),
    -- PENDING이면 만료 시각이 반드시 있다.
    CONSTRAINT CHK_orders_pending_has_hold_expiry CHECK (status <> 'PENDING' OR hold_expires_at IS NOT NULL),
    -- 확정되지 않은 주문에는 픽업 번호가 없다 (BR-026).
    CONSTRAINT CHK_orders_pickup_number_requires_confirmed CHECK (pickup_number IS NULL OR confirmed_at IS NOT NULL),
    -- 취소 주체·사유 필수 (BR-020).
    CONSTRAINT CHK_orders_canceled_requires_actor CHECK (status <> 'CANCELED' OR canceled_by IS NOT NULL),
    CONSTRAINT CHK_orders_admin_cancel_requires_reason CHECK (canceled_by <> 'ADMIN' OR cancel_reason IS NOT NULL)
);
-- 고객당 유효한 주문서 1건 강제 (FR-019, 03 A9)
CREATE UNIQUE INDEX UQ_orders_member_pending ON orders (member_id) WHERE status = 'PENDING';
-- 영업일 내 픽업 번호 유일 (BR-026)
CREATE UNIQUE INDEX UQ_orders_pickup_business_date_number
    ON orders (pickup_business_date, pickup_number) WHERE pickup_number IS NOT NULL;
CREATE INDEX IX_orders_member_created_at ON orders (member_id, created_at DESC);
-- 선점 만료 회수 배치 (BATCH-01, BR-008)
CREATE INDEX IX_orders_hold_expires_at_pending ON orders (hold_expires_at) WHERE status = 'PENDING';
-- 노쇼 자동 전환 배치 (BATCH-03, BR-021)
CREATE INDEX IX_orders_no_show_due_at ON orders (no_show_due_at) WHERE status IN ('CONFIRMED', 'READY');
CREATE INDEX IX_orders_pickup_slot_status ON orders (pickup_slot_id, status);
CREATE INDEX IX_orders_pickup_date_status_slot ON orders (pickup_business_date, status, pickup_slot_id);

CREATE TABLE order_items (
    id                     BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    order_id               BIGINT NOT NULL REFERENCES orders (id) ON DELETE RESTRICT,
    product_id             BIGINT NOT NULL REFERENCES products (id),
    product_name           VARCHAR(100) NOT NULL,
    sale_unit              VARCHAR(20) NOT NULL,
    quantity               SMALLINT NOT NULL,
    original_unit_price    INT NOT NULL,
    discount_rate          SMALLINT NOT NULL,
    unit_price             INT NOT NULL,
    line_amount            INT NOT NULL,
    product_closing_at     TIMESTAMPTZ NOT NULL,
    CONSTRAINT CHK_order_items_quantity_positive CHECK (quantity >= 1),
    CONSTRAINT CHK_order_items_original_price CHECK (original_unit_price >= 100),
    CONSTRAINT CHK_order_items_discount_rate CHECK (discount_rate IN (0, 30, 50, 70)),
    CONSTRAINT CHK_order_items_unit_price CHECK (unit_price >= 100),
    CONSTRAINT CHK_order_items_line_amount_non_negative CHECK (line_amount >= 0),
    CONSTRAINT UQ_order_items_order_product UNIQUE (order_id, product_id)
);
CREATE INDEX IX_order_items_order_id ON order_items (order_id);
CREATE INDEX IX_order_items_product_id ON order_items (product_id);

CREATE TABLE inventory_holds (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    order_id      BIGINT NOT NULL REFERENCES orders (id),
    product_id    BIGINT NOT NULL REFERENCES products (id),
    quantity      SMALLINT NOT NULL,
    status        VARCHAR(10) NOT NULL,
    expires_at    TIMESTAMPTZ NOT NULL,
    settled_at    TIMESTAMPTZ,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT CHK_inventory_holds_quantity_positive CHECK (quantity >= 1),
    CONSTRAINT CHK_inventory_holds_status CHECK (status IN ('HELD', 'CONSUMED', 'RELEASED', 'EXPIRED')),
    CONSTRAINT UQ_inventory_holds_order_product UNIQUE (order_id, product_id)
);
-- 만료 회수 배치와 지연 정리의 주 인덱스 (BR-008)
CREATE INDEX IX_inventory_holds_expires_at_held ON inventory_holds (expires_at) WHERE status = 'HELD';
CREATE INDEX IX_inventory_holds_product_status ON inventory_holds (product_id, status);

CREATE TABLE payment_attempts (
    id                   BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    order_id             BIGINT NOT NULL REFERENCES orders (id),
    attempt_no           SMALLINT NOT NULL,
    status               VARCHAR(10) NOT NULL,
    requested_amount     INT NOT NULL,
    idempotency_key      VARCHAR(64) NOT NULL,
    failure_reason       VARCHAR(30),
    requested_at         TIMESTAMPTZ NOT NULL,
    resolved_at          TIMESTAMPTZ,
    CONSTRAINT CHK_payment_attempts_attempt_no_range CHECK (attempt_no BETWEEN 1 AND 3),
    CONSTRAINT CHK_payment_attempts_status CHECK (status IN ('REQUESTED', 'SUCCEEDED', 'FAILED')),
    CONSTRAINT CHK_payment_attempts_requested_amount_non_negative CHECK (requested_amount >= 0),
    CONSTRAINT CHK_payment_attempts_failure_reason
        CHECK (failure_reason IS NULL OR failure_reason IN ('DECLINED', 'TIMEOUT', 'SYSTEM_ERROR')),
    CONSTRAINT UQ_payment_attempts_order_attempt_no UNIQUE (order_id, attempt_no),
    CONSTRAINT UQ_payment_attempts_idempotency_key UNIQUE (idempotency_key)
);
-- 주문당 성공 기록 1건 (05 §4.3-3)
CREATE UNIQUE INDEX UQ_payment_attempts_order_succeeded ON payment_attempts (order_id) WHERE status = 'SUCCEEDED';
CREATE INDEX IX_payment_attempts_order_attempt ON payment_attempts (order_id, attempt_no);

CREATE TABLE order_status_histories (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    order_id       BIGINT NOT NULL REFERENCES orders (id),
    from_status    VARCHAR(12),
    to_status      VARCHAR(12) NOT NULL,
    actor_type     VARCHAR(10) NOT NULL,
    actor_id       BIGINT REFERENCES members (id),
    reason         VARCHAR(200),
    occurred_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT CHK_order_status_histories_from_status CHECK (from_status IS NULL OR from_status IN
        ('PENDING', 'CONFIRMED', 'READY', 'COMPLETED', 'CANCELED', 'EXPIRED', 'FAILED', 'NO_SHOW')),
    CONSTRAINT CHK_order_status_histories_to_status CHECK (to_status IN
        ('PENDING', 'CONFIRMED', 'READY', 'COMPLETED', 'CANCELED', 'EXPIRED', 'FAILED', 'NO_SHOW')),
    CONSTRAINT CHK_order_status_histories_actor_type CHECK (actor_type IN ('CUSTOMER', 'ADMIN', 'SYSTEM')),
    -- 같은 상태로 두 번 전이하지 않는다. 배치 재실행·중복 요청 시 이력이 중복 생성되지 않는다 (FR-053).
    CONSTRAINT UQ_order_status_histories_order_to_status UNIQUE (order_id, to_status)
);
CREATE INDEX IX_order_status_histories_order_occurred ON order_status_histories (order_id, occurred_at);
-- 추가 전용 이력 테이블. 애플리케이션 DB 계정에는 이 테이블의 UPDATE·DELETE 권한을 부여하지 않는다 (10번 §6.5).

CREATE TABLE stock_ledgers (
    id                 BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    product_id         BIGINT NOT NULL REFERENCES products (id),
    order_id           BIGINT REFERENCES orders (id),
    reason             VARCHAR(20) NOT NULL,
    delta_total        INT NOT NULL DEFAULT 0,
    delta_held         INT NOT NULL DEFAULT 0,
    delta_confirmed    INT NOT NULL DEFAULT 0,
    delta_discarded    INT NOT NULL DEFAULT 0,
    after_total        INT NOT NULL,
    after_available    INT NOT NULL,
    after_held         INT NOT NULL,
    after_confirmed    INT NOT NULL,
    actor_type         VARCHAR(10) NOT NULL,
    actor_id           BIGINT REFERENCES members (id),
    note               VARCHAR(200),
    occurred_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT CHK_stock_ledgers_reason CHECK (reason IN
        ('ADMIN_ADJUST', 'HOLD', 'HOLD_RELEASE', 'HOLD_EXPIRE', 'CONFIRM', 'CANCEL_RESTORE', 'CANCEL_DISCARD')),
    CONSTRAINT CHK_stock_ledgers_after_total_non_negative CHECK (after_total >= 0),
    CONSTRAINT CHK_stock_ledgers_after_available_non_negative CHECK (after_available >= 0),
    CONSTRAINT CHK_stock_ledgers_after_held_non_negative CHECK (after_held >= 0),
    CONSTRAINT CHK_stock_ledgers_after_confirmed_non_negative CHECK (after_confirmed >= 0),
    CONSTRAINT CHK_stock_ledgers_actor_type CHECK (actor_type IN ('CUSTOMER', 'ADMIN', 'SYSTEM'))
);
-- 멱등성 보증 지점 (13번 §5). 같은 주문·품목에 대해 종결 처리가 두 번 기록될 수 없다.
CREATE UNIQUE INDEX UQ_stock_ledgers_order_product_reason
    ON stock_ledgers (order_id, product_id, reason) WHERE order_id IS NOT NULL;
CREATE INDEX IX_stock_ledgers_product_occurred ON stock_ledgers (product_id, occurred_at DESC);
CREATE INDEX IX_stock_ledgers_occurred ON stock_ledgers (occurred_at DESC);
-- 추가 전용 원장. 애플리케이션 DB 계정에는 이 테이블의 UPDATE·DELETE 권한을 부여하지 않는다 (10번 §4.3).

CREATE TABLE product_change_logs (
    id               BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    product_id       BIGINT NOT NULL REFERENCES products (id),
    changed_field    VARCHAR(30) NOT NULL,
    before_value     VARCHAR(255),
    after_value      VARCHAR(255),
    actor_id         BIGINT NOT NULL REFERENCES members (id),
    changed_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT CHK_product_change_logs_field CHECK (changed_field IN
        ('name', 'description', 'sale_unit', 'original_price', 'closing_at', 'max_order_quantity', 'status'))
);
CREATE INDEX IX_product_change_logs_product_changed_at ON product_change_logs (product_id, changed_at DESC);

CREATE TABLE member_restrictions (
    id                          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    member_id                   BIGINT NOT NULL REFERENCES members (id),
    reason                      VARCHAR(30) NOT NULL,
    trigger_order_id            BIGINT NOT NULL REFERENCES orders (id),
    triggered_no_show_count     SMALLINT NOT NULL,
    started_at                  TIMESTAMPTZ NOT NULL,
    ends_at                     TIMESTAMPTZ NOT NULL,
    CONSTRAINT CHK_member_restrictions_reason CHECK (reason IN ('NO_SHOW_ACCUMULATION')),
    CONSTRAINT CHK_member_restrictions_triggered_count CHECK (triggered_no_show_count = 3),
    CONSTRAINT CHK_member_restrictions_ends_after_started CHECK (ends_at > started_at),
    -- 같은 노쇼 사건으로 제한이 두 번 생성되지 않는다.
    CONSTRAINT UQ_member_restrictions_member_trigger UNIQUE (member_id, trigger_order_id)
);
CREATE INDEX IX_member_restrictions_member_ends ON member_restrictions (member_id, ends_at DESC);
