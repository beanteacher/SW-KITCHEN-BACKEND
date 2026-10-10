CREATE TABLE account (
  id            BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id       VARCHAR(20)  NOT NULL,
  email         VARCHAR(255) NULL,
  password_hash VARCHAR(100) NOT NULL,
  role          VARCHAR(10)  NOT NULL,
  created_at    DATETIME(6)  NOT NULL,
  updated_at    DATETIME(6)  NOT NULL,
  CONSTRAINT uk_account_user_id UNIQUE (user_id),
  CONSTRAINT uk_account_email UNIQUE (email),
  CONSTRAINT ck_account_role CHECK (role IN ('CUSTOMER', 'STAFF', 'ADMIN'))
);

-- 토큰 원문은 쿠키에만 두고 DB 에는 SHA-256 해시만 저장한다
CREATE TABLE refresh_token (
  id          BIGINT AUTO_INCREMENT PRIMARY KEY,
  account_id  BIGINT      NOT NULL,
  token_hash  CHAR(64)    NOT NULL,
  expires_at  DATETIME(6) NOT NULL,
  created_at  DATETIME(6) NOT NULL,
  CONSTRAINT uk_refresh_token_hash UNIQUE (token_hash),
  CONSTRAINT fk_refresh_token_account FOREIGN KEY (account_id) REFERENCES account (id),
  INDEX ix_refresh_token_expires (expires_at)
);
