# 作品详情页点赞能力设计文档

> **日期**: 2026-06-04
> **状态**: 待用户审核
> **范围**: BarcBackend + BarcFrontend
> **兼容目标**: BarcManageFrontendV2 继续正常显示 `like_count`

---

## 一、需求概述

本次需求仅实现作品详情页的点赞基础能力，不扩展到“我喜欢的作品”页面。

### 1.1 本次要做

1. 在用户前端作品详情页增加可点击心形
2. 未登录用户点击时仅提示“请先登录”
3. 已登录用户对同一作品只能有 0/1 个点赞状态
4. 再次点击时执行取消点赞
5. 点赞关系写入数据库，供后续“我喜欢的作品”能力复用
6. 作品总点赞数继续通过 `work.like_count` 提供给前台与管理端
7. 作品详情页需要知道“当前用户是否已点赞”

### 1.2 本次明确不做

1. 不实现“我喜欢的作品”页面、入口、列表接口
2. 不保留点赞历史，取消点赞后直接删除关系记录
3. 不改作品列表页、账号页的点赞交互
4. 不改管理端页面交互
5. 不做登录弹窗或点击后跳转登录页
6. 不做点赞通知、审计日志、推荐算法等扩展能力

---

## 二、现状与约束

### 2.1 已确认的前端结构

- 路由入口：`BarcFrontend/src/router/WorkRouter.ts`
- 详情页组件：`BarcFrontend/src/components/Work/WorkDetailComp.vue`
- 当前详情页已展示 `work.like_count`，但仅为静态展示，无点击逻辑
- 当前登录态来自 `localStorage` 中的 token + `UserPinia`
- 当前前端风格偏保守：请求成功后再更新界面，不采用明显的乐观更新模式

### 2.2 已确认的后端结构

- 详情接口：`GET /api/work/only?work_id=...`
- 详情服务：`WorkService.getWorksByIdService(String workId)`
- 作品表已有聚合字段：`work.like_count`
- 当前不存在“用户-作品点赞关系表”
- MySQL + Spring Boot + MyBatis + Flyway

### 2.3 已确认的兼容性约束

- 管理端 V2 当前直接展示 `detail.work.like_count`
- 旧管理端已弃用，但本次响应结构仍应保持向后兼容
- 因此不能移除或改变 `work.like_count` 的语义

### 2.4 关键技术约束

当前 `AuthInterceptor` 的行为是：

- 带有 `@IgnoreAuth` 的接口直接放行
- 放行后不会自动解析 `Authorization`，也不会写入 `request.setAttribute("uuid", ...)`

这意味着：

- `GET /api/work/only` 作为公开接口必须继续保留 `@IgnoreAuth`
- 如果要在该接口中补充 `liked_by_current_user`，不能依赖拦截器注入的 `uuid`
- 需要在 Controller/Service 内部手动读取请求头中的 `Authorization`，并通过 `JwtService` 做“可选解析”

该做法的好处是：

- 不改全局认证流程
- 不影响其他公开接口
- 将改动边界锁定在作品详情链路中

### 2.5 当前 token 传递现实

虽然项目文档中写有 `Token {jwt}` 格式说明，但当前实际代码链路是：

- 登录后前端直接把返回值原样存入 `localStorage`
- 前端请求头直接发送 `Authorization: token`
- `AuthInterceptor` 直接把请求头值传给 `JwtService.jwtParser(token)`

因此本次点赞能力必须遵循**当前真实运行链路**：

- `Authorization` 请求头传递**原始 jwt 字符串**
- 本次不顺手改全局 token 前缀协议
- 本次不引入 `Token ` 前缀剥离兼容逻辑，避免扩大改动面

---

## 三、总体设计结论

本次采用以下方案：

1. 新增独立点赞关系表 `work_like`
2. 保留 `work.like_count` 作为聚合计数字段
3. 新增点赞切换接口 `POST /api/work/like/toggle`
4. 在 `GET /api/work/only` 的响应中附加 `liked_by_current_user`
5. 前端仅改 `WorkDetailComp.vue`，不扩散到其他页面

这是当前改动面最小、未来扩展性最好、对管理端兼容性最稳的方案。

---

## 四、数据库设计

### 4.1 新增表 `work_like`

建议结构：

```sql
CREATE TABLE IF NOT EXISTS work_like (
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
```

### 4.2 设计理由

`work_like` 的职责仅有两个：

1. 表示某个用户当前是否喜欢某部作品
2. 为未来“我喜欢的作品”列表提供查询基础

取消点赞时直接删除记录，不保留历史痕迹，符合本次确认的 **A 方案：只保留当前仍然喜欢的作品**。

### 4.3 迁移策略

本次数据库改动采用“增量新增，不动旧字段语义”的方式：

1. 新增 Flyway migration：`V4__add_work_like_table.sql`
2. 同步更新 `src/main/resources/schema.sql`
3. 不做历史数据回填
4. 保持现有 `work.like_count` 原值不动

---

## 五、后端设计

### 5.1 新增模型与分层

建议新增以下后端文件：

- `api/work/model/WorkLikeModel.java`
- `api/work/mapper/WorkLikeMapper.java`
- `api/work/service/WorkLikeService.java`
- `api/work/controller/WorkLikeController.java`

### 5.2 `WorkLikeModel` 字段

- `id`
- `work_id`
- `user_uuid`
- `created_at`

命名保持与现有项目风格一致，延续 snake_case 字段名。

### 5.3 `WorkLikeMapper` 能力建议

建议至少包含：

1. `selectByWorkIdAndUserUuid(workId, userUuid)`
2. `insert(model)`
3. `deleteByWorkIdAndUserUuid(workId, userUuid)`
4. `countByWorkId(workId)`（可选，仅兜底校验或后续使用）

同时给 `WorkMapper` 增加**专用计数更新方法**，不要复用 `update(WorkModel)`：

1. `incrementLikeCount(workId)`
2. `decrementLikeCount(workId)`

原因：

- `workMapper.update(work)` 是整行更新
- 如果拿旧 `WorkModel` 回写，存在覆盖其他字段的风险
- 点赞计数应使用最小粒度 SQL，避免把无关字段带入变更

### 5.4 新增点赞切换接口

建议新增独立 Controller，路径前缀：`/api/work/like`

#### 接口定义

```http
POST /api/work/like/toggle
Authorization: {jwt}
Content-Type: application/json

Body:
{
  "work_id": "xxx"
}
```

后端入参对象明确使用 DTO，而不是临时 `Map`：

```java
public class WorkLikeToggleDto {
    private String work_id;
}
```

#### 返回定义

建议使用 `ResourceR` 返回对象，而不是 `ChangeR`。

返回数据：

```json
{
  "liked": true,
  "like_count": 12
}
```

含义：

- `liked = true`：当前请求执行后，用户处于已点赞状态
- `liked = false`：当前请求执行后，用户处于未点赞状态

#### 行为规则

1. 未登录或 token 无效：由现有拦截器拦截
2. 作品不存在：在 Controller/Service 内显式校验 `workMapper.selectById(workId)`，不存在时返回资源不存在或业务错误
3. 若当前用户未点赞：
   - 插入 `work_like`
   - `work.like_count + 1`
4. 若当前用户已点赞：
   - 删除 `work_like`
   - `work.like_count - 1`
5. 返回最新状态和最新计数

### 5.5 点赞切换事务策略

`toggle` 必须在同一事务内完成，推荐顺序：

#### 点赞路径

1. 查询当前关系是否存在
2. 不存在则写入 `work_like`
3. 仅当写入成功时执行 `incrementLikeCount`
4. 再次读取或计算当前结果并返回

#### 取消点赞路径

1. 查询当前关系是否存在
2. 存在则删除 `work_like`
3. 仅当删除成功时执行 `decrementLikeCount`
4. 再次读取或计算当前结果并返回

#### 并发兜底

为避免重复请求造成脏计数：

- 数据库唯一键 `uk_work_like_user` 作为最终兜底
- 如果并发导致插入唯一键冲突，Service 应捕获异常并重新读取当前状态返回
- `decrementLikeCount` 的 SQL 需要避免把计数写成负数

建议使用类似逻辑：

```sql
UPDATE work
SET like_count = CASE WHEN like_count > 0 THEN like_count - 1 ELSE 0 END
WHERE id = #{workId}
```

### 5.6 作品详情接口增强

当前接口保持不变：

```http
GET /api/work/only?work_id=xxx
```

但返回的作品数据中新增一个附加字段：

- `liked_by_current_user: boolean`

#### 关键实现方式

因为该接口是 `@IgnoreAuth`，所以不能依赖 `request.getAttribute("uuid")`。

建议将方法签名调整为：

```java
@SuchWorkAnno(selectType = "id", index = 1)
public ResponseEntity<J> getWorkOnlyControl(HttpServletRequest request, @RequestParam("work_id") String workId)
```

如果不希望依赖参数下标，也可以移除该注解并在方法内部显式校验作品是否存在；但无论采用哪种写法，都必须避免把 `HttpServletRequest` 误当作 `work_id` 传给 `SuchWorkAspect`。

流程：

1. 继续按现有逻辑查询作品详情
2. 手动读取 `Authorization` 请求头
3. 如果请求头为空：按匿名用户处理，`liked_by_current_user = false`
4. 如果请求头存在：使用 `JwtService.jwtParser(token)` 尝试解析
5. 解析成功：查询 `work_like` 是否存在
6. 解析失败：降级为匿名用户，不阻断详情访问

#### 这样设计的原因

- 详情页必须继续支持未登录访问
- 过期 token 不应导致公开作品详情无法打开
- 仅在有合法 token 时附加用户态字段，风险最小

### 5.7 `WorkModel` 扩展

建议在 `WorkModel` 中新增：

```java
private Boolean liked_by_current_user;
```

理由：

- 与现有 `view_count`、`like_count` 风格一致
- 详情接口可以直接返回统一对象
- 其他使用 `WorkModel` 的接口即使不赋值，该字段也只是 `null`，属于新增字段，不会破坏旧客户端

---

## 六、前端设计

### 6.1 修改范围

本次前端只改以下文件：

- `BarcFrontend/src/components/Work/WorkDetailComp.vue`
- `BarcFrontend/src/interfaces/WorkImpl.ts`
- 建议新增：`BarcFrontend/src/utils/workLikeApi.ts`

### 6.2 `WorkImpl` 扩展

在现有接口中新增可选字段：

```ts
liked_by_current_user?: boolean;
```

保持可选，避免其他读取 `WorkImpl` 的地方立即被迫联动修改。

### 6.3 详情页数据流调整

当前 `fetchWork(work_id)` 只调用：

```ts
GET /api/work/only
```

本次调整为：

1. 读取 `localStorage` 中 token
2. 若 token 存在，则对 `GET /api/work/only` 增加 `Authorization` 请求头
3. 若 token 不存在，则按当前匿名请求方式获取详情
4. 从详情响应中读取：
   - `like_count`
   - `liked_by_current_user`

### 6.4 心形交互设计

详情信息区当前已有静态节点：

```vue
<div class="like_box item"><HeartOutlined />{{work.like_count}}</div>
```

本次将其改为可点击交互节点。

建议新增前端状态：

- `likePending = ref(false)`：控制重复点击

点击规则：

1. 若无 token：
   - 调用 `infoMessage` 或 `errorMessage`
   - 文案固定为“请先登录”
   - 不跳转，不打开登录弹层
2. 若有 token 且 `likePending = false`：
   - 调用 `POST /api/work/like/toggle`
   - 请求中禁点心形
3. 请求成功后：
   - 用返回值更新 `work.like_count`
   - 用返回值更新 `work.liked_by_current_user`
4. 请求失败：
   - 保持当前界面状态不变
   - 只提示失败信息
5. 请求结束：
   - `likePending = false`

### 6.5 UI 表现建议

建议在保持现有 `HeartOutlined` 视觉风格的基础上增加“已点赞态”样式，做法有两种：

1. 继续使用 `HeartOutlined`，通过 class 改色
2. 已点赞态切换为 `HeartFilled`

本次推荐第 2 种，因为状态更直观，且只涉及详情页一个点位。

已点赞态建议：

- 心形填充
- 背景色加深
- hover 仍保留，但不做额外动画扩展

### 6.6 为什么不采用乐观更新

本次不推荐先本地加减 `like_count` 再回滚，原因：

- 当前项目整体风格偏服务端返回后更新
- 本次是数据库落库能力，优先保证状态一致性
- 乐观更新会增加回滚和重复点击处理复杂度

因此本次采用**保守更新策略**：以后端返回为准。

---

## 七、管理端兼容策略

本次不主动改管理端页面。

### 7.1 保证不破坏的点

1. `work.like_count` 继续存在，且语义不变
2. 管理端现有作品列表/详情继续只读该字段
3. 后端新增字段属于附加字段，不要求管理端消费

### 7.2 兼容边界

- 活跃管理端：`BarcManageFrontendV2`
- 旧管理端：`BarcManageFrontend` 已弃用，不作为主动适配目标

但由于本次接口改动采用“新增字段、不删旧字段”，旧管理端理论上也不会因响应结构变化而损坏。

---

## 八、异常处理与边界行为

### 8.1 未登录用户

- 作品详情仍可浏览
- 点心形时仅提示“请先登录”
- 不跳转、不弹登录层

### 8.2 token 失效或解析失败

#### 详情接口

- 不拦截访问
- 按匿名用户处理
- `liked_by_current_user = false`

#### toggle 接口

- 由认证拦截器拦截为未登录/未授权

### 8.3 作品不可访问

沿用当前 `WorkService.getWorksByIdService` 逻辑：

- PRIVATE → 私有作品无法访问
- BAN → 作品已被封禁
- OFF → 作品已被下架
- DELETED → 作品已被删除

点赞能力不改变这些原有判定。

### 8.4 刷新与重进页面

用户刷新详情页后：

- `like_count` 由后端重新返回
- `liked_by_current_user` 由后端重新判断

因此不会因本地状态缓存而失真。

---

## 九、实现范围清单

### 9.1 后端新增

1. `WorkLikeModel.java`
2. `WorkLikeMapper.java`
3. `WorkLikeService.java`
4. `WorkLikeController.java`
5. `V4__add_work_like_table.sql`

### 9.2 后端修改

1. `WorkModel.java` 增加 `liked_by_current_user`
2. `WorkMapper.java` 增加 `incrementLikeCount/decrementLikeCount`
3. `WorkController.java` 的 `getWorkOnlyControl(...)` 支持可选 token 解析
4. `WorkService.java` 的详情能力接入点赞状态组装
5. `schema.sql` 增加 `work_like` 表定义

### 9.3 前端新增

1. `utils/workLikeApi.ts`（建议）

### 9.4 前端修改

1. `components/Work/WorkDetailComp.vue`
2. `interfaces/WorkImpl.ts`

---

## 十、测试与验证清单

### 10.1 数据库层

- [ ] Flyway migration 可执行
- [ ] `schema.sql` 包含 `work_like`
- [ ] 唯一键 `(work_id, user_uuid)` 生效
- [ ] 外键删除联动正常

### 10.2 后端接口层

- [ ] 未登录访问 `/api/work/only` 成功，`liked_by_current_user = false`
- [ ] 登录访问未点赞作品详情时返回 `liked_by_current_user = false`
- [ ] 登录访问已点赞作品详情时返回 `liked_by_current_user = true`
- [ ] `POST /api/work/like/toggle` 首次调用后返回 `liked = true`
- [ ] 再次调用后返回 `liked = false`
- [ ] 记录操作前原始 `like_count`
- [ ] 首次点赞后 `like_count = 原值 + 1`
- [ ] 再次取消后 `like_count = 原值`
- [ ] 不要求 `work.like_count == COUNT(work_like)`，因为历史聚合值不做回填

### 10.3 前端交互层

- [ ] 未登录点击心形，仅提示“请先登录”
- [ ] 登录点击后心形状态更新正确
- [ ] 点赞数更新正确
- [ ] 请求中无法连续狂点
- [ ] 刷新页面后状态仍正确

### 10.4 并发与重复请求验证

- [ ] 前端快速连续点击时，请求中禁点生效
- [ ] 对同一用户重复发送点赞请求时，不会出现重复关系记录
- [ ] 重复/并发请求后，`like_count` 不会被写成负数或异常增长

### 10.5 管理端兼容层

- [ ] `BarcManageFrontendV2` 作品列表继续正常显示 `like_count`
- [ ] `BarcManageFrontendV2` 作品详情继续正常显示 `like_count`
- [ ] 未因后端新增字段导致页面报错

### 10.6 构建验证

- [ ] `BarcFrontend` 构建通过
- [ ] `BarcManageFrontendV2` 构建通过
- [ ] `BarcBackend` Maven 构建通过

---

## 十一、风险与回滚思路

### 11.1 主要风险

1. 重复点击导致并发计数不一致
2. 详情接口的可选 token 解析影响公开访问稳定性
3. 不小心复用了整行 `update(work)` 导致覆盖无关字段

### 11.2 风险控制措施

1. 前端增加 `likePending` 防抖
2. 后端使用事务 + 唯一键 + 专用计数 SQL
3. 详情接口解析 token 失败时降级为匿名，不阻断访问
4. 点赞计数只用专用 SQL，不走全量 `update`

### 11.3 回滚思路

若上线后发现点赞功能异常：

1. 前端可立即隐藏或禁用详情页心形点击逻辑
2. 后端可保留新表但停止调用新接口
3. 因为本次是增量新增，不会影响已有作品读取与管理端 `like_count` 展示

---

## 十二、最终结论

本次设计以“详情页最小闭环”为目标：

- 数据库新增 `work_like`
- 后端新增 `toggle` 能力
- 详情接口附加 `liked_by_current_user`
- 前端仅改详情页心形交互
- 管理端零功能改动、仅做兼容验证

该方案满足当前业务需求，同时为后续“我喜欢的作品”能力保留了最直接的数据基础。
