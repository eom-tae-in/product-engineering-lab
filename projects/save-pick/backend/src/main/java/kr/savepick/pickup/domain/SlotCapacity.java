package kr.savepick.pickup.domain;

/**
 * BR-016 — 픽업 시간대 정원의 값 객체. {@code reservedCount <= capacity}를 제약으로 걸지 않는다
 * (관리자가 정원을 줄여 기존 예약이 넘는 상태가 정상이다, 13번 §8).
 */
public record SlotCapacity(short capacity, short reservedCount) {

    public static SlotCapacity of(PickupSlot slot) {
        return new SlotCapacity(slot.getCapacity(), slot.getReservedCount());
    }

    public boolean hasRoom() {
        return reservedCount < capacity;
    }

    public boolean isFull() {
        return !hasRoom();
    }

    public boolean isOverCapacity() {
        return reservedCount > capacity;
    }
}
