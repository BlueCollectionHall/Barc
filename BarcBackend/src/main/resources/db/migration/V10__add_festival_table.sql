-- 节日管理（系统管理 → 节日管理，馆长可维护）
--
-- 设计说明：
-- 1、不论「月份式」（某月第一天至某月最后一天）还是「日期式」（精确某月某日到某月某日），
--    持久化统一为「某月某日 → 某月某日」：start_month/start_day/end_month/end_day，
--    并额外落一份「统一索引」start_index/end_index（月 * 32 + 日），便于用一条 SQL 做区间重叠判定；
-- 2、允许 start_index > end_index，表示该区间跨年环绕（如新年 12-01 → 02-28）；
-- 3、节日之间不允许重叠重合，由服务层用统一索引区间做环绕重叠校验；
-- 4、enabled = 0 表示「关闭」：保留数据与背景图绑定，仅不参与用户端节日判定。
CREATE TABLE IF NOT EXISTS festival (
    id VARCHAR(32) PRIMARY KEY NOT NULL ,
    name VARCHAR(64) NOT NULL ,
    start_month TINYINT NOT NULL ,
    start_day TINYINT NOT NULL ,
    end_month TINYINT NOT NULL ,
    end_day TINYINT NOT NULL ,
    start_index SMALLINT NOT NULL ,
    end_index SMALLINT NOT NULL ,
    enabled TINYINT(1) NOT NULL DEFAULT 1 ,
    created_by VARCHAR(32) NULL ,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP ,
    deleted_at TIMESTAMP NULL ,
    INDEX idx_festival_enabled (enabled, deleted_at) ,
    INDEX idx_festival_range (start_index, end_index)
);

-- 存量数据归一：把原来写死的 festival='newyear' 标签迁移为节日表里的「新年」，
-- 并把 background_image.festival 统一改写为该节日的 ID，使删除节日时能精确定位并自动解绑。
--
-- ⚠️ 这里必须写成幂等形式（先 DELETE 再 INSERT）：V10 曾在迁移记录被重建后重新执行，
-- 当时 festival 表中已存在同一固定 ID 的「新年」，普通 INSERT 会因主键冲突导致整个迁移失败，
-- 进而让 Flyway 的 validate 永久卡在 "Detected failed migration"。
-- 写成 DELETE + INSERT 后，无论重跑多少次都只会保留一条「新年」，不会破坏任何用户数据。
DELETE FROM festival WHERE id = '0000000000000000000000000000f001';

INSERT INTO festival (id, name, start_month, start_day, end_month, end_day, start_index, end_index, enabled, created_by)
VALUES ('0000000000000000000000000000f001', '新年', 12, 1, 2, 28, 386, 478, 1, NULL);

UPDATE background_image SET festival = '0000000000000000000000000000f001' WHERE festival = 'newyear';
