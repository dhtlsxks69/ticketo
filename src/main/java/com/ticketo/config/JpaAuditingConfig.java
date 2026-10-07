package com.ticketo.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * JPA Auditing 활성화 설정.
 *
 * <p>Auditing을 켜면 엔티티가 저장/수정될 때 {@code @CreatedDate}, {@code @LastModifiedDate}가 붙은
 * 필드에 시간이 자동으로 채워진다. 실제 시간 필드는 {@link com.ticketo.com.BaseTimeEntity}에 정의되어 있다.</p>
 *
 * <p>{@code @EnableJpaAuditing}을 메인 클래스가 아닌 별도 설정 클래스에 두는 이유:
 * {@code @WebMvcTest} 같은 슬라이스 테스트는 JPA 빈을 로드하지 않는데,
 * 메인 클래스에 두면 해당 테스트에서 "JPA metamodel must not be empty" 오류가 발생하기 때문이다.</p>
 */
@Configuration
@EnableJpaAuditing
public class JpaAuditingConfig {
}
