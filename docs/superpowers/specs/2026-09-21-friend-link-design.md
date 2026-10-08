# 友情链接（管理端「系统 → 友情链接」+ 用户端首页展示）— 勘探与方案（已拍板，待落码）

> 日期：2026-09-21
> 状态：**Q1~Q9 已全部拍板，落码与三端验证均已完成**（见第九章「落码记录」）；已本地 `git commit`，**未 push**
> 范围：`BarcBackend`（新增 `friendlink` 包 + Flyway `V13`）、`BarcManageFrontendV2`（新增页面与菜单项）、`BarcFrontend`（首页友链组件接入数据）、`docs/`（功能文档 / 结构文档 / 数据库初始化指南登记）
> 说明：本文所有「程序事实」都标注到 `文件:行号`；凡属推断的地方单独标注为「推断」，不与事实混写

---

## 拍板摘要（2026-09-21）

| # | 问题 | 拍板结论 |
|---|---|---|
| Q1 | 用户端是否接入 | **接入**：后端提供公开接口，用户端首页 `HomeFriendshipComp.vue` 渲染友链卡片 |
| Q2 | 页面与接口权限 | **馆长 `ADVANCED_ADMINISTRATOR`(32)，1匹配**（与同组「背景图设置」「节日管理」完全一致） |
| Q3 | 顺序交互 | **上移 / 下移按钮**，**不做批量排序接口**（一次移动 = 一次请求） |
| Q4 | 旧 logo 清理 | **交 GC 队列异步清理**（`cosGcService.enqueue`），与背景图删除链路一致 |
| Q5 | 链接与简介校验 | 链接：**仅要求 `http://` / `https://` 前缀 + 长度上限**；简介：可空 + 长度上限；不做域名真实性探测 |
| Q6 | 上传接口形态 | **独立上传接口** `POST /api/friend-link/upload` 返回 objectKey，随表单提交 |
| Q7 | 文档落点 | **spec 设计文档 + `功能文档.md` + `结构文档.md`** 同步登记 |
| Q8 | 卡片打开方式 | 用户端 **新标签页打开** `target="_blank"` + `rel="noopener noreferrer"` |
| Q9 | 上传与落库的先后顺序 | **方案 A「先传后落」**（独立上传接口 → 提交 key → 落库），与背景图 / 学园/部团/学生三图同构；**不采用「先落库再上传回填」**；孤儿对象由 GC 入队 + 后续巡检兜底治理（见 3.8） |

**拍板派生出来的三条设计结论（本文档负责固化）**

1. **Q3「不做批量排序」改变了排序接口的形态**：背景图用的是 `PUT /reorder` 全量序号（`BackgroundImageMapper.java:43-44`），而本次一次移动只影响两条记录，因此改为**单条移动接口** `PUT /api/friend-link/{id}/move?direction=up|down`，由后端在同一事务内交换相邻两条的 `sort_order`。**接口名不叫 `sort`**，避免与「批量重排序」混淆。
2. **Q3 同时决定了下标语义**：`sort_order` 是**全表连续序号（从 1 开始）**，插入时取 `MAX(sort_order) + 1`（沿用 `BackgroundImageMapper.java:40-41` 的做法）；删除后**留下空洞、不做归一化**——排序次序仍然正确，归一化只会多一次全表写。
3. **Q6 决定 logo 在友链表里是「先传后存」的普通字符串**：`logo_key` 允许为 NULL（用户明确「logo 非必填」），表单提交的是上传接口返回的 objectKey；**数据库只存 key，返回时由 `ImageUrlResolver.resolve` 签名拼成完整 URL**（宪法 §12、`ImageUrlResolver.java:15-20`）。

---

## 一、核心结论（先看这段）

1. **这是一个「零新机制」的功能**：对象存储、签名 URL、异步 GC、管理端 meta 驱动菜单、1匹配馆长鉴权、Flyway 建表——六条链路**全部已有现成范本**，本模块是它们的组合，不需要任何新的基础设施。

   最直接的范本是**背景图模块**（`BarcBackend/src/main/java/com/miaoyu/barc/background/**`）：它同样「图片必传/可不传 + 数据库存 key + 返回签名 URL + 手动排序 + 删除走 GC」。差别只有三点：①背景图按 `module` 分池，友链只有一池；②背景图用批量 `reorder`，友链用单条 `move`（Q3）；③背景图有 `enabled` 开关与场景标签，友链本期不需要。

2. **用户端首页确实已经留好了位置，但没有任何数据来源**：`HomeContainerComp.vue:30-32` 把 `HomeFriendshipComp` 放在第三块（`.c3`），而 `HomeFriendshipComp.vue:5-12` **只渲染了一个标题**「友情链接&特别感谢」，`<script setup>` 是空的（`:1-3`），全仓 `友链|friend|FRIEND` 只命中这两处。
   也就是说，**用户端「友情链接」当前是一块纯装饰占位**；Q1 拍板接入后，这块占位才会有真实内容。

3. **logo 的存储路径规则来自用户明确指定，与背景图现有规则不同**——这一点必须写清楚，否则落码时会「顺手照抄背景图」而写错：

   | 模块 | 现有/指定规则 | 落库 key 形态 |
   |---|---|---|
   | 背景图（现有事实） | `client/bg/{module}/{uuid前8位}-{秒级时间戳}.{ext}`（`BackgroundImageService.java:97-101`，前缀常量在 `:39`） | `client/bg/home/1A2B3C4D-1758364800.png` |
   | **友链 logo（本次指定）** | **`/friendlink/logo/{图片名}_{uuid第一段}_{秒级时间戳}.扩展名`** | `friendlink/logo/cover_1234ABCD_1758364800.png` |

   > **COS key 不带前导斜杠**：用户描述中的「image 桶的 `/friendlink/logo/...`」指的是桶内路径层级（COS 控制台会以 `/` 开头展示），而 `PutObjectRequest` 的 key 参数不能以 `/` 开头（COS 会创建名为空的对象目录），因此落库与上传时统一写作 `friendlink/logo/...`（**无前导斜杠**）；签名 URL 里自然呈现为 `https://<bucket>.cos.<region>.myqcloud.com/friendlink/logo/...`。
   > 这一层解释同时适用于背景图：它的前缀常量是 `client/bg/` 而非 `/client/bg/`（`BackgroundImageService.java:39`）。

4. **「上移下移但不做批量接口」需要在文档层面钉死事务语义**：`move` 必须在**一个事务**内完成「读相邻两条 → 交换 `sort_order`」，否则两条 UPDATE 之间失败会出现两行同序号。边界（已在首位上移 / 已在末位下移）应**直接返回失败提示**而不是静默无操作，避免前端出现「点了没反应」的错觉。

5. **权限链路前后端必须成对**：后端 `isHasElseUpper = true`（1匹配）+ `targetPermission = ADVANCED_ADMINISTRATOR`，前端 `minManagerPermissionBit: MANAGER_PERMISSION.ADVANCED_ADMINISTRATOR`。这是宪法 §18.2 ② 的约定，也是第三章列出的 7 处历史偏差（`2026-09-20-manage-sidebar-group-collapse-design.md:237-249`）里**唯一不会踩坑的写法**——因为两边的语义都是「持有馆长位」。

---

## 二、现状链路（程序事实）

### 2.1 对象存储链路（后端，本次复用的核心）

| 事实 | 位置 |
|---|---|
| 指定完整 objectKey 上传（不做二次重命名，**本次用它**） | `utils/tencent/cos/CosService.java:92-115` |
| 自动命名上传（`key + 时间戳-文件名.扩展名`，**本次不用**） | `CosService.java:51-83` |
| 删除单个对象 | `CosService.java:122-133` |
| 生成预签名 URL（有效期由调用方给 `Date`） | `CosService.java:165-176` |
| 批量签名（`parallelStream`，本次不用：友链只有一张图） | `CosService.java:185-189` |
| 桶枚举：`test` / `avatar` / `image` / `file` | `CosBucketConfigEnum.java:11-14` |
| 桶名解析 | `CosService.java:36-43` |
| **key → 展示 URL 的统一解析器（1 小时有效期，已是 http 开头的直接返回）** | `ImageUrlResolver.java:15-20`；有效期常量 `:13` |
| 异步 GC 入队（写 `cos_garbage_key` 表，由调度任务清理） | `api/service/CosGcService.java:16-20` |
| 背景图删除时**正是**「入队 GC + 软删记录」的写法（本次照抄） | `background/service/BackgroundImageService.java:213-222` |

### 2.2 背景图模块（本次主要照抄范本）

| 事实 | 位置 |
|---|---|
| 路径前缀常量 `client/bg/` | `BackgroundImageService.java:39` |
| **自定义命名**：`uuid前8位 + "-" + 秒级时间戳 + 扩展名` | `BackgroundImageService.java:97-101` |
| 上传前校验：非空 / ≤1MB / `content-type` 以 `image/` 开头 | `:77-86`；上限常量 `:37` |
| 落库：`id = GenerateUUID().getUuid32u()`、`object_key`、`filename`、`sort_order = MAX+1`、`created_by` | `:108-118` |
| 列表查询后逐条签名：`model.setUrl(imageUrlResolver.resolve(object_key))` | `:296-298`、`:137` |
| 删除：GC 入队 + 软删 | `:213-222` |
| 排序：批量 `PUT /reorder`，接收 `List<OrderItem>{id, sort_order}` | `:147-157`；请求体类 `:300-305` |
| Mapper：`selectMaxSortOrder` / `updateSortOrder` / `softDelete` | `background/mapper/BackgroundImageMapper.java:40-44`、`:65-66` |
| 模型：`object_key` + 非数据库列 `url` | `background/model/BackgroundImageModel.java:13`、`:25-26` |
| 控制器：`/api/background` + `@RequestParam("file") MultipartFile` | `background/controller/BackgroundController.java:22-23`、`:36-45` |
| 公开接口：`GET /api/background/all` + `@IgnoreAuth` | `BackgroundController.java:95-100` |

### 2.3 节日模块（权限与 CRUD 形态范本）

| 事实 | 位置 |
|---|---|
| **馆长 1匹配鉴权注解**（`identity=MANAGER`、`targetPermission=ADVANCED_ADMINISTRATOR`、`isSuchElseRequire=false`、`isHasElseUpper=true`） | `festival/service/FestivalService.java:52-57`（列表）、`:72-77`（新增）、`:113-118`（修改） |
| 控制器路径 `/api/festival`，管理端统一 `/admin/*` | `festival/controller/FestivalController.java:26`、`:33`、`:39`、`:48`、`:69` |
| 公开接口 `GET /api/festival/all` + `@IgnoreAuth` | `FestivalController.java:82-86` |
| 强校验 + 返回 `new J(1, "错误文案", null)` | `FestivalService.java:80-96` |

### 2.4 鉴权与拦截的既有约束（必须遵守）

| 事实 | 位置 |
|---|---|
| 切面要求**被校验 uuid 是方法第 `uuidIndex`（默认 0）个形参** | `annotation/RequireUserAndPermissionAnno.java:23-30`；`aspect/RequireUserAndPermissionAspect.java:29-65` |
| **本项目绝大多数鉴权写在 Service 方法上**，Controller 只取 `request.getAttribute("uuid")` | `BackgroundController.java:31`；`FestivalController.java:35` |
| 拦截路径白名单含 `/api/**` → 「无鉴权注解」≠「免登录」，只有 `@IgnoreAuth` 才免登录 | `WebConfig.java:32-42`（类在 `com.miaoyu.barc` 根包，不在 `config` 包） |
| `has(your, target) = (your & target) != 0`（宪法 §18.2 ②「1匹配」） | `permission/ComparePermission.java:18-20` |
| MANAGER 权限常量：`ADVANCED_ADMINISTRATOR = 32` | `permission/PermissionConst.java:16-21` |

### 2.5 管理端菜单链路

| 事实 | 位置 |
|---|---|
| 菜单由 `adminChildren[].meta` 聚合，按 `menuGroup` 分组、`menuOrder` 组内排序、`groupOrder` 组间排序 | `BarcManageFrontendV2/src/app/components/AppSidebar.vue:59-94` |
| **「系统」组 = `menuGroup: 'system'` + `menuGroupLabel: '系统'` + `groupOrder: 60`** | `app/router/routes.ts:168-171`（学园）、`:362-365`（BUG 反馈）、`:380-383`、`:398-401` |
| 「系统」组现有 4 项，`menuOrder` 依次为 50 / 60 / 70 / 80 | `routes.ts:171`、`:365`、`:383`、`:401` |
| 相邻的「系统管理」组是 `menuGroup: 'sysmanage'` + `groupOrder: 70`（两个中文标签极易混淆） | `routes.ts:416-419`、`:434-437` |
| 前端权限判定：有 `minManagerPermissionBit` 时走 1匹配并**直接 return** | `app/stores/permissions.ts:97-109`（bit 分支 `:103-105`） |
| `RouteMeta` 类型声明（新增 meta 字段必须同步这里；本次**不需要**新增字段） | `shared/types/router.d.ts:6-20` |
| 管理端页面壳层 `RoutePageShell`（title / eyebrow / actions 插槽） | `app/components/RoutePageShell.vue`；用法见 `backgrounds/views/BackgroundsListView.vue:278-297` |
| 上移/下移的现成交互与样式 | `BackgroundsListView.vue:237-251`（`moveItem`）、`:415-437`（按钮组，首末位禁用） |
| 上传前本地校验 + `el-upload :http-request` 自定义上传 | `BackgroundsListView.vue:179-190`、`:288-296` |
| 删除二次确认（`ElMessageBox.confirm`） | `BackgroundsListView.vue:253-267` |
| http 信封解包：`code === 0` 返回 `data`，否则抛 `ApiError(msg \|\| data)` | `shared/api/http.ts:35-60` |

### 2.6 用户端首页链路

| 事实 | 位置 |
|---|---|
| 首页容器把友链组件放在 `.c3` 区块 | `BarcFrontend/src/components/Home/HomeContainerComp.vue:30-32` |
| **友链组件当前只有标题、无数据、`<script setup>` 为空** | `BarcFrontend/src/components/Home/HomeFriendshipComp.vue:1-12` |
| 全仓 `友链\|友情链接\|friend\|Friend\|FRIEND` 命中仅 2 个文件、6 行 | 上述两文件（grep 实测，无遗漏） |
| 用户端公开接口的既有消费写法（含失败兜底） | 参考 `HomeBgComp.vue` 对 `GET /api/background/all` 的调用（背景图模块，功能文档 §3.11） |
| **用户端 API 层落点**：`src/utils/*Api.ts`（**没有 `src/api/` 目录**）；用 `baseHttp` + 手动解包 `ResponseImpl`（`code === 0`），`catch` 兜底 | `BarcFrontend/src/utils/backgroundApi.ts:1-2`、`:46-61`、`:70-85`；`utils/https.ts` |
| 用户端测试落点：与源文件同级的 `__tests__/`（不是仓库根的 `tests/`） | `BarcFrontend/src/utils/__tests__/backgroundApi.spec.ts` |

### 2.7 数据库与迁移

| 事实 | 位置 |
|---|---|
| 迁移目录当前最大版本为 **`V12`** → 本次新增 **`V13`** | `BarcBackend/src/main/resources/db/migration/`（`V12__add_official_account.sql`） |
| 新表必须走 Flyway，`schema.sql` 已退役 | `docs/AI开发指南.md:362-383` |
| **已应用到任何环境的迁移不可再修改**（Flyway checksum） | `docs/AI开发指南.md:380-383` |
| 软删模式：`deleted_at` + `WHERE deleted_at IS NULL` | `background_image` 全链路（`V8__add_background_image_table.sql:11`、`BackgroundImageMapper.java:19`） |
| 表结构登记位置 | `docs/功能文档.md:649-676`（表清单）；`docs/数据库初始化指南.md:222-233`（§4.7 必需/可选判定） |

---

## 三、方案设计

### 3.1 数据模型（Flyway `V13`）

新增 `BarcBackend/src/main/resources/db/migration/V13__add_friend_link.sql`：

```sql
-- 友情链接（管理端「系统 → 友情链接」维护，用户端首页展示）
--
-- 设计说明：
-- 1、logo_key 允许 NULL（用户明确：仅「链接」与「标题」必填），语义是 COS 对象 key，不是 URL；
--    对外返回时经 ImageUrlResolver.resolve() 签名拼成完整 URL（字段名 logo_url），符合 AGENTS §12；
-- 2、url 为外部链接，落库前由服务层校验 http:// / https:// 前缀（拍板 Q5）；
-- 3、sort_order 为全表连续序号（从 1 开始，插入取 MAX+1），上移/下移交换相邻两条；
--    删除后不回填空洞 —— 次序仍正确，归一化只会多一次全表写（拍板 Q3 的派生结论）；
-- 4、enabled 本期无管理界面（不提供启用/停用），先落列以便后续扩展；
--    注意：用户端公开接口本期**不按 enabled 过滤**，与 background_image 的公开接口语义不同，
--    如需改为过滤，属于行为变更，必须单独拍板（见 §5.2 行为变更清单外的事项）。
-- 5、不加外键（与 background_image / festival 保持一致），created_by 记录创建人 UUID。
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
```

字段与用户需求的对应关系：

| 用户要求的字段 | 表列 | 必填 | 说明 |
|---|---|---|---|
| 链接 | `url` | ✅ | 外部链接，服务层校验前缀 |
| 标题 | `title` | ✅ | 长度上限 64 |
| logo | `logo_key` | ❌ | COS object key，返回时签名成 `logo_url` |
| 简介 | `intro` | ❌ | 长度上限 255 |
| （顺序） | `sort_order` | — | 由管理端上移/下移维护 |

> 主键命名遵循宪法 §6.2：友链属于「其他实体表」，主键用 `id`（值为 `GenerateUUID().getUuid32u()`，与 `background_image` / `festival` 一致），**不用 `uuid`**。

### 3.2 logo 命名规则与 key 生成（本次的差异点，务必按此实现）

用户指定的路径：**`/friendlink/logo/{图片名}_{uuid第一段}_{秒级时间戳}.扩展名`**（image 桶）。

落码规则（新增 `FriendLinkService` 内的私有方法 `buildLogoKey`）：

```
① 前缀：            "friendlink/logo/"                      // 无前导斜杠，见 §一.3
② 图片名：          originalFilename 去扩展名后清洗
                    - 去掉路径分隔符（/ \）与空白、控制字符
                    - 只保留字母 / 数字 / 下划线 / 连字符 / 点 / 中文
                    - 清洗后为空 → 回落为 "logo"；超长截断至 50 字符
③ uuid 第一段：     new GenerateUUID().getUuid36l().split("-")[0].toUpperCase()
                    // 例：36 位 UUID 的 "1A2B3C4D"（8 位），语义与「uuid 第一段」严格一致
④ 秒级时间戳：      System.currentTimeMillis() / 1000
⑤ 扩展名：          StringUtils.getFilenameExtension(originalFilename)
                    // 为空时不拼 "."（与 BackgroundImageService.java:101 的处理一致）
最终：              "friendlink/logo/" + 图片名 + "_" + uuid第一段 + "_" + 秒级时间戳 + (ext != null ? "." + ext : "")
```

示例：原始文件名 `My Cover!.png` → `friendlink/logo/MyCover_1A2B3C4D_1758364800.png`。

> **为什么用 `_` 而非 `-` 作分隔符**：用户给定的格式串里分隔符就是 `_`，且 `uuid第一段` 与 `秒级时间戳` 之间用 `_` 可以避免与「图片名里本身含 `-`」的情况混淆（本规则不改动图片名内部的 `-`）。
> **为什么"uuid 第一段"取连字符切分的第一段而不是 `substring(0, 8)`**：`GenerateUUID.getUuid36l()` 返回的是标准 36 位带连字符 UUID（`GenerateUUID.java:32-34`），它的第一段恒为 8 位十六进制。"uuid 第一段"是用户的原话，取 `split("-")[0]` 是这一说法的直接实现；虽然结果与 `substring(0, 8)` 等价，但表述与代码语义一致，不会让后人误以为取的是 32 位串的前 8 位。

### 3.3 后端模块与接口

新增文件（新包 `com.miaoyu.barc.friendlink`，与 `festival` / `background` 平级）：

| 文件 | 职责 |
|---|---|
| `friendlink/model/FriendLinkModel.java` | 表映射模型（含非数据库列 `logo_url`） |
| `friendlink/mapper/FriendLinkMapper.java` | `selectAll` / `selectEnabledAll` / `selectById` / `selectMaxSortOrder` / `selectNeighbor` / `insert` / `update` / `updateSortOrder` / `softDelete` |
| `friendlink/service/FriendLinkService.java` | 上传、增删改、上移下移、列表签名、公开列表 |
| `friendlink/controller/FriendLinkController.java` | 6 个管理端接口 + 1 个公开接口 |

接口清单（路径 `/api/friend-link`，遵循 `festival` 的 `/admin/*` 风格）：

| 方法 | 路径 | 入参 | 鉴权 | 说明 |
|---|---|---|---|---|
| POST | `/api/friend-link/upload` | `file`(MultipartFile) | 馆长 1匹配 | 上传 logo，**只上传不落库**，返回 objectKey 字符串 |
| GET | `/api/friend-link/admin/list` | — | 馆长 1匹配 | 全部友链（未删除），按 `sort_order ASC, created_at ASC`，每项带签名 `logo_url` |
| POST | `/api/friend-link/admin/create` | JSON `{title, url, logo_key?, intro?}` | 馆长 1匹配 | 新增，`sort_order = MAX + 1` |
| PUT | `/api/friend-link/admin/{id}` | JSON `{title, url, logo_key?, intro?}` | 馆长 1匹配 | 修改（logo 变更时旧对象入 GC 队列） |
| PUT | `/api/friend-link/admin/{id}/move` | `?direction=up\|down` | 馆长 1匹配 | **单条移动**：同一事务内与相邻友链交换 `sort_order`（拍板 Q3） |
| DELETE | `/api/friend-link/admin/{id}` | — | 馆长 1匹配 | 软删记录 + logo 对象入 GC 队列 |
| GET | `/api/friend-link/all` | — | **@IgnoreAuth** | 用户端公开：按 `sort_order` 返回 `{title, url, intro, logo_url}` |

**关键实现约束：**

1. **`uuid` 必须是 Service 方法第 1 个形参**（切面默认 `uuidIndex = 0`），Controller 用 `request.getAttribute("uuid").toString()` 传入（`FestivalController.java:35` 同款）。
2. **logo 签名统一走 `ImageUrlResolver.resolve(logo_key)`**，不要自己 `new Date()` 拼有效期——解析器已封装 1 小时有效期与「已是完整 URL 则直接返回」的兼容分支（`ImageUrlResolver.java:15-20`）。
3. **`move` 的实现要点**（`@Transactional(rollbackFor = Exception.class)`）：
   - `direction` 只接受 `up` / `down`，其余返回 `new J(1, "方向不合法", null)`；
   - 先 `selectById(id)`，不存在 → `new ResourceR().resourceSuch(false, null)`（与 `BackgroundImageService.java:166-168` 同款语义）；
   - 取相邻行：`up` 取 `sort_order < 当前` 中最大者，`down` 取 `sort_order > 当前` 中最小者（`ORDER BY sort_order DESC/ASC LIMIT 1`，且 `deleted_at IS NULL`）；
   - **无相邻行（已在首/末位）→ 返回 `new J(1, "已经在最前 / 已经在最后", null)`**，不做静默无操作（§一.4）；
   - 交换两条的 `sort_order` 后返回成功；前端据此重新拉列表（或按响应就地交换，见 3.5）。
4. **`create` / `update` 的校验顺序**（返回 `new J(1, 文案, null)`）：
   - `title`：必填、`trim` 后非空、长度 ≤ 64；
   - `url`：必填、`trim` 后非空、长度 ≤ 512、**必须以 `http://` 或 `https://` 开头**（拍板 Q5，大小写不敏感）；
   - `intro`：可空；非空时 `trim` 后长度 ≤ 255；
   - `logo_key`：可空；非空时长度 ≤ 255 且**必须以 `friendlink/logo/` 开头**（防越权写入其他模块的对象路径，如背景图或头像桶内对象）。
5. **`upload` 的校验**（照抄 `BackgroundImageService.java:77-86`）：文件非空、`content-type` 以 `image/` 开头、大小 ≤ **1MB**（上限常量与背景图保持一致，便于前端复用同一套提示文案）；通过后 `cosService.uploadFileWithKey(file, buildLogoKey(file), CosBucketConfigEnum.image)`，`getCode() != 0` 时把 `J` 原样透出。
6. **`update` 的 logo 变更判定**：`旧 logo_key` 非空、`新 logo_key` 与其不同 → `cosGcService.enqueue(cosConfig.getImage().getBucketName(), 旧key)`（拍板 Q4；桶名取法同 `BackgroundImageService.java:219`）。
7. **`delete`**：记录不存在 → `resourceSuch(false, null)`；存在则先入 GC 队列（`logo_key` 非空时）再软删，返回 `resourceSuch(rows > 0, null)`。
8. **公开接口不返回 `logo_key`、`id`、`created_by` 等内部字段**，只返回用户端渲染所需的四项：`{title, url, intro, logo_url}`。

### 3.4 前端 API 封装（管理端）

新增 `BarcManageFrontendV2/src/modules/friend-link/api/friendLink.service.ts`：

```ts
export interface FriendLinkRecord {
  id: string
  title: string
  url: string
  logo_key: string | null
  intro: string | null
  sort_order: number
  created_by: string | null
  created_at: string | null
  updated_at: string | null
  /** 后端签名的完整 logo URL（非数据库列，logo_key 为空时为 null） */
  logo_url?: string | null
}

export interface FriendLinkPayload {
  title: string
  url: string
  logo_key: string | null
  intro: string | null
}
```

导出方法：`fetchFriendLinks()`、`createFriendLink(payload)`、`updateFriendLink(id, payload)`、`moveFriendLink(id, direction)`、`deleteFriendLink(id)`、`uploadFriendLinkLogo(file)`（`FormData` 字段名统一用 `file`，返回 objectKey 字符串）。

> 上传字段名用 `file`，与背景图 `/api/background/upload` 的 `@RequestParam("file")` 保持一致（`BackgroundController.java:38`），减少记忆负担。

### 3.5 管理端页面与菜单

新增：

| 文件 | 职责 |
|---|---|
| `src/modules/friend-link/api/friendLink.service.ts` | 6 个接口封装（3.4） |
| `src/modules/friend-link/views/FriendLinksListView.vue` | 列表页：表格 + 新增/编辑抽屉 + 上移/下移 + 删除 + logo 上传 |
| `src/modules/friend-link/__tests__/FriendLinksListView.spec.ts` | 页面测试 |
| `src/modules/friend-link/__tests__/friendLink.service.spec.ts` | 接口封装与 payload 组装测试 |

页面交互要点（全部有现成范本，不引入新依赖）：

- **壳层**：`RoutePageShell`，`title="友情链接"`，`eyebrow="Friend Links"`，右上角 `#actions` 放「新增友链」按钮（`BackgroundsListView.vue:278-297`）。
- **表格列**：序号（`sort_order`）→ logo 缩略图（`el-image`，`logo_url || logo_key` 兜底且空值显示占位）→ 标题 → 链接（超长省略 + `title` 提示）→ 简介 → 排序（上移/下移按钮组，首末位禁用）→ 操作（编辑 / 删除）。
- **新增 / 编辑**：用 `el-drawer` + `el-form`（必填校验：标题、链接；链接前缀校验 `^https?://`），logo 用**自研上传字段**（`el-upload` + `:http-request` 调 `uploadFriendLinkLogo`，成功后就地显示本地预览），不直接复用 `school-club-student/components/ImageUploadField.vue`——该组件的交互是「先建实体、再单独保存图片」（`:6-8`、`:122-140`），与「随表单一次提交」的友链语义不匹配。
- **上移/下移**：点击后调 `moveFriendLink(id, 'up'|'down')`，成功后**重新拉取列表**（比照 `BackgroundsListView.vue:237-251` 的乐观更新更简单，且能保证与后端真实序号一致）；失败时 `showError` 透出「已经在最前 / 已经在最后」。
- **删除**：`ElMessageBox.confirm('删除后该友链将不再展示，是否继续？', '确认删除', { type: 'warning' })`，成功后重新拉列表。
- 提示统一走 `@/shared/utils/message` 的 `showSuccess` / `showError` 与 `getErrorMessage`（`BackgroundsListView.vue:20-21`）。

修改 `src/app/router/routes.ts`，**插入到「系统」组 `menuOrder: 50`（学园）之前**，因为友链是系统组内最靠前的自定义内容入口：

```ts
{
  path: 'system/friend-links',
  name: 'system-friend-links',
  component: FriendLinksListView,
  meta: {
    title: '友情链接',
    subtitle: '维护用户端首页展示的友情链接，支持排序与 logo 上传。',
    requiresAuth: true,
    requiresManager: true,
    // 1匹配鉴权：仅馆长（ADVANCED_ADMINISTRATOR）可访问（拍板 Q2）
    minManagerPermissionBit: MANAGER_PERMISSION.ADVANCED_ADMINISTRATOR,
    menuLabel: '友情链接',
    menuGroup: 'system',
    menuGroupLabel: '系统',
    groupOrder: 60,
    menuOrder: 10,
  },
}
```

> **为什么 `menuOrder: 10`**：组内现有 50/60/70/80 是历史上的稀疏编号；新项取 10 即置于「系统」组首位，无需改动既有项的编号（改动 `menuOrder` 会影响侧边栏渲染顺序与既有测试断言，属于不必要的影响面）。
> **`RouteMeta` 无需改动**：本次没有新增 meta 字段（`shared/types/router.d.ts:6-20` 原样够用）。

### 3.6 用户端首页展示（拍板 Q1）

改造 `BarcFrontend/src/components/Home/HomeFriendshipComp.vue`（当前 29 行、无数据）：

1. `onMounted` 调 `GET /api/friend-link/all`；请求与类型定义放在 **`BarcFrontend/src/utils/friendLinkApi.ts`**——这是用户端 API 层的实测落点（现有同类文件：`utils/backgroundApi.ts`、`utils/workLikeApi.ts`、`utils/commentApi.ts`），**不存在 `src/api/` 目录**。
   写法照抄 `backgroundApi.ts:46-61`：用 `utils/https.ts` 的 `baseHttp`，**手动解包 `ResponseImpl`**（`data.code === 0` 才取 `data.data`），`catch` 内兜底返回空数组。
2. **本期不加模块级缓存**：`backgroundApi.ts:32`、`:47-49`、`:71-73` 确有 `cache` / `festivalCache`，但友链 logo 是**签名 URL、1 小时过期**（`ImageUrlResolver.java:13`），缓存到会话级反而会拿到过期地址；每次进首页重新拉取最稳（友链数据量个位数，代价可忽略）。
3. 卡片渲染：`logo_url` 有值时显示图片（`loading="lazy"`、固定尺寸 `object-fit: cover`），为空时用纯文字卡片兜底（不裂图）。
4. 卡片整体 `<a :href="item.url" target="_blank" rel="noopener noreferrer">`（拍板 Q8），标题 + 简介两行。
5. **失败 / 空数据处理**：接口异常或返回空数组时，**保留现有标题、不渲染任何卡片、不抛出可见错误**（与 `HomeFriendshipComp.vue:5-12` 现状降级等价，避免首页出现红色报错）。这一条需要在测试里显式断言。

样式沿用组件内既有 `scoped` 风格（白底 `.friendship_box`），新增卡片区用 grid 自适应换行；**不引入新的 UI 组件库依赖**。

### 3.7 权限汇总

| 层 | 配置 | 依据 |
|---|---|---|
| 后端 Service | `@RequireUserAndPermissionAnno({ @Check(identity=MANAGER, targetPermission=ADVANCED_ADMINISTRATOR, isSuchElseRequire=false, isHasElseUpper=true) })` | 宪法 §18.2 ②；`FestivalService.java:52-57` |
| 后端公开接口 | `@IgnoreAuth` + **不加**权限注解 | `FestivalController.java:82-86` |
| 前端路由 meta | `requiresAuth: true`、`requiresManager: true`、`minManagerPermissionBit: MANAGER_PERMISSION.ADVANCED_ADMINISTRATOR` | `routes.ts:432-433` 同款 |
| 前端页面内门禁 | **无**（与背景图 / 节日页面一致，不做按钮级门禁） | `2026-09-20-manage-sidebar-group-collapse-design.md:199-211` 的扫描结论 |

> 注意「学园、部团、学生」的既有偏差（前端比大小 ≥16、后端写接口 1匹配 bit16）**不适用于本模块**——本模块前后端都是馆长位 1匹配，不存在该错配。

### 3.8 上传流程选型（拍板 Q9：显式选择「先传后落」，与项目既有链路同构）

落码前专门核查过全项目的「图片上传 ↔ 数据库落库」顺序，结论是**项目里几乎全部是「COS 先传、数据库后落」**：

| 链路 | 顺序 | 证据 |
|---|---|---|
| 背景图上传 | COS 先传 → 再 INSERT | `BackgroundImageService.java:103` 上传、`:108-118` insert |
| 作品封面（编辑） | COS 先传 → 失败即 `deleteFile` 补偿 → 再落库 | `WorkService.java:341`、`:359-362` |
| 作品图集 | COS 先传 → 再 INSERT | `WorkImageService.java:86`、`:104` |
| 学园 / 部团 / 学生 三图 | **独立上传接口拿 key** → 前端再提交 key → 后端只 UPDATE | `FileService.java:39`（只传 COS 不碰表）+ `StudentService.java:180-224`（只 UPDATE） |
| 公告 | **完全不涉及 COS**（`/notice/upload` 收 `@RequestBody NoticeModel`，只 `noticeMapper.insert`） | `NoticeController.java:55-61`、`NoticeService.java:71-80` |
| 通用文件上传 | 纯 COS 代理，不落任何业务表 | `FileService.java:20-41` |
| 作品创建（唯一例外） | `@Transactional` 内先 `workMapper.insert` 拿 ID → 再上传 COS → 再写指针表，失败反向 `workMapper.delete` 补偿 | `WorkService.java:437-542`（insert `:475`、上传 `:498`、指针 `:511`、补偿 `:502`/`:514`） |

**另一个必须点明的事实：本项目从来不存在「双向索引」。**

- `background_image` 等表只存 `object_key`；key 里的 uuid 段是**当场新生成的一次性 UUID**（`BackgroundImageService.java:97-101`），与记录 `id` **无关**；
- 没有任何表记录「该 COS 对象属于哪条记录」，也从不往 COS 对象 metadata 写记录 ID；
- `ImageUrlResolver.isKey()`（`:22-24`）只是字符串前缀启发式，用于区分「key 还是已签名 URL」；
- 项目里唯一的「索引表」是 `cos_garbage_key`，由 `CosGcScheduler` 每 5 分钟消费一次（`api/scheduler/CosGcScheduler.java:25` 的 `BATCH_SIZE = 50`、`:27` 的 `@Scheduled(fixedDelay = 5 * 60 * 1000)`；失败记 `retry_count` / `last_error`，见 `api/mapper/CosGcMapper.java:9-18`）——**它是待清理队列，不是双向索引**。

因此本次考察过的四个流程候选及其取舍如下（**拍板结论：选 A**）：

| 方案 | 流程 | 优点 | 缺点 | 结论 |
|---|---|---|---|---|
| **A（选中）** | 独立上传接口拿 key → 前端随表单一并提交 → 后端 INSERT | 与背景图 / 学园部团学生三图**完全同构**；改动最小；无新增状态字段 | ①上传后放弃保存 → 孤儿对象；②上传成功但 INSERT 失败 → 孤儿对象 | **采用**；孤儿对象由 GC 入队 + 巡检兜底治理（见下） |
| B | 先定 ID（不落库）→ 传 COS → 一次性 INSERT | 上传失败时 DB 无脏记录；key 可嵌记录 ID 形成双向可推导 | key 要嵌记录 ID 即**改动用户给定的命名规则**；仍是先传后落，孤儿对象问题并未消失 | 不采用 |
| C | 先 INSERT 拿 ID → 传 COS → UPDATE 回填 | 真正「先落库后上传」 | COS 上传是网络 IO，**不能放进数据库事务**（长时间占用连接与行锁），只能拆两段、没有原子性；上传失败会留下 `logo_key = NULL` 的可见记录，需额外「未完成状态」与前端回显 | 不采用 |
| D | 先传 COS 临时目录 → 落库时 `cosService.moveFile` 到正式目录（`CosService.java:140-157` 已有能力） | 最接近「上传即落」语义 | 每次上传多一次 COS 调用；与现有**所有**链路都不同构；临时目录还需要独立清理策略 | 不采用 |

> 附带结论：**不改动用户给定的 logo 命名规则**——「uuid 第一段」仍然是**当场新生成** UUID 的第一段（§3.2），不改为记录 ID。因为方案 A 下 key 与记录本来就不同源，把 uuid 段改成记录 ID 只会造成「本项目只有友链这么做」的孤例，收益（可反查）在本期没有消费方。

**兜底治理（方案 A 的必要配套）**

| 层 | 处置 | 状态 |
|---|---|---|
| ① 删除 / 替换 logo | `cosGcService.enqueue` 入队，由 `CosGcScheduler` 5 分钟内清理；**沿用背景图既有做法** | 本期落码 |
| ② 上传成功但落库失败 | 上传接口与落库接口分离，后者失败时**不回滚 COS**；孤儿对象与背景图 / 学生图完全同意 | 本期落码（不额外处理） |
| ③ 上传后放弃保存（Q6 独立上传接口的固有副作用） | 需要「COS 孤儿对象巡检」：定期列出桶内对象与各业务表 `object_key` 集合比对，删除长期无引用的对象 | **独立需求，本期不做**（跨模块治理，涉及多个模块的表清单与保留期策略，须单独拍板；本模块只在文档登记，见 §4.4） |

---

## 四、影响面与边界

### 4.1 改动文件清单

**后端（新增 5 个文件 + 1 个迁移）**

| 动作 | 文件 |
|---|---|
| 新增 | `BarcBackend/src/main/resources/db/migration/V13__add_friend_link.sql` |
| 新增 | `BarcBackend/src/main/java/com/miaoyu/barc/friendlink/model/FriendLinkModel.java` |
| 新增 | `BarcBackend/src/main/java/com/miaoyu/barc/friendlink/mapper/FriendLinkMapper.java` |
| 新增 | `BarcBackend/src/main/java/com/miaoyu/barc/friendlink/service/FriendLinkService.java` |
| 新增 | `BarcBackend/src/main/java/com/miaoyu/barc/friendlink/controller/FriendLinkController.java` |
| 新增 | `BarcBackend/src/test/java/com/miaoyu/barc/friendlink/service/FriendLinkServiceTest.java` |
| 新增 | `BarcBackend/src/test/java/com/miaoyu/barc/friendlink/controller/FriendLinkControllerTest.java` |

**管理端（新增 4 个文件 + 改 1 个文件）**

| 动作 | 文件 |
|---|---|
| 新增 | `BarcManageFrontendV2/src/modules/friend-link/api/friendLink.service.ts` |
| 新增 | `BarcManageFrontendV2/src/modules/friend-link/views/FriendLinksListView.vue` |
| 新增 | `BarcManageFrontendV2/src/modules/friend-link/__tests__/FriendLinksListView.spec.ts` |
| 新增 | `BarcManageFrontendV2/src/modules/friend-link/__tests__/friendLink.service.spec.ts` |
| 修改 | `BarcManageFrontendV2/src/app/router/routes.ts`（新增 1 条路由，见 3.5） |

**用户端（新增 2 个文件 + 改 1 个文件）**

| 动作 | 文件 |
|---|---|
| 新增 | `BarcFrontend/src/utils/friendLinkApi.ts`（接口封装，写法照抄 `utils/backgroundApi.ts`） |
| 新增 | `BarcFrontend/src/utils/__tests__/friendLinkApi.spec.ts`（测试落点与 `backgroundApi.spec.ts` 同级） |
| 修改 | `BarcFrontend/src/components/Home/HomeFriendshipComp.vue` |

**文档（本次已在范围内）**

| 动作 | 文件 |
|---|---|
| 新增 | 本文件 `docs/superpowers/specs/2026-09-21-friend-link-design.md` |
| 修改 | `docs/功能文档.md`（新增 §3.13 友情链接模块；§四 菜单表新增一行；§八 表清单新增 `friend_link`） |
| 修改 | `docs/结构文档.md`（§2.2 包结构表新增 `friendlink`；§2.3 控制器表新增 `FriendLinkController`；§6.x 补友链存储命名规则） |
| 修改 | `docs/数据库初始化指南.md`（§4.7 登记 `friend_link` 为**可选**：管理端有增删改入口，可自行创建） |

### 4.2 明确不改动（边界）

- **不改 `CosService` / `ImageUrlResolver` / `CosGcService`**：宪法 §12 明确「COS client 代码已完善，尽量不要动」，本次只用不改。
- **不改 `BackgroundImageService` / `FestivalService`**：它们只是范本，本次不重构、不抽取公共父类（两个模块的字段与排序语义不同，强行抽象会把差异挤进参数里）。
- **不改 `AppSidebar.vue` / `permissions.ts` / `router/index.ts`**：菜单聚合与权限判定是 meta 驱动的，新增一条路由即自动生效。
- **不改 `RouteMeta` 类型声明**（`shared/types/router.d.ts`）。
- **不做数据迁移**：新表，无存量数据。
- **不提供启用/停用开关**：表内保留 `enabled` 列，但本期无界面、无接口、公开接口也不过过滤（见 §5.2 的明确声明）。
- **不做拖拽排序**（拍板 Q3 已排除）、**不做批量 reorder 接口**、**不做分页/搜索**（友链数量级为个位数）。

### 4.3 双端兼容性检查（宪法 §11.1）

| 检查项 | 结论 |
|---|---|
| 修改既有 API 返回结构 | **否**（全部为新增接口） |
| 新增必填字段到既有接口 | **否** |
| 删除返回字段 | **否** |
| 修改错误码 / 响应类 | **否**（沿用 `ResourceR` / `SuccessR` / `J`） |
| 新增权限门槛 | 仅作用于**新增接口**（`/api/friend-link/**`），不影响任何既有链路 |
| 对 `BarcFrontend` 的影响 | 仅 `HomeFriendshipComp.vue` 一处，且失败时降级为现状（§3.6 第 4 点），不阻断首页其他区块 |
| 对 `BarcManageFrontendV2` 的影响 | 仅新增菜单项与页面；「系统」组从 4 项变 5 项（侧边栏折叠测试若断言项数，需同步更新——落码时检查 `src/app/components/__tests__/AppSidebar.spec.ts` 是否存在组项数硬断言） |

### 4.4 风险与回滚

| 风险 | 处置 |
|---|---|
| 落码时 `V13` 已被占用 | 落码前重新确认 `db/migration/` 最大版本号（当前确认是 `V12`） |
| COS 孤儿对象（拍板 Q9 方案 A 的固有代价） | 成因有两类：①**上传后放弃保存**（独立上传接口的固有效应，背景图因为「上传即落库」不存在此类，但学园/部团/学生三图同样存在）；②**上传成功但落库失败**（DB 抖动）。处置：①② 本期**不额外做补偿**（与项目既有链路一致），其中 ① 需靠**跨模块「COS 孤儿对象巡检」**兜底——该巡检属**独立需求**，须单独拍板（涉及各模块 `object_key` 表清单与保留期策略），本模块仅在本文档登记，不实现 |
| `move` 并发点击导致序号错乱 | 单条移动 + `@Transactional`；两人同时移动同一行时最终结果以最后一次提交为准（友链数量极少、管理端并发极低，不做乐观锁） |
| 签名 URL 1 小时过期 | 用户端每次进首页都会重新拉接口（不做本地缓存），因此不存在「拿到 URL 后隔天再打开」的场景；管理端列表同理每次重新拉取。**已知限制**：用户在首页停留超过 1 小时后，卡片 logo 的 URL 会失效（浏览器已缓存图片则无感）。如需根治需改造 `ImageUrlResolver` 的缓存策略，**属独立需求** |
| 回滚 | 删除 4 个新增后端文件 + 前端新增文件 + 路由条目；`friend_link` 表可保留（空表无害）。**已执行的 Flyway 迁移不删不改**（`docs/AI开发指南.md:380-383`） |

---

## 五、测试与验证计划

### 5.1 后端测试（`src/test/java`，按包结构；覆盖率 ≥90%）

`FriendLinkServiceTest`（参照 `background/service/BackgroundImageServiceTest.java` 的 mock 手法）：

1. `listService`：签名被逐条调用（`verify(imageUrlResolver).resolve(...)`）；`logo_key` 为空时 `logo_url` 为 null。
2. `uploadService`：非图片 / 超 1MB / 空文件被拒；成功时断言 **`uploadFileWithKey` 收到的 objectKey 匹配 `friendlink/logo/{名}_{8位大写}_{秒级}.{ext}`**（用 `ArgumentCaptor`，这是本次最容易写错的一处）；`CosBucketConfigEnum.image` 被传入。
3. `createService`：标题/链接缺失或空白被拒；链接非 `http(s)://` 前缀被拒；`intro`/`logo_key` 超长被拒；`logo_key` 不以 `friendlink/logo/` 开头被拒；成功时 `sort_order == MAX + 1`、`created_by == 操作者 uuid`。
4. `updateService`：记录不存在返回 `resourceSuch(false, null)`；logo 变更时 `verify(cosGcService).enqueue(imageBucket, 旧key)`；logo 未变时 `verify(cosGcService, never()).enqueue(...)`。
5. `moveService`：`up` 在中间时两条 `sort_order` 互换算术断言；首位 `up` / 末位 `down` 返回失败且 **`verify(mapper, never()).updateSortOrder(...)`**；`direction` 非法被拒；记录不存在返回 `resourceSuch(false, null)`。
6. `deleteService`：`verify(cosGcService).enqueue(...)`（`logo_key` 非空）+ 软删被调用；`logo_key` 为 null 时不入队；记录不存在时不软删。
7. `publicAllService`：只返回四字段（不含 `id` / `logo_key` / `created_by`）；`logo_url` 来自解析器。

`FriendLinkControllerTest`：

8. 6 个管理端接口的馆长 1匹配：馆长位通过 / 副馆长(16) 被拒 / USER 身份被拒 / 用户不存在；
9. `GET /api/friend-link/all` 无需登录即可访问（`@IgnoreAuth` 生效）。

### 5.2 前端测试

`FriendLinksListView.spec.ts`（mock 手法照抄 `backgrounds/__tests__/BackgroundsListView.spec.ts:8-70` 的 `vi.hoisted` + `vi.mock`）：

1. 列表渲染：标题 / 链接 / 简介 / logo（`logo_url` 优先，空值走占位）；
2. 新增：表单必填校验（标题、链接）、链接前缀校验、「提交前未上传 logo」时 `logo_key` 为 null；
3. 上传：选图后调 `uploadFriendLinkLogo`，返回的 key 进入表单；非图片 / 超 1MB 被拦并提示；
4. 上移 / 下移：调用参数为 `(id, 'up'|'down')`；首行「上移」与末行「下移」按钮 `disabled`；失败时 `showError` 被调用；
5. 删除：先 `confirm`，取消则不调接口；确认则调 `deleteFriendLink` 并重新拉列表；
6. 接口异常：`showError` 被调用且页面不崩。

`friendLink.service.spec.ts`：6 个方法的 URL / method / payload 组装（含 `moveFriendLink` 的 query 参数、上传的 `FormData` 字段名）。

**用户端测试**（落点 `BarcFrontend/src/utils/__tests__/`，与 `backgroundApi.spec.ts` 同级）：
`friendLinkApi.spec.ts` 断言「`code === 0` 才返回数据」「异常 / 非 0 code 兜底空数组」；`HomeFriendshipComp` 的「接口成功渲染卡片（含 `target="_blank"` 与 `rel="noopener noreferrer"`）」与「接口失败仅保留标题、不渲染卡片」两条。

### 5.3 构建验证（宪法 §8）

```bash
cd BarcBackend && ./mvnw clean package
cd BarcManageFrontendV2 && npm run test:run && npm run build
cd BarcFrontend && npm run build
```

> 前端测试命令实测为 `npm run test:run`（`vitest run`）；测试文件位置实测为 `BarcManageFrontendV2/src/modules/<模块>/__tests__/*.spec.ts`，**不存在 `BarcManageFrontendV2/tests/` 目录**（与宪法 §7.1 的表述不一致，实际以 `src/modules/**/__tests__` 为准）。

---

## 六、拍板记录

| # | 问题 | 结论 |
|---|---|---|
| Q1 | 用户端是否接入 | 接入（公开接口 + `HomeFriendshipComp.vue` 渲染卡片） |
| Q2 | 页面与接口权限 | 馆长 `ADVANCED_ADMINISTRATOR`(32)，1匹配 |
| Q3 | 顺序交互 | 上移 / 下移按钮；**不做批量排序接口** → 派生出单条 `move` 接口与「序号空洞不归一化」 |
| Q4 | 旧 logo 清理 | GC 队列异步清理 |
| Q5 | 链接 / 简介校验 | 链接 http(s) 前缀 + 长度；简介可空 + 长度；不探测域名真实性 |
| Q6 | 上传接口形态 | 独立 `POST /api/friend-link/upload` 返回 objectKey，随表单提交 |
| Q7 | 文档落点 | spec 设计文档 + 功能文档 + 结构文档 |
| Q8 | 卡片打开方式 | 新标签页 `target="_blank"` + `rel="noopener noreferrer"` |
| Q9 | 上传与落库顺序 | **方案 A「先传后落」**（独立上传接口 → 提交 key → 落库），与项目既有链路同构；候选方案 B/C/D 的取舍见 3.8 |

**拍板后仍待落码时确认的 4 个细节（不需要再拍板，按本文档执行即可）**

| # | 细节 | 本文档的处置 |
|---|---|---|
| D1 | 「uuid 第一段」的取法 | `getUuid36l().split("-")[0]`（8 位，大写），见 3.2 |
| D2 | 「图片名」是否清洗 | 清洗（去路径分隔符/空白/控制字符，白名单字符，空则回落 `logo`，截断 50），见 3.2 |
| D3 | logo 大小上限 | 1MB，与背景图一致（可复用同一提示文案） |
| D4 | 公开接口是否过滤 `enabled` | **本期不过滤**（无界面维护该列），如需过滤属行为变更，须单独拍板 |

---

## 七、落码任务清单

| 顺序 | 动作 | 文件 |
|---|---|---|
| 1 | 新增迁移 | `BarcBackend/src/main/resources/db/migration/V13__add_friend_link.sql` |
| 2 | 新增模型 | `BarcBackend/.../friendlink/model/FriendLinkModel.java` |
| 3 | 新增 Mapper | `BarcBackend/.../friendlink/mapper/FriendLinkMapper.java` |
| 4 | 新增 Service | `BarcBackend/.../friendlink/service/FriendLinkService.java`（含 `buildLogoKey`） |
| 5 | 新增 Controller | `BarcBackend/.../friendlink/controller/FriendLinkController.java` |
| 6 | 新增后端测试 | `FriendLinkServiceTest` / `FriendLinkControllerTest` |
| 7 | 新增管理端 API | `BarcManageFrontendV2/src/modules/friend-link/api/friendLink.service.ts` |
| 8 | 新增管理端页面 | `BarcManageFrontendV2/src/modules/friend-link/views/FriendLinksListView.vue` |
| 9 | 新增管理端测试 | `FriendLinksListView.spec.ts` / `friendLink.service.spec.ts` |
| 10 | 挂菜单 | `BarcManageFrontendV2/src/app/router/routes.ts`（`system/friend-links`，`system` 组，`menuOrder: 10`） |
| 11 | 新增用户端 API | `BarcFrontend/src/utils/friendLinkApi.ts`（照抄 `utils/backgroundApi.ts` 的解包与兜底写法） |
| 12 | 改造用户端组件 | `BarcFrontend/src/components/Home/HomeFriendshipComp.vue` |
| 13 | 新增用户端测试 | `BarcFrontend/src/utils/__tests__/friendLinkApi.spec.ts` + `HomeFriendshipComp` 组件测试 |
| 14 | 构建与测试验证 | 5.3 三条命令 |
| 15 | 文档同步（落码阶段随代码提交） | `docs/功能文档.md`（§3.13 新增、§四菜单表、§八表清单）、`docs/结构文档.md`（§2.2 包结构、§2.3 控制器、存储命名规则）、`docs/数据库初始化指南.md`（§4.7 可选行） |
| 16 | 提交（**只 commit 不 push**） | 建议：`feat(系统): 新增友情链接模块并接入用户端首页` |
| 17 | **不在本期**：登记「COS 孤儿对象巡检」为独立需求 | 见 §3.8 兜底治理表 ③ 与 §4.4；本期只在文档登记，不写代码 |

---

## 八、附：本次核查覆盖的上传链路全集（供后续巡检需求复用）

| 模块 | 上传入口 | 业务表 | key 形态 |
|---|---|---|---|
| 背景图 | `POST /api/background/upload` | `background_image.object_key` | `client/bg/{module}/{uuid前8位}-{秒级}.{ext}` |
| 作品封面 / 图集 | `POST /api/work/...` | `work_cover_image.object_key`、`work_image.object_key` | `/{上传者uuid}/work_images/{时间戳}-{原文件名}` |
| 学园 三图 | `POST /api/file/upload?path=...` | `school.logo` / `beautify_logo` / `bg` | 由前端传 `path`，`FileService` 兜底 `/{uuid}/` |
| 部团 两图 | 同上 | `club.logo` / `bg` | 同上 |
| 学生 三图 | 同上 | `student.avatar_square` / `avatar_rectangle` / `body_image` | 同上 |
| **友链 logo（本次）** | `POST /api/friend-link/upload` | `friend_link.logo_key` | `friendlink/logo/{图片名}_{uuid第一段}_{秒级}.{ext}` |
| 公告 | **无 COS**（只存正文，图片靠外链或通用上传） | — | — |

> **巡检需求的难点已在此表暴露**：`school` / `club` / `student` / `work_*` 的 key 是**整条 URL 或 key 混存**（前端传 `path`），且 `FileService` 允许任意 `path`，
> 因此「桶内对象 → 业务表引用」的比对必须按表列清单逐列收集，并处理「值是完整 URL 而非 key」的情况（`ImageUrlResolver.isKey()` 的启发式判据就是为此存在的）。
> 这也是为什么巡检不宜塞进本模块顺手做——它是一次跨模块治理。

---

## 九、落码记录（2026-09-21 已完成）

实际落地内容，以及与第三章计划的差异（如实记录）：

**后端（与计划一致）**

| 文件 | 说明 |
|---|---|
| `BarcBackend/src/main/resources/db/migration/V13__add_friend_link.sql` | 建 `friend_link` 表（含 `idx_friend_link_order`、`idx_friend_link_logo_key`） |
| `.../friendlink/model/FriendLinkModel.java` | 表映射（含非数据库列 `logo_url`） |
| `.../friendlink/mapper/FriendLinkMapper.java` | 9 个方法（含 up / down 两个方向的相邻查询） |
| `.../friendlink/service/FriendLinkService.java` | 6 个管理端方法 + `publicAllService`；`buildLogoKey` / `sanitizeBaseName` 私有实现 |
| `.../friendlink/controller/FriendLinkController.java` | 6 个管理端接口 + `GET /all`（`@IgnoreAuth`） |
| `src/test/java/.../friendlink/service/FriendLinkServiceTest.java` | 35 例 |
| `src/test/java/.../friendlink/controller/FriendLinkControllerTest.java` | 4 例（注解契约 + 路径契约 + `@IgnoreAuth`） |

**与计划的三处差异（均为落地时的必要调整）**

1. **列表接口同时承担「移动后回传」**：`moveService` 成功时返回**交换后的完整列表**（而非单条或空响应），前端据此直接替换本地状态，少一次列表往返。这是 3.3 第 3 点「返回成功」的具体化。
2. **`FriendLinkItem` 只承载表单四字段**，`id` 走路径参数（与 `festival` 的 `updateService(uuid, id, item)` 形态一致），未把 `id` 塞进请求体。
3. **边界失败走 `new J(1, 文案, null)`**，因此前端读到的提示在 `msg` 上（`data` 为 null）——与项目既有的错误返回方式一致。

**管理端**

| 文件 | 说明 |
|---|---|
| `src/modules/friend-link/api/friendLink.service.ts` | 6 个方法（`moveFriendLink` 用 `params.direction`） |
| `src/modules/friend-link/views/FriendLinksListView.vue` | 列表 + 抽屉表单 + 上移/下移 + 删除 |
| `src/modules/friend-link/__tests__/FriendLinksListView.spec.ts` | 14 例 |
| `src/modules/friend-link/__tests__/friendLink.service.spec.ts` | 6 例 |
| `src/app/router/routes.ts` | 新增 `system/friend-links`（`system` 组，`menuOrder: 10`） |

管理端落地时的两处调整：

1. **抽屉里的「取消 / 保存」放在表单内，而不是 `el-drawer` 的 `#footer` 插槽**：项目测试环境的 `el-drawer` 被 stub（`src/test-utils/setup.ts:24-40`，只渲染默认插槽），footer 插槽不会渲染；放到表单内既保证测试可点，观感也更常见。
2. **提交前增加显式校验 `validateForm()`**：`el-form` 的 `rules` 负责行内红字提示的观感，但它由 `el-form` 内部驱动，在 `el-form-item` 被 stub 的环境下不会真正阻断提交（实测会被绕过并发出请求）。因此组件内再做一层显式校验，保证「校验不通过就绝不发请求」这条约束在任何环境下都成立；规则与 `rules`、后端 `FriendLinkService#validate` 三处保持一致。

**用户端（与计划有两处结构性差异）**

| 文件 | 说明 |
|---|---|
| `src/utils/friendLinkApi.ts` | `fetchFriendLinks()`，`code === 0` 才取数据，异常 / 非 0 / 非数组一律兜底空数组；**不加模块级缓存**（签名 URL 1 小时过期） |
| `src/components/Home/HomeFriendshipComp.vue` | **容器**：`onMounted` 取数 + 渲染区块标题 + 把数据交给子组件 |
| `src/components/Home/HomeFriendLinkListComp.vue` | **纯展示子组件**（计划外新增，见下） |
| `src/utils/__tests__/friendLinkApi.spec.ts` | 4 例 |
| `src/components/Home/__tests__/HomeFriendLinkListComp.spec.ts` | 3 例（渲染契约） |
| `src/components/Home/__tests__/HomeFriendshipComp.spec.ts` | 2 例（降级契约） |

两处结构差异的原因（都源于**用户端的渲染/测试环境约束**，不是设计变更）：

1. **拆出纯展示子组件 `HomeFriendLinkListComp`**：最初尝试把取数写成 `setup` 顶层 `await`（异步组件），但父级链路 `HomeContainerComp.vue:31` 直接引入该组件，**外层没有任何 `<Suspense>`**，异步 setup 在客户端会直接失效。因此最终形态是「容器同步 setup + `onMounted` 取数 + 纯展示子组件」，父子职责清晰且都能被测试覆盖。
2. **用户端测试改用 `@vue/server-renderer` 的 `renderToString`**：用户端**没有引入 `@vue/test-utils`**（`package.json` 实测），vitest 也跑在 node 环境（无 DOM），组件无法挂载；而 `@vue/server-renderer` 是 vue 自带的既有依赖，可在 node 下渲染出真实 HTML 并对产物做断言。
   > 试用过安装 `jsdom` 以支持组件挂载，但**网络受限（npm ECONNRESET）安装失败**，且 `package.json` / `package-lock.json` 未被改动（环境保持干净），故改用现在的方案。
   > 后果：`HomeFriendshipComp`（容器）的 SSR 断言只能覆盖「标题始终渲染 + 空态不渲染卡片」；卡片的 `target="_blank"` / `rel` / logo 有无渲染由纯展示子组件的测试完整覆盖。

**验证结果（2026-09-21）**

| 项 | 结果 |
|---|---|
| `BarcBackend`：`./mvnw clean package -DskipTests` | **BUILD SUCCESS**（产出 `BarcBackend-0.1.17.jar`） |
| `BarcBackend`：`./mvnw test` | **331 个用例，0 失败 0 错误**（新增友链 39 例全绿） |
| `BarcManageFrontendV2`：`npm run test:run` | **21 个 spec / 151 用例全绿**（新增友链 20 例） |
| `BarcManageFrontendV2`：`npm run build` | **成功**（`vue-tsc --build` + `vite build`，产物含 `FriendLinksListView-*.css`） |
| `BarcFrontend`：`npm run test:run` | **14 个 spec / 81 用例全绿**（新增友链 9 例） |
| `BarcFrontend`：`npm run build` | **成功** |
| `AppSidebar.spec.ts` 是否受影响 | **不受影响**：该 spec 只对「用户」组断言项数（`:149`），「系统」组无项数断言，故新增菜单项无需改测试 |
| 浏览器 / 视觉 | **未打开浏览器**（遵循 `AGENTS.md` §14） |

> ⚠️ 需要知悉的一条副作用：`BarcBackend/src/test/java/.../BarcBackendApplicationTests.java` 是 `@SpringBootTest`，
> 它使用主配置（开发库 `ba`）启动上下文，因此执行 `./mvnw test` 时 **Flyway 已在开发库应用 V13 并创建 `friend_link` 表**。
> 这是项目既有测试的固有行为（每次 `mvn test` 都会连开发库并应用迁移），本次迁移仅做 `CREATE TABLE IF NOT EXISTS`，
> 不含任何数据变更；这也意味着**开发库现在已经有 `friend_link` 表**（空表）。

---

> 变更记录：
> - 2026-09-21 初稿（Q1~Q8 拍板）；
> - 2026-09-21 追加 **Q9「上传与落库顺序」拍板（方案 A 先传后落）**，新增 §3.8 上传流程选型与兜底治理，
>   并在 §八 附录登记本次核查覆盖的全部上传链路（供后续「COS 孤儿对象巡检」需求复用）；
>   同期修正两处初稿的事实错误：①公告模块**不涉及 COS**（`/notice/upload` 只收 JSON 并 `insert`）；
>   ②用户端 API 层与测试的真实落点是 `src/utils/*Api.ts` 与 `src/utils/__tests__/`，**不存在 `src/api/` 目录**；
> - 2026-09-21 **完成落码**（见第九章「落码记录」）：后端 `friendlink` 包 + `V13`、管理端页面与菜单、用户端首页接入，
>   三端测试与构建全部通过；用户端因「父级无 `<Suspense>`、无 `@vue/test-utils`」拆出纯展示子组件并改用 `renderToString` 测试。
