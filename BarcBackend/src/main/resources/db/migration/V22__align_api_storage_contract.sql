-- 在任何 ALTER 前拒绝无法无损解释的旧数据；只报告冲突，不猜测状态或删除关系。
-- 执行前应先运行 db/validation/api-audit-preflight.sql 并完成备份。
CREATE TEMPORARY TABLE barc_v22_preflight (
    invalid_work_status BIGINT NOT NULL,
    conflicting_club_relation BIGINT NOT NULL,
    CONSTRAINT ck_barc_known_work_status CHECK (invalid_work_status = 0),
    CONSTRAINT ck_barc_single_school_per_club CHECK (conflicting_club_relation = 0)
);
INSERT INTO barc_v22_preflight
SELECT (SELECT COUNT(*) FROM work WHERE status IS NULL OR CAST(status AS CHAR) NOT IN ('PUBLIC','PRIVATE','BAN','OFF','DELETED')),
       (SELECT COUNT(*) FROM (SELECT club_id FROM school_club GROUP BY club_id HAVING COUNT(*) > 1) conflicts);
DROP TEMPORARY TABLE barc_v22_preflight;

-- 原数字主键按原值转为字符串，既有分类关联不变，新记录可写入 UUID。
ALTER TABLE work_category MODIFY COLUMN id VARCHAR(36) NOT NULL;
ALTER TABLE work MODIFY COLUMN status VARCHAR(16) NOT NULL DEFAULT 'PRIVATE';
ALTER TABLE feedback MODIFY COLUMN target_id VARCHAR(100) NULL;
ALTER TABLE trend_image MODIFY COLUMN image_url VARCHAR(255) NULL;
ALTER TABLE notice MODIFY COLUMN content LONGTEXT NOT NULL;
ALTER TABLE school MODIFY COLUMN cn_name VARCHAR(255) NOT NULL,
    MODIFY COLUMN jp_name VARCHAR(255) NULL, MODIFY COLUMN kr_name VARCHAR(255) NULL;
ALTER TABLE club MODIFY COLUMN cn_name VARCHAR(255) NOT NULL,
    MODIFY COLUMN jp_name VARCHAR(255) NULL, MODIFY COLUMN kr_name VARCHAR(255) NULL;
ALTER TABLE student MODIFY COLUMN cn_name VARCHAR(255) NOT NULL,
    MODIFY COLUMN jp_name VARCHAR(255) NULL, MODIFY COLUMN kr_name VARCHAR(255) NULL;

-- 单一学园归属由数据库约束兜底；历史重复必须先人工确认，不自动清理。
ALTER TABLE school_club ADD UNIQUE KEY uk_school_club_single (club_id);
