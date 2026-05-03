# BarcManageFrontend 后台前端设计注意事项

> 适用范围：`BarcManageFrontend/`
>
> 定位：这不是“理想后台模板”，而是“当前可运行代码的技术说明”。旧实现有技术债，但接口、结构、请求链路是活的。以后让 AI 扩展或重写类似后台时，先照着这里的事实做，再决定要不要整理。

---

## 1. 目的与范围

本文给未来 AI 一个直接可执行的后台前端约束，目标只有四件事：

1. 说清楚当前 `BarcManageFrontend` 的源码事实。
2. 说清楚哪些结构和约定应继续保留。
3. 说清楚哪些历史包袱不要继续复制。
4. 给新增后台页面一份最小实现清单。

本文只讨论 `BarcManageFrontend`，不把 `BarcFrontend` 的结构、示例代码、命名方式自动视为管理后台规范。

---

## 2. Source of truth 规则

### 2.1 核心规则

> **当文档与当前 `BarcManageFrontend` 代码冲突时，以当前 `BarcManageFrontend` 代码为准。**

### 2.2 为什么必须这样做

当前仓库里的旧文档对管理后台有明显过时或冲突信息：

- `docs/结构文档.md` 仍把 `BarcManageFrontend` 写成“已废弃”。
- `docs/AI开发指南.md` 的前端示例主要围绕 `BarcFrontend`、`src/router/`、直接 `axios` 调用展开。
- 但管理后台当前真实代码使用的是 `src/Routers/`、`src/Views/`、`src/Components/`、`src/Utils/Request.ts` 的 `baseHttp`。

所以以后 AI 做后台开发时，旧文档只能当背景材料，不能压过当前代码。

### 2.3 优先查看的源码文件

| 事实类型 | 源码位置 |
| --- | --- |
| 技术栈 | `BarcManageFrontend/package.json` |
| 应用入口 | `BarcManageFrontend/src/main.ts` |
| 后台根壳与权限分流 | `BarcManageFrontend/src/App.vue` |
| 路由总装配 | `BarcManageFrontend/src/Routers/index.ts` |
| 功能模块路由 | `BarcManageFrontend/src/Routers/Router/*.ts` |
| 请求入口 | `BarcManageFrontend/src/Utils/Request.ts` |
| 当前用户与会话状态 | `BarcManageFrontend/src/Stores/UserStore.ts` |
| 左侧菜单 | `BarcManageFrontend/src/Components/App/AppSideMenuComp.vue` |
| 业务样例 1 | `BarcManageFrontend/src/Components/User/UserPermissionComp.vue` |
| 业务样例 2 | `BarcManageFrontend/src/Components/Notice/*` |
| 统一消息提示 | `BarcManageFrontend/src/Utils/MessageAlert.ts` |
| 统一响应外壳 | `BarcManageFrontend/src/Interfaces/ResponseImpl.ts` |

---

## 3. 当前代码事实：技术栈与应用外壳

### 3.1 技术栈事实

以下是当前后台真实在用的组合，不要臆造别的基础设施：

| 事实 | 依据 |
| --- | --- |
| Vue 3 | `package.json` 依赖 `vue`，`src/main.ts` 使用 `createApp` |
| Vite | `package.json` 中有 `vite`、`@vitejs/plugin-vue`，脚本 `dev/build/preview` 走 Vite |
| Pinia | `package.json` 依赖 `pinia`，`src/main.ts` `createPinia()` 后 `app.use(pinia)` |
| vue-router | `package.json` 依赖 `vue-router`，`src/main.ts` `app.use(router)` |
| Element Plus | `package.json` 依赖 `element-plus`，`src/main.ts` `app.use(ElementPlus)` 并引入默认样式 |
| axios 请求底座 | `src/Utils/Request.ts` 使用 `axios.create(...)` 导出 `baseHttp` |
| Quill 富文本只在部分页面使用 | `package.json` 有 `@vueup/vue-quill`、`quill`，`NoticeUploadComp.vue` / `NoticeEditComp.vue` 使用 `QuillEditor` |

### 3.2 应用外壳事实

`src/App.vue` 是后台根壳，不要绕开它另起一套。

当前真实行为：

1. `onBeforeMount` 从 `localStorage` 读 `token`。
2. 有 `token` 时调用 `userStore.fetchUserInfo(token)` 恢复当前用户。
3. 没有 `token` 且当前路径是 `/` 时，弹出“请您先登录！”并跳到 `Sign`。
4. 模板层按 `userArchive` 和 `userArchive.identity` 分成三种状态：
   - 未登录：只渲染 `<RouterView/>`
   - 已登录且 `identity === 'MANAGER'`：渲染后台壳
   - 已登录但不是管理员：渲染无权限页

后台壳的组成也已经固定在 `App.vue` 里：

- `HeaderComp`
- `AppBgComp`
- `AppSideMenuComp`
- `AppContainerComp`

其中 `AppContainerComp.vue` 本身只负责在 `<el-main>` 中渲染 `<RouterView/>`。这说明当前架构是“统一后台壳 + 内容路由出口”，不是“每个页面自己拼头部和侧栏”。

---

## 4. 路由与模块配方

### 4.1 当前代码事实

`src/Routers/index.ts` 目前显式组装了 6 个模块路由：

- `HomeRouter`
- `SignRouter`
- `UserRouter`
- `WorkRouter`
- `MessageRouter`
- `NoticeRouter`

这些路由模块都放在 `src/Routers/Router/` 下，并统一塞进 `routes` 数组后交给 `createRouter(createWebHistory(...))`。

已存在的模块模式：

| 模式 | 例子 | 说明 |
| --- | --- | --- |
| 单模块根路由 + 默认子页 | `HomeRouter.ts`、`UserRouter.ts` | 先挂 `View`，再配 children |
| 模块内多子页 | `NoticeRouter.ts` | 同模块下拆 `list/upload/edit`，并用 `redirect` 指到默认页 |
| 薄 View | `UserView.vue`、`NoticeView.vue` | View 只保留 `<RouterView/>` |
| 直接单页 View | `HomeView.vue` | View 直接承载一个默认组件 |

同时也要看到不完整的地方：

- `WorkView.vue` 目前模板是空的。
- `MessageView.vue` 目前模板也是空的。

所以 `Work`、`Message` 只能说明“项目打算有这些模块”，不能说明“这里已经形成了成熟模板”。

### 4.2 新模块的推荐落地顺序

以后新增后台模块，优先照当前目录结构落地：

1. 在 `src/Routers/Router/` 新建 `XxxRouter.ts`。
2. 在 `src/Views/` 新建 `XxxView.vue`。
3. 在 `src/Components/Xxx/` 放业务页面组件。
4. 在 `src/Interfaces/` 补充该模块的类型定义。
5. 回到 `src/Routers/index.ts` 注册模块路由。
6. 只有当路由、命名和权限条件都明确时，才在 `AppSideMenuComp.vue` 里补菜单入口。

### 4.3 路由与菜单的关系

`AppSideMenuComp.vue` 说明了当前菜单的真实用法：

- 菜单项主要通过 `:route="{ name: '...' }"` 跳转。
- 菜单展示依赖 `userArchive.identity` 和 `userArchive.permission`。
- 菜单本身是导航入口，不是完整产品规格说明。

因此以后开发时要先让真实路由成立，再决定菜单是否暴露；不要反过来根据菜单文案推导“系统一定已经有对应页面”。

---

## 5. 请求、鉴权、会话约定

### 5.1 当前代码事实

| 事实 | 依据 |
| --- | --- |
| 当前可确认的共享请求入口是 `baseHttp` | `src/Utils/Request.ts` |
| `baseHttp` 只配置了 `baseURL` 和 `timeout` | `src/Utils/Request.ts` |
| 当前没有看到全局鉴权拦截器 | `src/Utils/Request.ts` 没有 `interceptors` 相关实现 |
| 受保护请求目前靠手动传 `Authorization` | `UserStore.ts`、`UserPermissionComp.vue`、`NoticeUploadComp.vue`、`NoticeEditComp.vue` |
| 会话中心是 `useUserStore()` | `src/Stores/UserStore.ts` |
| 当前用户状态至少分 `userArchive` 和 `userBasic` | `src/Stores/UserStore.ts` |
| 登录恢复由 `App.vue` 触发，用户详情由 `UserStore.ts` 拉取 | `src/App.vue`、`src/Stores/UserStore.ts` |
| 统一响应外壳是 `{ code, msg, data }` | `src/Interfaces/ResponseImpl.ts` |
| 统一消息提示封装在 `MessageAlert.ts` | `src/Utils/MessageAlert.ts` |

### 5.2 以后开发必须遵守的约束

1. **统一走 `baseHttp`。** 新页面不要自己再 `import axios` 起第二套实例。
2. **不要假装项目已有全局自动鉴权。** 当前没有看到拦截器，受保护请求就显式带 `Authorization`。
3. **当前用户、管理员身份、权限判断优先读 `UserStore`。** 不要在每个页面重复维护一份“当前登录人”。
4. **响应处理统一按 `ResponseImpl`。** 先看 `code`，提示信息优先复用 `successMessage / infoMessage / warningMessage / errorMessage`。
5. **不要虚构 service 层。** 当前项目真实状态是“组件和 Store 直接调 `baseHttp`”。如果未来要整理，也必须建立在现有结构之上，而不是假设仓库里已经有完整 `services/` 体系。

---

## 6. UI 与布局约定

### 6.1 当前代码事实

当前后台虽然粗糙，但视觉和页面节奏并不混乱，至少有这些稳定信号：

| 事实 | 依据 |
| --- | --- |
| 顶栏是半透明浅色块 | `src/Styles/Header.css` |
| 默认容器常用深灰半透明背景、模糊、白色阴影 | `src/Styles/DefaultContainer.css` |
| 进入动画统一是 `container_in` | `src/Styles/ContainerAni.css` |
| 内容区域由 `AppContainerComp.vue` 统一承接 `<RouterView/>` | `src/Components/App/AppContainerComp.vue` |
| 后台菜单放在左侧 `el-aside + el-menu` | `src/Components/App/AppSideMenuComp.vue` |
| 用户权限页面是“筛选栏 + 结果区 + 分页 + 弹窗修改” | `src/Components/User/UserPermissionComp.vue` |
| 公告模块是“列表页 + 独立新增页 + 独立编辑页” | `src/Components/Notice/NoticeListComp.vue`、`NoticeUploadComp.vue`、`NoticeEditComp.vue` |
| 富文本编辑当前用 Quill | `NoticeUploadComp.vue`、`NoticeEditComp.vue` |

### 6.2 应继续保持的布局习惯

1. 后台页面默认挂在现有根壳下，不要自己重做一套头部和菜单。
2. 列表型管理页优先沿用两种已存在节奏：
   - `UserPermissionComp.vue` 这种“筛选 + 列表/卡片 + 分页 + 弹窗”
   - `Notice/*` 这种“列表 + 新增页 + 编辑页”
3. 表单、菜单、分页、对话框优先继续使用 Element Plus。
4. 视觉上保持现有半透明、轻模糊、浅色阴影、轻动画的语气，不要突然切成另一套企业中台风格。
5. 当前已核对的管理后台代码里没有可确认的设计令牌系统，所以不要凭空造出“全局 design tokens 已存在”的前提。

---

## 7. 要保留的模式

以下模式来自当前代码，未来 AI 扩展后台时应优先继承：

1. **统一后台根壳**：以 `App.vue` 处理未登录、管理员、无权限三种状态。
2. **模块化路由拆分**：每个模块一个 `src/Routers/Router/*Router.ts`，由 `src/Routers/index.ts` 汇总。
3. **View 尽量薄，业务逻辑落到 Components**：`UserView.vue`、`NoticeView.vue` 已经这么做。
4. **单一请求入口**：统一从 `src/Utils/Request.ts` 的 `baseHttp` 出发。
5. **单一会话中心**：当前用户和基础信息放在 `src/Stores/UserStore.ts`。
6. **统一消息提示**：复用 `src/Utils/MessageAlert.ts`，不要每页直接散写 `ElMessage(...)`。
7. **统一响应外壳**：按 `ResponseImpl` 的 `code / msg / data` 解析。
8. **成熟业务参考优先级**：优先参考 `UserPermissionComp.vue` 和 `Notice/*`，而不是空壳模块。

---

## 8. 不要照抄的反模式

以下内容是“当前代码里存在”，但不应被未来 AI 复制成长期规范：

1. **把旧文档当最高规范。** 当前后台有冲突文档，代码优先。
2. **把 `Work` / `Message` 当成熟模板。** 这两个模块目前 View 还是空的，只能算占位。
3. **把菜单文案当完整需求来源。** `AppSideMenuComp.vue` 是现有导航，不是完整功能说明书。
4. **再造一套请求底座。** 当前没有第二个请求入口，也不要新建一个假想的拦截器体系。
5. **虚构不存在的基础设施。** 以当前已核对的管理后台代码和相关文档为准，没有可确认的全局鉴权拦截器、设计令牌系统、测试栈说明，也没有现成的完整 service 层。不要在文档或实现里把这些说成“项目已有”。
6. **把手动读取 token 描述成最佳实践。** 这是当前约束，不是值得扩散的优雅方案。
7. **继续复制明显的半成品交互。** 例如 `NoticeUploadComp.vue` 和 `NoticeEditComp.vue` 的“取消返回”按钮当前没有绑定行为。
8. **继续复制明显的请求低效写法。** `NoticeListComp.vue` 先拉公告分页，再逐条请求作者信息，存在典型的 N+1 请求味道。未来重做同类页面时应识别这是历史实现，不是推荐模板。
9. **把复杂业务继续全部堆进单文件页面。** 当前部分组件直接发请求是事实，但新模块至少要保持“模块内有边界”，不要进一步扩大耦合。

---

## 9. 未来后台页面实现清单

以后让 AI 新增后台页面或模块，至少要求它逐项确认下面这些点：

### 9.1 结构清单

- [ ] 这个页面是否应挂在当前 `App.vue` 管理后台壳内，而不是单独起布局。
- [ ] 是否新建了明确的 `src/Routers/Router/XxxRouter.ts`。
- [ ] 是否有对应的 `src/Views/XxxView.vue`。
- [ ] 业务组件是否放在 `src/Components/Xxx/`。
- [ ] 若要加菜单入口，是否已经先有真实路由和明确权限条件。

### 9.2 请求与鉴权清单

- [ ] 是否统一使用 `src/Utils/Request.ts` 的 `baseHttp`。
- [ ] 是否明确区分公开请求和需要鉴权的请求。
- [ ] 若接口需要登录，是否显式附带 `Authorization`。
- [ ] 是否按 `ResponseImpl` 处理返回值，而不是自己发明另一种响应协议。
- [ ] 是否复用了 `MessageAlert.ts` 做反馈提示。

### 9.3 状态与权限清单

- [ ] 是否优先复用了 `useUserStore()` 的当前用户状态。
- [ ] 是否避免在多个页面重复存一份当前用户信息。
- [ ] 是否把管理员身份和权限判断建立在现有 `userArchive` 数据上。

### 9.4 UI 清单

- [ ] 是否保持“顶栏 + 左侧菜单 + 主内容区”的后台骨架。
- [ ] 是否优先使用 Element Plus 现有组件。
- [ ] 是否延续当前半透明、轻模糊、浅色阴影、轻动画的视觉语气。
- [ ] 若是富文本后台，是否先参考 `NoticeUploadComp.vue` / `NoticeEditComp.vue` 的 Quill 用法。

### 9.5 边界清单

- [ ] 是否明确写明“文档冲突时当前 `BarcManageFrontend` 代码获胜”。
- [ ] 是否没有把 `Work` / `Message` 这种未完成模块当成强制规范。
- [ ] 是否没有虚构当前项目并不存在的拦截器、设计令牌、测试体系或成熟 service 层。

---

## 10. 给未来 AI 的一句话版本

先仿当前 `App.vue` 的后台根壳，按 `src/Routers/Router/*.ts` 做模块路由，统一走 `Request.ts` 的 `baseHttp`，把当前用户状态交给 `UserStore.ts`，优先参考 `UserPermissionComp.vue` 和 `Notice/*`；同时记住，旧文档会误导你，空壳模块不能当模板，不存在的基础设施不要脑补。
