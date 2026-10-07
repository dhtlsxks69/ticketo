package com.ticketo.com;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * 모든 엔티티가 공통으로 가지는 생성/수정 시간을 관리하는 부모 클래스.
 *
 * <p>동작 흐름:</p>
 * <ol>
 *     <li>엔티티가 이 클래스를 상속한다. ({@code public class Ticket extends BaseTimeEntity})</li>
 *     <li>{@link MappedSuperclass} 덕분에 부모의 필드가 자식 테이블의 컬럼으로 매핑된다. (이 클래스 자체는 테이블이 아님)</li>
 *     <li>persist/update 시점에 {@link AuditingEntityListener}가 호출되어 시간 값을 자동으로 채운다.</li>
 * </ol>
 *
 * <p>Auditing 기능은 {@link com.ticketo.config.JpaAuditingConfig}에서 활성화한다.</p>
 */
@Getter
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public abstract class BaseTimeEntity {

    /** 최초 저장 시각. 이후 수정되지 않도록 {@code updatable = false}로 지정한다. */
    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /** 마지막 수정 시각. 엔티티가 변경(Dirty Checking)될 때마다 갱신된다. */
    @LastModifiedDate
    @Column(nullable = false)
    private LocalDateTime updatedAt;
}
