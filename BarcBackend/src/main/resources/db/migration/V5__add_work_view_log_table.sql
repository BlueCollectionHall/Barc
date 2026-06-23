CREATE TABLE IF NOT EXISTS work_view_log (
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
