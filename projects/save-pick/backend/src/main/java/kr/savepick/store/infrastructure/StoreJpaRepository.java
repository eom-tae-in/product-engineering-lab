package kr.savepick.store.infrastructure;

import kr.savepick.store.domain.Store;
import kr.savepick.store.domain.StoreRepository;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StoreJpaRepository extends JpaRepository<Store, Short>, StoreRepository {
}
