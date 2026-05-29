-- 学园表加软删除
ALTER TABLE schools ADD COLUMN deleted_at TIMESTAMP NULL DEFAULT NULL;
CREATE INDEX idx_schools_deleted_at ON schools(deleted_at);

-- 部团表加软删除
ALTER TABLE club ADD COLUMN deleted_at TIMESTAMP NULL DEFAULT NULL;
CREATE INDEX idx_club_deleted_at ON club(deleted_at);

-- 学生表加软删除
ALTER TABLE students ADD COLUMN deleted_at TIMESTAMP NULL DEFAULT NULL;
CREATE INDEX idx_students_deleted_at ON students(deleted_at);

-- GC 队列表
CREATE TABLE IF NOT EXISTS cos_garbage_key(
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    bucket VARCHAR(100) NOT NULL,
    object_key VARCHAR(500) NOT NULL,
    retry_count INT DEFAULT 0,
    last_error TEXT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    processed_at TIMESTAMP NULL DEFAULT NULL,
    INDEX idx_processed (processed_at),
    INDEX idx_created (created_at)
);
