package kr.savepick.cart.api;

import jakarta.validation.Valid;
import java.time.ZoneId;
import java.util.UUID;
import kr.savepick.account.infrastructure.AuthenticatedPrincipal;
import kr.savepick.cart.application.CartOwner;
import kr.savepick.cart.application.CartService;
import kr.savepick.cart.application.CartValidationService;
import kr.savepick.common.error.BusinessException;
import kr.savepick.common.error.ErrorCode;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * API-012~016 장바구니 (11-api-spec.md §3). 비로그인은 {@code X-Guest-Token} 헤더로,
 * 로그인 고객은 인증 토큰으로 소유권을 식별한다(12-auth.md §3.3) — SecurityConfig에서
 * permitAll로 열어두고 이 컨트롤러가 두 경로를 함께 받는다.
 */
@RestController
@RequestMapping("/api/cart")
public class CartController {

    private static final String GUEST_TOKEN_HEADER = "X-Guest-Token";
    private static final ZoneId ZONE = ZoneId.of("Asia/Seoul");

    private final CartService cartService;
    private final CartValidationService cartValidationService;

    public CartController(CartService cartService, CartValidationService cartValidationService) {
        this.cartService = cartService;
        this.cartValidationService = cartValidationService;
    }

    /** API-012. FR-017, FR-018. */
    @GetMapping
    public CartResponse getCart(
            @AuthenticationPrincipal AuthenticatedPrincipal principal,
            @RequestHeader(value = GUEST_TOKEN_HEADER, required = false) String guestTokenHeader) {
        CartOwner owner = resolveOwner(principal, guestTokenHeader);
        return CartResponse.from(cartValidationService.getCart(owner), ZONE);
    }

    /** API-013. FR-016, FR-034. 게스트 토큰 헤더가 없으면 서버가 새로 발급해 응답에 담는다. */
    @PostMapping("/items")
    public ResponseEntity<AddCartItemResponse> addItem(
            @AuthenticationPrincipal AuthenticatedPrincipal principal,
            @RequestHeader(value = GUEST_TOKEN_HEADER, required = false) String guestTokenHeader,
            @Valid @RequestBody AddCartItemRequest request) {
        boolean issuedNewGuestToken = principal == null && isBlank(guestTokenHeader);
        CartOwner owner = issuedNewGuestToken
                ? CartOwner.ofGuest(UUID.randomUUID())
                : resolveOwner(principal, guestTokenHeader);

        CartService.AddItemResult result = cartService.addItem(owner, request.productId(), request.quantity());
        AddCartItemResponse body = new AddCartItemResponse(
                result.cartItemId(), result.productId(), result.quantity(), result.currentPrice(), result.cartItemCount(),
                issuedNewGuestToken ? owner.guestToken() : null);
        return ResponseEntity.status(HttpStatus.CREATED).body(body);
    }

    /** API-014. FR-017. {@code quantity = 0}이면 204로 삭제한다. */
    @PatchMapping("/items/{cartItemId}")
    public ResponseEntity<ChangeCartItemQuantityResponse> changeQuantity(
            @AuthenticationPrincipal AuthenticatedPrincipal principal,
            @RequestHeader(value = GUEST_TOKEN_HEADER, required = false) String guestTokenHeader,
            @PathVariable Long cartItemId,
            @Valid @RequestBody ChangeCartItemQuantityRequest request) {
        CartOwner owner = resolveOwner(principal, guestTokenHeader);
        CartService.ChangeQuantityResult result = cartService.changeQuantity(owner, cartItemId, request.quantity());
        if (result.deleted()) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.ok(
                new ChangeCartItemQuantityResponse(result.cartItemId(), result.quantity(), result.lineAmount(), result.totalAmount()));
    }

    /** API-015. FR-017. */
    @DeleteMapping("/items/{cartItemId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeItem(
            @AuthenticationPrincipal AuthenticatedPrincipal principal,
            @RequestHeader(value = GUEST_TOKEN_HEADER, required = false) String guestTokenHeader,
            @PathVariable Long cartItemId) {
        cartService.removeItem(resolveOwner(principal, guestTokenHeader), cartItemId);
    }

    /** API-016. FR-018. */
    @DeleteMapping("/items/unavailable")
    public RemoveUnavailableItemsResponse removeUnavailable(
            @AuthenticationPrincipal AuthenticatedPrincipal principal,
            @RequestHeader(value = GUEST_TOKEN_HEADER, required = false) String guestTokenHeader) {
        CartService.RemoveUnavailableResult result = cartService.removeUnavailableItems(resolveOwner(principal, guestTokenHeader));
        return new RemoveUnavailableItemsResponse(
                result.removedCartItemIds(), result.remainingItemCount(), result.totalAmount(), result.orderable());
    }

    private CartOwner resolveOwner(AuthenticatedPrincipal principal, String guestTokenHeader) {
        if (principal != null) {
            return CartOwner.ofMember(principal.memberId());
        }
        if (isBlank(guestTokenHeader)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "게스트 토큰 또는 인증 토큰이 필요합니다.");
        }
        try {
            return CartOwner.ofGuest(UUID.fromString(guestTokenHeader));
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "게스트 토큰 형식이 올바르지 않습니다.");
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
