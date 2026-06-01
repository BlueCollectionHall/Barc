-- 为 school 表添加 deleted_at 列
-- 注意：V1 迁移使用的是 schools 表名，现在需要为 school 表添加
-- 如果列已存在，Flyway 会报错，但这是预期的行为
ALTER TABLE school ADD COLUMN deleted_at TIMESTAMP NULL DEFAULT NULL;
CREATE INDEX idx_school_deleted_at ON school(deleted_at);

-- 为 student 表添加 deleted_at 列
-- 注意：V1 迁移使用的是 students 表名，现在需要为 student 表添加
-- 如果列已存在，Flyway 会报错，但这是预期的行为
ALTER TABLE student ADD COLUMN deleted_at TIMESTAMP NULL DEFAULT NULL;
CREATE INDEX idx_student_deleted_at ON student(deleted_at);
