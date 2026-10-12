-- 申请保留处理结果；同一用户可以在结案后重新申请，但同时只能有一笔待审申请。
ALTER TABLE work_claim
    ADD COLUMN status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    ADD COLUMN statement VARCHAR(2000) NULL,
    ADD COLUMN reviewer_uuid VARCHAR(32) NULL,
    ADD COLUMN processed_at DATETIME NULL,
    ADD COLUMN result_reason VARCHAR(500) NULL,
    -- 生成列只依赖状态，避免与 applicant_uuid 的级联外键触发 MySQL 生成列限制。
    ADD COLUMN pending_marker TINYINT GENERATED ALWAYS AS
        (CASE WHEN status = 'PENDING' THEN 1 ELSE NULL END) STORED,
    ADD INDEX idx_claim_work_created (work_id, created_at, id),
    ADD INDEX idx_claim_applicant_created (applicant_uuid, created_at, id),
    ADD INDEX idx_claim_status_created (status, created_at, id);

-- 旧建库脚本未为联合唯一键统一命名，按列组合定位；先补索引，保持外键可用。
SET @barc_claim_old_index = (
    SELECT index_name FROM information_schema.statistics
    WHERE table_schema = DATABASE() AND table_name = 'work_claim' AND non_unique = 0
    GROUP BY index_name HAVING GROUP_CONCAT(column_name ORDER BY seq_in_index) = 'work_id,applicant_uuid' LIMIT 1
);
SET @barc_claim_drop_sql = IF(@barc_claim_old_index IS NULL, 'SELECT 1',
    CONCAT('ALTER TABLE work_claim DROP INDEX `', REPLACE(@barc_claim_old_index, '`', '``'), '`'));
PREPARE barc_claim_statement FROM @barc_claim_drop_sql;
EXECUTE barc_claim_statement;
DEALLOCATE PREPARE barc_claim_statement;
ALTER TABLE work_claim ADD UNIQUE KEY uk_claim_pending (work_id, applicant_uuid, pending_marker);
