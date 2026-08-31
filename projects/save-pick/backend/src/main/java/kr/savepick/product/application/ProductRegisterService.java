package kr.savepick.product.application;

import java.time.LocalDateTime;
import kr.savepick.common.error.BusinessException;
import kr.savepick.common.error.ErrorCode;
import kr.savepick.common.time.ServerClock;
import kr.savepick.product.domain.ClosingTimePolicy;
import kr.savepick.product.domain.Product;
import kr.savepick.product.domain.ProductRepository;
import kr.savepick.store.application.StoreQueryService;
import kr.savepick.store.domain.Store;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * API-103 상품 등록 (BR-003, FR-040). 매장 영업 종료 시각은 StoreQueryService로 얻는다
 * (14-project-structure.md §4.1 product ──► store 읽기) — 하드코딩하지 않는다.
 */
@Service
public class ProductRegisterService {

    private final ProductRepository productRepository;
    private final StoreQueryService storeQueryService;
    private final ServerClock serverClock;

    public ProductRegisterService(ProductRepository productRepository, StoreQueryService storeQueryService, ServerClock serverClock) {
        this.productRepository = productRepository;
        this.storeQueryService = storeQueryService;
        this.serverClock = serverClock;
    }

    @Transactional
    public Product register(
            String name, String description, String saleUnit, int originalPrice,
            LocalDateTime closingAt, short maxOrderQuantity, Long actorId) {
        LocalDateTime now = serverClock.now();
        Store store = storeQueryService.getStore();
        if (!ClosingTimePolicy.isValid(closingAt, now, store.getCloseTime())) {
            throw new BusinessException(ErrorCode.CLOSING_TIME_INVALID);
        }

        Product product = Product.register(store.getId(), name, description, saleUnit, originalPrice, closingAt, maxOrderQuantity, now);
        return productRepository.save(product);
    }
}
