/**
 * Ticketo 빌드 설정 파일
 *
 * - Spring Boot Gradle 플러그인: 실행 가능한 jar(bootJar) 생성, bootRun 태스크 제공
 * - Dependency Management 플러그인: Spring Boot BOM 기준으로 라이브러리 버전을 일괄 관리
 *   (그래서 아래 의존성에는 버전을 직접 적지 않는다)
 */
plugins {
    java
    id("org.springframework.boot") version "3.5.6"
    id("io.spring.dependency-management") version "1.1.7"
}

group = "com.ticketo"
version = "0.0.1-SNAPSHOT"

java {
    toolchain {
        // 로컬에 설치된 JDK와 무관하게 Java 21로 컴파일하도록 고정
        languageVersion = JavaLanguageVersion.of(21)
    }
}

configurations {
    // Lombok 같은 어노테이션 프로세서를 compileOnly 범위에서도 사용할 수 있도록 연결
    compileOnly {
        extendsFrom(configurations.annotationProcessor.get())
    }
}

repositories {
    mavenCentral()
}

dependencies {
    // Web: 내장 Tomcat + Spring MVC (REST API 작성)
    implementation("org.springframework.boot:spring-boot-starter-web")
    // Data JPA: Hibernate 기반 ORM + Spring Data Repository
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    // MyBatis: mapper 인터페이스 + XML 기반 SQL 매핑
    implementation("org.mybatis.spring.boot:mybatis-spring-boot-starter:3.0.5")
    // Validation: @Valid, @NotBlank 등 Bean Validation(Hibernate Validator)
    implementation("org.springframework.boot:spring-boot-starter-validation")

    // 비밀번호 BCrypt 해시용 (Spring Security 전체가 아닌 crypto 모듈만 사용)
    implementation("org.springframework.security:spring-security-crypto")

    // Lombok: 컴파일 시점에 Getter/생성자 등 보일러플레이트 코드 생성
    compileOnly("org.projectlombok:lombok")
    annotationProcessor("org.projectlombok:lombok")

    // MySQL JDBC 드라이버: 런타임에만 필요하므로 runtimeOnly
    runtimeOnly("com.mysql:mysql-connector-j")

    // 테스트: JUnit 5, AssertJ, Mockito, Spring Test 포함
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testCompileOnly("org.projectlombok:lombok")
    testAnnotationProcessor("org.projectlombok:lombok")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<Test> {
    useJUnitPlatform()
}

tasks.withType<JavaCompile> {
    // 한글 주석/문자열 깨짐 방지 (Windows 기본 인코딩 MS949 대응)
    options.encoding = "UTF-8"
    // 생성자/메서드 파라미터 이름을 바이트코드에 보존 (@PathVariable, @RequestParam 이름 생략 시 필요)
    options.compilerArgs.add("-parameters")
}
