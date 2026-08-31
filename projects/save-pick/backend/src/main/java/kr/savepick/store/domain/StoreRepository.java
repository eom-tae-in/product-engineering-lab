package kr.savepick.store.domain;

import java.util.Optional;

public interface StoreRepository {
    Store save(Store store);

    Optional<Store> findById(Short id);
}
