plugins {
    java
    id("org.springframework.boot") version "3.3.5"
    id("io.spring.dependency-management") version "1.1.6"
}

group = "kr.savepick"
version = "0.0.1-SNAPSHOT"
description = "savePick API — docs/14-project-structure.md 기준 스캐폴딩"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

configurations {
    compileOnly {
        extendsFrom(configurations.annotationProcessor.get())
    }
}

repositories {
    mavenCentral()
}

extra["testcontainersVersion"] = "1.21.4"

dependencies {
    // web / api
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-validation")

    // persistence
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.flywaydb:flyway-core")
    implementation("org.flywaydb:flyway-database-postgresql")
    runtimeOnly("org.postgresql:postgresql")

    // security / auth (12-auth.md — 직접 구현한 JWT 필터)
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("io.jsonwebtoken:jjwt-api:0.12.6")
    runtimeOnly("io.jsonwebtoken:jjwt-impl:0.12.6")
    runtimeOnly("io.jsonwebtoken:jjwt-jackson:0.12.6")

    // batch (14-project-structure.md §1.1 — Spring @Scheduled + ShedLock)
    implementation("net.javacrumbs.shedlock:shedlock-spring:5.16.0")
    implementation("net.javacrumbs.shedlock:shedlock-provider-jdbc-template:5.16.0")

    // ops
    implementation("org.springframework.boot:spring-boot-starter-actuator")

    // test
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.security:spring-security-test")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.testcontainers:junit-jupiter")
    testImplementation("org.testcontainers:postgresql")
    // TestRestTemplate이 PATCH를 보내려면 JDK 기본 HttpURLConnection이 아닌 클라이언트가 필요하다
    // (HttpURLConnection은 PATCH를 지원하지 않고, POST/PUT 스트리밍 바디 + 401 응답 조합에서
    // HttpRetryException을 던진다). Spring Boot가 클래스패스에서 자동 감지해 사용한다.
    testImplementation("org.apache.httpcomponents.client5:httpclient5")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

dependencyManagement {
    imports {
        mavenBom("org.testcontainers:testcontainers-bom:${property("testcontainersVersion")}")
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
}
