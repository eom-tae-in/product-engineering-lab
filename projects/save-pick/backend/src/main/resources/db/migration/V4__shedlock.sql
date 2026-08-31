-- ShedLock(net.javacrumbs.shedlock) 분산 락 테이블. 6개 배치 작업이 공유한다
-- (docs/14-project-structure.md §1.1, §6). 스키마는 shedlock-provider-jdbc-template의
-- 표준 정의를 그대로 따른다.
CREATE TABLE shedlock (
    name        VARCHAR(64) NOT NULL,
    lock_until  TIMESTAMP(3) NOT NULL,
    locked_at   TIMESTAMP(3) NOT NULL,
    locked_by   VARCHAR(255) NOT NULL,
    PRIMARY KEY (name)
);
