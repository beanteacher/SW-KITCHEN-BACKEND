ALTER TABLE account
  DROP INDEX uk_account_email,
  DROP COLUMN email;

-- 소셜로만 가입한 고객은 아이디·비밀번호가 없다
ALTER TABLE account
  MODIFY user_id VARCHAR(20) NULL,
  MODIFY password_hash VARCHAR(100) NULL,
  ADD COLUMN name VARCHAR(50) NULL AFTER password_hash,
  ADD COLUMN status VARCHAR(10) NOT NULL DEFAULT 'ACTIVE' AFTER role,
  ADD COLUMN login_fail_count INT NOT NULL DEFAULT 0 AFTER status;

-- 이미 있는 계정은 이름이 없어 아이디로 채운다
UPDATE account SET name = user_id;

ALTER TABLE account
  MODIFY name VARCHAR(50) NOT NULL,
  ADD CONSTRAINT ck_account_status CHECK (status IN ('ACTIVE', 'SUSPENDED')),
  ADD CONSTRAINT ck_account_login CHECK ((user_id IS NULL) = (password_hash IS NULL)),
  ADD CONSTRAINT ck_account_login_fail CHECK (login_fail_count >= 0),
  ADD INDEX ix_account_name (name);

-- 재발급 뒤 30초 동안 그 토큰을 더 받는다 (여러 탭이 동시에 재발급해도 풀리지 않게)
ALTER TABLE refresh_token
  ADD COLUMN rotated_at DATETIME(6) NULL AFTER expires_at;
