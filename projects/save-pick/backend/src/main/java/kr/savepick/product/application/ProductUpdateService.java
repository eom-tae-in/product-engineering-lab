package kr.savepick.product.application;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import kr.savepick.common.error.BusinessException;
import kr.savepick.common.error.ErrorCode;
import kr.savepick.common.time.ServerClock;
import kr.savepick.product.domain.ClosingTimePolicy;
import kr.savepick.product.domain.Product;
import kr.savepick.product.domain.ProductChangeLog;
import kr.savepick.product.domain.ProductRepository;
import kr.savepick.product.domain.ProductStatus;
import kr.savepick.product.infrastructure.ProductChangeLogJpaRepository;
import kr.savepick.store.application.StoreQueryService;
import kr.savepick.store.domain.Store;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * API-105 상품 수정 (BR-003, BR-005, FR-041). 이미 확정된 주문의 스냅샷은 건드리지 않는다 —
 * order 도메인이 아직 없어(이번 슬라이스 범위 밖) 마감 단축의 영향 확정 주문 수는 항상 0으로
 * 고정한다. store 슬라이스의 StoreSettingService#updateSettings와 같은 임시 예외 패턴이며,
 * order 도메인이 생기면 실제 영향 건수를 계산하도록 교체해야 한다.
 */
@Service
public class ProductUpdateService {

    private final ProductRepository productRepository;
    private final ProductChangeLogJpaRepository productChangeLogJpaRepository;
    private final StoreQueryService storeQueryService;
    private final ServerClock serverClock;

    public ProductUpdateService(
            ProductRepository productRepository,
            ProductChangeLogJpaRepository productChangeLogJpaRepository,
            StoreQueryService storeQueryService,
            ServerClock serverClock) {
        this.productRepository = productRepository;
        this.productChangeLogJpaRepository = productChangeLogJpaRepository;
        this.storeQueryService = storeQueryService;
        this.serverClock = serverClock;
    }

    @Transactional
    public UpdateResult update(
            Long productId, String name, String description, String saleUnit,
            Integer originalPrice, LocalDateTime closingAt, Short maxOrderQuantity,
            boolean confirmEarlierClosing, Long actorId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        LocalDateTime now = serverClock.now();

        List<String> changedFields = new ArrayList<>();

        if (name != null && !name.equals(product.getName())) {
            logChange(productId, "name", product.getName(), name, actorId, now);
            product.updateName(name, now);
            changedFields.add("name");
        }
        if (description != null && !description.equals(product.getDescription())) {
            logChange(productId, "description", product.getDescription(), description, actorId, now);
            product.updateDescription(description, now);
            changedFields.add("description");
        }
        if (saleUnit != null && !saleUnit.equals(product.getSaleUnit())) {
            logChange(productId, "sale_unit", product.getSaleUnit(), saleUnit, actorId, now);
            product.updateSaleUnit(saleUnit, now);
            changedFields.add("saleUnit");
        }
        if (originalPrice != null && originalPrice != product.getOriginalPrice()) {
            logChange(productId, "original_price", String.valueOf(product.getOriginalPrice()), String.valueOf(originalPrice), actorId, now);
            product.updateOriginalPrice(originalPrice, now);
            changedFields.add("originalPrice");
        }
        if (closingAt != null && !closingAt.equals(product.getClosingAt())) {
            if (!product.isClosingAtEditable()) {
                throw new BusinessException(ErrorCode.PRODUCT_STATUS_TRANSITION_DENIED, "마감된 상품의 마감 시각은 수정할 수 없습니다.");
            }
            Store store = storeQueryService.getStore();
            if (!ClosingTimePolicy.isValid(closingAt, now, store.getCloseTime())) {
                throw new BusinessException(ErrorCode.CLOSING_TIME_INVALID);
            }

            // order 도메인이 없어 실제 영향받는 확정 주문 수를 계산할 수 없다 — 항상 0으로 고정한다.
            int affectedConfirmedOrderCount = 0;
            boolean shortened = closingAt.isBefore(product.getClosingAt());
            if (shortened && affectedConfirmedOrderCount > 0 && !confirmEarlierClosing) {
                throw new BusinessException(
                        ErrorCode.VALIDATION_ERROR,
                        "마감 시각을 앞당기면 이미 확정된 주문에 영향을 줍니다. confirmEarlierClosing을 true로 보내세요.",
                        java.util.Map.of("affectedConfirmedOrderCount", affectedConfirmedOrderCount));
            }

            logChange(productId, "closing_at", product.getClosingAt().toString(), closingAt.toString(), actorId, now);
            product.updateClosingAt(closingAt, now);
            changedFields.add("closingAt");
        }
        if (maxOrderQuantity != null && maxOrderQuantity != product.getMaxOrderQuantity()) {
            logChange(productId, "max_order_quantity", String.valueOf(product.getMaxOrderQuantity()), String.valueOf(maxOrderQuantity), actorId, now);
            product.updateMaxOrderQuantity(maxOrderQuantity, now);
            changedFields.add("maxOrderQuantity");
        }

        productRepository.save(product);
        return new UpdateResult(product, changedFields, 0);
    }

    private void logChange(Long productId, String field, String before, String after, Long actorId, LocalDateTime now) {
        productChangeLogJpaRepository.save(ProductChangeLog.of(productId, field, before, after, actorId, now));
    }

    public record UpdateResult(Product product, List<String> changedFields, int affectedConfirmedOrderCount) {
    }
}
