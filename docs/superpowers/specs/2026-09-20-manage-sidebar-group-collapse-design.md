# 管理台侧边栏分组折叠 — 勘探与方案（已拍板，待落码）

> 日期：2026-09-20
> 状态：**Q1~Q9 已全部拍板，并已于 2026-09-20 完成落码**（见第九章「落码记录」）；已通过 `npm run test:run`（19 spec / 130 用例）与 `npm run build`，**未 push**
> 范围：`BarcManageFrontendV2`（活跃管理端）侧边栏及其权限链路（拍板后新增 `DashboardView.vue` 的入口卡片删除）；后端只做**只读**勘探，未连接数据库、未做任何写入
> 说明：本文所有「程序事实」都标注到 `文件:行号`；截图相关推断单独标注为「推断」

---

## 拍板摘要（2026-09-20）

| # | 问题 | 拍板结论 |
|---|---|---|
| Q1 | 默认折叠策略 | **全部收起**，连激活组也收起（进后台首先只见 7 个组标签） |
| Q2 | 持久化 | **不持久化**，刷新即回默认（内存状态） |
| Q3 | 单项组（概览/公告/留言管理） | **同样可折叠**（与 Q1 一致，不做特判） |
| Q4 | 全展开/全收起快捷按钮 | 首版**不加** |
| Q5 | 仪表盘「可访问模块」入口卡片 | **直接删除该区块**（`DashboardView.vue` 进入改动清单） |
| Q6 | 多账号折叠状态隔离 | 因 Q2 不持久化，**无需处理** |
| Q7 | 无分组归属的隐藏二级页 | **不改变折叠状态** |
| Q8 | 7 处前后端判定偏差（第五章） | 本次**不修**，另开需求单独拍板 |
| Q9 | 组标签点击语义 | **只做展开/收起，不跳转** |

**拍板组合带来的三个设计简化（与第六/七章对应）**

1. Q1 + Q2 + Q7 组合后，折叠状态退化为**纯内存、只由用户点击驱动**：不需要 `watch(route.name)` 联动、不需要"首次非空后初始化默认值"的标志位、不需要持久化读写与脏 key 过滤。原 6.3 的时序坑随之消失（全收起状态下，"权限未加载＝空组集合"与"全收起"是同一种渲染结果）。
2. **配套必做项（Q1 的必要补偿）**：默认全收起后，用户一眼看不出"当前页在哪个组"。因此**包含当前激活路由的组标签必须有高亮态**（新增 `is-active-group`），否则会退化成"进了页面却不知道自己在哪"。
3. Q5 把范围从"只改侧边栏"扩大到"侧边栏 + 仪表盘一处删除"，但仪表盘**不引入**任何折叠交互（与侧边栏同构的选项 B 被否决）。

---

## 一、核心结论（先看这段）

1. **「组」不是一个数据结构，只是路由 meta 上的字符串标记。** 侧边栏的分组完全由 `adminChildren` 扁平数组按 `meta.menuGroup` 聚合而成（`AppSidebar.vue:26-63`），**组本身没有任何权限字段**；组是否出现，是「组内至少有一项通过 `canAccessRoute`」的**派生结果**。所以折叠功能必须建立在「先按权限过滤子项、再决定组是否渲染」的顺序上——这条顺序现在就成立，改折叠时不能破坏它。

2. **当前没有任何折叠能力。** 组标签是纯 `<div>`（`AppSidebar.vue:75`），没有点击、没有展开状态、没有持久化、没有与当前激活路由联动。整个侧边栏一次性铺开 **20 个可见菜单项 + 7 个分组标签**（见第三章），这正是"太长了"的直接原因。

3. **组内权限确实不统一，而且不止"不统一"：同一组里混着三种判定**——`1匹配`（按位与）、`比大小`（数值 ≥）、以及**完全没有权限要求**（只要求 MANAGER 身份）。第七章给出逐项证据。

4. **前端"菜单可见性"与后端"接口受理"存在 7 处已知偏差**（第五章），其中最典型的是：前端「学园、部团、学生」用**比大小 ≥16** 放行，后端写接口却是**1匹配 bit16**——馆长若只有 32 这一位，菜单看得见、点进去写操作会被后端拒绝。这些偏差**不属于**折叠需求本身，但折叠/权限显示方案必须知道它们存在，否则会把已有偏差"固化"成设计假设。

---

## 二、现状链路（程序事实）

### 2.1 侧边栏渲染链路（前端）

```
adminChildren（routes.ts，扁平数组）
  └─ 每项 meta：menuLabel / menuGroup / menuGroupLabel / menuOrder / groupOrder
                 + hiddenInMenu / minManagerPermission（比大小）/ minManagerPermissionBit（1匹配）
        │
        ├─ AppSidebar.menuGroups（AppSidebar.vue:26-63）
        │     1) 丢弃 hiddenInMenu、无 name、无 menuLabel 的项        (:30-32)
        │     2) canAccessRoute(meta, userArchive) 不通过 → 丢弃该子项 (:34-36)
        │     3) 按 meta.menuGroup 聚合（缺省 'default'，标签缺省 '导航'，序缺省 999）(:38-44)
        │     4) 组内按 menuOrder 排序（缺省 999）                    (:56-61)
        │     5) 组间按 groupOrder 排序（缺省 999）                   (:62)
        │
        └─ DashboardView.quickLinks（DashboardView.vue:14-18）
              复用同一套「menuLabel + !hiddenInMenu + canAccessRoute」过滤，但**平铺不分组**
```

关键实现位置：

| 事实 | 位置 |
|---|---|
| 组标签渲染为不可点击的 `div` | `AppSidebar.vue:75` |
| 菜单项为 `<button>`，激活判定 `route.name === item.name` | `AppSidebar.vue:76-85` |
| 点击后 `router.push({ name })` | `AppSidebar.vue:65-67` |
| 滚动容器（内容超高时才滚动，`scrollbar-gutter: stable`） | `AppSidebar.vue:116-141` |
| 组间距 `gap: 1rem`、组内 `gap: 0.45rem` | `AppSidebar.vue:121`、`:146` |
| 激活态左侧竖条 `::before` | `AppSidebar.vue:176-185` |

### 2.2 前端权限判定（唯一入口）

| 事实 | 位置 |
|---|---|
| `canAccessRoute`：先 `requiresManager` → 再 **bit 优先** → 否则比大小 | `app/stores/permissions.ts:97-109` |
| `minManagerPermissionBit` 存在时**直接 return**，同项的 `minManagerPermission` 被忽略 | `app/stores/permissions.ts:103-105` |
| `hasManagerPermissionBit`：`permission` 为 `null/undefined` → `false` | `shared/constants/permissions.ts:34-39` |
| `hasMinimumManagerPermission`：`required === undefined` → `true`；否则 `(permission ?? 0) >= required` | `shared/constants/permissions.ts:25-31` |
| `permission` 常量值（MANAGER） | `shared/constants/permissions.ts:3-10` |
| `userArchive` 初始为 `null`（登录恢复期间） | `app/stores/auth.ts:21`、`:67-113` |
| 路由守卫：`await bootstrap()` → `requiresAuth` → `requiresManager` → `canAccessRoute` 不过跳 `forbidden` | `app/router/index.ts:15-68`（判定在 `:64`） |
| `RouteMeta` 类型声明（新增 meta 字段必须同步这里） | `shared/types/router.d.ts:6-20` |

> 由于守卫在进入 `AdminLayout` 前已 `await bootstrap()`，侧边栏渲染时 `userArchive` 已有值；但 `userArchive` 仍是响应式的，权限刷新会重算 `menuGroups`（这一点在折叠状态初始化时会踩时序坑，见 6.3）。

### 2.3 后端裁决链路

| 事实 | 位置 |
|---|---|
| 全局登录拦截：除 `@IgnoreAuth` 外都要求 `Authorization`；解析成功后 `request.setAttribute("uuid", …)` | `interceptor/AuthInterceptor.java:33-36`、`:39-51` |
| `@RequireUserAndPermissionAnno.Check` 默认值：`uuidIndex=0`、`identity=USER`、`targetPermission=1`、`isSuchElseRequire=true`、`isHasElseUpper=false` | `annotation/RequireUserAndPermissionAnno.java:23-30` |
| 切面：**只传 uuidIndex（`isSuchElseRequire=true`）→ 只校验该 uuid 存在，不校验身份/权限** | `aspect/RequireUserAndPermissionAspect.java:36-42` |
| 切面：`Check` 数组中**第一个 Check 必定 return**（通过或拒绝），后续 Check 不可达 | `aspect/RequireUserAndPermissionAspect.java:29-65` |
| `@RequireSelfOrPermissionAnno`：「本人（uuid == authorUuid）」**或**「身份匹配 + 权限判定」；`\|\|` 短路，本人命中即放行 | `aspect/RequireSelfOrPermissionAspect.java:31-40`、`:54-66` |
| `@RequireSelfOrPermissionAnno` **没有** `isSuchElseRequire` 字段 → 该注解**永远**会做「本人或权限」判定，不存在"仅存在性"放行 | `annotation/RequireSelfOrPermissionAnno.java:12-20` |
| 切点 `@annotation(anno)` **不区分 Controller 与 Service**：本项目绝大多数鉴权写在 Service 方法上，Controller 上很少 | `aspect/RequireUserAndPermissionAspect.java:23` |
| 拦截路径白名单式注册：`/hello/**`、`/user/**`、`/api/**`、`/comment/**`、`/feedback/**`、`/trend/**`、`/notice/**` → **「无鉴权注解」≠「免登录」**，只有 `@IgnoreAuth` 才免登录 | `config/WebConfig.java:32-42` |
| `comparePermission.compare(your, target) = your >= target` | `permission/ComparePermission.java:11-13` |
| `comparePermission.has(your, target) = (your & target) != 0` | `permission/ComparePermission.java:18-20` |
| MANAGER 权限常量值 | `permission/PermissionConst.java:16-21` |

MANAGER 权限位（与前端一致）：

| 角色 | 常量 | 值 |
|---|---|---|
| 风纪委员 | `DISCIPLINARY_COMMITTEE` | 1 |
| 一级管理员 | `FIR_MAINTAINER` | 2 |
| 二级管理员 | `SEC_MAINTAINER` | 4 |
| 三级管理员 | `THI_MAINTAINER` | 8 |
| 副馆长 | `ADMINISTRATOR` | 16 |
| 馆长 | `ADVANCED_ADMINISTRATOR` | 32 |

### 2.4 权限值是「组合位」而不是「单一位」

- 权限调整页用 **多选** 勾选并按下标或合成一个整数：`editing.permission = values.reduce((acc, val) => acc | val, 0)`（`UsersPermissionsView.vue:73-81`）。
- 可分配的上限来自 `Integer.highestOneBit(permission)`（`UserPermissionService.java:41-56`），前端用它过滤选项（`UsersPermissionsView.vue:62-71`）。
- 提交时后端要求「操作者权限值 **严格大于** 目标值」两次（`UserPermissionService.java:71`、`:76`）——注意这是**数值**比较，与位语义无关。

> **推断（来自用户提供的侧边栏截图，未查数据库）**：该账号同时看到「用户投诉」（需 bit8）与「官方账号」（需 bit32），并看到作品管理组（需 bit4）与「评论投诉」（需 bit2），因此其 `permission` **至少包含 {2,4,8,32}**，是组合值。**不能假设「馆长 = 32 单一位」**，但也不能假设「每个馆长都带满所有位」——第七章的偏差在"缺位/单位"账号下会真实暴露。
> 另外，截图展示的是滚动区**顶部**（右侧有滚动条），下方仍有「留言管理」「系统」「系统管理」三组，并非"该账号看不到"。

---

## 三、菜单全清单（20 个可见项 + 6 个隐藏项）

数据来源：`app/router/routes.ts:30-452`（行号为各 `meta` 块位置）。

### 3.1 可见菜单项（参与侧边栏渲染）

| 菜单标签 | route name | path | 组（key / 标签 / groupOrder） | 组内序 | 前端权限 meta | 判定方式 |
|---|---|---|---|---|---|---|
| 仪表盘 | `dashboard` | `dashboard` | dashboard / 概览 / 10 | 10 | 无 | 仅 `requiresManager` |
| 用户列表 | `users-list` | `users/list` | users / 用户 / 20 | 10 | `minManagerPermission: THI(8)` | 比大小 ≥8 |
| 权限调整 | `users-permissions` | `users/permissions` | users / 用户 / 20 | 20 | `minManagerPermission: THI(8)` | 比大小 ≥8 |
| 用户投诉 | `users-complaints` | `users/complaints` | users / 用户 / 20 | 30 | `minManagerPermissionBit: THI(8)` | 1匹配 bit8 |
| 官方账号 | `users-official-accounts` | `users/official-accounts` | users / 用户 / 20 | 40 | `minManagerPermissionBit: ADVANCED(32)` | 1匹配 bit32 |
| 公告列表 | `notices-list` | `notices` | notices / 公告 / 30 | 10 | 无 | 仅 `requiresManager` |
| 上传审核 | `works-reviews` | `works/reviews` | works / 作品管理 / 40 | 5 | `minManagerPermissionBit: SEC(4)` | 1匹配 bit4 |
| 作品管理 | `works-list` | `works/list` | works / 作品管理 / 40 | 10 | bit4 | 1匹配 bit4 |
| 作品分类 | `works-categories` | `works/categories` | works / 作品管理 / 40 | 15 | bit4 | 1匹配 bit4 |
| 认领管理 | `works-claims` | `works/claims` | works / 作品管理 / 40 | 20 | bit4 | 1匹配 bit4 |
| 投诉处理 | `works-complaints` | `works/complaints` | works / 作品管理 / 40 | 30 | bit4 | 1匹配 bit4 |
| 评论投诉 | `works-comment-complaints` | `works/comment-complaints` | works / 作品管理 / 40 | 35 | `minManagerPermissionBit: FIR(2)` | 1匹配 bit2 |
| 操作日志 | `works-logs` | `works/logs` | works / 作品管理 / 40 | 40 | bit4 | 1匹配 bit4 |
| 留言管理 | `messages-list` | `messages/list` | messages / 留言管理 / 50 | 10 | `minManagerPermissionBit: FIR(2)` | 1匹配 bit2 |
| 学园、部团、学生 | `schools-list` | `schools` | system / 系统 / 60 | 50 | `minManagerPermission: 16`（裸字面量） | **比大小 ≥16** |
| BUG反馈 | `system-feedback-bug` | `system/feedback-bug` | system / 系统 / 60 | 60 | `minManagerPermissionBit: ADMIN(16)` | 1匹配 bit16 |
| 意见反馈 | `system-feedback-suggestion` | `system/feedback-suggestion` | system / 系统 / 60 | 70 | bit16 | 1匹配 bit16 |
| 其他反馈 | `system-feedback-other` | `system/feedback-other` | system / 系统 / 60 | 80 | bit16 | 1匹配 bit16 |
| 背景图设置 | `system-backgrounds` | `system/backgrounds` | sysmanage / 系统管理 / 70 | 10 | `minManagerPermissionBit: ADVANCED(32)` | 1匹配 bit32 |
| 节日管理 | `system-festivals` | `system/festivals` | sysmanage / 系统管理 / 70 | 20 | bit32 | 1匹配 bit32 |

**分组小结**：概览 1 项 / 用户 4 项 / 公告 1 项 / 作品管理 7 项 / 留言管理 1 项 / 系统 4 项 / 系统管理 2 项。

> 两个容易混淆的组标签：`system` → **「系统」**（学园 + 三个反馈），`sysmanage` → **「系统管理」**（背景图 + 节日）。改折叠 UI 时要按 key 记忆状态，不能按标签匹配。

### 3.2 隐藏路由（`hiddenInMenu`，不参与侧边栏）

| route name | path | 组信息 | 权限 meta | 位置 |
|---|---|---|---|---|
| `notices-new` | `notices/new` | **无** `menuGroup`/`menuLabel`/`groupOrder` | 仅 `requiresManager` | `routes.ts:133-144` |
| `notices-edit` | `notices/:noticeId/edit` | 无 | 仅 `requiresManager` | `routes.ts:145-156` |
| `clubs-list` | `schools/:schoolId/clubs` | 无 | `minManagerPermission: 16` | `routes.ts:174-186` |
| `students-list` | `schools/:schoolId/clubs/:clubId/students` | 无 | `minManagerPermission: 16` | `routes.ts:187-199` |
| `works-detail` | `works/:workId/detail` | 无 | `minManagerPermissionBit: SEC(4)` | `routes.ts:253-265` |
| `forbidden` | `403` | 无 | 仅 `requiresManager` | `routes.ts:440-451` |

> 这 6 项**没有任何组归属信息**。用户在这些页面（例如从公告列表点进编辑页、从作品列表点进详情）时，"当前激活路由 → 所属组"的映射会失败——折叠方案必须显式定义这种情况的行为（见 6.4），否则容易出现"进详情页后侧边栏突然全收起"的抖动。

### 3.3 每个菜单项对应页面 → 前端 API → 后端接口/鉴权

（前端 API 路径为 `modules/*/api/*.ts` 中拼装的相对路径；后端鉴权为**实际生效**的判定）

| 菜单 | 页面组件 | 前端 API（文件） | 后端接口 | 后端鉴权（生效） |
|---|---|---|---|---|
| 仪表盘 | `dashboard/views/DashboardView.vue` | `auth.service`：`/user/current_me`、`/user/basic_me`；`users.service`：`/user/permission/permissions_by_identity`、`/user/permission/all_identities`、`/user/permission/me_permission_near_max` | `UserService.getMeCurrentService` / `getMeBasicService`；`UserPermissionController:27-31,21-25`（`@IgnoreAuth`）、`:48-51` | 仅存在性（`RequireUserAndPermissionAnno.Check()`，`UserService.java:86-94`）；目录接口公开；`me_permission_near_max` 仅登录 |
| 用户列表 | `users/views/UsersListView.vue` + `UserQuickCreateDrawer` / `BanUserDialog` / `BanHistoryDrawer` | `users.service`：`queryUsersByPage`、`uploadNewUser`、`banUser`、`unbanUser`、`getBanHistory` | `POST /user/query/all_by_page`；`POST /user/manage/upload_new_user`；`POST /user/ban`、`/user/ban/unban`、`GET /user/ban/history` | 查询接口 `@IgnoreAuth`（**公开**，`UserQueryController:28-32`）；新建用户 **1匹配 bit8**（`UserManageService.java:21`）；封号/解封/记录 **比大小 ≥16**（`UserBanController.java:41-49,68-76,94-102,120-128`） |
| 权限调整 | `users/views/UsersPermissionsView.vue` | `users.service`：`queryUsersByPage`、`changePermission`、`fetchMyPermissionNearMax` | `POST /user/permission/change_permission` | **比大小 ≥8**（`UserPermissionService.java:62-65`）；服务内再判「操作者权限值严格大于目标值」（`:71`、`:76`）并改权限后发邮件（`:84-88`） |
| 用户投诉 | `feedback/views/FeedbackManageListView.vue`（`meta.feedbackType='USER'`） | `feedbackManage`：`getFeedbacksByType('USER')`、`updateFeedbackStatus` | `GET /feedback/feedbacks_by_type?type=USER&is_manager=true`；`PUT /feedback/update` | `FeedbackService.canManageFeedback`：`has(8) || compare(>=16)`（`:183-194`，USER 分支） |
| 官方账号 | `users/views/OfficialAccountsView.vue` | `officialAccount.service`：`fetchOfficialAccounts`、`bindOfficialAccount`、`searchOfficialAccountCandidates` | `GET /user/official/list`、`POST /user/official/bind`、`GET /user/official/search_user` | **1匹配 bit32**（`OfficialAccountService.java:75-79`、`:102-106`、`:158-162`） |
| 公告列表 | `notices/views/NoticesListView.vue`（+ `NoticeEditorView.vue`） | `notices.service`：`fetchNoticesByPage`、`fetchNotice`、`createNotice`、`updateNotice`、`deleteNotice` | `POST /notice/notices_by_page`、`GET /notice/only`（`@IgnoreAuth`）；`POST /notice/upload`；`PUT /notice/update`；`DELETE /notice/delete` | 列表/单条 **公开**（`NoticeController:35-39,44-48`）；发布：仅 MANAGER 身份（`NoticeService.java:71-72`）；修改：**比大小 ≥16 或作者本人**（`:82-83`）；删除：**比大小 ≥16**（`:93-94`）；页面内 `canDelete = permission >= 16`（`NoticesListView.vue:38`） |
| 上传审核 | `works/views/WorkReviewsView.vue` | `workManage`：`getWorkReviewList`、`reviewWork` | `POST /api/work/manage/reviews`、`POST /api/work/manage/review` | **1匹配 bit4**（`WorkManageService.java:136-140`、`:159-163`） |
| 作品管理 | `works/views/WorksListView.vue` + `WorkListComp` / `WorkDetailComp` | `workManage`：`getWorkListManage`、`getWorkDetailManage`、`getWorkEditDetail`、`updateWorkStatus`、`updateWorkContent` | `POST /api/work/manage/list`、`GET /api/work/manage/detail`、`/edit-detail`、`PUT /api/work/manage/status`、`/update` | **1匹配 bit4**（`WorkManageService.java:104-105,124-125,194-195,269-271,305-307`） |
| 作品分类 | `works/views/WorkCategoriesView.vue` | `categories.service`：`fetchCategoryTree`、`createCategory`、`updateCategory`、`moveCategorySort`、`deleteCategory` | `GET /api/category/manage/list`、`POST …/create`、`PUT …/{id}`、`PUT …/{id}/sort`、`DELETE …/{id}` | **1匹配 bit4**（`CategoryManageService.java:69-74,88-93,147-152,243,312`） |
| 认领管理 | `works/views/ClaimsListView.vue` + `ClaimListComp` | `workManage`：`getClaimsList`、`approveClaim`、`revokeClaim`、`assignAuthor`、`getClaimHistory`、`searchWorks`、`searchUsers` | `GET /api/work/manage/claims`；`POST …/claim/approve`、`/claim/revoke`、`/claim/assign`；`GET …/claim/history`、`/search-work`、`/search-user` | claims/revoke/assign/history **1匹配 bit4**（`:317,373,392,406`）；**approve 实际生效判定是 1匹配 bit1**（双 Check 只有首个生效，`:342-345`）；两个 search **比大小 ≥1**（`:473-477`、`:483-487`） |
| 投诉处理 | `works/views/ComplaintsListView.vue` + `ComplaintListComp` | `workManage`：`getComplaintsList`、`processComplaint`、`updateWorkStatus` | 前端实际请求 `GET /feedback/feedbacks_by_type?type=WORK`、`PUT /feedback/update`（`workManage.ts:90-94,117-124`） | `canManageFeedback`：`has(4) || compare(>=16)`（`FeedbackService.java:190`） |
| 评论投诉 | `works/views/CommentComplaintsView.vue` + `WorkCommentManage` | `commentManage`：`getCommentsByWork`、`deleteWorkComment`、`deleteWorkReply`、`getCommentComplaints`、`processCommentComplaint`、`getCommentById` | `GET /comment/work/comments_by_work`、`/comment_by_id`（公开）；`DELETE /comment/work/delete_comment`、`/delete_reply`；`GET /feedback/feedbacks_by_type?type=COMMENT`、`PUT /feedback/update` | 读取公开（`WorkCommentController:32-38,48-76`）；删除 **比大小 ≥2 或评论作者本人**（`WorkCommentService.java:92-95,97-108`）；COMMENT 反馈 `has(2) || compare(>=16)`（`FeedbackService.java:189`） |
| 操作日志 | `works/views/OperationLogView.vue` + `OperationLogComp` | `workManage`：`getOperationLogs` | `POST /api/work/manage/logs` | **1匹配 bit4**（`WorkManageService.java:443-444`） |
| 留言管理 | `messages/views/MessagesListView.vue` | `messages.service`：`fetchMessagesByPage`、`updateMessage`、`batchDeleteMessages`、`deleteMessage` | `POST /comment/message_board/admin/list`、`PUT …/admin/update`、`DELETE …/admin/batch_delete`、`DELETE /comment/message_board/delete` | admin 三件套 **1匹配 bit2**（`MessageBoardService.java:92-97,114-119,133-138`）；普通删除：作者本人或 **1匹配 bit2**（`:68-80`） |
| 学园、部团、学生 | `school-club-student/views/SchoolListView.vue` / `ClubListView.vue` / `StudentListView.vue` + `EntityFormDrawer` | `school.ts` / `club.ts` / `student.ts`（list / {id} / create / update / delete / check_id_available / 头像三接口） | `GET /api/school|club|student/list`、`GET /api/…/{id}`、`GET …/check_id_available`（`@IgnoreAuth`，公开）；`POST/PUT/DELETE /api/school|club|student…` | 写接口（含学生三张图片接口）**全部 1匹配 bit16**（`SchoolService.java:66-72,88-94,112-118`；`ClubService.java:81-87,112-118,141-147`；`StudentService.java:87-93,118-124,152-158,172-178,190-196,208-214`） |
| BUG / 意见 / 其他反馈 | `feedback/views/FeedbackManageListView.vue`（`meta.feedbackType` = `BUG`/`SUGGESTION`/`OTHER`） | `feedbackManage`：`getFeedbacksByType`、`updateFeedbackStatus` | `GET /feedback/feedbacks_by_type?type=…`、`PUT /feedback/update` | `canManageFeedback`：**比大小 ≥16**（`FeedbackService.java:192`）；另有未被前端使用的 `DELETE /feedback/delete` 同为 ≥16（`:156-163`） |
| 背景图设置 | `backgrounds/views/BackgroundsListView.vue` | `backgrounds.service`（7 个方法） | `GET /api/background/modules`、`/admin/list`；`POST /api/background/upload`；`PUT …/reorder`、`/{id}/enabled`、`/{id}/scene`；`DELETE /{id}` | **全部 1匹配 bit32**（`BackgroundImageService.java:56-61,66-71,125-130,141-146,159-164,177,207`） |
| 节日管理 | `system/views/FestivalsListView.vue` | `festivals.service`：`fetchFestivals`、`fetchFestivalOptions`、`createFestival`、`updateFestival`、`setFestivalEnabled`、`deleteFestival` | `GET /api/festival/admin/list`、`/options`；`POST …/admin/create`；`PUT …/admin/{id}`、`/{id}/enabled`；`DELETE …/admin/{id}` | 管理端 **全部 1匹配 bit32**（`FestivalService.java:52-57,72-77,113-118,155-160,182,225`） |

### 3.4 页面内权限门禁（"菜单可见 ≠ 操作可用"）

全项目 `*.vue` 扫描结果：**只有 4 个页面存在页面内权限相关逻辑**（其中**仅公告列表**是真正的按钮级门禁，仪表盘/权限调整是"过滤与上限"，用户列表只是标签展示），其余页面完全没有门禁。

| 页面 | 页面内判断 | 位置 |
|---|---|---|
| 公告列表 | `canDelete = (permission ?? 0) >= ADMINISTRATOR(16)`；编辑入口按"作者本人"分流 | `NoticesListView.vue:38`、`:174` |
| 仪表盘 | 入口卡片复用 `canAccessRoute` 过滤；权限展示用 `labelFor` | `DashboardView.vue:14-25` |
| 权限调整 | 可分配选项按 `myPermissionNearMax` 收口；提交禁用条件含"无可用选项" | `UsersPermissionsView.vue:59-71`、`:122-124` |
| 用户列表 | 仅用 `labelFor / optionsForIdentity` **展示**权限标签；封号/解封按钮**无门禁**（全部渲染） | `UsersListView.vue:55,142,280`；按钮在 `:396-408` |
| 其余全部页面（作品、留言、学园、背景图、节日、认领…） | **无页面内权限判断** | 扫描无 `permissionStore` / `authStore` / `MANAGER_PERMISSION` 命中 |

> 含义：当账号"菜单可见但权限不足"时，页面会正常打开、按钮正常显示，**只有点击后由后端返回 `insufficientAccountPermission`**。折叠/权限显示方案不应假设"页面内会兜底"。

---

## 四、前端可见性矩阵（按 permission 值展开）

规则：`requiresManager` 通过后，**有 bit 字段的项按位与，其余按数值 ≥**。

| permission（示例） | 概览 | 用户列表 / 权限调整 (≥8) | 用户投诉 (bit8) | 官方账号 (bit32) | 公告 | 作品管理组 (bit4×6, bit2×1) | 留言管理 (bit2) | 学园…(≥16) | 三反馈 (bit16) | 背景图 / 节日 (bit32) |
|---|---|---|---|---|---|---|---|---|---|---|
| 1（风纪委员） | ✅ | ❌ | ❌ | ❌ | ✅ | ❌ | ❌ | ❌ | ❌ | ❌ |
| 2（一级） | ✅ | ❌ | ❌ | ❌ | ✅ | 仅评论投诉 | ✅ | ❌ | ❌ | ❌ |
| 4（二级） | ✅ | ❌ | ❌ | ❌ | ✅ | 除评论投诉外 6 项 | ❌ | ❌ | ❌ | ❌ |
| 8（三级） | ✅ | ✅ | ✅ | ❌ | ✅ | ❌ | ❌ | ❌ | ❌ | ❌ |
| 16（副馆长） | ✅ | ✅ | ❌ | ❌ | ✅ | ❌ | ❌ | ✅（但写接口会被拒，见 5.5） | ✅ | ❌ |
| 32（馆长·单一位） | ✅ | ✅ | ❌ | ✅ | ✅ | ❌ | ❌ | ✅（写接口会被拒） | ❌ | ✅ |
| 62 = 2\|4\|8\|16\|32 | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ 全部 7 项 | ✅ | ✅ | ✅ | ✅ |
| 63 = 全位 | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |

**这张表就是"组长度随时间不可预测"的根源**：同一个账号的可见项从 2 个到 20 个不等，且**没有任何一种账号能看到"所有组都满"**（除了满位账号）。因此：

- 折叠默认值不能写死某个组集合，只能由"当前可见组 + 当前激活路由"推导；
- 组标签右侧也不要写死"共 N 项"，因为 N 随权限变化。

---

## 五、前端可见性 与 后端受理 的 7 处偏差（重要，本次不改）

| # | 菜单/功能 | 前端判定 | 后端实际受理 | 后果 |
|---|---|---|---|---|
| 5.1 | 用户投诉 | 1匹配 bit8 | `has(8) \|\| compare(≥16)`（`FeedbackService.java:191`） | 副馆长(16)/馆长(32) 若**没有** bit8：**看不到菜单，但后端允许处理** |
| 5.2 | 评论投诉 | 1匹配 bit2 | `has(2) \|\| compare(≥16)`（`:189`） | 同上，权限被前端多拦了一道 |
| 5.3 | 投诉处理（WORK） | 1匹配 bit4 | `has(4) \|\| compare(≥16)`（`:190`） | 同上 |
| 5.4 | BUG / 意见 / 其他反馈 | 1匹配 bit16 | **比大小 ≥16**（`:192`） | 馆长(32) 单一位：前端看不到、后端允许 |
| 5.5 | 学园、部团、学生 | **比大小 ≥16** | 写接口 **1匹配 bit16**（`SchoolService:66` 等 12 处） | 馆长(32) 单一位：**菜单看得见、读得到，写操作被拒**（最危险的一处：读公开、写 1匹配） |
| 5.6 | 认领管理 → 审批 | 1匹配 bit4 | **1匹配 bit1**（双 Check 仅首个生效，`WorkManageService.java:342-345`＋切面 `:29-65`） | 双向错配：只有 bit1 的账号后端能批但看不到菜单；只有 bit4 的账号看得到菜单但批不了 |
| 5.7 | 用户列表 → 封号 / 新建用户 | 菜单 ≥8（比大小）；页面无按钮门禁 | 封号 ≥16（比大小）；新建 **1匹配 bit8** | 三级管理员(8) 能看到菜单与按钮，点封号被拒；馆长(32) 单一位点"新增用户"被拒 |

> 偏差的**共同成因**（写在这里，避免误以为是折叠功能引入的）：同一批菜单项的"前端可见性"与"后端受理"分别在不同年代用不同语义写成——前端 meta 大量使用 1匹配（`minManagerPermissionBit`），后端则在同一功能里混用 1匹配与比大小，且 `FeedbackService` 还额外给"≥副馆长"开了兜底（`FeedbackService.java:189-192`）。
> 另有两条**与折叠无关的既有实现事实**一并登记：`POST /user/permission/change_permission` 的第二个 `Check(uuidIndex=1)` 同样不可达（`UserPermissionService.java:62-65`）；`DELETE /api/work/delete` 没有任何权限注解（`WorkController.java:264-268`）。

> 另有两处「公开接口」事实（与折叠无关，但属菜单链路一环）：`POST /user/query/all_by_page`（整个用户分页查询**无需登录**，`UserQueryController:28`）、`GET /comment/work/comments_by_work`、`/comment/work/comment_by_id`（`WorkCommentController:32,40,48`）。`docs/superpowers/specs/2026-09-19-official-account-design.md` 已把前者记为"顺带项②，本期不动"。
>
> 还有一处**旧链路**：`WorkManageService.getComplaintsList/processComplaint`（`:413-439`）仍直接操作**已弃用表 `feedback_work`**（`WorkFeedbackMapper.java:12-22`，`AGENTS.md` §6 明令禁止），但管理端前端已不再调用它们（前端走 `/feedback`）。本次不处理，仅登记。
>
> **后续处理（2026-10-12）**：该旧链路已整体下线（提交 `cb89a63`）——`FeedbackTypeOfController`、`WorkFeedbackController`、`WorkFeedbackService`、`WorkFeedbackMapper` 及 `WorkFeedbackModel` / `FeedbackTypeModel` / `FeedbackOptionModel` 全部删除，`WorkManageService` 的投诉区块与 `FeedbackMapper` 的两个 `@Deprecated` 查询一并移除。三张弃用表本身保留在库中，**未执行 DROP**。

---

## 六、折叠/展开设计方案（已按拍板结论定稿）

### 6.1 三条不可破坏的原则

| 原则 | 说明 |
|---|---|
| **P1 组不是权限主体** | 组可见性 = 「组内通过 `canAccessRoute` 的项数 > 0」，**不给组加权限字段**。任何"给组配一个 minPermission"的做法都会与"组内权限不统一"直接冲突（第三章、第四章已证）。 |
| **P2 先过滤、后分组** | 保持 `AppSidebar.vue:30-44` 现有顺序：先丢掉 hiddenInMenu / 无 label / 无权限的项，再聚合分组。折叠只包裹"聚合后的组"。 |
| **P3 折叠不参与鉴权** | 路由守卫（`router/index.ts:64`）与权限 store 一行不改；折叠只影响渲染。这样即使折叠状态计算错误，也不会出现"越权可见/不可达"问题。 |

### 6.2 状态模型

```ts
// 只记「用户点开过」的组 key；不在集合里 = 收起（初始空集合即"全部收起"）
const expandedGroups = ref<Set<string>>(new Set())
```

- 组 key 用 `meta.menuGroup`（缺省 `'default'`）；**不要用中文标签**匹配（「系统」/「系统管理」极易混淆）。
- 状态**只存在于 `AppSidebar.vue` 组件的内存里**（或抽成 composable），不进 store、不落任何存储（Q2 拍板）。
- 唯一的写入口是"点击组标签 toggle"（Q9）；没有任何 `watch` 会改写它（Q7）。

### 6.3 默认状态：全部收起（Q1）

`expandedGroups` 初始为空集合 ⇒ **所有组（含当前激活路由所在组）都是收起状态**，进入后台首先看到的是若干行组标签。

- 侧边栏最短，用户提出的"太长"被彻底解决。
- 代价与补偿：默认全收起后用户看不出"当前页在哪个组"——由 6.4 的**激活组高亮**补偿，这是**必备配套**而非可选装饰。
- **原"时序坑"已随拍板消解**：`userArchive` 初始为 `null` 时 `menuGroups` 是空数组（`auth.ts:21` + `permissions.ts:103-108` 对 null 权限一律返回 false），而"空数组 + 全收起"与"权限加载完成 + 用户未点开任何组"的渲染结果**完全一致**；因此不需要"首次非空后初始化"的标志位，也不会闪烁。
- 权限变化（例如权限调整后刷新自己的账号）导致某组消失时：该 key 留在 `expandedGroups` 里也无副作用（渲染按当前可见组遍历，不存在的 key 不会被读到）；若在意内存整洁，可在渲染时顺手清掉不可见的 key，属可选优化。

### 6.4 无分组归属的隐藏二级页 + 激活组高亮（Q7 与 Q1 配套）

第三章 3.2 列出 6 个 `hiddenInMenu` 路由没有 `menuGroup`（`notices-new`、`notices-edit`、`clubs-list`、`students-list`、`works-detail`、`forbidden`）。

- **拍板结论（Q7）**：进入这些页面**不改变任何折叠状态**（保持用户上一步看到的侧边栏）。
- 实现含义：既然默认全收起、且路由变化不改写状态，那么**实现里根本不需要 `watch(route.name)`**——折叠状态完全由点击驱动。这同时自动消解了原来担心的"进详情页侧边栏突变"问题。
- **激活组高亮（必备）**：渲染时计算 `activeGroupKey`（当前 `route.name` 命中的可见菜单项所属组；命中不到则为空），给对应组标签加 `is-active-group`。建议视觉：组标签文字加重 + 左侧 3px 竖条（沿用现有 `.app-sidebar__link.is-active::before` 的视觉语言），**独立于折叠箭头**。
- 组内菜单项的当前项高亮保持现状（`route.name === item.name`）。

### 6.5 交互与无障碍

| 项 | 做法 |
|---|---|
| 组标签 | `<div>` → `<button type="button" class="app-sidebar__group-toggle">`，保留现有 `.app-sidebar__group-label` 的排版样式 |
| 语义 | 按钮加 `aria-expanded="true|false"`、`aria-controls="<组key>-panel"`；面板容器加 `id` 与 `role="group"` |
| 键盘 | 原生 button 自带 Enter/Space；需补 `:focus-visible` 轮廓（现有样式无焦点态） |
| 视觉 | 箭头用纯 CSS（如 `▸` 字符或 2px 边框拼三角形）＋ `transform: rotate(90deg)` 过渡；沿用 `--barc-*` 令牌，不新增硬编码色值 |
| 动效 | 展开/收起用 `max-height`/`opacity` 或直接切换渲染（更稳：直接切换 + 一个 0.18s 的透明度过渡；避免 `max-height` 猜测值导致裁切） |
| 不引入 | **不引入 `el-menu` / `el-collapse`**：会重建结构与样式（V1 用的正是 `el-menu`，见 `BarcManageFrontend/.../AppSideMenuComp.vue`），回归面远大于收益；且 V2 的 `routes.ts` meta 驱动模型与 `el-menu` 的 `index/sub-menu` 模型并不对齐 |
| 单项组 | 拍板（Q3）：三个单项组（概览 / 公告 / 留言管理）**同样可折叠**，与其它组完全同构，不做 `items.length === 1` 特判；默认同样收起（与 Q1 一致） |

### 6.6 持久化：不持久化（Q2 拍板）

**结论：折叠状态只活在内存里，不写 `sessionStorage`、不写 `localStorage`。**

| 影响 | 说明 |
|---|---|
| 刷新 / 重新登录 | 回到"全部收起"默认态 |
| SPA 内路由跳转 | 状态保留（组件不销毁），因此 Q7 的"保持用户上一步看到的侧边栏"成立 |
| 多账号共用浏览器 | 不存在串状态问题（无存储即无隔离需求，Q6 随之关闭） |
| 实现 | 无需读写封装、无需脏 key 过滤、无需版本兼容；相比原方案**减少一处副作用面** |
| 代价 | 用户每次刷新都要重新展开自己常用的组；若后续觉得烦，只需在 `AppSidebar.vue` 内补一层 `sessionStorage` 读写即可（组 key 已是稳定值，加存储不需要改渲染逻辑） |

### 6.7 视觉与响应式

- 侧边栏容器已是 `overflow: hidden` + 内部滚动（`AppSidebar.vue:99,123`），折叠后总高度变小，滚动行为不变。
- 折叠不改变宽度（`--barc-sidebar-width: 280px`，`tokens.css:39`），`AdminLayout` 的 `<1080px` 响应式布局（侧边栏落到下方、高度 `2fr`，`AdminLayout.vue:78-86`）不受影响；但**窄屏下折叠更重要**（可视高度更小），建议首版就在该断点验证。
- 激活组高亮见 6.4（Q1 的必备配套）；**不要**用折叠箭头代替激活指示（现有激活竖条 `::before` 保留）。

### 6.8 仪表盘「可访问模块」入口卡片：删除（Q5 拍板）

`DashboardView.vue` 里那块平铺全部可访问模块的入口卡片**整体删除**：侧边栏已经承担导航职责，这块是重复入口，而且它本身就是"第二个长列表"。

删除范围（全部集中在 `DashboardView.vue` 一个文件）：

| 项 | 位置 | 处理 |
|---|---|---|
| `quickLinks` computed | `:14-18` | 删除 |
| `import { adminChildren } from '@/app/router/routes'` | `:6` | 删除（不再被引用） |
| `import { useRouter } from 'vue-router'` 与 `const router` | `:3`、`:10` | 若删除卡片后无其它引用，一并删除 |
| `permissionStore` 的 `canAccessRoute` 用法 | `:16`（`:8`/`:12` 为 store 引入） | 随卡片删除；`labelFor`（`:24` 权限展示）**保留**，因此 store 本身仍需保留 |
| 模板"入口 / 可访问模块" section | `:52-67` | 删除（含 `<button class="quick-link">`） |
| 样式 `.quick-links` / `.quick-link` | `:119-140` | 删除（避免 dead CSS） |
| 保留不动 | "当前会话" hero 卡片 `:35-50`、"边界 / Phase 0 约束"卡片 `:69-77` | — |

> 布局提示：hero 卡片当前是 `grid-row: span 2`（`:96-98`），删掉一张后右列只剩"边界"卡片，仍是 1+1 排布，栅格无需调整；若实际观感偏空，可单独微调（不属于本次必须项）。
> 该文件目前**没有任何测试**（见第七章），删除后建议补一条"仪表盘不再渲染模块入口列表"的断言，防止日后被加回来。

---

## 七、影响面、测试与构建

### 7.1 改动文件（拍板后确定）

| 文件 | 改动 |
|---|---|
| `BarcManageFrontendV2/src/app/components/AppSidebar.vue` | **必需**：内存折叠状态、组标签改 `<button>` + 箭头、面板渲染、激活组高亮、`:focus-visible`；**不写任何持久化** |
| `BarcManageFrontendV2/src/modules/dashboard/views/DashboardView.vue` | **必需（Q5）**：删除"可访问模块"入口卡片区块及其 import / state / dead CSS（明细见 6.8） |
| `BarcManageFrontendV2/src/shared/types/router.d.ts` | **不改**：拍板后不需要新增任何 `RouteMeta` 字段 |
| `BarcManageFrontendV2/src/app/router/routes.ts` | **不改**：没有"某组默认收起"的差异化配置，全部组默认收起 |
| 新增 `src/app/components/__tests__/AppSidebar.spec.ts` | 侧边栏折叠 / 权限过滤 / 激活组高亮 / 无障碍 |
| 新增 `src/modules/dashboard/__tests__/DashboardView.spec.ts` | 仪表盘不再渲染模块入口（Q5 的回归保护） |

> 无需改动的相关文件：`stores/permissions.ts`（判定逻辑一行不动）、`app/router/index.ts`（守卫不动）、后端（零改动）。

### 7.2 测试清单（目标 ≥90% 覆盖）

侧边栏 `AppSidebar.spec.ts`：

1. 权限过滤：mock `authStore.userArchive` + 权限位，断言无权限项不渲染、**整组为空的组不渲染**（例如只有 bit2 的账号不应出现「用户」组）。
2. 默认状态（Q1）：所有组 `aria-expanded="false"`，且**任何菜单项都不渲染**（连激活组也收起）。
3. 激活组高亮（Q1 配套）：当前 `route.name` 所在组带 `is-active-group`；跳到无分组归属的 `notices-new` / `works-detail` 时**没有任何组**带该标记。
4. 点击切换：点击组标签 → 面板渲染 + `aria-expanded="true"`；再点一次 → 收起。
5. 单项组（Q3）：概览 / 公告 / 留言管理同样可折叠。
6. 无路由联动（Q7）：在已展开 A 组的状态下跳到 B 组的页面，A 组保持展开、B 组保持收起。
7. 权限时序：`userArchive` 由 `null` → 有值过程中不抛错、不残留异常状态。
8. 无障碍：`aria-expanded` / `aria-controls` 存在且指向正确的面板 `id`。

仪表盘 `DashboardView.spec.ts`：

9. 不再渲染模块入口：断言不存在 `.quick-link` / "可访问模块" 文案（Q5）；仍渲染"当前会话"与权限标签（`labelFor` 保留）。

### 7.3 构建验证

```bash
cd BarcManageFrontendV2
npm run test:run    # vitest
npm run build       # vue-tsc --build + vite build
```

> 现有测试基线（实测）：测试一律位于 `BarcManageFrontendV2/src/modules/<模块>/__tests__/*.spec.ts`，共 **17 个 spec**；**不存在 `BarcManageFrontendV2/tests/` 目录**（与 `AGENTS.md` §7.1 的表述不一致，实际以 `src/modules/**/__tests__` 为准）。
> 其中只有两个 spec 直接断言路由 meta（`modules/works/__tests__/workReviewManage.spec.ts:5,57-62`、`modules/feedback/__tests__/FeedbackManageListView.spec.ts:7,269-294`）；**没有任何 AppSidebar / 壳层 / router 守卫测试**，`src/app/**` 下也没有 `__tests__` 目录。因此本次新增测试属净增量，不会与既有用例冲突。
> 测试配置：`vite.config.ts:74-78`（jsdom + `src/test-utils/setup.ts` + `css:false`）；命令 `npm run test:run`（单次）。

---

## 八、拍板结论（2026-09-20）

| # | 问题 | 拍板结论 |
|---|---|---|
| Q1 | 默认折叠策略 | **全部收起**（连激活组也收起）→ 配套：激活组标签必须高亮（6.4） |
| Q2 | 持久化 | **不持久化**（内存状态，刷新回默认） |
| Q3 | 单项组是否可折叠 | **可折叠**（与其它组同构，不做特判） |
| Q4 | 全展开 / 全收起快捷按钮 | **不加** |
| Q5 | 仪表盘「可访问模块」入口卡片 | **删除该区块**（6.8） |
| Q6 | 按账号隔离折叠状态 | **无需处理**（Q2 的派生结论） |
| Q7 | 无分组归属的隐藏二级页 | **不改变折叠状态**（等价于"不做路由联动"） |
| Q8 | 顺带修 7 处前后端判定偏差 | **本次不修**，另开需求单独拍板 |
| Q9 | 组标签点击语义 | **只折叠，不跳转** |

> 拍板已全部闭合，无遗留待定项；本文档此后即为落码依据。

---

## 九、落码记录（2026-09-20 已完成）

实际落地内容，以及与本章原计划的差异（如实记录）：

1. **未抽独立纯函数文件**：分组计算仍留在 `AppSidebar.vue` 内（保留原有 `menuGroups` computed 结构），改为新增一个模块级 `menuOrderIndex` 预索引，替代原先排序回调里对 `adminChildren` 的两次 `find`；排序结果不变。测试通过挂载组件覆盖，没有单独测纯函数。
2. 组件内 `const expandedGroups = ref<Set<string>>(new Set())` + `toggleGroup(key)`；空集合 = 全部收起（Q1/Q2），`toggleGroup` 是折叠状态的唯一写入口。
3. 组标签改为 `<button class="app-sidebar__group-toggle">`，带 `aria-expanded` / `aria-controls="sidebar-group-<key>"`，补 `:focus-visible` 轮廓与纯 CSS 折叠箭头。
4. 新增 `activeGroupKey` computed 标记激活组（`is-active-group`：字重加重 + 左侧 3px 竖条）；命中不到任何组时（`hiddenInMenu` 二级页）不高亮任何组。
5. 面板用 `v-if` 直接切换渲染，附 `sidebar-group-panel-in` 入场动画（0.18s）；**无** `watch(route.name)`、**无**任何存储读写（Q2/Q7）。
6. `DashboardView.vue` 删除 `quickLinks` computed、`adminChildren` / `useRouter` 引用、模板"入口 / 可访问模块" section，以及 `.quick-links` / `.quick-link` 样式；保留"当前会话"与"边界 / Phase 0 约束"两块（Q5）。
7. 新增测试：`src/app/components/__tests__/AppSidebar.spec.ts`（8 例）、`src/modules/dashboard/__tests__/DashboardView.spec.ts`（2 例）。

验证结果（2026-09-20）：

| 项 | 结果 |
|---|---|
| `npm run test:run` | **19 个 spec / 130 用例全部通过**（含本次新增 10 例） |
| `npm run build` | **成功**（`vue-tsc --build` 类型检查 + `vite build`） |
| 后端 | 零改动 |
| 浏览器 / 视觉 | **未打开浏览器**（遵循 `AGENTS.md` §14）；折叠观感与窄屏（<1080px）表现待人工确认 |

提交：`feat(系统): 管理台侧边栏分组支持折叠展开`（本地 commit，**未 push**）。

---

## 十、附录：证据索引（按文件）

| 文件 | 关键行 |
|---|---|
| `BarcManageFrontendV2/src/app/components/AppSidebar.vue` | 26-63（分组计算）、65-67（跳转）、74-86（模板）、116-141（滚动）、149-154（组标签样式）、156-185（菜单项样式） |
| `BarcManageFrontendV2/src/app/router/routes.ts` | 30-452（全部 adminChildren meta）、133-156（公告隐藏页）、174-199（部团/学生隐藏页）、253-265（作品详情隐藏页）、440-451（403） |
| `BarcManageFrontendV2/src/app/router/index.ts` | 15-68（守卫全流程，64 为权限判定） |
| `BarcManageFrontendV2/src/app/stores/permissions.ts` | 97-109（canAccessRoute） |
| `BarcManageFrontendV2/src/shared/constants/permissions.ts` | 3-10（常量）、25-39（两种判定） |
| `BarcManageFrontendV2/src/app/stores/auth.ts` | 21（初始 null）、67-113（bootstrap）、145-147（派生状态） |
| `BarcManageFrontendV2/src/shared/types/router.d.ts` | 6-20（RouteMeta 声明） |
| `BarcManageFrontendV2/src/modules/dashboard/views/DashboardView.vue` | 14-25（入口卡片过滤） |
| `BarcManageFrontendV2/src/modules/users/views/UsersPermissionsView.vue` | 59-81（组合位与上限）、112-124（提交禁用） |
| `BarcManageFrontendV2/src/modules/users/views/UsersListView.vue` | 296-410（新增/封号/解封按钮，无权限门禁） |
| `BarcManageFrontendV2/src/modules/notices/views/NoticesListView.vue` | 38（canDelete）、89/102/110（sessionStorage 先例） |
| `BarcManageFrontendV2/src/app/layouts/AdminLayout.vue` | 32（栅格）、78-86（窄屏） |
| `BarcManageFrontendV2/src/shared/styles/tokens.css` | 1-44（设计令牌） |
| `BarcManageFrontendV2/src/shared/api/http.ts` | 6-9（baseURL 与 `/api` 前缀不统一）、24-33（自动带 Authorization）、50-53/66-69（401 → 清 token） |
| `BarcBackend/.../config/WebConfig.java` | 32-42（拦截路径白名单：无注解≠免登录） |
| `BarcBackend/.../user/enumeration/UserIdentityEnum.java` | 8-10（身份只有 USER / MANAGER） |
| `BarcBackend/.../interceptor/AuthInterceptor.java` | 33-51 |
| `BarcBackend/.../annotation/RequireUserAndPermissionAnno.java` | 23-30（默认值） |
| `BarcBackend/.../aspect/RequireUserAndPermissionAspect.java` | 29-65（首个 Check 生效、仅存在性分支） |
| `BarcBackend/.../aspect/RequireSelfOrPermissionAspect.java` | 31-40、54-66 |
| `BarcBackend/.../permission/ComparePermission.java` | 11-13、18-20 |
| `BarcBackend/.../permission/PermissionConst.java` | 16-21 |
| `BarcBackend/.../user/service/UserPermissionService.java` | 41-56（highestOneBit）、62-92（改权限） |
| `BarcBackend/.../user/service/UserManageService.java` | 21（新建用户 1匹配 bit8） |
| `BarcBackend/.../user/controller/UserBanController.java` | 41-135（比大小 ≥16 ×4） |
| `BarcBackend/.../user/service/OfficialAccountService.java` | 75-79、102-106、158-162（1匹配 bit32） |
| `BarcBackend/.../user/controller/UserQueryController.java` | 28-32（公开分页查询） |
| `BarcBackend/.../feedback/service/FeedbackService.java` | 43-50、142-163（入口判定）、183-194（按类型分权） |
| `BarcBackend/.../api/work/service/WorkManageService.java` | 104/124/136/159/194/269/305/317/373/392/406/413/418/443（bit4 系列）、342-345（双 Check 仅首个生效）、473-487（比大小 ≥1） |
| `BarcBackend/.../api/service/CategoryManageService.java` | 69/88/147/243/312（bit4） |
| `BarcBackend/.../api/service/SchoolService.java` | 66/88/112（写接口 1匹配 bit16） |
| `BarcBackend/.../api/service/ClubService.java` | 81/112/141（同上） |
| `BarcBackend/.../api/service/StudentService.java` | 87/118/152/172/190/208（同上） |
| `BarcBackend/.../api/controller/SchoolController.java` | 18/27/51（读接口公开）、33-49（写接口） |
| `BarcBackend/.../notice/controller/NoticeController.java` | 24/35/44（公开）、55-87（写接口） |
| `BarcBackend/.../notice/service/NoticeService.java` | 71-80、82-91、93-102 |
| `BarcBackend/.../comment/service/MessageBoardService.java` | 68-90、92-97、114-119、133-138 |
| `BarcBackend/.../comment/service/WorkCommentService.java` | 92-95、97-108（比大小 ≥2 或本人） |
| `BarcBackend/.../comment/controller/WorkCommentController.java` | 32/40/48（公开读）、94-118（删除入口） |
| `BarcBackend/.../background/service/BackgroundImageService.java` | 56/66/125/141/159/177/207（1匹配 bit32） |
| `BarcBackend/.../festival/service/FestivalService.java` | 52/72/113/155/182/225（1匹配 bit32） |
| ~~`BarcBackend/.../feedback/mapper/WorkFeedbackMapper.java`~~ | ~~12-22（仍在用弃用表 `feedback_work`）~~ —— **2026-10-12 已随旧链路整体删除（`cb89a63`）** |

---

**本阶段产出：仅本文档。代码零改动、未 `git commit`。等拍板。**
