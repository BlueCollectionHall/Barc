CREATE TABLE IF NOT EXISTS work_like (
    id VARCHAR(36) PRIMARY KEY NOT NULL,
    work_id VARCHAR(100) NOT NULL,
    user_uuid VARCHAR(32) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_work_like_user (work_id, user_uuid),
    INDEX idx_work_like_work_id (work_id),
    INDEX idx_work_like_user_uuid (user_uuid),
    FOREIGN KEY (work_id) REFERENCES work(id) ON DELETE CASCADE,
    FOREIGN KEY (user_uuid) REFERENCES user_basic(uuid) ON DELETE CASCADE
);
