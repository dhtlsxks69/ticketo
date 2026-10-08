-- MyBatis는 테이블을 자동 생성하지 않으므로 기동 시 schema.sql로 생성한다. (spring.sql.init.mode=always)
-- 이미 예전 구조(phone, updated_at 컬럼)로 만든 member 테이블이 있다면 DROP TABLE member; 후 재기동한다.
CREATE TABLE IF NOT EXISTS member (
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    email      VARCHAR(255) NOT NULL,
    password   VARCHAR(100) NOT NULL COMMENT 'BCrypt 해시',
    name       VARCHAR(100) NOT NULL,
    created_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_member_email (email)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE IF NOT EXISTS ticket (
    id                 BIGINT       NOT NULL AUTO_INCREMENT,
    title              VARCHAR(200) NOT NULL COMMENT '공연명',
    venue              VARCHAR(200) NOT NULL COMMENT '공연장',
    event_at           DATETIME     NOT NULL COMMENT '공연 일시',
    price              INT          NOT NULL COMMENT '티켓 1매 가격(원)',
    total_quantity     INT          NOT NULL COMMENT '총 발행 수량',
    remaining_quantity INT          NOT NULL COMMENT '잔여 수량',
    created_at         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_ticket_event_at (event_at),
    CONSTRAINT chk_ticket_quantity CHECK (remaining_quantity >= 0 AND remaining_quantity <= total_quantity)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE IF NOT EXISTS reservation (
    id          BIGINT      NOT NULL AUTO_INCREMENT,
    member_id   BIGINT      NOT NULL,
    ticket_id   BIGINT      NOT NULL,
    quantity    INT         NOT NULL COMMENT '예매 수량',
    status      VARCHAR(20) NOT NULL DEFAULT 'RESERVED' COMMENT 'RESERVED / CANCELED',
    created_at  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    canceled_at DATETIME    NULL,
    PRIMARY KEY (id),
    KEY idx_reservation_member (member_id),
    KEY idx_reservation_ticket (ticket_id),
    CONSTRAINT fk_reservation_member FOREIGN KEY (member_id) REFERENCES member (id),
    CONSTRAINT fk_reservation_ticket FOREIGN KEY (ticket_id) REFERENCES ticket (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
