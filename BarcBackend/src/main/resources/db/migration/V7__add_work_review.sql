CREATE TABLE IF NOT EXISTS work_review (
    work_id VARCHAR(100) PRIMARY KEY NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/APPROVED/REJECTED',
    rejection_reason VARCHAR(500) NULL,
    reviewer_uuid VARCHAR(32) NULL,
    submitted_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    reviewed_at TIMESTAMP NULL DEFAULT NULL,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_work_review_status_submitted (status, submitted_at),
    INDEX idx_work_review_reviewer (reviewer_uuid),
    CONSTRAINT fk_work_review_work FOREIGN KEY (work_id) REFERENCES work(id) ON DELETE CASCADE,
    CONSTRAINT fk_work_review_reviewer FOREIGN KEY (reviewer_uuid) REFERENCES user_basic(uuid) ON DELETE SET NULL
);

-- 审核功能上线前的存量作品保持原有展示效果；仅迁移完成后的新上传从 PENDING 开始。
INSERT INTO work_review (work_id, status, submitted_at, reviewed_at, updated_at)
SELECT w.id, 'APPROVED', COALESCE(w.created_at, CURRENT_TIMESTAMP), CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM work w
LEFT JOIN work_review wr ON wr.work_id = w.id
WHERE wr.work_id IS NULL;
