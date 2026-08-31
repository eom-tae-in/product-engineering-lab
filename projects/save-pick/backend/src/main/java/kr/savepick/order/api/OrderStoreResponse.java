package kr.savepick.order.api;

import kr.savepick.store.domain.Store;

/** API-024. */
public record OrderStoreResponse(String name, String address, String phone) {

    public static OrderStoreResponse from(Store store) {
        if (store == null) {
            return null;
        }
        return new OrderStoreResponse(store.getName(), store.getAddress(), store.getPhone());
    }
}
