-- 全新数据库的 V13 累积基线；有迁移历史的数据库自动忽略本文件。
-- 既有 V1..V13 保持校验和不变。该脚本不用于覆盖现有数据库。
CREATE TABLE IF NOT EXISTS user_basic(
    uuid VARCHAR(32) PRIMARY KEY NOT NULL ,
    username VARCHAR(50) UNIQUE NOT NULL ,
    password VARCHAR(255) NOT NULL ,
    password_version TINYINT DEFAULT 1 ,
    email VARCHAR(255) UNIQUE NOT NULL ,
    email_verified BOOLEAN DEFAULT FALSE ,
    telephone VARCHAR(20) NULL ,
    safe_level TINYINT DEFAULT 100 ,
    safe_level_before_ban INT DEFAULT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);
CREATE TABLE IF NOT EXISTS user_archive(
    uuid VARCHAR(32) PRIMARY KEY NOT NULL ,
    nickname VARCHAR(50) NOT NULL DEFAULT '用户' ,
    avatar VARCHAR(255) NOT NULL ,
    gender TINYINT DEFAULT 0,
    birthday DATE NULL ,
    age INT NULL ,
    permission INT NOT NULL DEFAULT 1 ,
    identity VARCHAR(16) NOT NULL DEFAULT 'USER',
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP ,
    FOREIGN KEY (uuid) REFERENCES user_basic(uuid) ON DELETE CASCADE
);
CREATE TABLE IF NOT EXISTS verification_code (
    unique_id VARCHAR(36) PRIMARY KEY NOT NULL ,
    code VARCHAR(6) NOT NULL ,
    username VARCHAR(50) NOT NULL ,
    scenario VARCHAR(20) NOT NULL ,
    create_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ,
    expiration_at TIMESTAMP NOT NULL ,
    used BOOLEAN DEFAULT FALSE
);
CREATE TABLE IF NOT EXISTS category(
    id VARCHAR(50) PRIMARY KEY NOT NULL ,
    parent_id VARCHAR(50) NULL ,
    name VARCHAR(20) NOT NULL ,
    level TINYINT NOT NULL ,
    sort INT DEFAULT 0 ,
    is_enabled BOOLEAN DEFAULT TRUE ,
    icon TEXT NULL ,
    create_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (parent_id) REFERENCES category(id) ON DELETE CASCADE ,
    INDEX idx_parent (parent_id),
    INDEX idx_level (level)
);
CREATE TABLE IF NOT EXISTS work(
    id VARCHAR(100) PRIMARY KEY NOT NULL ,
    title VARCHAR(50) NOT NULL ,
    description TEXT NULL ,
    content LONGTEXT NULL ,
    banner_image VARCHAR(255) NOT NULL ,
    cover_image VARCHAR(255) NOT NULL ,
    view_count INT DEFAULT 0 ,
    like_count INT DEFAULT 0 ,
    author VARCHAR(32) NOT NULL ,
    author_nickname VARCHAR(50) NULL ,
    uploader VARCHAR(32) NULL ,
    is_claim BOOLEAN DEFAULT FALSE ,
    status VARCHAR(16) NOT NULL DEFAULT 'PRIVATE' ,
    student VARCHAR(255) NOT NULL ,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP ,
    content_updated_at TIMESTAMP NULL DEFAULT NULL ,
    INDEX idx_creator (author),
    INDEX idx_status (status)
);
CREATE TABLE IF NOT EXISTS work_review(
    work_id VARCHAR(100) PRIMARY KEY NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/APPROVED/REJECTED',
    rejection_reason VARCHAR(500) NULL,
    reviewer_uuid VARCHAR(32) NULL,
    submitted_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    reviewed_at TIMESTAMP NULL DEFAULT NULL,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_work_review_status_submitted (status, submitted_at),
    INDEX idx_work_review_reviewer (reviewer_uuid),
    FOREIGN KEY (work_id) REFERENCES work(id) ON DELETE CASCADE,
    FOREIGN KEY (reviewer_uuid) REFERENCES user_basic(uuid) ON DELETE SET NULL
);
CREATE TABLE IF NOT EXISTS work_category(
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    work_id VARCHAR(100) NOT NULL ,
    category_id VARCHAR(50) NOT NULL ,
    create_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_work_category (work_id, category_id) ,
    FOREIGN KEY (work_id) REFERENCES work(id) ON DELETE CASCADE ,
    FOREIGN KEY (category_id) REFERENCES category(id) ON DELETE CASCADE
);
CREATE TABLE IF NOT EXISTS work_like(
    id VARCHAR(36) PRIMARY KEY NOT NULL,
    work_id VARCHAR(100) NOT NULL,
    user_uuid VARCHAR(32) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_work_like_user (work_id, user_uuid),
    INDEX idx_work_like_work_id (work_id),
    INDEX idx_work_like_user_uuid (user_uuid),
    FOREIGN KEY (work_id) REFERENCES work(id) ON DELETE CASCADE,
    FOREIGN KEY (user_uuid) REFERENCES user_basic(uuid) ON DELETE CASCADE
);
CREATE TABLE IF NOT EXISTS work_view_log(
    id VARCHAR(36) PRIMARY KEY NOT NULL,
    work_id VARCHAR(100) NOT NULL,
    view_date DATE NOT NULL,
    viewer_type VARCHAR(16) NOT NULL,
    viewer_user_uuid VARCHAR(32) NULL,
    viewer_ipv4 VARCHAR(15) NULL,
    viewer_ipv6 VARCHAR(39) NULL,
    viewer_ip_hash VARCHAR(64) NULL,
    dedupe_key VARCHAR(128) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_work_view_log_dedupe (work_id, view_date, dedupe_key),
    INDEX idx_work_view_log_work_id (work_id),
    INDEX idx_work_view_log_view_date (view_date),
    INDEX idx_work_view_log_viewer_user_uuid (viewer_user_uuid),
    FOREIGN KEY (work_id) REFERENCES work(id)
);
CREATE TABLE IF NOT EXISTS school(
    deleted_at TIMESTAMP NULL DEFAULT NULL,
    id VARCHAR(200) PRIMARY KEY NOT NULL ,
    cn_name VARCHAR(100) NOT NULL,
    jp_name VARCHAR(100) NULL,
    kr_name VARCHAR(100) NULL,
    en_name VARCHAR(255) NULL,
    introduce TEXT NULL,
    logo VARCHAR(255) NULL,
    beautify_logo VARCHAR(255) NULL,
    bg VARCHAR(255) NULL,
    UNIQUE KEY uk_name (cn_name)
);
CREATE TABLE IF NOT EXISTS club(
    deleted_at TIMESTAMP NULL DEFAULT NULL,
    id VARCHAR(200) PRIMARY KEY NOT NULL,
    school VARCHAR(200) NULL,
    cn_name VARCHAR(100) NOT NULL,
    jp_name VARCHAR(100) NULL,
    kr_name VARCHAR(100) NULL,
    en_name VARCHAR(255) NULL,
    logo VARCHAR(255) NULL,
    bg VARCHAR(255) NULL,
    UNIQUE KEY uk_name (cn_name)
);
CREATE TABLE IF NOT EXISTS school_club(
    id INT PRIMARY KEY AUTO_INCREMENT,
    school_id VARCHAR(200) NOT NULL ,
    club_id VARCHAR(200) NOT NULL ,
    FOREIGN KEY (school_id) REFERENCES school(id) ON DELETE CASCADE ,
    FOREIGN KEY (club_id) REFERENCES club(id) ON DELETE CASCADE ,
    UNIQUE KEY uk_school_club (school_id, club_id)
);
CREATE TABLE IF NOT EXISTS student(
    deleted_at TIMESTAMP NULL DEFAULT NULL,
    id VARCHAR(255) PRIMARY KEY NOT NULL,
    cn_name VARCHAR(50) NOT NULL ,
    jp_name VARCHAR(100) NULL ,
    kr_name VARCHAR(100) NULL ,
    en_name VARCHAR(255) NOT NULL ,
    introduce TEXT NULL ,
    avatar_square VARCHAR(255) NULL ,
    avatar_rectangle VARCHAR(255) NOT NULL ,
    body_image VARCHAR(255) NOT NULL ,
    school VARCHAR(100) NULL ,
    club VARCHAR(100) NOT NULL,
    INDEX idx_club (club),
    INDEX idx_school (school) ,
    CONSTRAINT fk_student_club FOREIGN KEY (club) REFERENCES club(id) ON DELETE CASCADE ,
    CONSTRAINT fk_student_school FOREIGN KEY (school) REFERENCES school(id) ON DELETE RESTRICT
);
CREATE TABLE IF NOT EXISTS barc_naigos_uuid(
    uuid VARCHAR(32) PRIMARY KEY NOT NULL ,
    naigos_uuid VARCHAR(36) UNIQUE NOT NULL ,
    FOREIGN KEY barc_naigos_uuid(uuid) REFERENCES user_basic(uuid) ON DELETE CASCADE
);
CREATE TABLE IF NOT EXISTS work_claim(
    id VARCHAR(36) PRIMARY KEY NOT NULL ,
    work_id VARCHAR(100) NOT NULL ,
    applicant_uuid VARCHAR(32) NOT NULL ,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ,
    FOREIGN KEY (work_id) REFERENCES work(id) ON DELETE CASCADE ,
    FOREIGN KEY (applicant_uuid) REFERENCES user_basic(uuid) ON DELETE CASCADE ,
    UNIQUE KEY (work_id, applicant_uuid)
);
CREATE TABLE IF NOT EXISTS work_comment(
    id VARCHAR(36) PRIMARY KEY NOT NULL ,
    work_id VARCHAR(100) NOT NULL ,
    author VARCHAR(32) NOT NULL ,
    content TEXT NOT NULL ,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (work_id) REFERENCES work(id) ON DELETE CASCADE ,
    INDEX idx_work_id (work_id)
);
CREATE TABLE IF NOT EXISTS work_comment_reply(
    id VARCHAR(36) PRIMARY KEY NOT NULL ,
    parent_id VARCHAR(36) DEFAULT NULL,
    author VARCHAR(32) NOT NULL ,
    reply_user VARCHAR(32) DEFAULT NULL,
    content TEXT NOT NULL ,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (parent_id) REFERENCES work_comment(id) ON DELETE CASCADE ,
    INDEX idx_parent_id (parent_id)
);
CREATE TABLE IF NOT EXISTS work_image(
    id VARCHAR(36) PRIMARY KEY NOT NULL ,
    work_id VARCHAR(36) NOT NULL ,
    sort INT NOT NULL DEFAULT 0,
    object_key VARCHAR(255) NOT NULL ,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_work_id (work_id),
    FOREIGN KEY (work_id) REFERENCES work(id) ON DELETE CASCADE
);
CREATE TABLE IF NOT EXISTS work_cover_image(
     id VARCHAR(36) PRIMARY KEY NOT NULL ,
     work_id VARCHAR(36) UNIQUE KEY NOT NULL ,
     object_key VARCHAR(255) NOT NULL ,
     created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
     INDEX idx_work_id (work_id),
     FOREIGN KEY (work_id) REFERENCES work(id) ON DELETE CASCADE
);
CREATE TABLE IF NOT EXISTS message_board(
    id VARCHAR(36) PRIMARY KEY NOT NULL ,
    author VARCHAR(32) NOT NULL ,
    content TEXT NOT NULL ,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_created_at (created_at)
);
# 【已废弃】feedback_type / feedback_option
# 反馈分类已改由后端 FeedbackTypeEnum 与前端本地映射驱动，
# 旧接口已经下线；仅保留历史表定义，禁止新增读写链路。
CREATE TABLE IF NOT EXISTS feedback_type(
    id VARCHAR(50) PRIMARY KEY NOT NULL ,
    name VARCHAR(20) NOT NULL,
    INDEX idx_id (id)
);
CREATE TABLE IF NOT EXISTS feedback_option(
    id VARCHAR(100) PRIMARY KEY NOT NULL ,
    name VARCHAR(20) NOT NULL ,
    parent_id VARCHAR(50) NOT NULL ,
    INDEX idx_id (id),
    INDEX idx_parent_id (parent_id),
    FOREIGN KEY (parent_id) REFERENCES feedback_type(id) ON DELETE CASCADE
);
# 【已废弃】feedback_work
# 作品投诉已统一走通用 feedback 表（type = WORK），禁止再使用该链路。
CREATE TABLE IF NOT EXISTS feedback_work(
    id VARCHAR(36) PRIMARY KEY NOT NULL ,
    work_id VARCHAR(36) NOT NULL ,
    ipv4 VARCHAR(15) DEFAULT NULL ,
    device_info TEXT DEFAULT NULL ,
    author VARCHAR(32) DEFAULT NULL ,
    reason_option VARCHAR(100) NOT NULL ,
    content TEXT DEFAULT NULL,
    email VARCHAR(255) NOT NULL ,
    status BOOLEAN DEFAULT FALSE,
    note TEXT DEFAULT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_work_id (work_id),
    INDEX idx_status (status),
    INDEX idx_created_at (created_at),
    FOREIGN KEY (work_id) REFERENCES work(id) ON DELETE CASCADE
);
CREATE TABLE IF NOT EXISTS feedback(
    id VARCHAR(36) PRIMARY KEY NOT NULL ,
    target_id VARCHAR(36) NOT NULL COMMENT '目标对象ID',
    ipv4 VARCHAR(15) DEFAULT NULL ,
    ipv6 VARCHAR(39) DEFAULT NULL ,
    author VARCHAR(32) DEFAULT NULL,
    email VARCHAR(255) DEFAULT NULL,
    content TEXT NOT NULL ,
    echo TEXT DEFAULT NULL,
    type VARCHAR(20) NOT NULL ,
    status ENUM('PENDING', 'PROCESSING', 'COMPLETED', 'REJECTED') DEFAULT 'PENDING' NOT NULL ,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_target_id (target_id),
    INDEX idx_author (author),
    INDEX idx_type (type),
    INDEX idx_status (status),
    INDEX idx_created_at (created_at),
    INDEX idx_updated_at (updated_at)
);
CREATE TABLE IF NOT EXISTS trend(
    id VARCHAR(36) PRIMARY KEY NOT NULL ,
    title VARCHAR(255) NOT NULL ,
    content TEXT NOT NULL ,
    author VARCHAR(32) NOT NULL ,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_author (author),
    INDEX idx_created_at (created_at),
    INDEX idx_updated_at (updated_at)
);
CREATE TABLE IF NOT EXISTS trend_image(
    id VARCHAR(36) PRIMARY KEY NOT NULL ,
    trend_id VARCHAR(36) NOT NULL ,
    sort INT NOT NULL DEFAULT 0,
    image_url VARCHAR(255) NOT NULL ,
    image_name VARCHAR(255) NOT NULL ,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_trend_id (trend_id),
    FOREIGN KEY (trend_id) REFERENCES trend(id) ON DELETE CASCADE
);
CREATE TABLE IF NOT EXISTS notice (
    id VARCHAR(36) PRIMARY KEY NOT NULL ,
    title VARCHAR(255) NOT NULL ,
    content TEXT NOT NULL ,
    author VARCHAR(32) NOT NULL ,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_id (id),
    INDEX idx_author (author),
    INDEX idx_created_at (created_at),
    INDEX idx_updated_at (updated_at)
);
CREATE TABLE IF NOT EXISTS work_operation_log (
    id VARCHAR(36) PRIMARY KEY NOT NULL,
    work_id VARCHAR(100) NOT NULL,
    operator_uuid VARCHAR(32) NOT NULL,
    operation_type VARCHAR(30) NOT NULL COMMENT '操作类型：含 REVIEW_APPROVE/REVIEW_REJECT 等作品管理动作',
    detail TEXT NULL COMMENT '操作详情JSON',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_work_id (work_id),
    INDEX idx_operator (operator_uuid),
    INDEX idx_operation_type (operation_type),
    INDEX idx_created_at (created_at),
    FOREIGN KEY (work_id) REFERENCES work(id) ON DELETE CASCADE,
    FOREIGN KEY (operator_uuid) REFERENCES user_basic(uuid) ON DELETE CASCADE
);
CREATE TABLE IF NOT EXISTS background_image (
    id VARCHAR(36) PRIMARY KEY NOT NULL ,
    module VARCHAR(32) NOT NULL ,
    object_key VARCHAR(255) NOT NULL ,
    filename VARCHAR(255) NULL ,
    sort_order INT NOT NULL DEFAULT 0 ,
    enabled TINYINT(1) NOT NULL DEFAULT 1 ,
    time_period VARCHAR(16) NULL ,
    festival VARCHAR(32) NULL COMMENT '绑定的节日ID，NULL 表示无节日通用图；节日被删除时自动置 NULL' ,
    created_by VARCHAR(32) NULL ,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP ,
    deleted_at TIMESTAMP NULL ,
    INDEX idx_background_module_enabled_order (module, enabled, sort_order) ,
    INDEX idx_background_object_key (object_key) ,
    INDEX idx_background_festival (festival)
);
CREATE TABLE IF NOT EXISTS festival (
    id VARCHAR(32) PRIMARY KEY NOT NULL ,
    name VARCHAR(64) NOT NULL ,
    start_month TINYINT NOT NULL ,
    start_day TINYINT NOT NULL ,
    end_month TINYINT NOT NULL ,
    end_day TINYINT NOT NULL ,
    start_index SMALLINT NOT NULL COMMENT '统一索引：月份 * 32 + 日' ,
    end_index SMALLINT NOT NULL COMMENT '统一索引：小于 start_index 表示该区间跨年环绕（如新年 12-01 → 02-29）' ,
    enabled TINYINT(1) NOT NULL DEFAULT 1 COMMENT '关闭后保留数据与绑定关系，仅不参与用户端节日判定' ,
    created_by VARCHAR(32) NULL ,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP ,
    deleted_at TIMESTAMP NULL ,
    INDEX idx_festival_enabled (enabled, deleted_at) ,
    INDEX idx_festival_range (start_index, end_index)
);

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

CREATE TABLE IF NOT EXISTS user_ban_record (
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

CREATE TABLE IF NOT EXISTS friend_link (
    id          VARCHAR(32)  PRIMARY KEY NOT NULL,
    title       VARCHAR(64)  NOT NULL,
    url         VARCHAR(512) NOT NULL,
    logo_key    VARCHAR(255) NULL,
    intro       VARCHAR(255) NULL,
    sort_order  INT          NOT NULL DEFAULT 0,
    enabled     TINYINT(1)   NOT NULL DEFAULT 1,
    created_by  VARCHAR(32)  NULL,
    created_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted_at  TIMESTAMP NULL,
    INDEX idx_friend_link_order (sort_order),
    INDEX idx_friend_link_logo_key (logo_key)
);

-- 新安装采用 V11 修正后的节日种子，不重放历史删除语句。
INSERT INTO festival(id,name,start_month,start_day,end_month,end_day,start_index,end_index,enabled) VALUES ('0000000000000000000000000000f001','新年',12,1,2,29,385,93,1);
