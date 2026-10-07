package com.ticketo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Ticketo 애플리케이션의 진입점(Entry Point).
 *
 * <p>{@link SpringBootApplication}은 아래 세 가지를 합친 어노테이션이다.</p>
 * <ul>
 *     <li>{@code @SpringBootConfiguration} : 이 클래스를 설정 클래스로 등록</li>
 *     <li>{@code @EnableAutoConfiguration} : 클래스패스의 의존성(Web, JPA, MySQL 등)을 보고 필요한 빈을 자동 구성</li>
 *     <li>{@code @ComponentScan} : 현재 패키지({@code com.ticketo})와 하위 패키지의 컴포넌트를 스캔</li>
 * </ul>
 *
 * <p>따라서 모든 클래스는 반드시 {@code com.ticketo} 하위 패키지에 위치해야 빈으로 등록된다.</p>
 */
@SpringBootApplication
public class TicketoApplication {

    /**
     * 애플리케이션을 실행한다.
     *
     * <p>동작 흐름: ApplicationContext 생성 → 자동 구성 및 빈 등록 →
     * DataSource/JPA 초기화 → 내장 Tomcat 기동(기본 8080 포트)</p>
     *
     * @param args 실행 시 전달되는 커맨드라인 인자 (예: {@code --spring.profiles.active=local})
     */
    public static void main(String[] args) {
        SpringApplication.run(TicketoApplication.class, args);
    }
}
