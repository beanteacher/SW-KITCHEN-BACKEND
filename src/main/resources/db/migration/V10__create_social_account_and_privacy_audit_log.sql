CREATE TABLE social_account (
  id                BIGINT AUTO_INCREMENT PRIMARY KEY,
  account_id        BIGINT      NOT NULL,
  provider          VARCHAR(10) NOT NULL,
  provider_user_id  VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,   -- 카카오·네이버가 주는 회원 번호
  created_at        DATETIME(6) NOT NULL,
  CONSTRAINT uk_social_account_provider_user UNIQUE (provider, provider_user_id),
  CONSTRAINT uk_social_account_account UNIQUE (account_id, provider),               -- 한 계정에 카카오·네이버 각 1개
  CONSTRAINT ck_social_account_provider CHECK (provider IN ('KAKAO', 'NAVER')),
  CONSTRAINT fk_social_account_account FOREIGN KEY (account_id) REFERENCES account (id)
);

-- 직원이 고객 개인정보를 본 기록. 계정이 지워져도 남아야 해서 FK 없이 계정 번호와 아이디를 복사해 둔다
CREATE TABLE privacy_audit_log (
  id           BIGINT AUTO_INCREMENT PRIMARY KEY,
  account_id   BIGINT       NOT NULL,
  user_id      VARCHAR(20)  NOT NULL,
  ip           VARCHAR(45)  NOT NULL,
  action       VARCHAR(10)  NOT NULL,
  method       VARCHAR(10)  NOT NULL,
  path         VARCHAR(200) NOT NULL,
  query        VARCHAR(500) NULL,
  status       SMALLINT     NOT NULL,
  created_at   DATETIME(6)  NOT NULL,
  CONSTRAINT ck_privacy_audit_log_action CHECK (action IN ('READ', 'CREATE', 'UPDATE', 'DELETE', 'DOWNLOAD')),
  INDEX ix_privacy_audit_log_account (account_id, created_at),
  INDEX ix_privacy_audit_log_created (created_at)
);
