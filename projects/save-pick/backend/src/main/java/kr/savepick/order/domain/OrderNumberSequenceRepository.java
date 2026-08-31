package kr.savepick.order.domain;

/** BR-026 — 주문 번호 채번용 전역 시퀀스({@code order_no_seq}, V3 마이그레이션)의 다음 값. */
public interface OrderNumberSequenceRepository {

    long nextValue();
}
