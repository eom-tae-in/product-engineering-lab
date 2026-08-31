package kr.savepick.pickup.infrastructure;

import java.time.LocalDate;
import java.util.Optional;
import kr.savepick.pickup.domain.PickupNumberSeq;
import kr.savepick.pickup.domain.PickupNumberSeqRepository;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 10-erd.md §3.4, 13번 §7.1 — 락 순서 5번(마지막). 원자적 발급은 두 문장(조건부 UPDATE 시도 →
 * 실패 시 최초 삽입 시도)으로 수행한다. 두 시도 모두 실패하면(이미 999) 소진이다.
 * PostgreSQL의 {@code UPDATE ... RETURNING} 한 문장(10번 §3.4)과 동등하되, Spring Data JPA
 * 네이티브 쿼리의 RETURNING 반환 매핑 리스크를 피하기 위해 두 조건부 문장으로 나눴다.
 */
public interface PickupNumberSeqJpaRepository extends JpaRepository<PickupNumberSeq, LocalDate>, PickupNumberSeqRepository {

    @Override
    Optional<PickupNumberSeq> findByBusinessDate(LocalDate businessDate);

    @Modifying(clearAutomatically = true)
    @Query("update PickupNumberSeq p set p.lastNumber = p.lastNumber + 1 "
            + "where p.businessDate = :businessDate and p.lastNumber < 999")
    int incrementIfBelowMax(@Param("businessDate") LocalDate businessDate);

    @Modifying(clearAutomatically = true)
    @Query(value = "INSERT INTO pickup_number_seqs (business_date, last_number) VALUES (:businessDate, 1) "
            + "ON CONFLICT (business_date) DO NOTHING", nativeQuery = true)
    int insertInitial(@Param("businessDate") LocalDate businessDate);

    /**
     * 최대 2회 시도한다. 1회차: 증가 시도 → 실패 시 최초 삽입 시도. 동시에 두 요청이 같은 날짜를
     * 처음 발급하면 한쪽만 삽입에 성공하고, 나머지는 2회차에서 증가에 성공한다. 두 시도 모두
     * 실패했다면(행이 있고 999 도달) 소진이다.
     */
    @Override
    default Optional<Short> incrementAndGet(LocalDate businessDate) {
        for (int attempt = 0; attempt < 2; attempt++) {
            if (incrementIfBelowMax(businessDate) == 1) {
                return findByBusinessDate(businessDate).map(PickupNumberSeq::getLastNumber);
            }
            if (insertInitial(businessDate) == 1) {
                return Optional.of((short) 1);
            }
        }
        return Optional.empty();
    }
}
