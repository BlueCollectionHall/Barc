-- 发布前只读核查；由维护者连接目标库执行并保存结果，本脚本不修改任何业务数据。
-- 前提：目标库已具备 V13 的活跃表。未知数字状态、重复部团归属必须先确认业务含义。
START TRANSACTION READ ONLY;
SELECT DATABASE() AS checked_database, VERSION() AS mysql_version;

-- 对照迁移基线检查类型漂移，尤其是旧手工建库的状态、身份、图片与关联字段。
SELECT table_name, column_name, column_type, is_nullable
FROM information_schema.columns
WHERE table_schema = DATABASE() AND (
    (table_name = 'user_archive' AND column_name = 'identity') OR
    (table_name = 'user_basic' AND column_name IN ('safe_level_before_ban','auth_version','local_password_enabled')) OR
    (table_name = 'work' AND column_name IN ('status','title')) OR
    (table_name = 'work_category' AND column_name = 'id') OR
    (table_name = 'feedback' AND column_name = 'target_id') OR
    (table_name = 'trend_image' AND column_name IN ('image_url','object_key')) OR
    (table_name = 'notice' AND column_name = 'content') OR
    (table_name IN ('school','club','student') AND column_name IN ('id','deleted_at','cn_name'))
) ORDER BY table_name, ordinal_position;

SELECT CAST(status AS CHAR) AS work_status, COUNT(*) AS work_count FROM work GROUP BY status;
SELECT id, CAST(status AS CHAR) AS unknown_status FROM work
WHERE status IS NULL OR CAST(status AS CHAR) NOT IN ('PUBLIC','PRIVATE','BAN','OFF','DELETED');
SELECT club_id, COUNT(*) AS school_relations, GROUP_CONCAT(school_id ORDER BY school_id) AS school_ids
FROM school_club GROUP BY club_id HAVING COUNT(*) > 1;
SELECT s.id AS student_id, s.school AS student_school, sc.school_id AS club_school, s.club AS club_id
FROM student s JOIN school_club sc ON sc.club_id = s.club
WHERE NOT (s.school <=> sc.school_id) AND s.deleted_at IS NULL;

-- 仅列出存在 URL 污染的记录编号，不输出签名或令牌。历史数据不自动改写。
SELECT 'school' AS entity_type, id FROM school WHERE logo REGEXP '^https?://' OR beautify_logo REGEXP '^https?://' OR bg REGEXP '^https?://'
UNION ALL SELECT 'club', id FROM club WHERE logo REGEXP '^https?://' OR bg REGEXP '^https?://'
UNION ALL SELECT 'student', id FROM student WHERE avatar_square REGEXP '^https?://' OR avatar_rectangle REGEXP '^https?://' OR body_image REGEXP '^https?://';
SELECT bucket, COUNT(*) AS queued_count FROM cos_garbage_key GROUP BY bucket;
COMMIT;
