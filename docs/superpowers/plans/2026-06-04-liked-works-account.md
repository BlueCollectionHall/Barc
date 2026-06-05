# 个人中心“喜欢的作品” Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在个人中心现有“作品集 / 收录集”右侧新增第三个“喜欢”分栏，公开展示目标用户当前点赞的公开作品，并复用现有列表与分页体系。

**Architecture:** 后端在 `/api/work/like` 命名空间下新增一个按 `username` 查询的公开分页接口，返回现有 `PageResultDto<WorkModel>` 结构，过滤 `PUBLIC` 作品并按点赞时间倒序。前端沿用 `AccountItemComp.vue + AccountWorkItemListPinia.ts + AccountWorkListComp.vue` 的现有模式，只扩一个新的 `type=likes` 分支，不新建独立页面体系。

**Tech Stack:** Java 17、Spring Boot、MyBatis、Maven、JUnit 5、Mockito、Vue 3、TypeScript、Pinia、Axios、Element Plus

---

## 文件结构与职责

### 后端新增文件

- `BarcBackend/src/test/java/com/miaoyu/barc/api/work/service/WorkLikeListServiceTest.java`
  - 按用户名分页查询“喜欢的作品”的服务层单元测试

### 后端修改文件

- `BarcBackend/src/main/java/com/miaoyu/barc/api/work/controller/WorkLikeController.java`
  - 新增公开接口 `POST /api/work/like/list_by_username`
- `BarcBackend/src/main/java/com/miaoyu/barc/api/work/service/WorkLikeService.java`
  - 新增 liked-list 分页查询能力
- `BarcBackend/src/main/java/com/miaoyu/barc/api/work/mapper/WorkLikeMapper.java`
  - 新增按用户名分页查询与计数方法
- `BarcBackend/src/main/java/com/miaoyu/barc/api/work/service/WorkService.java`
  - 仅在必要时复用现有封面签名能力；不改现有详情点赞逻辑

### 前端新增文件

- `BarcFrontend/src/utils/accountLikeApi.ts`
  - 个人中心“喜欢的作品”分页请求 helper
- `BarcFrontend/src/utils/__tests__/accountLikeApi.spec.ts`
  - helper 的最小单测

### 前端修改文件

- `BarcFrontend/src/components/Account/AccountItemComp.vue`
  - 放出第三个 `喜欢` 选项
- `BarcFrontend/src/stores/AccountWorkItemListPinia.ts`
  - 新增 `type=likes` 数据分支
- `BarcFrontend/src/components/Account/AccountWorkListComp.vue`
  - 仅在极小改动前提下补空态；否则不动主结构

### 仅验证不修改的关联文件

- `BarcFrontend/src/views/AccountView.vue`
- `BarcFrontend/src/components/HeaderFooter/HeaderComp.vue`
- `BarcFrontend/src/components/Account/AccountUserCardComp.vue`
- `BarcFrontend/src/interfaces/PageImpl.ts`
- `BarcFrontend/src/interfaces/WorkImpl.ts`

---

## Task 1: 先锁后端分页契约与失败测试

**Files:**
- Create: `BarcBackend/src/test/java/com/miaoyu/barc/api/work/service/WorkLikeListServiceTest.java`
- Modify: `BarcBackend/src/main/java/com/miaoyu/barc/api/work/mapper/WorkLikeMapper.java`
- Modify: `BarcBackend/src/main/java/com/miaoyu/barc/api/work/service/WorkLikeService.java`

- [ ] **Step 1: 写 liked-list 服务失败测试**

至少覆盖：

```java
@Test
void listLikedWorksByUsername_WhenUserExists_ShouldReturnPagedPublicWorksSortedByLikeTime() {}

@Test
void listLikedWorksByUsername_WhenUsernameMissing_ShouldReturnFailure() {}

@Test
void listLikedWorksByUsername_WhenUserNotFound_ShouldReturnEmptyPage() {}

@Test
void listLikedWorksByUsername_ShouldSignCoverImagesBeforeReturn() {}
```

- [ ] **Step 2: 运行测试确认先红**

Run: `./mvnw.cmd -Dtest=WorkLikeListServiceTest test`
Workdir: `BarcBackend`

Expected:
- 因缺少 list 接口/mapper/service 方法而失败

- [ ] **Step 3: 在 `WorkLikeMapper` 中补分页查询与 count 方法定义**

建议直接使用注解 SQL，避免为本次新增 XML 文件：

```java
List<WorkModel> selectPublicLikedWorksByUsername(...)
Long countPublicLikedWorksByUsername(...)
```

SQL 必须满足：
- 通过 `user_basic.username -> uuid`
- 联表 `work_like` 与 `work`
- 仅 `w.status = 'PUBLIC'`
- `ORDER BY wl.created_at DESC`

- [ ] **Step 4: 在 `WorkLikeService` 中写最小分页逻辑**

逻辑要求：
- 用户名为空白 -> 普通错误
- 用户不存在 -> 返回空 `PageResultDto<WorkModel>`，不是接口报错
- 结果要走现有封面签名流程
- 返回结构必须兼容 `PageResultImpl<WorkImpl>`

- [ ] **Step 5: 跑 `WorkLikeListServiceTest` 至绿**

Run: `./mvnw.cmd -Dtest=WorkLikeListServiceTest test`
Workdir: `BarcBackend`

Expected: PASS

---

## Task 2: 暴露公开接口 `list_by_username`

**Files:**
- Modify: `BarcBackend/src/main/java/com/miaoyu/barc/api/work/controller/WorkLikeController.java`
- Modify: `BarcBackend/src/main/java/com/miaoyu/barc/api/work/service/WorkLikeService.java`
- Test: `BarcBackend/src/test/java/com/miaoyu/barc/api/work/service/WorkLikeListServiceTest.java`

- [ ] **Step 1: 先补接口层失败测试（如需要可加 controller test）**

目标行为：
- `POST /api/work/like/list_by_username?username=...`
- 不要求登录
- body 为 `PageRequestDto`

- [ ] **Step 2: 在 `WorkLikeController` 中新增公开端点**

```java
@IgnoreAuth
@PostMapping("/list_by_username")
public ResponseEntity<J> listLikedWorksByUsernameControl(
        @RequestParam("username") String username,
        @RequestBody PageRequestDto pageRequestDto
) {
    return workLikeService.listLikedWorksByUsername(username, pageRequestDto);
}
```

- [ ] **Step 3: 复核公开接口边界**

检查点：
- 不依赖 `request.getAttribute("uuid")`
- 不要求 `Authorization`
- 查询对象完全由 `username` 决定

- [ ] **Step 4: 跑后端本次组合测试**

Run: `./mvnw.cmd "-Dtest=WorkLikeServiceTest,WorkLikeControllerTest,WorkLikeListServiceTest,WorkServiceLikeStateTest" test`
Workdir: `BarcBackend`

Expected: PASS

---

## Task 3: 增加前端 liked-list API helper 与测试

**Files:**
- Create: `BarcFrontend/src/utils/accountLikeApi.ts`
- Create: `BarcFrontend/src/utils/__tests__/accountLikeApi.spec.ts`

- [ ] **Step 1: 先写 helper 失败测试**

至少覆盖：

```ts
it('requests liked works page by username with PageRequest payload', async () => {})
it('rejects non-zero response code with backend message', async () => {})
it('rejects malformed page payload with controlled error', async () => {})
```

- [ ] **Step 2: 运行测试确认先红**

Run: `npm run test:run -- src/utils/__tests__/accountLikeApi.spec.ts`
Workdir: `BarcFrontend`

Expected:
- 因 helper 文件不存在或返回不匹配而失败

- [ ] **Step 3: 写最小 helper 实现**

建议：

```ts
export async function fetchLikedWorksByUsername(username: string, pageRequest: PageRequestImpl): Promise<PageResultImpl<WorkImpl>>
```

请求：
- `POST /api/work/like/list_by_username?username=...`
- body: `pageRequest`

并校验返回至少具备：
- `list`
- `total`
- `page_num`
- `page_size`
- `total_page`

- [ ] **Step 4: 跑 helper 测试至绿**

Run: `npm run test:run -- src/utils/__tests__/accountLikeApi.spec.ts`
Workdir: `BarcFrontend`

Expected: PASS

---

## Task 4: 放出个人中心第三个“喜欢”分栏

**Files:**
- Modify: `BarcFrontend/src/components/Account/AccountItemComp.vue`

- [ ] **Step 1: 先写最小行为检查（若不单测组件，则以构建验证代替）**

目标：
- `itemList` 中存在第三项 `喜欢`
- 顺序在 `收录集` 右边
- 点击后写入 `type=likes`

- [ ] **Step 2: 放出第三个 item**

将现有注释项恢复为正式项：

```ts
{label: "喜欢", value: "likes", icon: "HeartOutlined", color: "#F85A54"}
```

- [ ] **Step 3: 修正当前 `selectedItem` 与 query 同步细节**

因为现有组件对 `selectedItem` 的初始化较粗，建议最小修正：
- 初次挂载时按 `route.query.type` 回填
- 避免永远默认高亮 `works`

- [ ] **Step 4: 静态验证**

Run: `npm run build`
Workdir: `BarcFrontend`

Expected: PASS

---

## Task 5: 在 Pinia 中增加 `type=likes` 分支

**Files:**
- Modify: `BarcFrontend/src/stores/AccountWorkItemListPinia.ts`
- Modify: `BarcFrontend/src/components/Account/AccountWorkListComp.vue`（仅在极小改动补空态时）

- [ ] **Step 1: 先写/更新前端 helper 使用的失败场景**

至少保证：
- `type=likes` 时不再调用 `/api/work/works_by_page`
- 而是调用新的 liked-list helper

- [ ] **Step 2: 在 store 的 switch 中加入 `likes` 分支**

语义：

```ts
case "likes": {
  pageResult.value = await fetchLikedWorksByUsername(username, pageRequest.value)
  return
}
```

并保留：
- `works` → authored
- `collections` → uploader

注意：当前 `AccountWorkItemListPinia.ts` 的结构是在 `switch` 后统一执行一次 `/api/work/works_by_page`。因此 `likes` 分支**不能只 `break`**，否则会继续落到旧请求里并覆盖 liked-list 结果。

本任务必须二选一：

1. `likes` 分支里成功取到结果后直接 `return`
2. 或者把 `works / collections` 的旧请求逻辑收进各自分支内部

本次优先推荐方案 1，因为改动最小。

- [ ] **Step 3: 仅在极小改动下补空态**

如果成本极低：
- 在 `AccountWorkListComp.vue` 加一个 `v-else` 文本空态 `暂无数据`

如果会引出额外结构重写：
- 直接跳过，不做这项

- [ ] **Step 4: 跑前端测试与构建**

Run:
- `npm run test:run -- src/utils/__tests__/accountLikeApi.spec.ts`
- `npm run build`

Workdir: `BarcFrontend`

Expected: PASS

---

## Task 6: 全量验证与兼容性确认

**Files:**
- Verify: `BarcBackend/src/main/java/com/miaoyu/barc/api/work/controller/WorkLikeController.java`
- Verify: `BarcBackend/src/main/java/com/miaoyu/barc/api/work/service/WorkLikeService.java`
- Verify: `BarcFrontend/src/components/Account/AccountItemComp.vue`
- Verify: `BarcFrontend/src/stores/AccountWorkItemListPinia.ts`
- Verify: `BarcFrontend/src/components/Account/AccountWorkListComp.vue`

- [ ] **Step 1: 跑后端目标测试与打包**

Run:
- `./mvnw.cmd "-Dtest=WorkLikeServiceTest,WorkLikeControllerTest,WorkLikeListServiceTest,WorkServiceLikeStateTest" test`
- `./mvnw.cmd -DskipTests package`

Workdir: `BarcBackend`

Expected: PASS

- [ ] **Step 2: 跑用户前端测试与构建**

Run:
- `npm run test:run -- src/utils/__tests__/workLikeApi.spec.ts src/utils/__tests__/accountLikeApi.spec.ts`
- `npm run build`

Workdir: `BarcFrontend`

Expected: PASS

- [ ] **Step 3: 跑管理端 V2 构建确认未受影响**

Run: `npm run build`
Workdir: `BarcManageFrontendV2`

Expected: PASS

- [ ] **Step 4: 做最小人工核对清单**

必须核对：
- 自己主页能看到 `喜欢`
- 别人主页能看到 `喜欢`
- 喜欢列表只显示 `PUBLIC` 作品
- 顺序按点赞时间倒序
- 点开作品详情正常
- 从详情页取消喜欢后，刷新/重新进入喜欢列表时该作品消失
- `作品集 / 收录集` 原有行为未损坏

- [ ] **Step 5: 做实现后审查**

检查点：
- 没把 liked-list 混进 `/api/work/works_by_page`
- 没把 liked-list 做成登录态专属接口
- 没扩到列表页直接取消喜欢
- 没改管理端 work 接口契约

---

## 执行顺序建议

1. Task 1
2. Task 2
3. Task 3
4. Task 4
5. Task 5
6. Task 6

后端 liked-list 契约应先稳定，再接前端第三分栏与 store 分支。前后端存在明显依赖，不建议并行开发。
