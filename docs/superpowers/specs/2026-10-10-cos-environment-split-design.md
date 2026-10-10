# 对象存储（腾讯云 COS）开发 / 生产环境区分 — 勘探、方案与落码记录（已完成）

> 日期：2026-10-10
> 状态：**Q1~Q14 已全部拍板，落码与验证均已完成**（见 §9「落码记录」）；已本地 `git commit`，**未 push**
> 范围：`BarcBackend`（COS 配置类 / 客户端 / 服务层 / 配置 yaml）、`docs/`（功能文档 / 结构文档 / AI 开发指南登记）
> 说明：本文所有「程序事实」都标注到 `文件:行号`；凡属推断的地方单独标注为「推断」，不与事实混写
> 前置结论：**双端前端零改动**——两端都没有硬编码任何 COS 域名或桶名，图片一律直接使用后端返回的完整 URL（见 §2.6）

---

## 拍板摘要（2026-10-10）

| # | 问题 | 拍板结论 |
|---|---|---|
| Q1 | 四个桶的配置落点 | **基础 `application.yaml` 放开发桶**（`custom_domain` 留空），**生产桶在 `application-prod.yaml` 覆写** |
| Q2 / Q2b / Q13 | `application-prod.yaml`（用户已提供该文件）与配置结构 | 在该文件**追加 `tencent.cos` 覆写段**：image / avatar 两组各写 **`bucket-name` + `custom_domain` 两个键**（`bucket-region` / `base-url` 因 **Q13=B 清理死键**而不再写入）；**数据源段一字不动** |
| Q3 | 配置键名风格 | **保持现状混用**（`bucket-name` 与 `custom_domain` 并存），**不改既有键名** |
| Q4 | GC 队列的桶名 | **`CosGcScheduler` 统一使用「当前环境配置的 image 桶名」删除，忽略 DB 里的 `bucket` 列**（列保留作审计，不做数据迁移） |
| Q5 | `avatar` 头像桶 | **只做配置就位**，代码链路不动（当前 avatar 桶确实无消费方） |
| Q6 | 验证方式 | **单测 + 构建验证**；真实上传**只允许在开发桶做一次**，且需用户明确许可；**严禁在生产环境测试** |
| Q7 | 调试接口 `/hello/test_cos` | **删除**（全仓 0 调用方，且 `@IgnoreAuth` 可签任意 key） |
| Q8 → 被 Q13 取代 | 无读取方的历史字段 | 原拍板「不清理」；**在用户看到逐键消费证据后，改为 Q13=B：清理零消费键** |
| Q9 | 生产 Profile 的 COS 指纹校验 | **新增**：prod 下 `tencent.cos.image.bucket-name` 必须以 `miaoyu-barc-image-` 开头，否则拒绝启动 |
| Q10 | 交付方式 | **改码 + 测试 + 构建验证 + 本地 commit（不 push）**；**代码、配置、文档合并为同一个 commit** |
| Q11 | 开发库老数据 | **不迁移**，接受开发环境引用生产桶 key 的老图 404；文档写明 |
| Q12 | 文档更新范围 | **设计文档 + `docs/结构文档.md` + `docs/AI开发指南.md` + `docs/数据库初始化指南.md`** 共四处登记 |
| Q13 | 配置冗余清理 | **清掉零消费的 `bucket-region`（4 处）、`base-url`（4 处）、`image-prefix`、`temp-prefix`**，并同步删除 `CosConfig.java` 中的对应字段；**保留** 顶层 `bucket-name` / `custom_domain`（兜底）与 `test` 节点 |
| Q14 | `/api/file/delete` 的「上传入 image 桶、强删却删兜底桶」缺陷 | **本次不修**，仅在文档登记为已知问题（§8.4） |

**拍板派生出来的三条硬约束（落码时必须遵守）**

1. **`application.yaml` 的 `tencent.cos` 一旦改成开发桶，生产环境就完全依赖 `application-prod.yaml` 覆写**：当前该文件只有 9 行、只含数据源（`application-prod.yaml:1-9`）。因此 Q2b 的覆写段与基础配置的改动**必须在同一个 commit 内一起落地**，否则一旦单独部署基础配置就会让生产连上开发桶。
2. **Q9 的指纹校验是上述风险的程序化兜底**：它把「生产误连开发桶」从「依赖人工审查」变成「启动即拒绝」，与现有数据源指纹校验（`BarcBackendApplication.java:75-81`）同构。
3. **Q10 的「文档一起 commit」有一个安全前提**：`BarcBackend/.gitignore:8-9` 明确忽略 `**/application.yaml` 与 `**/application-prod.yaml`，本次提交**不得**把这两个文件（含明文密钥）纳入版本控制。提交前必须执行 `git status` 确认这两个文件未出现在待提交列表中（见 §8.2）。

---

## 〇、本次任务的用户口径（原文摘要）

1. 腾讯云现有 4 个桶：
   - 生产：`miaoyu-barc-image-1309572720`、`miaoyu-barc-avatar-1309572720`
   - 开发：`dev-miaoyu-barc-image-1309572720`、`dev-miaoyu-barc-avatar-1309572720`
2. 生产桶**已配置自定义域名**：`https://image-cos-9c98ec3e.barc.work`、`https://avatar-cos-3ef160ef.barc.work`
3. 开发桶**不配自定义域名**，使用腾讯云官方域名：
   `https://dev-miaoyu-barc-image-1309572720.cos.ap-shanghai.myqcloud.com`、
   `https://dev-miaoyu-barc-avatar-1309572720.cos.ap-shanghai.myqcloud.com`
4. 要求：按环境使用不同桶 → 上传到不同桶 → **数据库仍然只存 Key** → 接口返回时的**签名与 URL 拼接随环境变化**。

---

## 一、核心结论（先看这段）

1. **「数据库只存 Key」这条本次不需要动任何代码**：全仓扫描确认，所有落库的图片字段存的都已经是 objectKey，没有任何地方把签名 URL 写进数据库（§2.4）。因此本次改动是**纯配置与解析层**的改动，DAO / 表结构 / 数据库数据**零改动**。
2. **环境区分的现成机制已经存在**：`dev` / `prod` Profile 由启动方式自动判定（`BarcBackendApplication.java:37-53、143-150`），生产 Profile 会在连库前校验配置指纹（`:62-82`）。COS 只要落进同一套机制即可，**不需要新造环境判断**。
3. **真正的改动面很小**：`application.yaml`（补开发桶 + 清空开发自定义域名）、未来的 `application-prod.yaml`（覆写生产桶 + 生产自定义域名），以及 `CosService` / `CosClient` 里「按桶取配置」的取数逻辑（`CosService.java:36-43`、`CosClient.java:29-85`）。**`CosService` 对外方法签名一律不变**（宪法 §12）。
4. **代码里「按桶类型取端点」原来只看 3 个桶的字段**（`test` / `avatar` / `image`）外加一个 `default` 分支走顶层字段。要让「同一个桶类型在 dev/prod 拿到不同桶名 + 不同端点」，只需让这两处**继续读同一份 `CosConfig` 上的对应节点**，由 Profile 覆盖文件去换值即可——**这是本次方案的最小改动路径**。
5. **两个必须提醒用户的事实**（都在下面有出处）：
   - ① `avatar` 头像桶当前**没有任何上传链路在用**，`CosBucketConfigEnum.avatar` 只被解析、从未被调用（§2.3 表）；本次把它配好属于「为将来准备」，不是修复现状。
   - ② 开发环境**换桶后看不到生产库里的老图**：老数据的 key 只存在于生产桶，开发库若引用了老 key，签名出来是 404（§2.7 风险 R2）。
6. **一个不得不做决定的历史包袱**：GC 队列 `cos_garbage_key.bucket` 里**存的是当时配置的桶名**（`CosGcMapper.java:9`、`CosGcService.java:16-20`、`StudentService.java:234/240` 等 6 处），而删除时**直接用这一列当桶名去删**（`CosGcScheduler.java:32-35`）。换桶（或换环境）后，这一列里躺着的旧桶名会指向错误目标 —— 见 Q4。

---

## 二、现状链路（程序事实）

### 2.1 环境区分链路（已有，本次复用）

| 事实 | 位置 |
|---|---|
| 启动时按 class 来源判定默认 Profile：`jar` → `prod`，其余 → `dev`，无法识别时兜底 `dev` | `BarcBackendApplication.java:143-150` |
| 显式 `--spring.profiles.active` / JVM 系统属性 / 环境变量优先，跳过自动判定 | `:101-116` |
| `dev` 与 `prod` 同时激活 → 拒绝启动 | `:63-67` |
| 生产 Profile：`application-prod.yaml` 缺失 → 拒绝启动；`spring.datasource.url` 不以 `jdbc:mysql://127.0.0.1:51001/ba-prod` 开头 → 拒绝启动 | `:62-82` |
| 现有环境判定测试（16 个用例，`MockEnvironment` 驱动） | `BarcBackendApplicationTest.java:19-224` |
| 该测试完整覆盖「生产配置缺失 / 生产指纹不符 / 双 Profile 冲突 / 显式 Profile 识别」四类分支 | 同上 |

> **结论**：本次 COS 环境区分**必须复用**这套机制，禁止新写一个「读某个自定义 flag 判断环境」的第二套标准。

### 2.2 COS 配置与客户端链路

| 事实 | 位置 |
|---|---|
| 配置类绑定前缀 `tencent.cos`；顶层含 `secretId/secretKey/region/bucketName/bucketRegion/baseUrl/imagePrefix/tempPrefix/customDomain`；三个子节点 `avatar` / `test` / `image`（内层 `CosBucketPojo`：`bucketName/bucketRegion/baseUrl/customDomain`） | `CosConfig.java:16-39` |
| 桶枚举：`test` / `avatar` / `image` / `file` | `CosBucketConfigEnum.java:10-14` |
| 上传 / 删除 / 移动时**按枚举解析桶名** | `CosService.java:36-43` |
| 客户端：`clientName == null` → 兜底 `test`；按枚举分支构建 `COSClient`，**若该桶 `customDomain` 非空则用 `CustomEndpointBuilder` 换掉 SDK 默认端点** | `CosClient.java:29-85` |
| 自定义端点构造器（把 `https://域名` 拆成 host + protocol 交给 SDK） | `CustomEndpointBuilder.java:13-34` |
| 预签名 URL：由 SDK `generatePresignedUrl` 生成，并附加 `response-content-disposition=inline` | `CosService.java:165-176` |
| 批量签名：`parallelStream` 逐个调用单签 | `CosService.java:185-189` |
| `baseUrl`（顶层 + 各桶）**全项目仅出现在 `CosConfig.java:25、37` 两行定义处，没有任何读取方** | grep 全仓结果 |
| `imagePrefix` / `tempPrefix` **同样只有定义、没有读取方** | 同上 |
| `moveFile()` **全项目无调用方** | grep 全仓结果 |
| `bucketRegion`（顶层 + 各桶）**无读取方**；SDK 实际用的是顶层 `region` | `CosClient.java:42/52/62/73` |

> **推断**：`baseUrl` / `imagePrefix` / `tempPrefix` / `bucketRegion` 是从早期版本遗留下来、或为将来预留的字段。当前「签名 URL 的域名从哪来」**完全由 `CustomEndpointBuilder` + SDK 决定**，与 `baseUrl` 无关。
>
> **本次设计要点**：若要让「开发用官方域名、生产用自定义域名」这条规则**只在一份配置里表达**，最干净的做法是**继续沿用 `customDomain` 作为唯一开关**（配了就换端点、没配就用官方域名），而不是新增一个「URL 拼接前缀」字段。是否顺带清理无读取方的字段见 Q8。

### 2.3 COS 消费方全景（一个都不能漏）

**A. 上传链路（会写入桶）**

| # | 业务 | 目标桶 | 代码位置 |
|---|---|---|---|
| 1 | 通用文件上传 `/api/file/upload`（学园/部团/学生三图走这里） | **image**（控制器固定传 `null` → 服务层兜底 `image`） | `FileController.java:20-27`、`FileService.java:20-41` |
| 2 | 作品创建（封面 + 图集，`@Transactional`） | image | `WorkService.java:439-542`（上传 `:498`、`:522`） |
| 3 | 作者替换作品封面 | image | `WorkService.java:335-367`（`:341`） |
| 4 | 作品图集增补 | image | `WorkImageService.java:78-105`（`:86`） |
| 5 | 背景图上传（管理端） | image | `BackgroundImageService.java:72-123`（`:103`） |
| 6 | 友情链接 logo 上传（管理端） | image | `FriendLinkService.java:91-105`（`:104`） |

**B. 删除链路（会删桶内对象）**

| # | 业务 | 目标桶 | 代码位置 |
|---|---|---|---|
| 7 | 通用文件强删 `/api/file/delete` | `null` → 兜底 test 的桶 | `FileService.java:46-49` |
| 8 | 作品封面替换后删旧图 / 失败回滚删新图 | image | `WorkService.java:359-362` |
| 9 | 作品创建失败补偿删除 | image | `WorkService.java:516、557、562` |
| 10 | 图集单张删除 | image | `WorkImageService.java:104` |
| 11 | 异步 GC 队列消费（定时，5 分钟） | **用 DB 里存的桶名** | `CosGcScheduler.java:27-43`（`:34-35`） |

**C. 签名 / URL 返回链路（本次最关心的一条）**

| # | 接口 | 解析方式 | 代码位置 |
|---|---|---|---|
| 12 | 作品详情（用户端） | `cosService.generateSignedUrl(..., image)` | `WorkService.java:237` |
| 13 | 作者侧 / 管理端作品编辑详情（封面 + 图集批量） | 同上 + `generateBatchSignedUrl` | `WorkService.java:378-390` |
| 14 | 作品列表 / 点赞列表等批量封面 | `generateBatchSignedUrl` | `WorkService.java:590、629-630` |
| 15 | 管理端作品详情 | `generateSignedUrl` + `generateBatchSignedUrl` | `WorkManageService.java:203-215` |
| 16 | 图集批量签名（`/api/work/image/...`） | `generateBatchSignedUrl` | `WorkImageService.java:59` |
| 17 | 学园 / 部团 / 学生 三图、友链 logo、背景图 | **统一走 `ImageUrlResolver.resolve`**（内部固定 `image` 桶，1 小时有效） | `ImageUrlResolver.java:13-20`；调用点 `ClubService.java:162-163`、`SchoolService.java:136-138`、`StudentService.java:226-230`、`FriendLinkService.java:280、395`、`BackgroundImageService.java:263、297` |
| 18 | 调试接口 `/hello/test_cos?key=`（`@IgnoreAuth`，无鉴权可签任意 key） | `generateSignedUrl(key, null)` → 兜底 test 桶 | `HelloWorldController.java:57-66` |

**D. 不经过 COS 的图片链路（本次明确排除，避免误伤）**

| 业务 | 实际存储 | 位置 |
|---|---|---|
| 动态 / 趋势图（trend） | **MinIO**（`https://file.naigos.cn:52011/...`），与 COS 无关 | `TrendService.java:88`、`TrendImageService.java:45`、`MinioObjects.java:26-45` |
| 用户头像 `user_archive.avatar` | **奶果外部 URL**（注册时写入 `https://file.naigos.cn:52011/avatar/...`） | `SignService.java:177`、`SignService.java:115` |
| 公告 / 反馈 / 评论 / 留言板 / 作品打标等 | 完全不涉及对象存储 | 见 `2026-09-21-friend-link-design.md:375-377` |

> **因此**：`CosBucketConfigEnum.avatar` 与 `miaoyu-barc-avatar-*` / `dev-miaoyu-barc-avatar-*` 当前**没有消费方**；`user_archive.avatar` 也不是 COS key。头像桶本次只做「配置就位」，是否要顺手让某些三图字段改用它见 Q5。

### 2.4 「数据库只存 Key」的现状核实

| 表 / 字段 | 存的内容 | 证据 |
|---|---|---|
| `work_cover_image.object_key` | COS key | `WorkService.java:353`、`WorkCoverImageModel` |
| `work_image.object_key` | COS key | `WorkImageService.java:102` |
| `background_image.object_key` | COS key | `BackgroundImageService.java:111` |
| `friend_link.logo_key` | COS key | `FriendLinkService.java:137` |
| `student.avatar_square / avatar_rectangle / body_image` | key 或历史完整 URL（`ImageUrlResolver` 对 `http(s)://` 开头的值原样返回） | `StudentService.java:226-230`、`ImageUrlResolver.java:17` |
| `school.logo / beautify_logo / bg`、`club.logo / bg` | 同上 | `SchoolService.java:136-138`、`ClubService.java:162-163` |
| `cos_garbage_key.bucket` | **桶名**（不是 key，是本次唯一的例外） | `CosGcMapper.java:9`、`V1__add_soft_delete_and_gc.sql:14-24` |

> 结论：**没有任何地方把签名 URL 落库**；`cos_garbage_key.bucket` 是唯一一处「把桶相关值写进数据库」的地方，见 Q4。

### 2.5 现有配置实况（`application.yaml:83-109`）

```yaml
tencent:
  cos:
    image:
      bucket-name: miaoyu-barc-image-1309572720          # ← 生产桶，配在开发用的基础配置里
      bucket-region: ap-shanghai
      base-url: https://miaoyu-barc-image-1309572720.cos.ap-shanghai.myqcloud.com
      custom_domain: https://image-cos-9c98ec3e.barc.work
    avatar:
      bucket-name: miaoyu-barc-avatar-1309572720         # ← 同上
      bucket-region: ap-shanghai
      base-url: https://miaoyu-barc-avatar-1309572720.cos.ap-shanghai.myqcloud.com
      custom_domain: https://avatar-cos-3ef160ef.barc.work
    test:
      bucket-name: test-default-1309572720
      bucket-region: ap-shanghai
      base-url: https://test-default-1309572720.cos.ap-shanghai.myqcloud.com
      custom_domain: https://cos.barc.work
    secret_id: <已脱敏>                              # 明文密钥（既存事实，本次禁止改动，宪法 §15）
    secret-key: <已脱敏>
    region: ap-shanghai
    bucket-name: test-default-1309572720                 # 顶层兜底桶
    bucket-region: ap-shanghai
    base-url: https://cos.barc.work
    image-prefix: images/
    temp-prefix: temp/
    custom_domain: https://cos.barc.work                 # 顶层兜底自定义域名
```

三个关键事实：

1. **当前 `application.yaml` 里的 image / avatar 桶就是生产桶**，且**带着生产自定义域名**。也就是说：**今天在 IDE 里跑后端（dev Profile），上传会落到生产桶，返回的签名 URL 也走生产自定义域名**（`CosClient.java:64-69`）。这正是本次要修的现状问题。**同时本节这份「现状」也说明：真正决定环境的只有 `bucket-name` 与 `custom_domain` 两个键**（逐键证据见 §2.8）。
2. **`application-prod.yaml` 由用户提供，且当前只含数据源覆写**：文件共 9 行（`application-prod.yaml:1-9`），内容是 `spring.datasource.url/username/password` 三项，`tencent.cos` **完全继承基础配置**。这解释了一个重要事实：**今天生产之所以能用对桶，纯粹是因为 `application.yaml` 里写的就是生产桶**；一旦基础配置改为开发桶，`application-prod.yaml` **必须**补上 COS 覆写段（Q2b 拍板：连 `bucket-region` / `base-url` 等未变项一并写全）。该文件被 `.gitignore:9` 忽略，不入版本控制。
3. 配置项命名**风格不一致**：`bucket-name`（kebab）与 `custom_domain`（下划线）混用（`:88-89`）。Spring Boot 宽松绑定两种都能映射，功能上无害；Q3=B 决定本次**不改**既有键名。

### 2.6 前端侧核实（为什么说「双端零改动」）

| 事实 | 位置 |
|---|---|
| 用户端默认 API 基址只写 `https://api.barc.work`，**无任何 COS 域名 / 桶名** | `BarcFrontend/src/utils/https.ts:4` |
| 管理端同上 | `BarcManageFrontendV2/src/shared/constants/auth.ts:2` |
| 用户端全仓 grep `myqcloud` / `barc.work` / `cos-`：**0 处命中** | grep 结果 |
| 管理端全仓 grep 同样关键词：仅 1 处测试里的邮箱字符串 | grep 结果 |
| 图片渲染方式：`<img :src="item.cover_image">` 等**直接使用后端 URL**，不做二次拼接 | `WorkItemListComp.vue:58`、`WorkDetailComp.vue:250`、`AccountWorkListComp.vue:43`、`StudentBgComp.vue:36`、`HomeFriendLinkListComp.vue:31` |
| 友链组件测试甚至断言「后端给什么 URL 就渲染什么」 | `HomeFriendLinkListComp.spec.ts:64` |

> **结论**：只要后端返回的仍是「可直接访问的完整 URL」，两端连一个字符都不用改；本次**不改双端**，但仍需按宪法 §11 做双端兼容性核查（§6.4）。

### 2.7 风险与边界（先说清楚，避免落码后返工）

| 编号 | 风险 / 边界 | 说明与处置 |
|---|---|---|
| R1 | **老数据跨环境不可见** | 开发库中若存在引用「生产桶 key」的记录，切到开发桶后签名出的 URL 必然 404。**这是换桶的固有代价，不是 bug**；处置：文档写明，不做数据迁移（Q11 确认） |
| R2 | **生产自定义域名的签名行为必须在生产验证** | 当前代码已经依赖 `CustomEndpointBuilder` 生成签名 URL（`CosClient.java:43-49`），生产早已在用；本次**不改变该机制**，但按宪法「禁止在生产环境测试」，生产侧的端到端验证只能由用户在真实部署时确认（Q6） |
| R3 | **GC 队列里可能遗留旧桶名** | 见 Q4 |
| R4 | **`/hello/test_cos` 无鉴权可签任意 key** | `HelloWorldController.java:57-66`；`@IgnoreAuth` + 兜底 test 桶。生产环境暴露该接口等于「任人签名」，且本次会把 test 桶配置留在基础配置里。是否清理见 Q7 |
| R5 | **密钥仍是明文，且开发/生产共用同一对** | `application.yaml:100-101`；宪法 §15 禁止本次改动密钥。若 dev/prod 需要不同密钥（例如给开发桶单独的子账号密钥），需用户另行提供（不在本次范围，仅登记） |
| R6 | **`application-prod.yaml` 不入 Git** | `.gitignore:9`。本次若新建该文件，它不会被提交，需随部署产物下发（与数据源配置同一约定，见 `数据库初始化指南.md:61`） |

### 2.8 逐键消费证据（Q13 的判定依据，2026-10-10 补测）

对 `tencent.cos` 下**每一个键**做全仓 grep（`main` + `test`），结果如下。这是 Q13 清理死键的直接依据，落码时**按此表执行**。

**① 真正被消费的键（必须保留）**

| 键 | 唯一消费点 | 作用 |
|---|---|---|
| `secret_id` / `secret-key` | `CosClient.java:30-33` | 构造 `BasicCOSCredentials` |
| `region`（顶层） | `CosClient.java:42、52、62、73` | 构造 `new Region(...)`；**四个分支全部读顶层 region，与各桶的 `bucket-region` 无关** |
| `image.bucket-name` | `CosService.java:39` | 上传 / 删除 / 签名的目标桶 |
| `avatar.bucket-name` | `CosService.java:38` | 同上（当前无上传消费方，仅枚举解析需要） |
| `test.bucket-name` | `CosService.java:40` | 仅在 clientName 走 `test` 时使用（见 ②） |
| `image.custom_domain` | `CosClient.java:64-69` | 非空 → 自定义端点；空 → 腾讯云官方端点 |
| `avatar.custom_domain` | `CosClient.java:54-60` | 同上 |
| `test.custom_domain` | `CosClient.java:44-50` | 同上 |

**② 依赖「传 null」才可达的键（保留，但出口正在收窄）**

| 键 | 可达路径 | 现状 |
|---|---|---|
| 顶层 `bucket-name` | `CosService.resolveBucket()` 的 `default ->`（`CosService.java:41`） | `main` 中**唯一**传 `null` 的调用是 `HelloWorldController.java:64`（Q7=A 删除）；`FileService.java:48` 也传 `null`（Q14=B 不修，保留） |
| 顶层 `custom_domain` | `CosClient.cosClient()` 的 `default ->`（`CosClient.java:75-79`） | 同上；另外 `clientName == null` 会先被改成 `test`（`CosClient.java:36-39`），因此**实际很难走到 `default` 分支** |

> 结论：这两项**保留**（兜底语义有用，且 Q14 保留的 `/api/file/delete` 仍会走到），但它们是「隐性兜底」而非主链路，不能让它们承担环境切换的职责。

**③ 零消费的键（Q13=B：本次删除）**

| 键 | 出现处 | 消费点 |
|---|---|---|
| `bucket-region`（顶层） | `application.yaml:107`；字段 `CosConfig.java:24` | **0 处** |
| `bucket-region`（image / avatar / test 各一份） | `application.yaml:90、95、100`；字段 `CosConfig.java:36` | **0 处** |
| `base-url`（顶层） | `application.yaml:108`；字段 `CosConfig.java:25` | **0 处** |
| `base-url`（image / avatar / test 各一份） | `application.yaml:91、96、101`；字段 `CosConfig.java:37` | **0 处** |
| `image-prefix` | `application.yaml:109`；字段 `CosConfig.java:26` | **0 处** |
| `temp-prefix` | `application.yaml:111`；字段 `CosConfig.java:27` | **0 处** |

> 共 **8 处 `bucket-region` / `base-url` 重复项 + 2 个前缀项**，全部由 `custom_domain` + SDK 官方端点取代。Q13=B 落地后：yaml 删除 10 个键，`CosConfig.java` 删除 6 个字段（顶层 4 个 + 内层 2 个）。
>
> **清理后的完整目标形态见 §4.1。**

**④ 另一个「看起来在用、其实是死的」枚举**

| 项 | 现状 |
|---|---|
| `CosBucketConfigEnum.file` | `CosBucketConfigEnum.java:14` 定义；全仓**无任何调用方**（`CosBucketConfigEnum.file` 在 `main`/`test` 中 0 命中） |
| `CosService.moveFile()` | `CosService.java:140-157` 定义；全仓**无任何调用方** |

> 这两项**本次不动**（Q13 只针对配置死键；动枚举与删方法会触及宪法 §12 的「COS client 代码尽量不要动」），仅在本文档登记。

---

## 三、问题与拍板结论（Q1~Q14，已全部拍板）

> 推荐项即最终采纳项；下表保留「选项」列以记录当时比较过的替代方案。逐条结论见开篇「拍板摘要」。
> **注意**：Q8 的结论已被 Q13 取代（用户看到 §2.8 的逐键消费证据后决定清理死键），**以 Q13 为准**。

| # | 问题 | 选项 | 结论 |
|---|---|---|---|
| **Q1** | 四个桶的配置**放在哪个文件** | **A.** 基础 `application.yaml` 放**开发桶**（并把 `custom_domain` 留空），新建 `application-prod.yaml` 覆写**生产桶 + 生产自定义域名**——与数据源「基础配开发、prod 覆写」完全同构<br>**B.** 全部四个桶都写进 `application.yaml`，由代码按 Profile 选一套（新增环境判断，偏离现状）<br>**C.** `application.yaml` 用 `spring.config.activate.on-profile` 分组写 dev/prod 两套 | ✅ **A**（已拍板） |
| **Q2** | `application-prod.yaml` 当前**不存在**，怎么办 | **A.** 由我新建一份**只含 COS 覆写段**的模板（`bucket-name` + `custom_domain`），数据源段留占位注释，交给用户补全后自行保存（该文件不入 Git）<br>**B.** 不改文件，只在设计文档与 `结构文档.md` 里写清「生产需在 `application-prod.yaml` 追加这一段」<br>**C.** 用户手上已有该文件，稍后自行粘贴（我只给配置片段） | ✅ **用户已提供 `application-prod.yaml` 实体文件**（9 行、只含数据源）→ 采用「在现有文件上追加 `tencent.cos` 覆写段」，详见 Q2b |
| **Q3** | 配置项命名风格 | **A.** 新增项统一用**中划线（kebab）**并**顺便把 `custom_domain` 改成 `custom-domain`**（两处：`application.yaml` 与 `CosConfig` 的绑定不受影响，Spring 宽松绑定同时兼容）<br>**B.** 保持现状混用，只**新增**项，不动旧键名（零风险） | ✅ **B**（保持现状混用，不动旧键名） |
| **Q4** | GC 队列的**桶名入库**怎么处理 | **A.** `CosGcScheduler` 忽略 DB 里的 `bucket` 列，**改用当前环境配置的 image 桶名**删除（DB 列保留、向后兼容）<br>**B.** 保持现状（用 DB 里的桶名），接受「换桶/换环境后队列里的旧桶名会删错目标」<br>**C.** 队列改为**只存 key**，桶由环境决定（需考虑是否兼容既有数据） | ✅ **A**（落码时采用 §4.3 的 **A1**：忽略 DB 列，统一用当前环境 image 桶名） |
| **Q5** | `avatar` 头像桶是否需要**接上** | **A.** 本次只**配置就位**（dev/prod 各一套桶名与端点），代码链路不动——与「当前无消费方」的现状一致<br>**B.** 顺带把「学生三图」或「用户头像」改成走 avatar 桶（**会改到上传与签名链路，触及更多接口**） | ✅ **A**（已拍板） |
| **Q6** | 验证方式与范围 | **A.** 单测（mock `COSClient` 验证「dev 拿开发桶、prod 拿生产桶、端点随 `custom_domain` 变化」）+ 构建验证；**真实 COS 上传只允许在开发桶做一次，需你明确许可**，绝不在生产环境测试<br>**B.** 只做单测与构建，**完全不做**真实上传联调<br>**C.** 其他（请说明） | ✅ **A**（已拍板） |
| **Q7** | 调试接口 `/hello/test_cos` | **A.** 删除（当前无调用方，生产暴露等于任人签名）<br>**B.** 保留但**仅 dev Profile 注册**<br>**C.** 原样保留 | ✅ **A**（删除） |
| **Q8** | 无读取方的历史字段（`baseUrl`、`imagePrefix`、`tempPrefix`、`bucketRegion`） | **A.** 保持现状，**不清理**（本次最小改动）<br>**B.** 顺带在 yaml 与 `CosConfig` 里清理掉（会动到 `CosConfig`，属公共配置类） | ❌ 原选 A（不清理）；**已被 Q13 取代 → 改为清理（等同本项 B）** |
| **Q9** | 生产 Profile 是否要加**COS 桶指纹校验** | **A.** 加：与数据源校验同构，prod 下校验 `image.bucket-name` 必须是 `miaoyu-barc-image-...`，否则拒绝启动（防止生产误连开发桶）<br>**B.** 不加，靠配置审查 | ✅ **A**（已拍板） |
| **Q10** | 本次交付方式 | **A.** 我改代码 + 写测试 + 跑构建验证 + 按宪法 §13 **本地 commit 但不 push**<br>**B.** 我只出代码与测试，不 commit（你自行提交） | ✅ **A**（已拍板） |
| **Q11** | 老数据（开发库里引用生产桶 key 的记录） | **A.** 不迁移，接受开发环境老图 404，文档写明<br>**B.** 需要迁移方案（跨桶拷贝 + 改库，属独立需求） | ✅ **A**（已拍板） |
| **Q12** | 文档更新范围 | **A.** 本文档 + `docs/结构文档.md`（COS 环境区分章节 + 桶对照表）+ `docs/AI开发指南.md`（环境说明补 COS）+ `docs/数据库初始化指南.md`（prod 配置清单补 COS 段）<br>**B.** 只本文档 + `结构文档.md`<br>**C.** 其他（请说明） | ✅ **A**（已拍板） |
| **Q13** | 配置冗余清理（用户看到 §2.8 逐键证据后提出） | **A.** 只加注释标注死键，结构不动（忠于原 Q8=A）<br>**B.** 清掉零消费的 `bucket-region` / `base-url` / `image-prefix` / `temp-prefix`，保留顶层 `bucket-name` / `custom_domain` 兜底与 `test` 节点<br>**C.** 连顶层兜底桶、`test` 节点、`CosBucketConfigEnum.file`、`default` 分支一起收掉 | ✅ **B**（清零消费键；连带删 `CosConfig.java` 中 6 个对应字段） |
| **Q14** | `/api/file/delete` 上传进 image 桶、强删却删顶层兜底桶 | **A.** 顺手修：删除时显式传入与上传一致的桶<br>**B.** 不修，仅登记为已知问题 | ✅ **B**（不修，登记进 §8.4） |

---

## 四、方案（已按拍板结论固化）

### 4.1 配置形态（Q1=A / Q2b+Q13=B / Q3=B）

**`BarcBackend/src/main/resources/application.yaml`（基础 = 开发环境；已按 Q13=B 清掉死键）**

```yaml
tencent:
  cos:
    image:
      bucket-name: dev-miaoyu-barc-image-1309572720
      custom_domain: ""      # 开发桶无自定义域名：留空 → SDK 走腾讯云官方端点
    avatar:
      bucket-name: dev-miaoyu-barc-avatar-1309572720
      custom_domain: ""      # 同上
    test:                    # Q13=B：节点保留（test 桶仍被 CosService.resolveBucket 解析）
      bucket-name: test-default-1309572720
      custom_domain: https://cos.barc.work
    secret_id: <保持现状，禁止改动>          # 宪法 §15：本次不动密钥
    secret-key: <保持现状，禁止改动>
    region: ap-shanghai                       # 四个客户端分支都读它
    bucket-name: test-default-1309572720      # 顶层兜底桶：default 分支仍可达（/api/file/delete）
    custom_domain: https://cos.barc.work      # 顶层兜底自定义域名：保留
```

**被 Q13=B 删除的 10 个键**（清理理由见 §2.8 ③）：

| 删除的键 | 原出现处 |
|---|---|
| `image.bucket-region` / `image.base-url` | 原 `application.yaml:90-91` |
| `avatar.bucket-region` / `avatar.base-url` | 原 `:95-96` |
| `test.bucket-region` / `test.base-url` | 原 `:100-101` |
| `bucket-region`（顶层） / `base-url`（顶层） | 原 `:107-108` |
| `image-prefix` | 原 `:109` |
| `temp-prefix` | 原 `:111` |

**`BarcBackend/src/main/resources/application-prod.yaml`（在现有 9 行文件上追加；只写 4 个键）**

```yaml
# 现有内容保持不动（第 1-9 行：注释 + spring.datasource 三项）
spring:
  datasource:
    url: jdbc:mysql://127.0.0.1:51001/ba-prod
    username: ${BARC_PROD_DB_USERNAME:${barc.datasource.production.username}}
    password: ${BARC_PROD_DB_PASSWORD:${barc.datasource.production.password}}

# ↓↓↓ 本次新增：生产环境 COS 覆写段 ↓↓↓
# 说明：只覆写「桶名」与「自定义域名」——它们是决定生产用哪个桶、哪个域名的唯一两个键
#      （消费点：CosService.resolveBucket 与 CosClient.cosClient，见设计文档 §2.8 ①）。
#      区域 region 与密钥继续继承 application.yaml，不在此重复。
tencent:
  cos:
    image:
      bucket-name: miaoyu-barc-image-1309572720
      custom_domain: https://image-cos-9c98ec3e.barc.work
    avatar:
      bucket-name: miaoyu-barc-avatar-1309572720
      custom_domain: https://avatar-cos-3ef160ef.barc.work
```

> 该文件被 `.gitignore:9` 忽略，**不会被本次 commit 提交**，需随部署产物下发（与数据源配置同一约定）。

### 4.2 代码形态（COS 对外方法签名一律不变）

| 位置 | 改动 |
|---|---|
| `CosService.resolveBucket()` `:36-43` | **不改**（继续读 `cosConfig` 对应节点，值由 Profile 决定） |
| `CosService` 全部对外方法 | **不改签名**（宪法 §12） |
| `CosClient.cosClient()` `:29-85` | **不改逻辑**；确认「`customDomain` 为空 → 直接用 SDK 官方端点」这一分支在开发桶下被走到（现状 `:44/54/64/75` 已是「非空才替换」） |
| 所有上传 / 删除 / 签名消费方（§2.3 A/B/C 表，除 GC） | **不改**（`CosBucketConfigEnum.image` 的语义不变：当前环境的 image 桶） |
| **`CosConfig.java`** | **删除 6 个零消费字段**（Q13=B）：顶层 `bucketRegion`(`:24`)、`baseUrl`(`:25`)、`imagePrefix`(`:26`)、`tempPrefix`(`:27`)，内层 `CosBucketPojo.bucketRegion`(`:36`)、`baseUrl`(`:37`)。保留 `secretId/secretKey/region/bucketName/customDomain` 与 `avatar/test/image` 三个节点 |
| `BarcBackendApplication.validateProductionConfiguration()` `:62-82` | **扩展**（Q9=A）：追加 COS 桶指纹校验，见 §4.5 |
| `CosGcScheduler.consume()` `:27-43` | **改动**（Q4=A1）：删除时使用「当前环境 image 桶名」，忽略 DB 列 |
| `HelloWorldController.testCos()` `:57-66` | **删除**（Q7=A），同时删掉该类里不再使用的 `CosService` 注入与 `@IgnoreAuth` 导入 |
| `FileService.forceDeleteFileService()` `:46-49` | **不改**（Q14=B：缺陷登记在 §8.4，本次不收口） |

### 4.3 GC 队列的处理细节（Q4 = A1，已拍板）

现状：入队写的是**桶名**（`StudentService.java:234/240`、`SchoolService.java:143/149`、`ClubService.java:168/174`、`BackgroundImageService.java:219`、`FriendLinkService.java:177/259`，全部取自 `cosConfig.getImage().getBucketName()`），消费时直接拿它当桶名（`CosGcScheduler.java:32-35`）。

**落码采用 A1**：`consume()` 忽略 `item.getBucket()`，统一使用 `cosConfig.getImage().getBucketName()` 删除：

```java
// 改动示意（落码时补中文注释说明「为什么忽略 DB 列」）
String bucket = cosConfig.getImage().getBucketName();   // 以当前环境配置为准，避免用历史桶名误删
String key = item.getObject_key();
cosClient.cosClient(CosBucketConfigEnum.image).deleteObject(bucket, key);
```

- DB 列 `cos_garbage_key.bucket` **保留**（写入逻辑不动、历史数据不迁移），仅作为审计线索；
- 日志里**同时打印** `item.getBucket()` 与本次实际使用的桶名，便于排查历史遗留；
- `CosGcService.enqueue(bucket, key)` 签名**不动**（本次只清配置死键，不收敛业务方法签名；与 Q14=B 同一原则）。

### 4.4 测试方案（Q6=A / Q9=A / Q4=A1）

| 测试 | 内容 | 落点 |
|---|---|---|
| `CosConfigProfileTest`（新增） | 以 `dev` Profile 绑定 `tencent.cos` → 断言 `image.bucketName == dev-miaoyu-barc-image-1309572720`、`image.customDomain` 为空；以 `prod` Profile（用 `application-prod.yaml` 的真实资源）→ 断言 `image.bucketName == miaoyu-barc-image-1309572720`、`customDomain == https://image-cos-9c98ec3e.barc.work`，avatar 组同理（**不发起网络请求、不需要真实凭据**） | `src/test/java/com/miaoyu/barc/utils/tencent/cos/` |
| `CosClientEndpointTest`（新增） | `customDomain` 为空 → 不设置自定义端点；非空 → `CustomEndpointBuilder` 生效；覆盖 `test/avatar/image/default` 四分支 | 同上 |
| `CosServiceBucketRoutingTest`（新增） | `resolveBucket()` 对四个枚举返回对应配置桶名 | 同上 |
| `CosGcSchedulerTest`（新增） | 入队记录里的 `bucket` 与**当前配置**桶名不一致时，断言删除调用使用的是**当前配置**桶名（即 A1 生效） | `src/test/java/com/miaoyu/barc/api/scheduler/` |
| `BarcBackendApplicationTest`（扩展） | 新增用例：prod 下 `tencent.cos.image.bucket-name` 不满足 `miaoyu-barc-image-` 前缀（含为空、为开发桶名）→ 抛 `IllegalStateException`；满足前缀 → 不抛 | `BarcBackendApplicationTest.java`（与现有 16 个用例同风格） |

覆盖率目标：宪法 §7 要求 **≥90%**；本次新增/改动类必须满足。

### 4.5 生产 COS 桶指纹校验（Q9=A）

与数据源指纹校验（`BarcBackendApplication.java:75-81`）同构，新增常量与校验：

```java
// 生产 image 桶指纹：仅锁定「生产 image 桶」这一条最关键的判定。
// 为什么只校验 image：全部六个上传链路、全部 URL 签名链路用的都是 image 桶（§2.3），
// 一旦生产误连开发桶，影响面最大；avatar 桶当前无消费方，先不做强校验以免过度约束。
static final String PRODUCTION_IMAGE_BUCKET_PREFIX = "miaoyu-barc-image-";
```

- 只在 `prod` Profile 生效（`dev` 下完全跳过，保持开发桶可用）；
- 校验的最终值取自 **`environment.getProperty("tencent.cos.image.bucket-name")`**，也就是「基础配置 + `application-prod.yaml` 覆写」之后的**最终生效值**，能真正拦住「只改了基础配置、忘了覆写」的情况；
- 校验失败时抛 `IllegalStateException`，提示语明确写出期望前缀（与现有数据源校验的错误文案风格一致）。

---

## 五、落码清单（已拍板，待用户下达开工指令）

| # | 文件 | 改动内容 | 依据 |
|---|---|---|---|
| 1 | `BarcBackend/src/main/resources/application.yaml` | image / avatar 两组桶名改为**开发桶**、`custom_domain` 置空；**删除 10 个零消费键**（`bucket-region`×4、`base-url`×4、`image-prefix`、`temp-prefix`）；密钥与 `region`、`test` 节点、顶层兜底两项**不动** | Q1=A / Q13=B / Q3=B |
| 2 | `BarcBackend/src/main/resources/application-prod.yaml` | 追加 `tencent.cos` 覆写段（image / avatar 各 `bucket-name` + `custom_domain` 两键）；数据源段不动 | Q2b + Q13=B |
| 3 | `BarcBackend/src/main/java/com/miaoyu/barc/utils/tencent/cos/CosConfig.java` | 删除 6 个零消费字段：顶层 `bucketRegion` / `baseUrl` / `imagePrefix` / `tempPrefix`，内层 `bucketRegion` / `baseUrl` | Q13=B |
| 4 | `BarcBackend/src/main/java/com/miaoyu/barc/BarcBackendApplication.java` | 新增 `PRODUCTION_IMAGE_BUCKET_PREFIX` 常量 + 在 `validateProductionConfiguration` 内追加 COS 指纹校验 | Q9=A |
| 5 | `BarcBackend/src/main/java/com/miaoyu/barc/api/scheduler/CosGcScheduler.java` | 删除时使用当前环境 image 桶名（A1），日志双打印，补中文注释 | Q4=A1 |
| 6 | `BarcBackend/src/main/java/com/miaoyu/barc/HelloWorldController.java` | 删除 `/hello/test_cos` 方法与不再使用的 `CosService` 注入 / 导入 | Q7=A |
| 7 | `BarcBackend/src/test/java/**` | 按 §4.4 新增 4 个测试类 + 扩展 1 个测试类 | Q6=A |
| 8 | `docs/superpowers/specs/2026-10-10-cos-environment-split-design.md` | 本文档（状态与拍板结论） | Q12=A |
| 9 | `docs/结构文档.md` | 新增「COS 环境区分」小节 + 桶对照表 + 删除死键的登记 | Q12=A |
| 10 | `docs/AI开发指南.md` | §1.2 / §1.3 环境说明补 COS 一段（含 `application-prod.yaml` 必须带 COS 覆写段） | Q12=A |
| 11 | `docs/数据库初始化指南.md` | 「生产配置清单」补 `tencent.cos` 覆写段 | Q12=A |
| — | **不动** | `CosService` 对外签名与其 6 个上传消费方、`CosClient`、`FileService.forceDeleteFileService`（Q14=B）、双端前端、数据库结构与数据、密钥、`CosBucketConfigEnum.file` 与 `moveFile`（§2.8 ④ 仅登记） | 宪法 §12 / Q5 / Q11 / Q14 |

---

## 六、验证要求（宪法 §八 / §十六）

### 6.1 后端构建

```bash
cd BarcBackend
./mvnw clean package
```

### 6.2 测试

```bash
cd BarcBackend
./mvnw test
```

### 6.3 手工验证（Q6=A 且用户许可后）

1. **dev 起服务**（IDE / `./mvnw spring-boot:run`）→ 调 `/api/file/upload` 传一张测试图 → 断言返回的 key 无域名前缀；
2. 用该 key 调签名接口 → 断言域名是 `dev-miaoyu-barc-image-1309572720.cos.ap-shanghai.myqcloud.com`；
3. **腾讯云控制台确认对象落在开发桶**；
4. 生产桶仅在部署后由用户侧确认（宪法禁止在生产环境测试）。

### 6.4 双端兼容性核查（宪法 §11）

- 两端均无 COS 域名 / 桶名硬编码（§2.6 已核实 0 命中）；
- 接口返回字段结构**完全不变**（仍是完整 URL 字符串），无需改两端；
- `/hello/test_cos` 删除前已确认两端**零调用**（grep 结果：0 处）。

---

## 七、交付与提交纪律（Q10 拍板：代码 + 配置 + 文档合并为同一个 commit，不 push）

### 7.1 强制安全前提（提交前逐条核对）

| # | 核对项 | 方法 |
|---|---|---|
| 1 | `application.yaml` **不在**待提交列表 | `git status --short` 确认无该文件（`.gitignore:8` 已忽略） |
| 2 | `application-prod.yaml` **不在**待提交列表 | 同上（`.gitignore:9` 已忽略） |
| 3 | 提交内容里**没有明文密钥**（`secret_id` / `secret-key` 片段不得出现在任何文档 diff 中） | 逐文件复核 diff |
| 4 | 本文档只记录**桶名与域名**，不记录任何密钥 | 本文档已满足（全文无密钥值） |

### 7.2 提交信息形态（宪法 §13）

```
feat(文件): 对象存储按开发/生产环境区分桶与访问域名

1、基础配置改用开发桶 dev-miaoyu-barc-image/avatar-1309572720，并清空开发环境自定义域名；
2、application-prod.yaml 追加 COS 覆写段，生产改用 miaoyu-* 桶与 image/avatar 自定义域名；
3、清理 tencent.cos 下无消费方的 bucket-region、base-url、image-prefix、temp-prefix 及 CosConfig 对应字段；
4、为生产 Profile 增加 image 桶指纹校验，防止生产误连开发桶；
5、GC 队列消费改为以当前环境 image 桶名删除，避免历史桶名误删；
6、删除无鉴权调试接口 /hello/test_cos；
7、新增 COS 环境区分相关单元测试并扩展启动校验测试；
8、同步更新结构文档、AI开发指南、数据库初始化指南，并登记 file/delete 桶错位等已知问题；
```

- 只 commit，**禁止 push**（宪法 §13.1）；
- 提交前必须已通过 `./mvnw clean package` 与全部测试（§6.1 / §6.2）。

### 7.3 提交范围之外的清理

- `.dsh-acl-recovery/`（见 §8.3）为临时恢复产物，**不提交**；落码时建议一并删除。

---

## 八、附录

### 8.1 桶对照表（本次配置的目标形态）

| 环境 | 桶类型 | 桶名 | 访问域名 | 配置来源 |
|---|---|---|---|---|
| 开发 | image | `dev-miaoyu-barc-image-1309572720` | `https://dev-miaoyu-barc-image-1309572720.cos.ap-shanghai.myqcloud.com`（SDK 官方端点） | `application.yaml` |
| 开发 | avatar | `dev-miaoyu-barc-avatar-1309572720` | `https://dev-miaoyu-barc-avatar-1309572720.cos.ap-shanghai.myqcloud.com`（SDK 官方端点） | `application.yaml` |
| 生产 | image | `miaoyu-barc-image-1309572720` | `https://image-cos-9c98ec3e.barc.work`（自定义域名） | `application-prod.yaml` |
| 生产 | avatar | `miaoyu-barc-avatar-1309572720` | `https://avatar-cos-3ef160ef.barc.work`（自定义域名） | `application-prod.yaml` |

### 8.2 本次排除项（明确不做）

| 项 | 原因 |
|---|---|
| MinIO 链路（trend 动态图） | 与 COS 无关，用户需求未涉及 |
| 用户头像 `user_archive.avatar` | 存的是奶果外部 URL，不是 COS key |
| 数据库结构与数据迁移 | 需求明确「数据库仍然只是存 Key」，现状已满足 |
| 密钥 / 环境变量改造 | 宪法 §15 禁止本次改动密钥 |
| 双端前端改动 | 无硬编码，返回结构不变 |
| 跨桶历史数据迁移 | Q11=A：接受开发环境老图 404 |
| 无读取方历史字段的「保留」 | **Q8=A 已被 Q13=B 取代**：本次确实清理死键 |
| 配置键名风格统一 | Q3=B：保持现状混用（`bucket-name` 与 `custom_domain` 并存） |
| `/api/file/delete` 的桶错位修复 | Q14=B：仅登记，见 §8.4 |
| `CosBucketConfigEnum.file` 与 `CosService.moveFile()` 的清理 | 属 COS 代码本身，宪法 §12 保护范围，仅登记于 §2.8 ④ |

### 8.3 环境备注（与本次任务无关，仅记录）

- 本次会话开始时工作区根目录 `D:\Project_Yasenbaka\Barc` 存在 Windows 文件权限问题（缺 `WRITE_OWNER`），导致所有 `pwsh` 命令报 `SetNamedSecurityInfoW failed (Win32 5)`；已按 DSH 沙箱 ACL 修复流程处理，**为工作区根目录补了一条当前用户完全控制授权**（备份与回滚脚本在 `D:\Project_Yasenbaka\Barc\.dsh-acl-recovery\`）。该目录为临时恢复产物，**不应提交**（见 §7.3）。
- 同一修复流程产生的报告文件：`D:\Project_Yasenbaka\Barc\.dsh-acl-recovery\acl-report-2123d31b56de42acad189714f488684a.jsonl`。

### 8.4 已知问题登记（Q14=B：本次不修，留给后续需求）

| 编号 | 问题 | 证据 | 影响与建议 |
|---|---|---|---|
| **K1** | `/api/file/delete` 强制删除与上传**用的不是同一个桶** | 上传：`FileController.java:26` 传 `null` → `FileService.java:35-37` 兜底为 `image` 桶；删除：`FileService.java:48` 直接传 `null` → `CosService.java:41` 走 `default` 分支，删的是**顶层兜底桶**（当前为 `test-default-1309572720`） | 该链路当前**无前端调用方**，故未暴露。一旦启用会出现「删不掉且静默返回失败」。建议后续需求中把删除改为显式传入目标桶 |
| **K2** | `CosBucketConfigEnum.file` 无任何调用方 | `CosBucketConfigEnum.java:14`；`main`/`test` 均 0 命中 | 无功能影响，建议后续清理 |
| **K3** | `CosService.moveFile()` 无任何调用方 | `CosService.java:140-157`；0 命中 | 无功能影响，建议后续清理 |
| **K4** | `/hello/test_cos` 在删除前是**无鉴权**接口 | `HelloWorldController.java:57-66`（`@IgnoreAuth`） | Q7=A 本次删除，K4 随本次一并消除 |
| **K5** | COS 密钥明文且开发/生产共用同一对 | `application.yaml`（`secret_id` / `secret-key`），宪法 §15 禁止本次改动 | 密钥会随 Maven 打进 `BOOT-INF/classes/`（`数据库初始化指南.md:61` 已登记风险）。建议单独立项做密钥外置 |

---

## 九、落码记录（2026-10-10）

### 9.1 实际改动文件

| # | 文件 | 改动 |
|---|---|---|
| 1 | `BarcBackend/src/main/resources/application.yaml` | `tencent.cos` 改为开发桶（image / avatar 的 `bucket-name` + `custom_domain: ""`）；删除 10 个零消费键（`bucket-region`×4、`base-url`×4、`image-prefix`、`temp-prefix`）；保留密钥、`region`、`test` 节点与顶层兜底两项 |
| 2 | `BarcBackend/src/main/resources/application-prod.yaml` | 追加 `tencent.cos` 覆写段（image / avatar 各 `bucket-name` + `custom_domain`）；数据源段未动（该文件不入 Git） |
| 3 | `BarcBackend/src/main/java/com/miaoyu/barc/utils/tencent/cos/CosConfig.java` | 删除 6 个零消费字段（顶层 `bucketRegion` / `baseUrl` / `imagePrefix` / `tempPrefix`，内层 `bucketRegion` / `baseUrl`），并补写环境区分说明与「不要加回」提示 |
| 4 | `BarcBackend/src/main/java/com/miaoyu/barc/BarcBackendApplication.java` | 新增 `PRODUCTION_IMAGE_BUCKET_PREFIX` 常量 + `validateProductionConfiguration` 内追加 COS 桶指纹校验 |
| 5 | `BarcBackend/src/main/java/com/miaoyu/barc/api/scheduler/CosGcScheduler.java` | 删除时改用当前环境 image 桶名（A1），日志同时打印实际桶名与队列记录桶名 |
| 6 | `BarcBackend/src/main/java/com/miaoyu/barc/HelloWorldController.java` | 删除 `/hello/test_cos` 端点及其 `CosService` 注入、`IgnoreAuth` / `SuccessR` / `RequestParam` / `Date` 导入 |
| 7 | `BarcBackend/src/test/java/**` | 新增 4 个测试类 + 扩展 1 个测试类（见 9.2） |
| 8 | `docs/结构文档.md` | 新增「COS 的开发 / 生产环境区分」小节（桶对照、实现要点、死键与已知问题） |
| 9 | `docs/AI开发指南.md` | §1.2 环境说明补 COS 段；§1.3 依赖服务表补环境区分说明 |
| 10 | `docs/数据库初始化指南.md` | §1.2 启动校验增加第 3 条（COS 桶指纹）；新增 §1.4「对象存储（腾讯云 COS）的环境切换」含 `application-prod.yaml` 配置片段；§七 相关文件表更新 |

### 9.2 测试与验证结果

| 测试类 | 用例数 | 结果 | 覆盖点 |
|---|---|---|---|
| `CosConfigProfileTest`（新增） | 3 | ✅ | 开发桶绑定、生产覆写绑定、已清理历史键不再绑定（结构化断言） |
| `CosClientEndpointTest`（新增） | 4 | ✅ | 空 `custom_domain` → 官方端点；非空 → 自定义端点；非法域名被忽略；四个桶分支（含 `null` 兜底） |
| `CosServiceBucketRoutingTest`（新增） | 5 | ✅ | 上传 / 指定 key 上传 / 删除 / 签名都使用当前环境桶名；桶配置缺失时返回失败结果而不抛出 |
| `CosGcSchedulerTest`（新增） | 4 | ✅ | 用当前配置桶名删除（不用历史桶名）、批量处理、异常标记失败、空队列不动作 |
| `BarcBackendApplicationTest`（扩展） | 18（+4） | ✅ | 生产误用开发桶 / 缺桶配置 → 拒绝启动；开发 Profile 不校验；原 14 个用例保持通过 |

**构建验证**：`./mvnw clean package` → **BUILD SUCCESS**，**350 个测试全部通过**，产出 `BarcBackend-0.1.17.jar`。

### 9.3 落码过程中的事实修正（与勘测阶段的差异，务必留档）

1. **实际配置文件在勘测后被改过**：`application.yaml` 的 `server.port` 由 51010 改为 51000、`spring.sql.init.mode` 改为 `never`、并新增了 `barc.datasource.production` 凭据段。落码时以**磁盘最新内容**为准，改动只落在 `tencent.cos` 块内，其余一律未动。
2. **「`custom_domain` 为空时端点构建器为 null」的初始假设不成立**：COS SDK 的 `ClientConfig` 在未设置自定义端点时会自带 `RegionEndpointBuilder`（由区域推导官方域名），并非 null。因此测试断言改为「**不是** `CustomEndpointBuilder`，且其构建出的主机含 `myqcloud.com`」——与「走腾讯云官方端点」的语义严格一致。
3. **`test` 桶与顶层兜底两项仍然可达**：`/api/file/delete`（Q14=B 不修）经 `CosService.resolveBucket` 的 `default` 分支与 `test` 桶，因此这两项**必须保留**；只有删除该链路才会让它们变成死配置（见 §8.4 K1）。

### 9.4 尚未执行项（需用户明确许可）

- **开发桶真实上传联调**（Q6=A 的第二步）：需用户许可后执行「dev 起服务 → 上传一张测试图 → 断言返回 key 不含域名前缀 → 签名 URL 域名为 `dev-miaoyu-barc-image-1309572720.cos.ap-shanghai.myqcloud.com`」；**严禁在生产环境测试**。
- 生产自定义域名的端到端行为只能由用户在真实部署时确认（§2.7 R2）。
