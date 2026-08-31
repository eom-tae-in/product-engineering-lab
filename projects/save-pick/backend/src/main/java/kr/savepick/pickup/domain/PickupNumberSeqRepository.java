package kr.savepick.pickup.domain;

import java.time.LocalDate;
import java.util.Optional;

public interface PickupNumberSeqRepository {

    Optional<PickupNumberSeq> findByBusinessDate(LocalDate businessDate);

    /**
     * BR-026, 10-erd.md §3.4 — 영업일 내 순번을 원자적으로 발급한다. 행이 없으면 1로 새로 만들고,
     * 있으면 999 미만일 때만 1 증가시킨다. 999에 도달했으면(소진) 빈 값을 돌려준다 — 예외로
     * 바꾸는 것은 application 계층({@code PickupNumberIssuer})의 몫이다.
     */
    Optional<Short> incrementAndGet(LocalDate businessDate);
}
