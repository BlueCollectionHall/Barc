-- 会话撤销版本与密码算法版本分开：封禁、改密、改绑会话必须立即失效。
-- 不改写已有口令；旧口令在成功登录后升级，外部账号的历史默认口令在代码中拒绝登录。
ALTER TABLE user_basic
    ADD COLUMN auth_version BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN local_password_enabled BOOLEAN NOT NULL DEFAULT TRUE;

-- 邮箱长度与账号表保持一致。发码锁表用于同一邮箱/场景串行签发，避免并发互相作废新码。
ALTER TABLE verification_code MODIFY COLUMN username VARCHAR(255) NOT NULL;
CREATE TABLE verification_issue_guard (
    email VARCHAR(255) NOT NULL,
    scenario VARCHAR(20) NOT NULL,
    issued_at TIMESTAMP NULL,
    PRIMARY KEY (email, scenario)
);
CREATE INDEX idx_verification_email_scenario ON verification_code(username, scenario, used);
