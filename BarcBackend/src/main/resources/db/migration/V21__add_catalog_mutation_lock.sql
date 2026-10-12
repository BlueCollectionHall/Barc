-- 小型配置集合的事务行锁，不修改或删除现有配置与业务数据。
CREATE TABLE IF NOT EXISTS catalog_mutation_lock (
    scope VARCHAR(64) NOT NULL PRIMARY KEY
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
INSERT INTO catalog_mutation_lock(scope) VALUES ('presentation'), ('friendlink'), ('category')
ON DUPLICATE KEY UPDATE scope=VALUES(scope);
