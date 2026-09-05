CREATE TABLE IF NOT EXISTS background_image (
    id VARCHAR(36) PRIMARY KEY NOT NULL,
    module VARCHAR(32) NOT NULL,
    object_key VARCHAR(255) NOT NULL,
    filename VARCHAR(255) NULL,
    sort_order INT NOT NULL DEFAULT 0,
    enabled TINYINT(1) NOT NULL DEFAULT 1,
    created_by VARCHAR(32) NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted_at TIMESTAMP NULL,
    INDEX idx_background_module_enabled_order (module, enabled, sort_order),
    INDEX idx_background_object_key (object_key)
);
