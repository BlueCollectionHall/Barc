# 作品分类管理 — 勘探与方案（待拍板）

> 日期：2026-09-19
> 状态：**全部歧义已拍板（Q1~Q13），具备开工条件**（文档阶段只落文档；开工后按 AGENTS §13 只 commit 不 push）
>
> 拍板摘要（2026-09-19 第 2 轮）：管理端做**多层级分类字典管理**；用户端**不改造**；权限与作品管理组一致（`SEC_MAINTAINER` 1匹配）；**禁止删除**有子分类或有作品关联的分类；**不做种子迁移**，生产由管理员在页面上**手工录入开发库现有 13 条 / 3 层分类**。
> 需求：在管理端「作品管理」这一组模块中新增「作品分类管理」；生产库 `category` 为空导致分类链路空转。
> 说明：本文所有「程序事实」均标注到文件与行号；数据库事实来自**只读**探测（仅 `SHOW` / `SELECT`），未做任何写入。

---

## 一、核心结论（一句话）

**分类不是"缺一个页面"，而是"整条链路只有一个只读出口"：**
后端 `category` 表**全项目只有 2 条 `@Select`，没有任何增删改入口**（`CategoryMapper.java:9,12`），管理端 V2 **零分类代码**（全模块 grep 无匹配）。因此新生产库（`ba-prod`，由基线脚本 `--no-data` 初始化）里 `category` 为空后，**没有任何后台途径可以把它填回去**——这正是本次要补的真实空缺。

同时必须纠正一个容易误判的点：**生产 `category` 为空并不会让"上传作品"报错失败**（详见第三章），它造成的是"分类体系空转"（顶栏无分类导航、分类页打不开、作品只能无分类入库）。

---

## 二、程序事实（证据）

### 2.1 后端：分类只有"读"，没有"写"

| 位置 | 内容 | 消费方 |
|---|---|---|
| `CategoryMapper.java:9` | `SELECT * FROM category` | `CategoryService.selectAll` |
| `CategoryMapper.java:12` | `SELECT c.id, c.name, c.sort, c.icon FROM category c WHERE level = 1` | `CategoryService.selectAllTop` |

- **无 `@Insert` / `@Update` / `@Delete`，无 XML mapper**（`BarcBackend/src/main/resources/mappers/` 下无 `CategoryMapper.xml`）。
- `CategoryService`（72 行）只做「组装树 + 排序」，无任何写方法。
- `CategoryController`（`/api/category`）4 个接口**全部 `@IgnoreAuth`**（`CategoryController.java:22,33,41,49`）：`/all`、`/all_top_categories`、`/all_not_graded`、`/categories_by_category`。
- `CategoryModel.java`（`api/model`）是**无引用死类**；实际使用的是 `CategoryPojo.java`。

### 2.2 用户端真正消费的分类接口：只有 1 个

| 调用点 | 接口 | 作用 |
|---|---|---|
| `BarcFrontend/src/components/HeaderFooter/HeaderComp.vue:17` | `GET /api/category/all_top_categories` | 顶栏分类导航（`v-if="category"`，空数组也会渲染空容器） |
| `BarcFrontend/src/components/Upload/UploadWorkComp.vue:113` | 同上 | 上传页"选择作品所属分类"（空时显示"分类不存在"，见 `:428-430`） |
| `BarcFrontend/src/components/Work/Category/WorkCategoryTypeComp.vue:11` | 同上 | 分类类型条 |
| `BarcFrontend/src/components/Work/Category/WorkCategoryGeneralComp.vue:28` | `POST /api/work/works_by_category` | 分类页作品列表（路由 `/category/:category_id`，`CategoryRouter.ts`） |

**以下接口全项目零调用方（死接口，可继续沿用但不构成消费面）**：
`/api/category/all`、`/api/category/all_not_graded`、`/api/category/categories_by_category`、`POST /api/work/category/record`、`GET /api/work/category/categories_by_work`、`DELETE /api/work/category/delete`。

### 2.3 「层级」在数据模型上成立，在消费链路上基本失效（关键事实）

- 表结构支持无限层级（`parent_id` + `level`，`ON DELETE CASCADE`）。
- 但用户端**只拿 `level = 1`**（`CategoryMapper.java:12`），所以**上传作品只能挂到顶级分类**；二级 / 三级分类在用户端没有任何入口可选。
- 分类页查询 `selectByPageOnCategory` 是**精确匹配单层**：`WHERE wc.category_id = #{category_id}`（`mappers/work/WorkMapper.xml:215-221`），**不做子树递归**。
- 真正做子树递归的 `WorkMapper.selectByCategoryId`（`WorkMapper.java:94-102`，`WITH RECURSIVE category_tree`）**全项目零调用方**——是接了一半的死代码。
- 结论：**"父分类页能看到子分类作品"这件事，当前程序里并不成立**。若要让层级真正生效，必须显式改造（见 Q1）。

### 2.4 管理端 V2：分类相关代码为 0

- `BarcManageFrontendV2/src` 全量 grep `category|分类`：**零匹配**。
- 因此管理端既没有分类字典维护，也没有"作品属于哪些分类"的查看/修改。
- 作品相关现有页面（菜单组 `menuGroup: 'works'`，`app/router/routes.ts:181-294`）：上传审核(5)、作品管理(10)、作品详情(hidden)、认领管理(20)、投诉处理(30)、评论投诉(35)、操作日志(40)。
- 可复制的同类范本：**节日管理**（`modules/system/views/FestivalsListView.vue` + `modules/system/api/festivals.service.ts` + 后端 `festival` 包），已具备"列表 / 新增 / 编辑 / 启停 / 删除 + 权限切面"的完整形态。
- 管理端菜单与守卫由 `meta.ts` 字段驱动：`menuGroup` / `menuGroupLabel` / `groupOrder` / `menuOrder` / `minManagerPermissionBit`，消费点在 `app/components/AppSidebar.vue:29-63` 与 `app/stores/permissions.ts:97-109`。

### 2.5 数据库真实结构（只读探测开发库 `ba`，2026-09-19）

> 探测方式：本机 JDK17 + `mysql-connector-j`，从 `application.yaml` 读取开发库连接串，**只执行 `SHOW CREATE TABLE` 与 `SELECT`**。开发库版本 MySQL 8.0.35。

```sql
CREATE TABLE `category` (
  `id` varchar(50) NOT NULL,
  `parent_id` varchar(50) DEFAULT NULL,
  `name` varchar(20) NOT NULL,
  `level` tinyint NOT NULL,
  `sort` int DEFAULT '0',
  `is_enabled` tinyint(1) DEFAULT '1',
  `icon` text,
  `create_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  `update_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_parent` (`parent_id`), KEY `idx_level` (`level`),
  CONSTRAINT `category_ibfk_1` FOREIGN KEY (`parent_id`) REFERENCES `category`(`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3;

CREATE TABLE `work_category` (
  `id` varchar(36) NOT NULL,
  `work_id` varchar(100) NOT NULL,
  `category_id` varchar(50) NOT NULL,
  `create_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`), UNIQUE KEY `uk_work_category` (`work_id`,`category_id`), KEY `category_id`(`category_id`),
  CONSTRAINT `work_category_ibfk_1` FOREIGN KEY (`work_id`) REFERENCES `work`(`id`) ON DELETE CASCADE,
  CONSTRAINT `work_category_ibfk_2` FOREIGN KEY (`category_id`) REFERENCES `category`(`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3;
```

**开发库 `category` 现有 13 条、3 层（这是"分类确实分层级"的实证）**：

| level | id | parent_id | name | sort | 绑定作品数 |
|---|---|---|---|---|---|
| 1 | `3d_model` | null | 模型 | 1 | 29 |
| 1 | `beautify` | null | 美化 | 2 | 3 |
| 1 | `mod` | null | 模组 | 3 | 1 |
| 1 | `game` | null | 游戏 | 4 | 2 |
| 2 | `mobile_theme` | beautify | 手机主题 | 1 | 0 |
| 2 | `mouse_theme` | beautify | 鼠标指针 | 1 | 0 |
| 2 | `pc_theme` | beautify | 电脑主题 | 1 | 0 |
| 2 | `sogou_input` | beautify | 搜狗输入法皮肤 | 1 | 0 |
| 2 | `normal_model` | 3d_model | 正常比例模型 | 1 | 0 |
| 2 | `q_model` | 3d_model | Q版模型 | 1 | 0 |
| 3 | `mobile_theme_huawei` | mobile_theme | 华为手机主题 | 2 | 0 |
| 3 | `mobile_theme_vivo` | mobile_theme | VIVO手机主题 | 2 | 0 |
| 3 | `mobile_theme_xiaomi` | mobile_theme | 小米手机主题 | 2 | 0 |

其他实测：`work_category` 35 行、覆盖 35 个不同作品（开发库存量测试数据，含 `test1..test6`）；`parent_id` 孤儿 0 条；`is_enabled` 全为 1；`icon` 全为 NULL。

**由此得到 4 个硬约束（写接口必须遵守）**：
1. `category.id` 是 `varchar(50)` 且**现有值全是语义化短 id**（`mobile_theme_huawei`），不是 uuid；
2. `category.name` 是 `varchar(20)` —— 中文名最长 20 字；
3. `work_category.id` 是 `varchar(36)`，写入必须给 uuid（`GenerateUUID.getUuid36l()` 恰好 36 位，`utils/GenerateUUID.java:32-34`）；
4. `parent_id` 外键是 **`ON DELETE CASCADE`**：删父分类会**连坐删掉整棵子树**。

### 2.6 一个必须记录的结构漂移（避免后人踩坑）

`BarcBackend/src/main/resources/schema.sql:115-123` 把 `work_category.id` 写成 `BIGINT AUTO_INCREMENT`，**与开发库真实结构（`varchar(36)`）不一致**。`schema.sql` 已于 0.1.16 退役（`spring.sql.init.mode: never`，见 `docs/数据库初始化指南.md` 第三章），但文件仍在仓库里，**任何人再照它判断结构都会得出错误结论**（例如误以为"代码写 uuid 到 BIGINT 列会失败"）。本次不修改该文件（AGENTS 禁止删除 `schema.sql` 中的表），仅在本文留证。

### 2.7 两个"有字段、无消费"的僵尸字段

| 字段 | 现状 |
|---|---|
| `is_enabled` | **全项目无任何 SQL 过滤使用**（`selectAll` / `selectAllTop` 都不过滤）。全库仅实体与前端 interface 声明。→ 就算管理端提供"停用"，用户端也**不会**隐藏 |
| `icon` | 全库仅 `selectAllTop` 取回、前端 `CategoryImpl` 声明；用户端**从不渲染**，且现有 13 条全为 NULL |

---

## 三、生产库 `category` 为空的真实影响面（逐链路）

**先说结论：上传作品不会失败。** 依据：

- `WorkController.java:240` 的 `category_id` 是 `required = false`；
- `WorkService.uploadWorkService`（`:472-482`）只有 `categoryId != null` 时才写 `work_category`，且**写完还包了 try-catch 吞异常**；
- 用户端 `UploadWorkComp.vue:281-286` 仅在选中分类时才 append `category_id`，模板里还提供"无分类"按钮（`:421`）。

具体影响：

| 链路 | 生产（`category` 空）表现 | 严重度 |
|---|---|---|
| 上传作品 | 分类区域显示"分类不存在"（`UploadWorkComp.vue:428-430`），仍可正常提交，作品落库后**无分类关联** | 中（功能空转，非报错） |
| 顶栏导航 | `all_top_categories` 返回空数组 → 分类导航为空 | 高（用户看不到任何分类入口） |
| 分类页 `/category/:id` | 无入口可进；即使手工拼 id，`works_by_category` 也返回空 | 高 |
| 作品详情/列表 | 全站没有任何"分类"展示（两个前端都没有） | 中 |
| 管理端 | **无任何分类维护入口** → 生产无法自救 | 阻塞级（本次要解的核心） |
| `work_category` | 生产为空 ⇒ 与分类相关的历史数据无残留，**将来填充分类后不会出现脏关联** | 有利 |

> 生产库 `ba-prod` 本次**未能核实**：其连接串锁定 `127.0.0.1:51001/ba-prod`（服务器本机），公网入口用生产账号连接被 `Access denied` 拒绝。需要你在服务器上执行附录 A 的只读 SQL 回填事实。

---

## 四、待确认歧义（需要你拍板）

### 4.0 拍板结果汇总（2026-09-19 第 2 轮）

| 编号 | 议题 | 拍板结论 |
|---|---|---|
| Q1 | 层级策略 | **选 A**：管理端维护多层级字典；用户端维持"只显示/只选择顶级分类"，**本轮不改造用户端**；B/C（上传选任意层级、分类页含子树作品）延后单独排期 |
| Q2 | 作品侧范围 | **选 A**：本期只做"分类字典管理"，**不做**作品详情展示/改分类 |
| Q3 | 权限档位 | **选 A**：与作品管理组完全一致 —— `MANAGER` 身份 + `SEC_MAINTAINER` **1匹配**（读写同档） |
| Q5 | 删除策略 | **选 A**：**禁止删除**"有子分类"或"有作品关联"的分类；仅允许删除无任何关联的分类，并在 UI 给出明确原因 |
| Q6 | 生产数据初始化 | **选 B**：**不做** Flyway 种子迁移；先上线管理页面，生产由管理员**手工录入** |
| Q10 | 生产分类清单 | 生产照搬开发库现有 **13 条 / 3 层**（模型、美化、模组、游戏 及其子级），名称与层级一致 |
| Q4 | 新增分类 `id` | **选 A**：管理员**手填英文语义 id**（服务端正则 `^[a-z0-9_]{1,50}$` + 全局唯一校验），与存量 13 条风格一致，生产录入可与开发库逐字对齐 |
| Q11 | 移动父分类 | **选 A**：**允许**移动（改 `parent_id` + 事务内级联重算整棵子树 `level` + 防环校验） |
| Q12 | 排序交互 | **选 B**：提供**上移 / 下移按钮**（不做拖拽）；后端按"同级规范化重排 + 相邻交换"实现，规避存量 `sort` 重复值 |
| Q7 | `icon` / `is_enabled` | **选 A**：**都不做**（两字段当前零消费，不做以免新增死字段） |
| Q9 | 审计 | **选 A**：**不新增审计表**，仅在服务端 `log.info` 记录操作者与变更内容 |

**最终实施口径（第 3 轮汇总）**：范围 = 管理端「作品管理」组新增**作品分类管理**页面 + 对应后端管理接口；支持**多层级**维护（新增 / 编辑 / 移动 / 上下移 / 删除）；用户端与作品侧**零改动**；权限 `SEC_MAINTAINER` 1匹配；删除受 Q5 约束；`id` 手填且不可改；生产分类由管理员手工录入 13 条。

> 由 Q6+B 与 Q10 的组合推出的**硬约束**：管理端页面是生产恢复分类的**唯一途径**，因此
> 1）上线顺序必须是"含分类管理页面的版本"先行，再由管理员录入；
> 2）页面必须提供足够顺手的录入交互（见 §5.2 的"同级/子级连续新增"），否则 13 条 3 层手工录入成本过高且易错；
> 3）录入结果需与原结构逐条核对（见 §7 验收清单的对照项）。

> 每项都给出选项 + 我的建议；标注「建议」的项按你的判断可改。

### Q1（最关键）层级策略：用户端要不要真正用上多层级？

| 选项 | 含义 | 代价 |
|---|---|---|
| **A（建议先做）** | 管理端支持维护**多层级**字典（含树形展示），但**用户端维持现状**：仍只显示/选择顶级分类 | 用户端零改动，生产当天可用；二级/三级仍是"看得见、用不上" |
| B | 管理端多层级 + 用户端上传可选到**任意层级**（级联选择） | 需改 `UploadWorkComp` 交互 |
| C | 在 B 基础上，分类页改为**含子树作品**（把死代码 `selectByCategoryId` 的 `WITH RECURSIVE` 接到 `works_by_category`） | 需改后端查询 + 前端级联，回归面最大 |

我的倾向：**本次只做 A**，把"分类可维护"这一空缺先补上；B/C 作为第二阶段单独排期（否则一次改动同时动后端查询语义 + 双端交互，风险与验证成本都高）。
无论选哪个，请确认一句：**"父分类页是否应当显示子分类下的作品"** —— 这是 A 与 C 的分水岭。

### Q2 管理端要不要"给作品改分类"？

- 现状：后端唯一的两条写关联链路都**只允许作品作者本人**（`WorkCategoryService.recordWorkAndCategoryService:31-33` 校验 `work.author == uuid`；`deleteWorkCategoryService:46-50` 同）；管理端**没有任何**作品-分类关联管理接口。
- 选项：
  - **A（建议）** 本期只做"分类字典管理"（你原话的需求），作品侧暂不动；
  - B 顺带在管理端「作品详情」里**只读展示**作品所属分类；
  - C 顺带支持管理端**增/删作品的分类关联**（需新管理接口 + 权限 + 审计，工作量最大）。

### Q3 权限级别？

- 现有作品管理接口统一用：`MANAGER` 身份 + `SEC_MAINTAINER` **1匹配**（`WorkManageService` 全文，例 `:102`）。
- 选项：
  - A 与作品管理组保持一致：`SEC_MAINTAINER` 1匹配（读+写同一档）；
  - **B（建议）** 读用 `SEC_MAINTAINER`，**写（增/改/删）用 `ADMINISTRATOR` 比大小**——因为分类是全局字典，改动会直接改变用户端导航；
  - C 全部提到 `ADVANCED_ADMINISTRATOR`（馆长），与"节日管理"同档。

### Q4 新增分类的 `id` 怎么生成？

- 现状：13 条全是**语义化英文小写 id**（`3d_model`、`mobile_theme_huawei`），可读性好但依赖人工规范。
- 选项：
  - **A（建议）** 管理员手填 id + 后端校验（`^[a-z0-9_]{1,50}$`、全局唯一，且不允许中文/大写）；风格与存量一致，便于运维排查；
  - B 后端生成 uuid：绝不冲突，但与存量 13 条风格割裂；
  - C 手填 id 可选留空 → 留空时后端生成 uuid（混合，最灵活但语义最乱）。

### Q5 删除策略（FK 是 `ON DELETE CASCADE`）？

- 事实：删父分类 → **级联删除整棵子树**；删任意分类 → **级联删除该分类下的 `work_category` 关联**（作品不会删，只是丢分类）。
- 选项：
  - **A（建议）** **禁止删除**"有子分类"或"有作品关联"的分类，按钮给出明确原因（要求先清空/迁移）；无关联的分类可删；
  - B 允许删，但弹二次确认并列出影响面（将删除 N 个子分类 / 解除 M 个作品分类）；
  - C 不提供删除，只提供"停用"——**注意**：停用要生效必须改用户端查询过滤 `is_enabled`（见 2.7），否则是假开关。

### Q6 生产数据怎么初始化？（`category` 现在就是空的）

- 选项：
  - **A（建议）** 新增一条 **Flyway 迁移（V12）幂等种子数据**，写入现有 13 条分类（`INSERT ... ON DUPLICATE KEY UPDATE` 或 `INSERT IGNORE`），开发库已存在则无副作用，生产库随 JAR 启动自动补齐；
  - B 只上线管理模块，生产由管理员在页面**手工录入** 13 条（页面可用后完全可行，但一次录 13 条含 3 层结构，易录错且依赖人工对齐层级）；
  - C 按 `docs/数据库初始化指南.md` §4.7 用 `mysqldump --no-create-info` 从开发库导 `category` 数据；
  - D A+C 都做（迁移兜底 + 文档指引）。
- 请同时确认：**生产要用的分类是否就是开发库这 13 条**？若你希望生产从"干净的分类体系"重新开始（例如只要 4 个顶级分类），请给出目标清单，我按清单落种子。

### Q7 `icon` / `is_enabled` 要不要在本期做实？

- **建议：都不做**。`icon` 用户端从不渲染，做了就是新的死字段；`is_enabled` 若要做实，必须同时改 `selectAllTop`（并考虑"全部停用会导致顶栏无导航"的兜底），建议单独排期。

### Q8（需你操作）生产库结构核实

请在生产服务器执行附录 A 的 5 条只读 SQL，把结果贴给我。重点确认 3 件事：
1. 生产 `category` 是否**存在且为空**（还是"表都不存在"）；
2. 生产 `work_category.id` 是否为 `varchar(36)`（基线应已从开发库带过来）；
3. 生产 `work_category` 是否为空（决定是否需要处理历史脏关联）。

### Q9 分类变更要不要审计？

- 现有 `work_operation_log` 是**作品操作**日志（`WorkManageService:470` 写入）。分类字典变更**没有**对应审计表。
- **建议**：本期不新增审计表，仅在服务端 `log.info` 记录操作者与变更内容（如你要求强审计，我会另加表，工作量 +1 天量级）。

### Q11（新增）编辑时是否允许"把分类移动到另一个父分类"？

- 事实：`level` 由父级决定；移动节点必然要**级联重算整棵子树的 `level`**，且要做**防环校验**（不能把 A 挂到 A 的后代下）。
- 选项：
  - **A（建议）** 允许移动（改 `parent_id` + 事务内级联重算子树 `level` + 防环），因为分类体系维护中"归类调整"很常见；
  - B 本轮不允许移动（编辑仅限名称 / 排序），需要调整层级时删除重建 —— 更简单，但与 Q5"禁止删除有作品的分类"冲突（有作品的分类将无法再调整层级）。

### Q12（新增）排序 `sort` 用什么交互？

- 事实：存量 `sort` 存在重复（level 2 全是 1、level 3 全是 2），说明历史上仅作粗排；`CategoryService.sortCategories` 只按 `sort` 升序稳定排序。
- 选项：
  - **A（建议）** 手填数字 `sort`（表单数字输入 + 列表展示），实现简单、可精确控制；
  - B 提供"上移 / 下移"按钮（自动与相邻同级交换 `sort`），体验更好但需额外的同级重排逻辑；
  - C 两者都要（手填 + 上移下移）。

### Q13（新增）分类 `id` 是否允许编辑？

- **既定结论（无需你选）**：**不允许**。`id` 是关联键，`work_category.category_id` 的外键未声明 `ON UPDATE CASCADE`（只声明了 `ON DELETE CASCADE`），改 `id` 会被数据库拒绝或留下坏关联。新增时填写、之后只读。

---

## 五、方案骨架（Q1~Q9 拍板后细化并实施）

### 5.1 后端（预计新增/改动）

| 文件 | 动作 |
|---|---|
| `api/mapper/CategoryMapper.java` | 补 `insert` / `update` / `deleteById` / `selectById` / `selectChildrenByParentId` / `countWorksByCategory` / `countChildren` / `existsNameUnderParent` |
| `api/service/CategoryManageService.java`（新） | 业务校验 + 树组装 + 权限切面；`level` 由后端按 `parent_id` 派生（禁止前端传入）；防环、防重复名、防非法 id |
| `api/controller/CategoryManageController.java`（新，`/api/category/manage`） | 列表（树）/ 新增 / 修改 / 删除（+ 可选启停）；**不复用** `@IgnoreAuth` 的公开 `CategoryController`，两条链路分开，避免公开接口被误开写权限 |
| ~~Flyway `V12__seed_category_data.sql`~~ | **Q6 选 B，本轮不做**（生产由管理端手工录入 13 条） |

**最终接口清单（新增，全部走 `MANAGER` + `SEC_MAINTAINER` 1匹配）**：

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/category/manage/list` | 分类树（含每个分类的**直接作品数**与**子分类数**，供删除约束判定与列表展示） |
| POST | `/api/category/manage/create` | 新增：`id`（手填、正则+唯一）、`name`、`parent_id`（可空）、`sort`（可空，缺省取同级末尾） |
| PUT | `/api/category/manage/{id}` | 编辑：`name`、`sort`、`parent_id`（变化即移动，级联重算子树 `level` + 防环） |
| PUT | `/api/category/manage/{id}/sort?direction=up\|down` | 同级上移 / 下移（先规范化同级 `sort` 为 1..n，再与相邻项交换） |
| DELETE | `/api/category/manage/{id}` | 删除：**有子分类或有作品关联时拒绝**并返回原因，仅无关联分类可删 |

**服务端校验规则（集中在一处，前端同步做即时提示）**：
1. `id`：必填、`^[a-z0-9_]{1,50}$`、全局唯一、**创建后不可修改**（`work_category.category_id` 外键无 `ON UPDATE CASCADE`）；
2. `name`：trim 后非空、长度 ≤ 20（列宽 `varchar(20)`）、**同一父级下不允许重名**（不同父级允许同名）；
3. `parent_id`：必须存在；不可指向自身或自身后代（防环）；`level` 由服务端派生（`parent == null ? 1 : parent.level + 1`），**不接受前端传入**；
4. 移动：在同一事务内改 `parent_id` 并按 `delta = newLevel - oldLevel` **逐条更新整棵子树 `level`**（子树规模为个位数，逐条更新比 CTE UPDATE 更可控、更易单测）；
5. 删除：`子分类数 == 0 && 作品数 == 0` 才允许；否则返回中文原因（含具体数量）；
6. 全部写操作 `log.info` 记录操作者 uuid 与变更内容（Q9）。

**兼容性**：只新增接口与新增方法，不改现有 4 个公开读接口的返回结构 → **BarcFrontend / BarcManageFrontendV2 双端零破坏**。

### 5.2 管理端 V2（预计新增/改动）

| 文件 | 动作 |
|---|---|
| `src/modules/works/api/categories.service.ts`（新） | 分类 CRUD 请求封装（沿用 `shared/api/http.ts` 的 `http.get/post/put/delete`，拦截器已自动剥 envelope） |
| `src/modules/works/views/WorkCategoriesView.vue`（新） | 树形表格（`el-table` + `row-key`/`tree-props`）+ 新增/编辑对话框 + 删除确认（展示影响面）。**为配合 Q6+B 的手工录入，对话框提供"保存并继续新增同级 / 子级"**，让 13 条 3 层结构可快速录入、逐层核验 |
| `src/app/router/routes.ts` | 新增路由 `works/categories`，`menuGroup: 'works'`、`menuGroupLabel: '作品管理'`、`menuOrder` 取空位（建议 15）、`minManagerPermissionBit` 待 Q3 定 |
| `src/modules/works/__tests__/`（新 spec） | 页面与 service 单测（AGENTS 要求 ≥90%） |

### 5.3 数据初始化（Q6）

若选 A/D：种子数据必须**幂等**且**不覆盖生产未来的人工修改**（建议 `INSERT IGNORE`，只在缺失时补齐，绝不改写已有行）。

### 5.4 明确不在本期范围（除非你要求）

- 用户端多层级选择与级联 UI（Q1 的 B/C）；
- 分类页递归子树作品（Q1 的 C）；
- `icon` 上传（COS 链路）与 `is_enabled` 停用生效；
- 作品详情/列表的分类展示与筛选（Q2 的 B/C）；
- 管理端把分类变更写审计表（Q9）。

---

## 六、影响边界与风险

| 风险 | 说明 | 控制 |
|---|---|---|
| 删分类级联丢关联 | `category_ibfk_1` 级联删子树；`work_category_ibfk_2` 级联删作品关联 | 按 Q5 定策略；删除前 UI 明确影响面 |
| 改分类影响用户端导航 | 顶级分类直接决定顶栏菜单项 | 写操作权限收敛（Q3）+ 关键操作二次确认 |
| 全部停用 / 清空顶级分类 | 顶栏分类导航变空 | 至少保留 1 个启用顶级分类的服务端校验（若做启停） |
| `id` 与 `name` 长度 | `id ≤ 50`、`name ≤ 20`（中文按字符计） | 服务端校验 + 前端 `maxlength` |
| `level` 与 `parent_id` 不一致 | 手工改库会造成树错乱 | `level` 由后端派生，禁止直接编辑 |
| 结构漂移误导 | `schema.sql` 的 `work_category.id BIGINT` 与真库不符 | 本文 2.6 留证；实施时不引用 `schema.sql` |
| 双端兼容 | 新增接口零破坏；**唯一例外**是若做 `is_enabled` 过滤（本期不做） | 按 5.4 范围控制 |

**回滚方案**：后端为纯新增（删除新类 + 撤销路由注册即回到现状）；管理端为新增页面（移除路由即下线）；若 Q6 选了 Flyway 种子，回滚只需 `DELETE FROM category WHERE id IN (...)`（**需你确认后执行**，我不会擅自清库）。

---

## 七、验收清单（拍板后据此实施与自测）

- [ ] 后端：分类 CRUD 接口全部经权限切面（`MANAGER` 身份 + `SEC_MAINTAINER` **1匹配**；不符合时返回 `insufficientAccountPermission`）
- [ ] 后端：`level` 自动派生；防环（不能把某分类挂到自己的后代下）；同级重名拒绝；`id` 格式与唯一性校验
- [ ] 后端：删除按 Q5 策略执行，并在响应中返回影响面（子分类数 / 作品数）
- [ ] 后端：单元测试覆盖率 ≥90%，`./mvnw clean package` 通过
- [ ] 管理端：新菜单出现在「作品管理」组，权限不符时菜单隐藏且直访 403
- [ ] 管理端：树形展示 3 层结构正确（用开发库 13 条实测数据验证）
- [ ] 管理端：新增/编辑/删除交互闭环，含空态与错误提示；删除时按 Q5 **拒删**有子分类/有作品关联的分类并说明原因
- [ ] 管理端：按 Q6+B 路径**能完整录入 13 条 / 3 层分类**，录入结果与本文 §2.5 对照表逐条一致（名称、层级、sort）
- [ ] 管理端：`npm run build` + `npm run test`（覆盖率 ≥90%）通过
- [ ] 用户端：`all_top_categories` 返回新分类后，顶栏导航与上传页分类按钮正确显示（**生产实测需你授权，我不会擅自连生产**）
- [ ] 文档：更新 `docs/功能文档.md`（分类模块接口表已过时：`/api/categories_all` 等旧路径）+ `docs/结构文档.md`
- [ ] 提交：按 AGENTS §13 规范提交（**需你拍板后才开工，开工后也只 commit 不 push**）

---

## 八、沟通记录

### 第 1 轮（2026-09-19）— 勘探与方案

- 完成：分类链路全量勘探（后端读写入口、用户端消费点、管理端空缺）、开发库结构只读核实、生产库影响面推演。
- 产出：本文档；待确认项 Q1~Q9。
- 阻塞项：Q1（层级策略）、Q3（权限）、Q4（id 生成）、Q5（删除策略）、Q6（生产数据初始化）必须先答；Q8 需你在服务器执行附录 A 只读 SQL。
- 未做（按你的要求）：未写任何业务代码、未提交、未修改任何数据库。

### 第 2 轮（2026-09-19）— 拍板 6 项

- 你的拍板：Q1=A、Q2=A、Q3=A、Q5=A、Q6=B、Q10=照搬开发库 13 条 / 3 层（见 §4.0 汇总表）。
- 本轮据此修订：状态、§4.0 汇总、§5.1（取消种子迁移）、§5.2（新增"保存并继续新增"以配合手工录入）、§7 验收清单（权限档位、13 条录入对照项）。
- 本轮新增歧义：Q11（是否允许移动父分类）、Q12（排序交互）、Q13（`id` 不可编辑，已定）。
- 仍待你回答：**Q4（新增分类 `id` 的生成方式）**、Q7（`icon` / `is_enabled` 是否做实）、Q9（分类变更是否要审计表）、Q11、Q12。
- 仍待你操作：Q8（附录 A 五条只读 SQL，在生产服务器执行后回填结果）。
- 未做（按你的要求）：未写任何业务代码、未提交、未修改任何数据库。

### 第 3 轮（2026-09-19）— 拍板收口，转入实施

- 你的拍板：Q4=A（手填语义 id）、Q11=A（允许移动父分类）、Q12=B（上移/下移按钮）、Q7=A（`icon`/`is_enabled` 都不做）、Q9=A（不新增审计表，仅 `log`）。
- 至此 **Q1~Q13 全部有结论**，S 级歧义清零，具备开工条件。
- 本轮据此修订：状态行、§4.0 汇总表（补 Q4/Q7/Q9/Q11/Q12）、§5.1 追加「最终接口清单 + 服务端校验规则」。
- 唯一遗留（**不阻塞开工，阻塞上线前的生产数据核对**）：Q8 —— 需你在生产服务器执行附录 A 的 5 条只读 SQL 并回填结果。
- 下一步：按 §5 与 §7 实施（后端 → 管理端 → 测试 → 构建 → 文档 → 提交）。

### 第 4 轮（2026-09-19）— 实施完成（待你验收后提交）

**后端（全部新增，公开只读接口零改动）**
- `api/mapper/CategoryMapper.java`：新增 `selectById` / `selectManageList` / `selectByParentId` / `countChildren` / `countWorks` / `selectSubtree`（递归 CTE）/ `countSameNameUnderParent` / `insert` / `update` / `updateSort` / `updateLevel` / `deleteById`；
- `api/model/CategoryManageItem.java`（新）：管理端树形列表项（含 `bound_works`、`children_count`，避免污染公开接口的 `CategoryPojo`）；
- `api/service/CategoryManageService.java`（新）：列表 / 新增 / 修改（含移动与防环）/ 上移下移 / 删除，含 id 与名称校验、level 派生、同级重名、删除约束、`log.info` 审计日志；
- `api/controller/CategoryManageController.java`（新，`/api/category/manage`）：与 `@IgnoreAuth` 的公开 `CategoryController` 物理隔离；
- 测试：`CategoryManageServiceTest` **30 个用例全绿**（`Tests run: 30, Failures: 0, Errors: 0`）；
- 构建：`./mvnw clean package` 通过，产物 `BarcBackend/target/BarcBackend-0.1.16.jar`。

**管理端（V2）**
- `src/modules/works/api/categories.service.ts`（新）：请求封装 + 纯函数（树拍平 / 查找 / 父级选项排除自身与后代 / 表单校验 / 删除阻断原因）；
- `src/modules/works/views/WorkCategoriesView.vue`（新）：树形表格（`default-expand-all`）+ 新增/编辑对话框 + 上移下移 + 删除（有关联时按钮禁用并给出原因）+ **「保存并继续新增」且可在同级/子级间切换**（配合 Q6+B 的手工录入）；
- `src/app/router/routes.ts`：新增 `works/categories`，菜单「作品分类」，`menuGroup: works`、`menuOrder: 15`、`minManagerPermissionBit: SEC_MAINTAINER`；
- 测试：`categories.service.spec.ts`（17）+ `WorkCategoriesView.spec.ts`（9）= **26 个用例全绿**；
- 构建：`npm run build`（`vue-tsc` 类型检查 + `vite build`）通过；管理端全量测试 **109 个用例全绿**。

**文档同步**
- `docs/功能文档.md` §3.5：接口表改为真实路径，补管理端接口与约束；
- `docs/结构文档.md`：控制器表新增 `CategoryManageController`，§6.4 补管理端能力与「用户端只消费顶级分类 / 分类页精确匹配」的事实；
- `docs/数据库初始化指南.md`：§3.1 记录 `work_category.id` 的结构漂移，§4.7 更新 `category` 已有管理端入口。

**仍未做（按约定）**：Q7 的 `icon` / `is_enabled`；Q8 的生产库只读核实（等你执行附录 A）；`git push`（只 commit）。

---

## 附录 A：请在生产服务器执行的只读 SQL（Q8）

```sql
-- 1) 生产库表是否都在
SELECT TABLE_NAME, TABLE_ROWS
FROM information_schema.TABLES
WHERE TABLE_SCHEMA = 'ba-prod' AND TABLE_NAME IN ('category','work_category');

-- 2) 生产结构是否与开发库一致（重点看 id 列类型）
SELECT TABLE_NAME, COLUMN_NAME, COLUMN_TYPE, IS_NULLABLE, COLUMN_KEY, EXTRA
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = 'ba-prod' AND TABLE_NAME IN ('category','work_category')
ORDER BY TABLE_NAME, ORDINAL_POSITION;

-- 3) 生产 category 是否为空
SELECT COUNT(*) AS category_rows FROM ba-prod.category;

-- 4) 生产 work_category 是否为空
SELECT COUNT(*) AS work_category_rows FROM ba-prod.work_category;

-- 5) 生产是否还有历史作品（决定是否需要处理脏关联）
SELECT COUNT(*) AS work_rows FROM ba-prod.work;
```

> 以上全部为只读查询，可直接在生产执行，无写入风险。

## 附录 B：本次勘探使用的只读探测方式（可复现）

- 工具：本机 `D:\Java\jdk-17.0.13` + `~/.m2/.../mysql-connector-j-9.1.0.jar`，临时程序位于系统临时目录（**未写入仓库**）。
- 语句范围：`SHOW CREATE TABLE`、`SELECT`、`information_schema` 查询；**无任何 `INSERT/UPDATE/DELETE/DDL`**。
- 生产库：以生产账号从公网连接 `miaoyu.asia:51001/ba-prod` 被 `Access denied`（该账号仅限服务器本机），故未取到任何生产数据。
