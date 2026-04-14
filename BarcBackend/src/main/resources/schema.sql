# CREATE TABLE IF NOT EXISTS schools (
#     id VARCHAR(200) PRIMARY KEY NOT NULL ,
#     cn_name VARCHAR(100) NOT NULL,
#     jp_name VARCHAR(100) NULL,
#     kr_name VARCHAR(100) NULL,
#     en_name VARCHAR(255) NULL,
#     introduce TEXT NULL,
#     logo VARCHAR(255) NULL,
#     beautify_logo VARCHAR(255) NULL,
#     bg VARCHAR(255) NULL
# );
# CREATE TABLE IF NOT EXISTS school_clubs (
#     id VARCHAR(200) PRIMARY KEY NOT NULL,
#     school VARCHAR(200) NULL,
#     cn_name VARCHAR(100) NOT NULL,
#     jp_name VARCHAR(100) NULL,
#     kr_name VARCHAR(100) NULL,
#     en_name VARCHAR(255) NULL,
#     logo VARCHAR(255) NULL,
#     bg VARCHAR(255) NULL
# );
# CREATE TABLE IF NOT EXISTS students (
#     id VARCHAR(255) PRIMARY KEY NOT NULL,
#     cn_name VARCHAR(50) NOT NULL ,
#     jp_name VARCHAR(100) NULL ,
#     kr_name VARCHAR(100) NULL ,
#     en_name VARCHAR(255) NOT NULL ,
#     introduce TEXT NULL ,
#     avatar_square VARCHAR(255) NULL ,
#     avatar_rectangle VARCHAR(255) NOT NULL ,
#     body_image VARCHAR(255) NOT NULL ,
#     school VARCHAR(100) NULL ,
#     club VARCHAR(100) NOT NULL
# );
CREATE TABLE IF NOT EXISTS user_basic(
    uuid VARCHAR(32) PRIMARY KEY NOT NULL ,
    username VARCHAR(50) UNIQUE NOT NULL ,
    password VARCHAR(255) NOT NULL ,
    password_version TINYINT DEFAULT 1 ,
    email VARCHAR(255) UNIQUE NOT NULL ,
    email_verified BOOLEAN DEFAULT FALSE ,
    telephone VARCHAR(20) NULL ,
    safe_level TINYINT DEFAULT 100 ,
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
    status TINYINT DEFAULT 0 ,
    student VARCHAR(255) NOT NULL ,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP ,
    INDEX idx_creator (author),
    INDEX idx_status (status)
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
CREATE TABLE IF NOT EXISTS school(
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
    FOREIGN KEY student(club) REFERENCES club(id) ON DELETE CASCADE ,
    FOREIGN KEY student(school) REFERENCES school(id) ON DELETE RESTRICT
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