-- 官方业务指定账号（管理端「用户 → 官方账号」维护）
--
-- 设计说明：
-- 1、business_key 为主键：一个业务只允许绑定一个账号（拍板 Q7）；
-- 2、user_uuid 加唯一索引：一个账号只能绑定一个业务（拍板 D2）；
-- 3、user_uuid NOT NULL：本模块不提供「解绑」，只能「更换」（拍板 D1）；
--    「无记录」与「未绑定」是同一状态，首次绑定前该状态才存在；
-- 4、不加外键（拍板 D4）：账号被删除时不因 RESTRICT 卡住既有账号链路；
--    悬空绑定由写链路的 JOIN 校验与页面提示双重兜底；
-- 5、updated_by 记录最后操作的管理员 UUID（沿用 AGENTS §6.2「用 uuid 关联用户」）。
CREATE TABLE IF NOT EXISTS official_account (
    business_key VARCHAR(64) PRIMARY KEY NOT NULL COMMENT '业务键，取 OfficialAccountEnum.name()',
    user_uuid    VARCHAR(32) NOT NULL COMMENT '被指定的官方账号 UUID（user_archive.uuid）',
    remark       VARCHAR(255) NULL COMMENT '绑定备注',
    updated_by   VARCHAR(32) NULL COMMENT '最后操作的管理员 UUID',
    created_at   TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at   TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_official_account_user (user_uuid),
    INDEX idx_official_account_updated_by (updated_by)
);
