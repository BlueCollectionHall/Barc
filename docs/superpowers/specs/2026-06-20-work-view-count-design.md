# 作品浏览量能力设计文档

> **日期**: 2026-06-20
> **状态**: 已获用户批准，待实施
> **范围**: BarcBackend
> **兼容目标**: 继续通过 `work.view_count` 为前台与管理端提供累计浏览量

---

## 一、需求概述

本次需求为作品接入浏览量能力，并在浏览量增长上做严格、可审计的控制。

### 1.1 本次要做

1. 在作品详情接口 `GET /api/work/only` 成功返回可见作品时尝试记录浏览
2. 保留 `work.view_count` 作为累计总浏览量字段
3. 新增长期保留的浏览入账明细表，用于去重与后续历史看板
4. 登录用户按账号 UUID 在 **UTC+8 自然日** 内对同一作品只入账 1 次
5. 匿名用户按真实客户端 IP 在 **UTC+8 自然日** 内对同一作品只入账 1 次
6. 匿名访问后，用户同日登录再次访问同一作品时，允许再次入账 1 次
7. 作者本人登录访问自己的作品不计入浏览量
8. 明细表同时保存 IP 明文与 IP 哈希，且长期保留
9. 浏览量写入失败时不影响作品详情接口正常返回

### 1.2 本次明确不做

1. 不改作品列表页或其他接口的浏览量逻辑
2. 不记录“未成功入账”的重复访问尝试
3. 不做历史浏览明细回填
4. 不做机器人/爬虫过滤，第一版先全部计入
5. 不引入 Redis、消息队列或异步补偿链路
6. 不新建专用看板接口，本次只把历史明细落库

---

## 二、现状与约束

### 2.1 已确认的后端结构

- 主表定义：`BarcBackend/src/main/resources/schema.sql`
- 作品主表已有 `work.view_count INT DEFAULT 0`
- 作品详情接口：`GET /api/work/only?work_id=...`
- 详情控制器：`BarcBackend/src/main/java/com/miaoyu/barc/api/work/controller/WorkController.java`
- 详情服务：`BarcBackend/src/main/java/com/miaoyu/barc/api/work/service/WorkService.java`
- 作品持久层：`BarcBackend/src/main/java/com/miaoyu/barc/api/work/mapper/WorkMapper.java`
- 现有最接近的模式：`work_like`（去重关系表 + 主表聚合字段回写）

### 2.2 当前详情访问链路

`WorkController.getWorkOnlyControl(...)` 会调用 `WorkService.getWorksByIdService(HttpServletRequest, String)`，其当前职责包括：

1. 根据 `work_id` 查询作品
2. 拦截 `PRIVATE / BAN / OFF / DELETED` 状态
3. 查询封面指针并生成签名 URL
4. 可选解析 `Authorization` 请求头，补充 `liked_by_current_user`
5. 返回作品详情响应

本次浏览量逻辑应接入该链路，但不能破坏原有可见性校验与详情响应语义。

### 2.3 当前身份与 IP 现状

- 受保护接口通过 `AuthInterceptor` 将登录用户 UUID 注入 `request.setAttribute("uuid", ...)`
- `GET /api/work/only` 带有 `@IgnoreAuth`，不会自动注入 UUID
- `WorkService` 已经在详情路径中支持对 `Authorization` 做“可选解析”
- 当前后端没有统一的客户端 IP 提取与规范化工具
- 当前代码中没有可信代理头读取逻辑，也没有 `ForwardedHeaderFilter` 之类的配置

### 2.4 部署现实

生产环境为：

- 前端静态资源由 Nginx 提供
- Nginx 反代到后端 JAR (`0.0.0.0:51000`)
- 域名为 `api.barc.work`

本地开发为：

- 前端 `npm run dev`
- 后端 `mvn springboot:run`

因此浏览量能力必须支持：

1. 生产环境从 Nginx 覆盖后的真实 IP 头中提取客户端地址
2. 本地开发环境回退到 `request.getRemoteAddr()`

---

## 三、业务规则总表

### 3.1 记数触发条件

只有当以下条件全部满足时，才尝试入账：

1. 命中 `GET /api/work/only`
2. `work_id` 对应作品存在
3. 作品状态允许公开访问
4. 封面签名成功，接口可正常返回作品详情

以下情况不入账：

- 作品不存在
- 作品为 `PRIVATE / BAN / OFF / DELETED`
- 封面指针缺失导致详情返回受控错误
- 作者本人登录访问自己的作品
- 匿名访问但无法解析出合法客户端 IP

### 3.2 去重规则

#### 登录用户

- 同一作品
- 同一 UTC+8 自然日
- 同一 `viewer_user_uuid`

最多只入账 1 次。

#### 匿名用户

- 同一作品
- 同一 UTC+8 自然日
- 同一规范化客户端 IP

最多只入账 1 次。

#### 匿名后登录

匿名访问与登录访问使用不同 dedupe identity：

- 匿名：`IPV4:<hash>` 或 `IPV6:<hash>`
- 登录：`USER:<uuid>`

因此同一用户若先匿名访问，再登录访问，同日允许再入账 1 次。

---

## 四、总体设计结论

本次采用**单表“成功入账浏览明细”方案**：

1. 新增表 `work_view_log`
2. 每条记录只表示一次**真正成功入账**的浏览
3. `work.view_count` 继续作为累计总浏览量
4. 浏览详情返回路径内同步尝试写入浏览量
5. 通过唯一键 + `DuplicateKeyException` 实现并发防重

这是当前代码库中改动面最小、最贴近 `work_like` 既有模式、又能保留历史明细的方案。

---

## 五、数据库设计

### 5.1 新增表 `work_view_log`

建议结构：

```sql
CREATE TABLE IF NOT EXISTS work_view_log (
    id VARCHAR(36) PRIMARY KEY NOT NULL,
    work_id VARCHAR(100) NOT NULL,
    view_date DATE NOT NULL,
    viewer_type VARCHAR(16) NOT NULL,
    viewer_user_uuid VARCHAR(32) DEFAULT NULL,
    viewer_ipv4 VARCHAR(15) DEFAULT NULL,
    viewer_ipv6 VARCHAR(39) DEFAULT NULL,
    viewer_ip_hash CHAR(64) DEFAULT NULL,
    dedupe_key VARCHAR(80) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_work_view_log_daily (work_id, view_date, dedupe_key),
    INDEX idx_work_view_log_work_date (work_id, view_date),
    INDEX idx_work_view_log_date (view_date),
    INDEX idx_work_view_log_user_uuid (viewer_user_uuid),
    INDEX idx_work_view_log_ip_hash (viewer_ip_hash)
);
```

### 5.2 字段语义

- `id`: 36 位 UUID 主键
- `work_id`: 关联作品 ID
- `view_date`: 业务日期，按 **UTC+8** 计算
- `viewer_type`: `USER` / `IPV4` / `IPV6`
- `viewer_user_uuid`: 登录用户 UUID，匿名时为空
- `viewer_ipv4`: 规范化后的 IPv4 明文
- `viewer_ipv6`: 规范化后的 IPv6 明文
- `viewer_ip_hash`: 规范化 IP 的 SHA-256 十六进制小写串
- `dedupe_key`: 浏览去重主键拼接值
- `created_at`: 实际入账时间

### 5.3 `dedupe_key` 规则

- 登录：`USER:<uuid>`
- 匿名 IPv4：`IPV4:<viewer_ip_hash>`
- 匿名 IPv6：`IPV6:<viewer_ip_hash>`

这样可以把业务去重规则直接映射到数据库唯一键上，而不需要编写复杂 OR 查询。

### 5.4 约束取舍

保留外键：

- 无

不新增 `viewer_user_uuid -> user_basic.uuid` 外键，原因是：

1. 浏览历史属于长期留存审计数据
2. 账号删除不应导致历史浏览明细被级联删除
3. 作品即使未来发生物理删除，浏览历史也应继续保留
4. 当前需求更重视历史看板稳定性，而不是强实时关系约束

### 5.5 迁移策略

1. 新增 Flyway migration：`V5__add_work_view_log_table.sql`
2. 同步更新 `schema.sql`
3. 不调整 `work.view_count` 字段定义
4. 不做历史回填
5. 现有 `work.view_count` 保留原值，仅从上线后继续累计

---

## 六、后端设计

### 6.1 新增模型与分层

建议新增以下文件：

- `api/work/model/WorkViewLogModel.java`
- `api/work/mapper/WorkViewLogMapper.java`
- `api/work/service/WorkViewService.java`
- `utils/web/ClientIpResolver.java`
- `utils/web/ClientIpInfo.java`

### 6.2 `WorkViewLogMapper` 责任

最小能力：

1. `insert(WorkViewLogModel model)`

可选预留能力：

2. `countByWorkIdAndDate(workId, viewDate)`
3. `countByWorkId(workId)`

第一版浏览量写入只依赖 `insert(...)` 即可。

### 6.3 `WorkMapper` 变更

新增专用最小粒度 SQL：

1. `incrementViewCount(workId)`

不复用 `update(WorkModel)`，避免把旧对象中的其他字段误带入数据库更新。

### 6.4 `WorkViewService` 责任

核心公开方法建议为：

```java
void recordViewIfNeeded(HttpServletRequest request, WorkModel work)
```

其内部负责：

1. 可选解析当前登录用户 UUID
2. 判断是否为作者本人访问
3. 匿名场景提取并规范化真实 IP
4. 生成 `view_date`
5. 生成 `dedupe_key`
6. 组装 `WorkViewLogModel`
7. 尝试插入 `work_view_log`
8. 插入成功后执行 `work.view_count + 1`
9. 捕获重复键与其他异常并按降级策略处理

### 6.5 `ClientIpResolver` 责任

需要统一承担以下工作：

1. 读取 `X-Real-IP`
2. 读取 `X-Forwarded-For`
3. 回退到 `request.getRemoteAddr()`
4. 丢弃空白值、`unknown`、非法地址
5. 使用标准库规范化 IPv4 / IPv6
6. 将 IPv4-mapped IPv6 折叠为 IPv4
7. 生成 IP 版本与 SHA-256 哈希

### 6.6 真实 IP 契约

生产环境必须要求 Nginx 覆盖请求头：

```nginx
proxy_set_header X-Real-IP $remote_addr;
proxy_set_header X-Forwarded-For $remote_addr;
```

后端读取优先级固定为：

1. `X-Real-IP`
2. `X-Forwarded-For`
3. `request.getRemoteAddr()`

这比“直接盲信客户端自带 X-Forwarded-For 首值”更安全。

---

## 七、详情链路接入方案

### 7.1 接入点

接入在：

- `WorkService.getWorksByIdService(HttpServletRequest request, String workId)`

### 7.2 接入时机

保持现有主流程顺序：

1. 查作品
2. 校验作品状态
3. 查询封面并签名
4. 组装 `liked_by_current_user`
5. **调用 `workViewService.recordViewIfNeeded(request, work)`**
6. 返回详情响应

浏览量写入只发生在成功详情路径内。

另外，`WorkService` 作为详情聚合层也应将浏览量记录视为 best-effort：即使浏览量服务意外抛出未吞掉的运行时异常，详情服务也要兜底 catch 并继续返回作品。

### 7.3 作者排除规则

仅排除：

- `currentUserUuid.equals(work.getAuthor())`

本次不额外排除 `uploader`，除非其与作者相同。

---

## 八、事务与失败降级策略

### 8.1 小事务边界

`WorkViewService` 内部需要一个真正生效的小事务，包住：

1. 插入 `work_view_log`
2. `work.view_count + 1`

这样可以保证：

- 要么两步都成功
- 要么两步都回滚

实现上不能采用“同类内部 `@Transactional` 自调用”的方式，因为 Spring 代理不会拦截 self-invocation。推荐采用以下任一安全方案：

1. `TransactionTemplate`
2. 独立的事务协作类（例如 `WorkViewWriteService`）

本次优先推荐 `TransactionTemplate`，因为额外边界更少、实现更直接。

### 8.2 重复访问

若插入触发唯一键冲突：

- 视为正常重复访问
- 不更新 `work.view_count`
- 不向外抛异常

### 8.3 其他失败

若浏览量服务内部发生非重复异常：

- 回滚本次浏览量事务
- 记录 error 日志
- 不影响 `GET /api/work/only` 正常返回作品详情

### 8.4 为什么不做补偿

第一版不引入：

- 异步队列
- 重试任务
- 失败补偿表

原因是当前项目形态与本次需求复杂度不匹配，先确保主链路稳定与行为可验证更重要。

---

## 九、测试与验证策略

建议采用单元测试优先的 TDD 方式覆盖：

1. 匿名首次访问成功入账
2. 同匿名同日重复访问不再入账
3. 匿名次日再次访问重新入账
4. 登录首次访问成功入账
5. 同登录用户同日重复访问不再入账
6. 匿名后登录同日再次访问可再入账
7. 作者本人登录访问不入账
8. 匿名访问拿不到合法 IP 时跳过
9. 非公开作品不入账
10. 浏览量写入异常不影响详情接口返回
11. IPv4 / IPv6 / IPv4-mapped IPv6 能被规范化到稳定 dedupe key

为了可靠覆盖“跨日”行为，浏览量服务需要注入可控时间源（例如 `Clock`），而不是直接读取系统当前时间。

---

## 十、实施边界总结

本次实现只影响 BarcBackend 中作品详情链路与新增浏览明细表，不扩散到前台其他页面、不改管理端接口、不追加看板 API。最终目标是：在不破坏现有详情行为与响应结构的前提下，让作品获得**可控、可审计、可保留历史**的浏览量能力。
