package kr.savepick.product.domain;

/**
 * 05-state-rules.md §5.1 상품 상태값. 문자 그대로 일치해야 한다 (14-project-structure.md §7.1).
 */
public enum ProductStatus {
    DRAFT,
    ON_SALE,
    HIDDEN,
    CLOSED
}
