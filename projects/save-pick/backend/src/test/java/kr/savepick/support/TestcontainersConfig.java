package kr.savepick.support;

import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * 14-project-structure.md §1.1 — 제약·락 테스트는 실제 PostgreSQL(Testcontainers)에서 돌린다.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfig {

    @Bean
    @ServiceConnection
    PostgreSQLContainer<?> postgresContainer() {
        return new PostgreSQLContainer<>("postgres:15");
    }
}
