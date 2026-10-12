-- 新动态图片保存 COS key；历史 MinIO URL 原样保留，不自动搬迁或删除用户文件。
ALTER TABLE trend_image ADD COLUMN object_key VARCHAR(255) NULL;
CREATE INDEX idx_trend_image_object_key ON trend_image(object_key);
CREATE TABLE IF NOT EXISTS legacy_object_retirement (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    provider VARCHAR(20) NOT NULL,
    bucket VARCHAR(100) NOT NULL,
    object_key VARCHAR(500) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    reviewed_at TIMESTAMP NULL
);
