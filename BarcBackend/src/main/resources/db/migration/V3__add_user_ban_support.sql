-- V3__add_user_ban_support.sql
-- 为用户封号功能添加数据库支持

-- 1. 为student表添加safe_level_before_ban字段
ALTER TABLE student ADD COLUMN safe_level_before_ban INT DEFAULT NULL COMMENT '封号前的safe_level值，用于解封恢复';

-- 2. 创建封号记录表
CREATE TABLE user_ban_record (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    user_id VARCHAR(255) NOT NULL COMMENT '被封号用户ID',
    ban_type TINYINT NOT NULL COMMENT '封号类型：0-风险冻结，1-临时封号，2-违规封号',
    ban_reason VARCHAR(500) NOT NULL COMMENT '封号原因',
    ban_duration_days INT DEFAULT NULL COMMENT '封号天数（临时封号使用）',
    banned_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '封号时间',
    unbanned_at DATETIME DEFAULT NULL COMMENT '解封时间',
    unban_reason VARCHAR(500) DEFAULT NULL COMMENT '解封原因',
    operator_id VARCHAR(255) NOT NULL COMMENT '操作人ID（管理员或系统）',
    operator_type TINYINT NOT NULL DEFAULT 1 COMMENT '操作人类型：0-系统自动，1-管理员',
    safe_level_before_ban INT NOT NULL COMMENT '封号前的safe_level值',
    safe_level_after_ban INT NOT NULL COMMENT '封号后的safe_level值',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_user_id (user_id),
    INDEX idx_ban_type (ban_type),
    INDEX idx_banned_at (banned_at),
    INDEX idx_unbanned_at (unbanned_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户封号记录表';
