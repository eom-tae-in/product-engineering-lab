package kr.savepick.common.audit;

/**
 * 이력·원장에 남기는 행위 주체 구분 (12-auth.md §4.6, 05번 상태 이력).
 */
public enum ActorType {
    CUSTOMER,
    ADMIN,
    SYSTEM
}
