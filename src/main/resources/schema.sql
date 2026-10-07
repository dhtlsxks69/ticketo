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
