package kr.savepick.pickup.api;

import jakarta.validation.constraints.Min;

/** API-119. 필드를 생략하면 그 항목은 바꾸지 않는다. */
public record UpdatePickupSlotRequest(@Min(1) Short capacity, Boolean blocked) {
}
