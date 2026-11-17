-- 고객 계정만 한 행. 생년월일·휴대폰 번호는 AES-GCM 암호문이다
CREATE TABLE account_identity (
  account_id           BIGINT       PRIMARY KEY,
  ci_hash              CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,   -- 본인확인 CI 의 SHA-256. 같은 사람 확인에만 쓴다
  birth_date_enc       VARCHAR(100) NOT NULL,
  gender               CHAR(1)      NOT NULL,
  phone_enc            VARCHAR(100) NOT NULL,
  terms_agreed_at      DATETIME(6)  NOT NULL,
  privacy_agreed_at    DATETIME(6)  NOT NULL,
  marketing_agreed     BOOLEAN      NOT NULL,
  marketing_changed_at DATETIME(6)  NOT NULL,
  CONSTRAINT uk_account_identity_ci UNIQUE (ci_hash),   -- 한 사람 한 계정
  CONSTRAINT ck_account_identity_gender CHECK (gender IN ('M', 'F')),
  CONSTRAINT fk_account_identity_account FOREIGN KEY (account_id) REFERENCES account (id)
);

-- 한 번 쓴 본인확인 건 번호. 같은 건으로 두 번 가입·변경하지 못하게 한다
CREATE TABLE used_identity_verification (
  verification_id  VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,
  used_at          DATETIME(6) NOT NULL,
  INDEX ix_used_identity_verification_used_at (used_at)
);
