-- 友情链接（管理端「系统 → 友情链接」维护，用户端首页「友情链接&特别感谢」展示）
--
-- 设计说明：
-- 1、logo_key 允许 NULL（需求明确：仅「链接」与「标题」必填），语义是 COS 对象 key 而不是 URL；
--    对外返回时统一经 ImageUrlResolver.resolve() 签名拼成完整 URL（模型里的 logo_url 字段），符合 AGENTS §12；
-- 2、url 为外部链接，落库前由服务层校验 http:// / https:// 前缀（拍板 Q5），不做域名真实性探测；
-- 3、sort_order 为全表连续序号（从 1 开始，新增时取 MAX(sort_order) + 1），管理端上移/下移时
--    由服务端在同一事务内与相邻记录交换序号；删除后**不回填空洞**——相对次序仍然正确，
--    归一化只会多一次全表写（拍板 Q3 的派生结论）；
-- 4、enabled 本期没有管理界面（不提供启用/停用），先落列以便后续扩展；
--    注意用户端公开接口本期**不按 enabled 过滤**，与 background_image 的公开接口语义不同，
--    如需改为过滤属于行为变更，必须单独拍板（见 docs/superpowers/specs/2026-09-21-friend-link-design.md §5.2）；
-- 5、不加外键（与 background_image / festival 保持一致），created_by 记录创建人 UUID；
-- 6、logo_key 建立普通索引：孤儿对象巡检（独立需求）需要按 key 反查引用。
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
