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
import kr.savepick.product.infrastructure.ConfirmedOrderReadDao;
import kr.savepick.product.infrastructure.ProductChangeLogJpaRepository;
import kr.savepick.store.application.StoreQueryService;
import kr.savepick.store.domain.Store;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * API-105 상품 수정 (BR-003, BR-005, FR-041). 이미 확정된 주문의 스냅샷은 건드리지 않는다
 * ({@code order_items}를 수정하지 않는다).
 *
 * <p>마감 시각을 앞당길 때는 그보다 픽업이 늦은 확정 주문 수를 {@link ConfirmedOrderReadDao}로
 * 세어, {@code confirmEarlierClosing} 동의 없이는 실행하지 않는다(FR-041 예외).
 */
@Service
public class ProductUpdateService {

    private final ProductRepository productRepository;
    private final ProductChangeLogJpaRepository productChangeLogJpaRepository;
    private final StoreQueryService storeQueryService;
    private final ConfirmedOrderReadDao confirmedOrderReadDao;
    private final ServerClock serverClock;

    public ProductUpdateService(
            ProductRepository productRepository,
            ProductChangeLogJpaRepository productChangeLogJpaRepository,
            StoreQueryService storeQueryService,
            ConfirmedOrderReadDao confirmedOrderReadDao,
            ServerClock serverClock) {
        this.productRepository = productRepository;
        this.productChangeLogJpaRepository = productChangeLogJpaRepository;
        this.storeQueryService = storeQueryService;
        this.confirmedOrderReadDao = confirmedOrderReadDao;
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
        // 마감 시각을 바꾸지 않으면 영향받는 확정 주문도 없다(11번 API-105 응답 필드).
        int affectedConfirmedOrderCount = 0;

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

            // 11번 API-105 — 마감을 앞당겨 이미 확정된 주문의 픽업 시간대보다 빨라지는 경우를 센다.
            boolean shortened = closingAt.isBefore(product.getClosingAt());
            affectedConfirmedOrderCount = shortened
                    ? confirmedOrderReadDao.countConfirmedOrdersPickedUpAfter(productId, closingAt)
                    : 0;
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
        return new UpdateResult(product, changedFields, affectedConfirmedOrderCount);
    }

    private void logChange(Long productId, String field, String before, String after, Long actorId, LocalDateTime now) {
        productChangeLogJpaRepository.save(ProductChangeLog.of(productId, field, before, after, actorId, now));
    }

    public record UpdateResult(Product product, List<String> changedFields, int affectedConfirmedOrderCount) {
    }
}
