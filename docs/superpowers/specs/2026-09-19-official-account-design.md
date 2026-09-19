# 官方账号（业务指定账号）— 勘探与方案（已拍板，待落码）

> 日期：2026-09-19
> 状态：**全部歧义已拍板（Q1~Q8 / D1~D4 / 顺带①~⑥ / 新派生①~⑤），具备开工条件**（本阶段只落本文档，不 commit；开工后按 AGENTS §13 只 commit 不 push）
> 说明：本文所有「程序事实」均标注到文件与行号；数据库事实来自**只读**探测（仅 `SELECT` / `SHOW`），探测目标为**开发库 `ba`**，未做任何写入，也未连接生产库 `ba-prod`。

## 拍板摘要

**主干（Q1~Q8）**

| # | 结论 |
|---|---|
| Q1 | 官方账号**只做「绑定已有账号」**，模块不提供代建；生产需先自行注册账号 |
| Q2 | 未绑定时**直接报错阻断写入**，绝不回落旧常量、绝不自动兜底到某个账号 |
| Q3 | **不做上线数据迁移**（生产库为空库）；开发库历史数据不处理 |
| Q4 | 绑定对象**不限制身份/权限/登录态**，它本质就是被赋予官方身份的普通账号 |
| Q5 | 副作用**完全保持现状**（通知邮件照发、「我的作品」照常） |
| Q6 | 页面权限为**馆长 `ADVANCED_ADMINISTRATOR`(32)，1匹配** |
| Q7 | **一个业务仅可绑定一个账号** |
| Q8 | 业务键由**后端枚举固定**，管理端只显示可绑定项 |

**默认项（D1~D4）**

| # | 结论 |
|---|---|
| D1 | **只能更换绑定，不提供解绑** → 接口由 4 个减为 3 个（无 `unbind`） |
| D2 | **一个账号只能绑定一个业务** → `user_uuid` 加唯一索引 |
| D3 | 列表返回**全部业务项**（含未绑定），未绑定项在页面强提示「未配置」 |
| D4 | **不加外键**，悬空绑定由写链路与页面双重兜底 |

**顺带项（①~⑥）**

| # | 结论 |
|---|---|
| ① | 管理端 `author_display` 的裸 UUID 兜底 —— **本期不动** |
| ② | `/user/query/all_by_page` 公开问题 —— **本期不动** |
| ③ | `search-user` / `search-work` 无权限注解 —— **本期一并补齐**（见 4.10） |
| ④ | 「蔚蓝收录助手」文案 —— **本期一并订正**（见第六章清单） |
| ⑤ | `work.author` 其它写入入口 —— **不排查** |
| ⑥ | `schema.sql` 已退役仍在仓库 —— **保持现状** |

**新派生（①~⑤）**

| # | 结论 |
|---|---|
| ① | **换绑时把该业务的未认领作品一并迁到新账号**（精确 `is_claim=0 AND author=旧UUID`，同一事务） |
| ② | 写链路**同时校验绑定账号仍存在**，否则等同未配置并报错阻断 |
| ③ | `search-work` 与 `search-user` **一并补齐**权限校验 |
| ④ | 注解取值：`identity=MANAGER`、`targetPermission=DISCIPLINARY_COMMITTEE(1)`、`isHasElseUpper=false`（比大小） |
| ⑤ | 业务展示名取 **「未认领收录作品临时归属」**（与账号昵称解耦；指具体账号时用其真实昵称「蔚蓝收录认领助手」） |

---

## 一、核心结论（一句话）

**不是"缺一个配置页面"，而是"一条业务归属链路的落点被写死成了一个常量"：**

未认领收录作品的 `author` 由 `WorkAttributionConst.COLLECTION_ASSISTANT_UUID`（`BarcBackend/src/main/java/com/miaoyu/barc/api/work/constant/WorkAttributionConst.java:9`）写死为 `707B0FBF6AAA35B788069B07AEFEA12B`，全项目 **main 代码里只有 2 处写入点**（`WorkService.java:456`、`WorkManageService.java:378`）直接引用它。该常量**没有任何管理入口、没有兜底分支、没有配置载体**——数据库 36 张表里没有 `official/config/setting` 类表（只读探测结论），因此"换一个官方账号"当前**唯一的办法是改代码重新发版**。

而生产库是空库、账号不存在，于是这个 UUID 在生产的实际效果是：**用户端「暂归属」退化成「未知用户」**（`BarcFrontend/src/components/Work/WorkDetailComp.vue:263`），**管理端列表/审核队列里"暂归属"直接显示一串裸 UUID**（`mappers/work/WorkMapper.xml:312` 与 `:346` 的 `COALESCE(NULLIF(author_archive.nickname,''), w.author)`）。这正是本次要补的真实空缺。

---

## 二、程序事实（证据）

### 2.1 唯一硬编码点：一个常量，两处写入

| 位置 | 内容 |
|---|---|
| `WorkAttributionConst.java:4-9` | `public final class WorkAttributionConst`，唯一成员 `COLLECTION_ASSISTANT_UUID = "707B0FBF6AAA35B788069B07AEFEA12B"`；注释写作「未认领收录作品在平台内的暂时归属账号：蔚蓝收录助手」 |
| `WorkService.java:451-457` | 上传作品分支：`is_claim=true` → `author = 上传者`；`is_claim=false`（搬运收录）→ `uploader = 上传者`、`author = COLLECTION_ASSISTANT_UUID` |
| `WorkManageService.java:371-382` | `revokeClaim`（撤销认领）：先 `work.setIs_claim(false)`（:377），再 `work.setAuthor(COLLECTION_ASSISTANT_UUID)`（:378），随后 `workMapper.update` |

全仓 `[0-9A-F]{32}` 扫描（`BarcBackend/src/main`）：**仅命中这一处**（`WorkAttributionConst.java:9`）。即"平台业务指定账号"目前全项目只有这一个，且是硬编码。

引用面全集（12 处匹配）：

| 文件 | 行 | 性质 |
|---|---|---|
| `api/work/service/WorkService.java` | 11、456 | 生产写入点 1 |
| `api/work/service/WorkManageService.java` | 13、378 | 生产写入点 2 |
| `api/work/constant/WorkAttributionConst.java` | 4、5、9 | 定义 |
| `src/test/java/.../WorkAttributionServiceTest.java` | 3、31、35、37、40 | 测试引用 |

> 结论：**替换成本极低**——main 侧只有 2 个调用点，测试侧 1 个文件。

### 2.2 归属字段的语义边界（已有文档约束）

`docs/作品上传审核.md:13-21` 已明确三字段不可互相替代：

- `author`：平台内当前归属账号 UUID。自创作品保存上传作者；**未认领的收录作品保存「蔚蓝收录助手」**；认领通过后改为认领用户。
- `uploader`：执行收录操作的平台用户 UUID，只用于收录作品。
- `author_nickname`：站外原作者署名，可能没有平台账号。

同一语义也写在 `WorkAttributionService.java:9-12`（类注释）、`WorkEditDetailDto.java:20`、`WorkService.java:401` 的行内注释里。**这些注释的措辞需按第六章订正。**

### 2.3 `author` 的全部消费点（影响面清单）

| 位置 | 用途 | 换成"可配置账号"后是否受影响 |
|---|---|---|
| `WorkAttributionService.java:19-21` | 把 `author` 解析为平台归属昵称（`/edit-detail` 的 `author_display`），查不到时返回「未知用户」 | 否，按 UUID 解析，天然支持任意账号 |
| `mappers/work/WorkMapper.xml:312`、`:346` | 管理端列表/审核队列 `author_display`，**查不到时回落裸 UUID** | 否（顺带① 拍板本期不动） |
| `mappers/work/WorkMapper.xml:323-324`、`:355-356` | 管理端关键词检索：`is_claim=1` 才用 `author_archive.nickname`，`is_claim=0` 用 `author_nickname` | 否 |
| `WorkMapper.java:87-91` `selectByUsername` | `JOIN user_basic ON w.author = ub.uuid`（INNER JOIN） | **是**：账号悬空时该查询查不到作品（生产现状）；绑定真实账号后恢复正常 |
| `WorkMapper.java:68-72` `selectByUuid`、`WorkMapper.xml:201-212` `selectPublicByUuidWithFilters` | 「某账号的作品」列表 | 否，但绑定后该账号会看到全部未认领作品（Q5 拍板：保持现状） |
| `WorkService.java:265`、`:366-368`、`WorkImageService.java:81`、`WorkImageController.java:55`、`WorkCategoryService.java:31`、`WorkCategoryMapper.java:17` | 作品属主判定（作者本人能力） | 否，但**绑定到可登录账号后，该账号即获得这些未认领作品的全部作者权限**（Q4 拍板：不限制） |
| `WorkViewService.java:80` | 作者本人浏览不计入浏览量 | 否 |
| `WorkManageService.java:479-487` `notifyAuthor` | 封禁/下架/删除时向 `work.getAuthor()` 的邮箱发通知 | **是**：绑定真实账号后会把全部未认领作品的管理通知发到该账号邮箱（Q5 拍板：保持现状） |
| `WorkLikeMapper.java:24` | 喜欢列表带出 `w.author` | 否 |
| `WorkMapper.java:114-121` | 插入/更新 `author` 字段 | 否（值来源改为可配置） |

> 关键事实：**`work.author` 没有外键**（只读探测 `information_schema.key_column_usage` 中 `referenced_table_name='user_archive'` 为空；`SHOW CREATE TABLE work` 仅有 `PRIMARY KEY` + `idx_creator` + `idx_status`）。所以写入一个不存在的 UUID **不会报错**——脏归属是静默产生的，这也解释了技术债为什么能存活至今。

### 2.4 数据库事实（只读探测开发库 `ba`，MySQL 8.0.35，2026-09-19）

| 探测项 | 结果 |
|---|---|
| `user_archive` 中 `707B0FBF6AAA35B788069B07AEFEA12B` | **存在 1 条** |
| 该账号画像 | 昵称 `蔚蓝收录认领助手`；`identity=MANAGER`；`permission=1`（风纪委员）；`avatar` 有值 |
| 关联 `user_basic` | `username=workslibrary`、`email=claim@barc.work`、`email_verified=1`、`safe_level=100`、`created_at=2025-05-10 00:32:53` |
| `work` 总数 / 未认领 / 已认领 | 70 / 29 / 41 |
| `author = 旧常量` 的作品 | **15 篇，全部 `is_claim=0`**（其中 14 篇 `uploader` 为真实用户，1 篇 `status=BAN`） |
| `is_claim=0` 但 `author ≠ 旧常量` | **14 篇**（`author` 是真实用户：`D0703C90…`×9、`5523E5C8…`×5，多为 2025-05 的历史数据） |
| 悬空 `author`（`author` 在 `user_archive` 中不存在） | **0**（开发库账号齐全） |
| 表数量 / Flyway 版本 | 36 张 / 最高 `V11`（`fix new year index`，2026-09-15 应用，`success=1`） |
| `official/config/setting` 类表 | **0 张** |

由此得出两条重要推论：

1. **开发库有账号、生产库没有** —— 这正是用户描述的现象；`707B…` 在生产是**悬空 UUID**（无 FK、无报错、静默降级）。
2. **`is_claim=0` 与 `author=助手账号` 在开发库并不等价**（29 条未认领里只有 15 条指向助手账号）。因此任何"数据订正"都必须用 `is_claim=0 AND author=旧常量` **双条件**锁定；本期的换绑迁移同样只认这个双条件，**不会误伤**这 14 条错位数据。

### 2.5 管理端 V2 现状

- 菜单由路由 `meta` 驱动，构建点在 `src/app/components/AppSidebar.vue`（`groupOrder` 排序见 `:42`）。
- 「用户」分组现有 3 项（`src/app/router/routes.ts:46-97`）：`用户列表`（menuOrder 10）、`权限调整`（20）、`用户投诉`（30），`menuGroup: 'users'`、`menuGroupLabel: '用户'`、`groupOrder: 20`。
- 路由权限判定：`src/app/stores/permissions.ts:97-109`，`minManagerPermissionBit` 走 `hasManagerPermissionBit`（`:34-39`，`(permission & bit) !== 0`，即宪法 §18 的**1匹配**）；未写 `minManagerPermissionBit` 时才回落 `>=` 比大小。
- 权限常量：`src/shared/constants/permissions.ts:3-10`，`ADVANCED_ADMINISTRATOR = 1 << 5`（馆长）。
- **全仓 grep `official|OFFICIAL|官方` = 零匹配**：管理端与后端都没有任何"官方账号"既存实现。

### 2.6 可复用资产，以及两个管理口的权限缺口

| 资产 | 位置 | 复用方式 |
|---|---|---|
| 用户关键词搜索 SQL | `UserArchiveMapper.java:106-112` `searchByKeyword`（UUID/username/nickname 三路 `OR`，`LIMIT 15`） | 新模块直接调用，接口自建 |
| 前端"远程搜索选人"交互 | `BarcManageFrontendV2/src/modules/works/views/ClaimsListView.vue:9-24`、`:89-93`（`el-select` + `remote` + `filterable`） | 直接照搬写法 |
| 枚举驱动的配置项模块 | `background/enumeration/BackgroundModuleEnum.java:22-61`（`code`/`label` + `options()` 供管理端下拉） | 新业务键枚举照此形态 |
| 完整"列表 + 维护 + 权限切面"竖切范本 | 后端 `festival` 包；前端 `modules/system/views/FestivalsListView.vue` + `modules/system/api/festivals.service.ts` | 页面骨架参照它 |
| 新增表规范 | `docs/AI开发指南.md:362-383`：**必须走 Flyway**，下一个版本号是 **`V12`** | 迁移文件按此写 |

**权限缺口（顺带③，本期一并补齐）：**

| 位置 | 现状 |
|---|---|
| `WorkManageController.java:138-143` `GET /api/work/manage/search-user` | 方法上**没有任何 `@RequireUserAndPermissionAnno`**，只有 `AuthInterceptor` 的登录门槛（`WebConfig.java:35` 覆盖 `/api/**`） |
| `WorkManageController.java:132-136` `GET /api/work/manage/search-work` | 同上 |

同文件其余管理接口都通过 Service 层的 `@RequireUserAndPermissionAnno` 做了鉴权，只有这两个辅助搜索接口是裸的——因为 AOP 切面（`aspect/RequireUserAndPermissionAspect.java:28-34`）要求**被校验的 `uuid` 必须是方法第 `uuidIndex`（默认 0）个形参**，而这两个 Controller 方法的第一个形参是 `keyword`，所以注解无处可挂。修法见 4.10。

---

## 三、问题定性

### 3.1 过耦合的三个层面

1. **常量耦合**：业务语义（"未认领作品的临时归属"）被固化成一个编译期常量，改配置必须改代码。
2. **归属与业务耦合**：`author` 既是"平台内的账号 UUID"，又被当成"某个业务环节的占位符"；一旦业务要指定别的账号，归属链路无法表达。
3. **缺失配置载体**：既无表、无接口、无页面，**连"当前绑定的是谁"都无处可查**。

### 3.2 生产症状（可验证）

| 端 | 位置 | 表现 |
|---|---|---|
| 用户端作品详情 | `BarcFrontend/src/components/Work/WorkDetailComp.vue:262-266` | `暂归属：{{authorArchive?.nickname \|\| '未知用户'}}`，账号不存在 → **「暂归属：未知用户」** |
| 用户端作品列表 | `BarcFrontend/src/components/Work/WorkItemListComp.vue:24-42` | 仅在 `is_claim=true` 时反查昵称；未认领项沿用 `author_nickname`（站外署名），本身不受影响 |
| 管理端作品详情 | `BarcManageFrontendV2/.../WorkDetailComp.vue:20-24` | 走 `/api/work/manage/edit-detail` → `WorkManageService.java:231` → `WorkAttributionService.java:20` 兜底「未知用户」 |
| 管理端作品列表/审核队列 | `mappers/work/WorkMapper.xml:312`、`:346` | `COALESCE(NULLIF(nickname,''), w.author)` → **直接显示裸 UUID `707B0FBF…`** |
| 按用户名查作品 | `WorkMapper.java:87-91` | INNER JOIN `user_basic`，账号不存在 → 该账号作品查不出来 |

> 同一份数据在管理端两条链路上兜底文案不一致（一条「未知用户」、一条裸 UUID）。顺带① 拍板**本期不动**，此处仅作记录。

---

## 四、方案设计

### 4.1 数据模型（Flyway `V12`）

新增 `BarcBackend/src/main/resources/db/migration/V12__add_official_account.sql`：

```sql
-- 官方业务指定账号（管理端「用户 → 官方账号」维护）
--
-- 设计说明：
-- 1、business_key 为主键：一个业务只允许绑定一个账号（Q7）；
-- 2、user_uuid 加唯一索引：一个账号只能绑定一个业务（D2）；
-- 3、user_uuid NOT NULL：本模块不提供「解绑」，只能「更换」（D1）；
--    「无记录」与「未绑定」是同一状态，首次绑定前该状态才存在；
-- 4、不加外键（D4）：账号被删除时不因 RESTRICT 卡住既有账号链路；
--    悬空绑定由写链路的 JOIN 校验（见 4.6）与页面提示双重兜底；
-- 5、updated_by 记录最后操作的管理员 UUID（沿用 AGENTS §6.2「用 uuid 关联用户」）。
CREATE TABLE IF NOT EXISTS official_account (
    business_key VARCHAR(64) PRIMARY KEY NOT NULL COMMENT '业务键，取 OfficialAccountEnum.name()',
    user_uuid    VARCHAR(32) NOT NULL COMMENT '被指定的官方账号 UUID（user_archive.uuid）',
    remark       VARCHAR(255) NULL COMMENT '绑定备注',
    updated_by   VARCHAR(32) NULL COMMENT '最后操作的管理员 UUID',
    created_at   TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at   TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_official_account_user (user_uuid),
    INDEX idx_official_account_updated_by (updated_by)
);
```

字段命名遵循宪法 §6.2：主键非用户表用语义键（`business_key`），关联用户统一用 `uuid` 值（`user_uuid` / `updated_by`）。

### 4.2 业务键枚举（后端固定）

新增 `BarcBackend/src/main/java/com/miaoyu/barc/user/enumeration/OfficialAccountEnum.java`，形态照抄 `BackgroundModuleEnum`：

```java
/**
 * 官方业务指定账号的业务键。
 * displayName 是「业务名」，与具体绑定账号的昵称解耦（新派生⑤）：
 * 例如业务名「未认领收录作品临时归属」当前绑定到昵称为「蔚蓝收录认领助手」的账号。
 */
public enum OfficialAccountEnum {
    /** 未认领收录作品的临时归属账号：搬运收录上传、管理员撤销认领时写入 work.author。 */
    COLLECTION_ASSISTANT("未认领收录作品临时归属",
            "未认领收录作品的临时归属账号；搬运收录上传与管理员撤销认领时，作品 author 临时指向该账号。");
    // 未来新增业务：在此追加枚举值即可，管理端列表自动出现，无需改表。
}
```

- `name()` 作为 `business_key` 落库值；
- `displayName` / `description` 供管理端展示；
- 提供 `fromName(String)` 做入参合法性校验，非法业务键直接拒绝。

> **落码调整**：`options()` 未实现——列表接口需要「业务项 × 绑定状态」的合并结果，
> 服务层直接遍历 `values()` 更直接，避免多一个只被用一次的 DTO。

> Q8 拍板：新增业务**必须改代码发版**，换取类型安全 + 后端调用点可静态引用。

### 4.3 后端模块与接口

新增文件（`com.miaoyu.barc.user` 包，与用户体系同域）：

| 文件 | 职责 |
|---|---|
| `user/model/OfficialAccountModel.java` | 表映射模型 |
| `user/model/vo/OfficialAccountItemVo.java` | 列表返回项（业务名 + 绑定状态 + 绑定账号信息 + 迁移提示） |
| `user/mapper/OfficialAccountMapper.java` | `selectAll` / `selectAllWithAccount`（列表联表）/ `selectByBusinessKey` / `selectBoundUuid`（JOIN 校验版）/ `countByUserUuid` / `upsert` / **`migrateUnclaimedAuthor`** |
| `user/service/OfficialAccountService.java` | 列表合并、绑定校验、换绑事务、**`findBoundUuid(OfficialAccountEnum)`** |
| `user/controller/OfficialAccountController.java` | 3 个管理端接口（下表） |

接口（路径沿用 `user` 包既有风格；`/user/**` 已被 `AuthInterceptor` 覆盖，见 `WebConfig.java:32-42`）：

| 方法 | 路径 | 入参 | 说明 |
|---|---|---|---|
| `GET` | `/user/official/list` | `uuid` | 返回**全部**业务项（D3）：`business_key`、`display_name`、`description`、`bound`、`user_uuid`、`nickname`、`username`、`avatar`、`updated_at`、`updated_by_nickname` |
| `POST` | `/user/official/bind` | `uuid`、`business_key`、`user_uuid`、`remark?` | 首次绑定 / 更换绑定（D1，无解绑）；更换时同事务迁移未认领作品（新派生①）；返回 `migrated_works` 数量 |
| `GET` | `/user/official/search_user` | `uuid`、`keyword` | 账号选择器数据源，复用 `UserArchiveMapper.searchByKeyword`（`:106-112`） |

**`OfficialAccountService` 的核心契约（供写链路调用）：**

```java
/**
 * 读取某业务被指定的官方账号 UUID。
 * 未绑定（无记录）或绑定账号已被删除时返回 null —— 调用方必须据此阻断写操作并提示管理员，
 * 不允许回落到任何硬编码 UUID（Q2），也不允许写入悬空 UUID（新派生②）。
 */
public String findBoundUuid(OfficialAccountEnum business)
```

实现走一条 SQL（见 4.6），不做缓存：每次写操作一次主键级查询，成本可忽略，且天然避免绑定变更后的缓存不一致。

### 4.4 换绑语义（新派生①）

`POST /user/official/bind` 在一个 `@Transactional` 内按序执行：

1. 校验 `business_key` 合法（`OfficialAccountEnum.fromName`），非法 → `ErrorR`；
2. 校验目标账号存在（`userArchiveMapper.selectByUuid(userUuid) != null`），不存在 → `UserR().noSuchUser()`；
3. 校验该账号未被**其它**业务绑定（D2 唯一索引 + 显式查询给出友好提示，避免直接抛 SQL 约束异常）；
4. 读取旧绑定（`selectByBusinessKey`）；
5. `upsert` 新绑定；
6. **若存在旧绑定且 `oldUuid != newUuid`**：
   ```sql
   UPDATE work SET author = #{newUuid}
   WHERE is_claim = 0 AND author = #{oldUuid}
   ```
   受影响行数即 `migrated_works`，随接口返回并在管理端提示。

**为什么迁移条件必须带 `is_claim = 0`**：`is_claim = 1` 的作品 author 是真实认领人，任何情况下都不能动；而 `is_claim = 0 AND author = 旧绑定UUID` 精确等价于"该业务此前写入的作品"——因为能写入绑定 UUID 的路径**只有** 2.1 列出的那两处。这个双条件同时**不会误伤**开发库那 14 条 `is_claim=0` 但 author 为真实用户的错位数据（见 2.4）。

**与 Q3 的区别（避免自相矛盾）**：Q3 的"不做数据迁移"指的是**上线时**把 `author = 707B…` 的存量数据迁到新账号（生产空库，无此需求）；这里的迁移是**运行期一致性维护**，属于"绑定语义"本身。

> 注意：换绑迁移 SQL（`migrateUnclaimedAuthor`）**收在 `OfficialAccountMapper` 而不是 `WorkMapper`**。
> 原因：本模块位于 `user` 包，而 `api.work` 已经依赖 `user`（WorkService / WorkManageService 都注入 user 下的 Mapper）；
> 若把这条 UPDATE 放进 `WorkMapper`，就会形成 `user ↔ api.work` 的包级双向依赖。放在本模块自己的 Mapper 里，
> 依赖方向始终是 `api.work → user` 单向。该方法**只允许**被 `OfficialAccountService` 调用。

### 4.5 调用点改造（替换硬编码常量）

| 文件:行 | 现状 | 改造后 |
|---|---|---|
| `WorkService.java:456` | `requestModel.setAuthor(WorkAttributionConst.COLLECTION_ASSISTANT_UUID)` | 先 `findBoundUuid(COLLECTION_ASSISTANT)`；为 `null` → `return ResponseEntity.ok(new ErrorR().normal("官方账号未配置：…"))`；否则 `setAuthor(boundUuid)` |
| `WorkManageService.java:378` | `work.setAuthor(WorkAttributionConst.COLLECTION_ASSISTANT_UUID)` | **把解析提前到 `work.setIs_claim(false)`（:377）之前**；`null` 则直接返回错误、不落库；否则 `setAuthor(boundUuid)` |
| `WorkAttributionConst.java` | 常量定义 | **整文件删除**（生产空库、无历史依赖；保留只会给后人重新写死的入口） |
| `WorkAttributionServiceTest.java:3,31,35,37,40` | 引用常量 | 改为测试内自定义 UUID 字面量，与生产常量解耦 |

`uploadWorkService`（`WorkService.java:433-457`）是 `@Transactional`，未绑定分支发生在任何写库动作（`workMapper.insert` 在 `:464`）**之前**，直接 return 不产生脏写，无需回滚处理。

### 4.6 悬空绑定防护（新派生②）

`OfficialAccountMapper` 的读取 SQL 直接 JOIN 账号表，一条 SQL 同时保证"已绑定 + 账号存在"：

```java
/**
 * 读取业务绑定的官方账号 UUID。
 * JOIN user_archive：绑定行存在但账号已被删除时返回 null —— 在调用方语义中等同「未配置」，
 * 从而保证 work.author 永远不会写入悬空 UUID。
 */
@Select("SELECT oa.user_uuid FROM official_account oa " +
        "JOIN user_archive ua ON ua.uuid = oa.user_uuid " +
        "WHERE oa.business_key = #{business_key}")
String selectBoundUuid(@Param("business_key") String businessKey);
```

因此 `findBoundUuid` 返回 `null` 有且仅有两种成因，统一按 Q2 报错阻断：**①业务从未绑定 ②绑定账号已被删除**。管理端列表页对这两种成因给出不同的提示文案（「未配置」/「绑定账号已不存在」）。

### 4.7 「未绑定即阻断」的用户可见文案（必须同步处理，否则提示会退化）

**已探明的真实陷阱**：`ErrorR.normal("…")`（`response/ErrorR.java:6-12`）把人类可读文案放在 **`data`**，`msg` 固定为 `"失败：Error"`。

- 用户端上传页 `BarcFrontend/src/components/Upload/UploadWorkComp.vue:302-307` 读的是 **`data.msg`** → 直接上线会让作者看到 **「失败：Error」**，完全不知道要去找管理员。
- 管理端 `BarcManageFrontendV2/src/shared/api/http.ts:44` 用 `payload.msg || payload.data`，同样 `msg` 优先；但 `ClaimsListView.vue:73-76` 自己 `catch` 后统一弹「操作失败」，影响较小。
- 仓库中**已有正确写法可抄**：`BarcFrontend/src/utils/manageWorkStatusActions.ts:81` 使用 `data.data || data.msg || '再次提审失败'`。

改造项（1 行级）：把 `UploadWorkComp.vue:306` 改为 `infoMessage(data.data || data.msg)`，使「官方账号未配置」如实透出。

### 4.8 管理端页面与菜单

新增：

| 文件 | 职责 |
|---|---|
| `src/shared/types/officialAccount.ts` | `OfficialAccountItem` / `BindOfficialAccountPayload` / `BindOfficialAccountResult`（含 `migrated_works`） |
| `src/modules/users/api/officialAccount.service.ts` | 3 个接口封装 |
| `src/modules/users/views/OfficialAccountsView.vue` | 列表页：每个业务一行，展示**业务名 + 说明 + 当前绑定账号（头像/昵称/@username）+ 更新时间**；未绑定行红色强提示；操作列只有「绑定 / 更换」 |
| `src/modules/users/__tests__/OfficialAccountsView.spec.ts` | 页面测试（≥90% 覆盖要求） |

修改 `src/app/router/routes.ts`，插入到「用户投诉」之后：

```ts
{
  path: 'users/official-accounts',
  name: 'users-official-accounts',
  component: OfficialAccountsView,
  meta: {
    title: '官方账号',
    subtitle: '指定各官方业务在平台内使用的账号；未配置时对应业务写链路会被阻断。',
    requiresAuth: true,
    requiresManager: true,
    // 1匹配鉴权：仅馆长（ADVANCED_ADMINISTRATOR）权限位可访问（Q6）
    minManagerPermissionBit: MANAGER_PERMISSION.ADVANCED_ADMINISTRATOR,
    menuLabel: '官方账号',
    menuGroup: 'users',
    menuGroupLabel: '用户',
    groupOrder: 20,
    menuOrder: 40,
  },
}
```

交互要点：

- 账号选择器照抄 `ClaimsListView.vue:9-24 / :89-93`（`el-select` + `remote` + `filterable`），数据源换成 `/user/official/search_user`；
- 已绑定业务的按钮文案为**「更换」**，无「解绑」（D1）；
- 点「更换」先弹二次确认，文案说明**将有一批未认领作品被重新归属**；成功后若 `migrated_works > 0`，提示「已绑定，并重新归属 N 篇未认领作品」。

### 4.9 权限（1匹配）

后端 3 个接口统一注解：

```java
@RequireUserAndPermissionAnno({
    @RequireUserAndPermissionAnno.Check(
        identity = UserIdentityEnum.MANAGER,
        targetPermission = PermissionConst.ADVANCED_ADMINISTRATOR,
        isHasElseUpper = true,          // 1匹配（宪法 §18.2 ②）
        isSuchElseRequire = false)      // 执行完整身份+权限校验
})
```

要点（依据 `aspect/RequireUserAndPermissionAspect.java:28-52`）：

- `uuidIndex` 默认 `0`，因此**服务层方法第 1 个形参必须是操作者 `uuid`**；
- 身份不匹配或权限位不含 `ADVANCED_ADMINISTRATOR` → 返回 `UserR().insufficientAccountPermission()`；
- 前端 `minManagerPermissionBit` 与后端 `isHasElseUpper = true` **语义必须成对**，两侧都是 1匹配。

### 4.10 顺带③：补齐两个管理口的权限校验

`search-user` 与 `search-work` 当前裸奔，根因是注解无处可挂（`uuidIndex` 默认 0，而 Controller 方法第 1 形参是 `keyword`）。修法是把校验下沉到 Service 层，与同文件其它接口保持一致：

| 现状 | 改造后 |
|---|---|
| `WorkManageController.java:132-136` → `workManageService.searchWorks(keyword)`（`WorkManageService.java:457-460`） | Controller 传 `uuid(r)`；Service 改为 `searchWorks(String uuid, String keyword)` 并加注解（新派生④：`identity=MANAGER`、`targetPermission=PermissionConst.DISCIPLINARY_COMMITTEE`、`isHasElseUpper=false` 比大小、`isSuchElseRequire=false`），等价于**是管理者即可** |
| `WorkManageController.java:138-143` → `workManageService.searchUsers(keyword)`（`WorkManageService.java:462-465`） | 同上 |

新模块的 `/user/official/search_user` 直接按 4.9 的馆长 1匹配注解实现，不复用这两个接口。

**影响面**：`search-user` / `search-work` 的调用方是管理端页面 `ClaimsListView.vue:83-93`（指派作者页签，路由 `works-claims` 的准入权限是 `SEC_MAINTAINER` 位）。收紧为"是管理者即可"（`permission >= 1`）**不会影响任何现有管理端页面**；仅会拦掉 `identity != MANAGER` 的普通登录用户。

---

## 五、影响面与兼容性

### 5.1 双端兼容性

| 检查项 | 结论 |
|---|---|
| 是否修改既有 API 返回结构 | **否**。本期新增 3 个接口（均挂 `/user/official/*`），既有接口零字段增删 |
| 是否新增必填字段 | 否 |
| 是否删除返回字段 | 否 |
| 是否修改错误码 | 否（沿用 `ErrorR()` / `UserR()`） |
| 是否新增权限门槛 | **是**，2 处：`search-user` / `search-work` 由"仅需登录"变为"需 MANAGER + 比大小(>=1)"。已核对其唯一调用方是管理端 `ClaimsListView.vue`，准入权限为 `SEC_MAINTAINER`，不受影响 |
| 前端文案修正 | `UploadWorkComp.vue:306` 的 1 行改动，是错误提示正确透出的前提（见 4.7） |

### 5.2 行为变更清单（必须写进变更说明）

| # | 变更 | 触发条件 |
|---|---|---|
| 1 | 搬运收录上传在**未配置官方账号**时由"静默写死 UUID"变为**报错阻断** | `WorkService.java:456` |
| 2 | 撤销认领在**未配置官方账号**时由"静默写死 UUID"变为**报错阻断** | `WorkManageService.java:378` |
| 3 | 绑定账号被删除后，上述两条链路同样进入阻断状态 | 4.6 的 JOIN 校验 |
| 4 | **更换绑定会批量改写该业务未认领作品的 `author`**（`is_claim=0 AND author=旧UUID`） | 4.4 |
| 5 | `search-user` / `search-work` 新增 MANAGER 身份与权限门槛 | 4.10 |

### 5.3 拍板保留的副作用（明确不改）

| 副作用 | 位置 | 结论 |
|---|---|---|
| 封禁/下架/删除通知邮件会发给被绑定的官方账号邮箱 | `WorkManageService.java:479-487` | 保持现状（Q5） |
| 官方账号登录后「我的作品」包含全部未认领作品 | `WorkMapper.xml:201-212`、`WorkController.java:110` | 保持现状（Q5） |
| 官方账号即获得未认领作品的作者全部能力（编辑、改封面、图集、分类） | `WorkService.java:366-368`、`WorkImageService.java:81`、`WorkCategoryService.java:31` | 保持现状（Q4） |
| 官方账号浏览自己"名下"作品不计入浏览量 | `WorkViewService.java:80` | 保持现状 |

### 5.4 明确不改动的部分（边界）

- 不改 BarcFrontend 的归属展示逻辑（`WorkDetailComp.vue` / `WorkItemListComp.vue`）——绑定账号后它们天然正确。
- 不改 `WorkAttributionService`（按 UUID 解析，与账号是否官方无关）。
- 不加「官方账号」标记字段（`user_archive` 不加 `is_official`）——官方身份**只由绑定关系表达**，避免账号表与配置表双写不一致。
- 不改 `WorkMapper.xml:312/:346` 的裸 UUID 兜底（顺带① 本期不动）。
- 不改 `/user/query/all_by_page` 的 `@IgnoreAuth`（顺带② 本期不动）。
- 不做上线数据迁移（Q3）。

---

## 六、文案与注释订正清单（顺带④，本期一并执行）

订正原则：**描述链路语义时**说"官方业务账号（管理端「用户 → 官方账号」可配置）"；**指向具体账号时**用其真实昵称「蔚蓝收录认领助手」。

| 位置 | 现状 | 订正为 |
|---|---|---|
| `WorkAttributionConst.java:8` | 注释「蔚蓝收录助手」 | 该文件本期删除，无需改 |
| `WorkService.java:401` | `// author 始终是平台内归属账号；未认领作品会解析为蔚蓝收录助手，而不是站外原作者署名。` | `// author 始终是平台内归属账号；未认领作品会解析为当前绑定的官方账号（OfficialAccountEnum.COLLECTION_ASSISTANT），而不是站外原作者署名。` |
| `WorkEditDetailDto.java:20` | `// author 对应的平台当前归属账号昵称；未认领时为蔚蓝收录助手` | `// author 对应的平台当前归属账号昵称；未认领时为绑定官方账号的昵称，账号缺失时为「未知用户」` |
| `docs/作品上传审核.md:17` | 「未认领的收录作品保存"蔚蓝收录助手"」 | 「未认领的收录作品保存当前绑定的官方账号 UUID（管理端「用户 → 官方账号」配置）」 |
| `docs/作品上传审核.md:21` | 「应分别展示"暂归属：蔚蓝收录助手"」 | 「应分别展示"暂归属：<当前绑定官方账号的昵称>"」 |
| `WorkAttributionServiceTest.java:36` | `assistant.setNickname("蔚蓝收录助手")` | 改为测试自有字面量（与生产常量解耦） |
| `BarcManageFrontendV2/.../WorkDetailComp.spec.ts:109` | `detail.author_display = '蔚蓝收录助手'` | 改为中性的测试昵称 |
| `BarcManageFrontendV2/.../workAttribution.spec.ts:22` | `author_display: '蔚蓝收录助手'` | 改为中性的测试昵称 |
| `BarcFrontend/.../UpdateAccountComp.vue:220` | 「名下所有作品将划归"蔚蓝收录助手"账号下」 | 该「删除账号」链路**后端尚未实现**（全仓无删除入口），文案改为「划归官方账号名下」以免误导 |

以上 8 处（含删除文件）即为全仓 `蔚蓝收录助手` 的全部命中，出自 grep 实测，无遗漏。

---

## 七、测试与验证计划

**后端**（`src/test/java`，按包结构；覆盖率 ≥90%）

1. `OfficialAccountServiceTest`：列表合并（枚举 × 绑定状态，含未绑定项）、首次绑定、**更换绑定并断言 `migrateUnclaimedAuthor` 被以 `(旧UUID, 新UUID)` 调用**、绑定不存在的账号被拒、绑定已被其它业务占用的账号被拒、非法业务键被拒、`findBoundUuid` 在"无记录"与"账号已删除"两种情形下均返回 `null`。
2. `OfficialAccountControllerTest`：3 个接口的馆长 1匹配权限（馆长通过 / 副馆长被拒 / USER 被拒 / 用户不存在）。
3. `WorkService` / `WorkManageService` 改造点：**未绑定时返回错误且 `verify(workMapper, never()).insert/update(...)`**；已绑定时 `author` 等于绑定账号 UUID。
4. `WorkManageServiceTest` 新增：`searchUsers` / `searchWorks` 的权限注解生效（MANAGER 通过、USER 被拒）。
5. 移除 `WorkAttributionServiceTest` 对已删除常量的引用后，确保其仍通过。

**前端**

6. `OfficialAccountsView.spec.ts`：列表渲染（含未绑定强提示）、首次绑定调用参数、更换前二次确认文案、`migrated_works > 0` 时的提示语、接口失败提示。

**构建验证（AGENTS §8）**

7. `BarcBackend`：`./mvnw clean package`
8. `BarcManageFrontendV2`：`npm run build`（顺带 `npx vue-tsc --noEmit`）
9. `BarcFrontend`：`npm run build`（因改了 `UploadWorkComp.vue`）

---

## 八、上线顺序（生产为空库，必须按此顺序）

1. 发版（含 `V12` 迁移）→ 启动即建 `official_account` 表；
2. 先在生产按既有注册流程创建官方账号（Q1：本期不做代建）；
3. 馆长登录管理端「用户 → 官方账号」，把该账号绑定到「未认领收录作品临时归属」业务；
4. **在此之前**，搬运收录上传与撤销认领会返回「官方账号未配置」——这是设计预期（Q2），不是故障；
5. 全链路复核：上传一篇搬运收录作品 → 管理端详情与用户端详情「暂归属」均显示该账号昵称 → 撤销认领后仍指向该账号；
6. 换绑复核（可在开发库验证）：更换绑定后确认旧绑定账号名下的未认领作品已全部转到新账号，且已认领作品未被改动。

> **与《数据库初始化指南》§4.7 的关系**：该节的判定标准是「业务上缺它是否跑不通」（`docs/数据库初始化指南.md:234-236`，由 `fcbd64a` 确立）。按此标准 `official_account` 属 **必需** 表——**结构**由 `V12` 迁移自动创建，但**数据**（绑定记录）没有默认值，缺失会导致搬运收录上传与撤销认领被阻断。因此 §4.7 需新增一行登记本表，与 `category` 的"必需"同级并列。
5. 全链路复核：上传一篇搬运收录作品 → 管理端详情与用户端详情「暂归属」均显示该账号昵称 → 撤销认领后仍指向该账号；
6. 换绑复核（可在开发库验证）：更换绑定后确认旧绑定账号名下的未认领作品已全部转到新账号，且已认领作品未被改动。

---

## 九、拍板记录

| 组 | # | 问题 | 结论 |
|---|---|---|---|
| Q | 1 | 官方账号从哪来 | 只做「绑定已有账号」，不代建 |
| Q | 2 | 未绑定时的兜底 | 直接报错阻断写入 |
| Q | 3 | 上线数据迁移 | 不做（生产空库；开发库数据不处理） |
| Q | 4 | 绑定对象是否限制身份/权限/登录态 | 不限制 |
| Q | 5 | 副作用是否隔离 | 完全保持现状 |
| Q | 6 | 页面权限位 | 馆长 `ADVANCED_ADMINISTRATOR`(32)，1匹配 |
| Q | 7 | 绑定基数 | 一个业务仅可绑定一个账号 |
| Q | 8 | 业务键来源 | 后端枚举固定 |
| D | 1 | 解绑能力 | 只能更换，不能解绑 |
| D | 2 | 账号复用 | 一个账号只能绑一个业务 |
| D | 3 | 未绑定业务展示 | 全部展示并强提示未配置 |
| D | 4 | 外键 | 不加外键 |
| 顺带 | ① | 裸 UUID 兜底 | 本期不动 |
| 顺带 | ② | `/user/query/all_by_page` 公开 | 本期不动 |
| 顺带 | ③ | `search-user` 权限 | 本期补齐（并一并补 `search-work`） |
| 顺带 | ④ | 「蔚蓝收录助手」文案 | 本期一并订正（清单见第六章） |
| 顺带 | ⑤ | `author` 其它写入入口排查 | 不排查 |
| 顺带 | ⑥ | `schema.sql` | 保持现状 |
| 派生 | ① | 换绑后历史未认领作品 | 一并迁到新账号（精确 `is_claim=0 AND author=旧UUID`） |
| 派生 | ② | 悬空绑定防护 | 写链路 JOIN 校验账号存在 |
| 派生 | ③ | `search-work` 是否一并 | 一并补齐 |
| 派生 | ④ | 注解取值 | `MANAGER` + `DISCIPLINARY_COMMITTEE(1)` + 比大小 |
| 派生 | ⑤ | 业务展示名 | 「未认领收录作品临时归属」 |

---

## 十、顺带发现（本期未纳入，供后续单独决策）

| # | 发现 | 位置 | 影响 |
|---|---|---|---|
| 1 | 管理端 `author_display` 兜底是**裸 UUID**，与详情的「未知用户」不一致 | `mappers/work/WorkMapper.xml:312`、`:346` | 顺带① 已拍板本期不动 |
| 2 | 用户列表分页接口是**公开**的（`@IgnoreAuth`），任何人可拉全量用户分页 | `UserQueryController.java:28-32` | 顺带② 已拍板本期不动 |
| 3 | 同一分页链路里**硬编码了库名前缀**：`LEFT JOIN ba.user_basic b` | `UserArchiveMapper.java:19`（`selectByPage`，被 `UserService.java:52` 在"无筛选条件"分支调用） | **新发现，未拍板**。开发库与生产库**同实例不同库名**（`ba` / `ba-prod`），该 SQL 在生产会把 `user_basic` 指到**开发库**，属跨库读取；同时一旦实例上不存在 `ba` 库会直接报错 |
| 4 | 「删除账号」文案已存在但后端无任何删除入口 | `BarcFrontend/.../UpdateAccountComp.vue:216-225`；`user` 包内 grep `delete` 仅命中验证码删除 | 文案本期顺手改成中性表述（第六章），功能未实现 |
| 5 | `work.author` 无外键、写链路无兜底 | 只读探测 `SHOW CREATE TABLE work` | 顺带⑤ 已拍板不排查；本期堵住了唯一已知入口 |
| 6 | `schema.sql` 已退役但仍在仓库，且与真实结构不等价 | `application.yaml:28-32`、`docs/AI开发指南.md:364-365` | 顺带⑥ 保持现状 |

---

## 十一、落码任务清单

| 动作 | 文件 |
|---|---|
| 新增 | `BarcBackend/src/main/resources/db/migration/V12__add_official_account.sql` |
| 新增 | `BarcBackend/src/main/java/com/miaoyu/barc/user/enumeration/OfficialAccountEnum.java` |
| 新增 | `BarcBackend/src/main/java/com/miaoyu/barc/user/model/OfficialAccountModel.java` |
| 新增 | `BarcBackend/src/main/java/com/miaoyu/barc/user/model/vo/OfficialAccountItemVo.java` |
| 新增 | `BarcBackend/src/main/java/com/miaoyu/barc/user/mapper/OfficialAccountMapper.java`（含换绑迁移 SQL） |
| 新增 | `BarcBackend/src/main/java/com/miaoyu/barc/user/service/OfficialAccountService.java` |
| 新增 | `BarcBackend/src/main/java/com/miaoyu/barc/user/controller/OfficialAccountController.java` |
| 新增 | `BarcBackend/src/test/java/com/miaoyu/barc/user/service/OfficialAccountServiceTest.java` |
| 修改 | `BarcBackend/.../api/work/mapper/WorkMapper.java`（**未改动**：换绑迁移改放 `OfficialAccountMapper`，见 4.4 说明） |
| 修改 | `BarcBackend/.../api/work/service/WorkService.java`（`:401` 注释、`:456` 绑定解析） |
| 修改 | `BarcBackend/.../api/work/service/WorkManageService.java`（`:377-378` 顺序调整 + 绑定解析；`:457-465` 两个搜索下沉鉴权） |
| 修改 | `BarcBackend/.../api/work/controller/WorkManageController.java`（`:132-143` 传 `uuid(r)`） |
| 修改 | `BarcBackend/.../api/work/model/WorkEditDetailDto.java`（`:20` 注释） |
| 修改 | `BarcBackend/src/test/java/.../WorkAttributionServiceTest.java`（去常量引用 + 改测试字面量） |
| 删除 | `BarcBackend/src/main/java/com/miaoyu/barc/api/work/constant/WorkAttributionConst.java` |
| 新增 | `BarcManageFrontendV2/src/shared/types/officialAccount.ts` |
| 新增 | `BarcManageFrontendV2/src/modules/users/api/officialAccount.service.ts` |
| 新增 | `BarcManageFrontendV2/src/modules/users/views/OfficialAccountsView.vue` |
| 新增 | `BarcManageFrontendV2/src/modules/users/__tests__/OfficialAccountsView.spec.ts` |
| 修改 | `BarcManageFrontendV2/src/app/router/routes.ts`（新增 `users/official-accounts`） |
| 修改 | `BarcFrontend/src/components/Upload/UploadWorkComp.vue`（`:306` 错误文案透出） |
| 修改 | `BarcFrontend/src/components/Update/UpdateAccountComp.vue`（`:220` 文案） |
| 修改 | `docs/作品上传审核.md`（`:17`、`:21`） |
| 修改 | `docs/数据库初始化指南.md`（§4.7 新增一行：`official_account` —— **必需**，管理端「用户 → 官方账号」维护；缺失会导致搬运收录上传与撤销认领被阻断） |
| 修改 | `docs/功能文档.md`、`docs/结构文档.md`（用户分组新增页面；`author` 来源改为可配置官方账号） |
