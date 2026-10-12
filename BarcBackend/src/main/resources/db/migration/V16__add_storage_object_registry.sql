-- 对象注册在上传前独立提交；业务回滚或放弃表单后仍可找到待回收对象。
CREATE TABLE IF NOT EXISTS storage_object (
    bucket VARCHAR(100) NOT NULL,
    object_key VARCHAR(500) NOT NULL,
    owner_uuid VARCHAR(36) NULL,
    state VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (bucket, object_key)
);
