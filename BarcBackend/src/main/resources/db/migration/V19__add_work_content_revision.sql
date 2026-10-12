-- 内容修改与管理状态分别持久化；存量处罚作品未知原可见性，恢复默认私有。
ALTER TABLE work ADD COLUMN content_revision BIGINT NOT NULL DEFAULT 0,
                 ADD COLUMN restore_status VARCHAR(16) NULL;
ALTER TABLE work MODIFY COLUMN title VARCHAR(255) NOT NULL,
                MODIFY COLUMN author_nickname VARCHAR(255) NULL;
