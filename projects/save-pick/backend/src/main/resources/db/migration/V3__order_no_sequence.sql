-- 주문 번호(orders.order_no, BR-026) 발급용 전역 시퀀스.
-- 형식은 ORD-YYYYMMDD-NNNNNN이며(docs/00-status.md G-6), NNNNNN은 이 시퀀스 값을 6자리로
-- 0-패딩한 값이다. 날짜별로 초기화하지 않고 서비스 전체에서 단조 증가한다 — order_no의
-- 전역 유일성(BR-026 "서비스 전체에서 유일하며 재사용하지 않는다")을 시퀀스 하나로 보장하기
-- 위함이다(주문서 생성 시점에는 아직 orders.id를 알 수 없어 id 기반 채번을 쓸 수 없다).
CREATE SEQUENCE order_no_seq START WITH 1 INCREMENT BY 1;
