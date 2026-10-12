-- 邮件与业务事务同时入库；投递失败可重试，业务回滚不留下成功通知。
CREATE TABLE IF NOT EXISTS mail_outbox (
    id VARCHAR(36) PRIMARY KEY,
    event_key VARCHAR(160) NOT NULL UNIQUE,
    recipient VARCHAR(255) NOT NULL,
    subject VARCHAR(255) NOT NULL,
    body TEXT NOT NULL,
    expires_at TIMESTAMP NULL,
    attempts INT NOT NULL DEFAULT 0,
    lease_token VARCHAR(36) NULL,
    lease_until TIMESTAMP NULL,
    next_attempt_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    sent_at TIMESTAMP NULL,
    INDEX idx_mail_pending (sent_at, next_attempt_at)
);
