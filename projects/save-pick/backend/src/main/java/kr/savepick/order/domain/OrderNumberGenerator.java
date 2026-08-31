package kr.savepick.order.domain;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * BR-026 — 주문 번호 형식 {@code ORD-YYYYMMDD-NNNNNN} (docs/00-status.md G-6 기술 확정값).
 * 순수 포매팅만 담당한다. 실제 채번(원자적 증가)은
 * {@code order/domain/OrderNumberSequenceRepository}가 맡는다(14-project-structure.md §9.1).
 */
public final class OrderNumberGenerator {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.BASIC_ISO_DATE;

    private OrderNumberGenerator() {
    }

    public static String generate(LocalDate date, long sequenceValue) {
        return "ORD-" + date.format(DATE_FORMAT) + "-" + String.format("%06d", sequenceValue);
    }
}
