-- 保存部团迁移前逐学生归属，用于审查和人工反向迁移，不自动恢复历史数据。
CREATE TABLE IF NOT EXISTS entity_relation_change (
    id VARCHAR(36) PRIMARY KEY,
    operator_uuid VARCHAR(36) NOT NULL,
    club_id VARCHAR(255) NOT NULL,
    old_school VARCHAR(255) NULL,
    new_school VARCHAR(255) NOT NULL,
    previous_students JSON NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
