# 背景图场景化（时段+节日）— 最终对齐方案

> 日期：2026-09-07
> 状态：**已实施并验证通过**（未提交）
> 目标：让"管理端配置体验"与"用户端实际生效规则"一致、明朗。
> 说明：基于全工程代码勘探，「程序事实」均标注到文件与行号。

---

## 一、勘探结论（核心一句话）

**"时段 + 节日"这套场景化筛选逻辑，当前只对「首页 home」这一个模块真正生效；
管理端却对全部模块的每一张图都强制展示并配置「时段」「节日」两个标签。**
对于未接入场景筛选的模块，这两列标签在用户端被完全忽略 —— 属于"纯摆设"。
这正是管理端"乱、不明朗"的根源。

---

## 二、程序事实（证据）

### 2.1 用户端：场景化真正生效范围 = 仅 home

| 模块 | 组件 | 选图方式 | 是否判定时段/节日 |
|------|------|---------|-------------------|
| home | `src/components/Home/HomeBgComp.vue` | `fetchModuleBackgroundItems` + `pickBackgroundByScene` | ✅ 是（唯一） |
| sign | `src/components/Sign/SignBgComp.vue` | `fetchModuleBackgrounds` | ❌ 否 |
| join | `src/components/Join/JoinBgComp.vue` | `fetchModuleBackgrounds` | ❌ 否 |
| feedback | `src/components/Feedback/FeedbackBgComp.vue` | `fetchModuleBackgrounds` | ❌ 否 |
| messageboard | `src/components/MessageBoard/MbBgComp.vue` | `fetchModuleBackgrounds` | ❌ 否 |
| student | — | 与首页共用背景图 | 视同 home |

证据：
- `src/utils/backgroundApi.ts:34-37` —— `fetchModuleBackgrounds` **只返回 url 数组，丢弃时段/节日标签**：
  ```ts
  return items.map((item) => item.url);
  ```
- 所以 sign / join / feedback / messageboard 四处都是 `Math.random()` 从全池随机抽一张，**不读取**时段/节日标签。

### 2.2 用户端节日优先级（疑点 2）——home 已达标，有测试佐证

- 裁决逻辑 `src/utils/backgroundApi.ts:103-105`：
  ```ts
  const festivalPool = items.filter((item) => item.festival === festival);
  const generalPool = items.filter((item) => item.festival == null);
  const basePool = festival ? (festivalPool.length > 0 ? festivalPool : generalPool) : generalPool;
  ```
  - 节日季：有节日图 → 只取节日图（节日优先）；无节日图 → 回退非节日通用图。
  - 非节日季：只取非节日图，节日图被排除。
- 测试覆盖 `src/utils/__tests__/backgroundApi.spec.ts:59-81`（节日优先 / 无节日图回退 / 非节日季排除节日图），全部通过。

### 2.3 用户端无时段穿插（疑点 3）——home 已达标，有测试佐证

- 逻辑 `src/utils/backgroundApi.ts:111`：
  ```ts
  const timePool = basePool.filter((item) => !item.time_period || item.time_period === time_period);
  ```
  - 无时段图被无条件纳入每个时段候选池 → 参与各时段随机轮播。
- 测试佐证 `src/utils/__tests__/backgroundApi.spec.ts:41-50`。

### 2.4 后端：公开接口不裁决

- `BackgroundImageService.publicAllService`（`BackgroundImageService.java:225-236`）只把启用图按 module 分组成 list 原样返回，**不做任何节日/时段筛选**。
- 结果：优先级裁决全部发生在用户前端 `pickBackgroundByScene`；后端只负责"是否启用 + 返回标签字段"。

### 2.5 管理端：逐图、逐模块展示并配置标签

- `BarcManageFrontendV2/.../views/BackgroundsListView.vue:195-219`：每张图所在行都有「时段」「节日」两个 `el-select`，逐图要求配置。
- 模块切换 `BackgroundsListView.vue:157`；后端共 6 模块（`BackgroundModuleEnum`：sign / home / join / feedback / messageboard / student）。
- 管理端只有"单张图片点击放大"预览（`el-image` 的 `preview-src-list`，`BackgroundsListView.vue:178-187`），**没有"当前时刻会展示哪些图"的候选池预览**（本方案**明确不做**此功能）。

---

## 三、最终对齐的决策

| 序号 | 决定 | 落地含义 |
|------|------|---------|
| 1 | 保留首页场景化，其余模块纯随机 | 场景化仅 home 启用；其余模块维持全池随机 |
| 2 | 未启用场景化的模块，管理端隐藏「时段」「节日」两列 | 只在启用场景化的模块显示这两列；其余隐藏 |
| 3 | 未启用模块的代码加中文注释，方便以后接上场景化 | 说明扩展点：将来某模块要启用，只需换用 `fetchModuleBackgroundItems` + `pickBackgroundByScene` |
| 4 | **不做**"当前时刻候选池预览" | 最小改动，只解决"乱" |
| 5 | student 不补组件，与首页共用；**管理端模块列表去掉 student 项** | 学生详情页复用首页背景图；管理端不再出现独立的"学生详情"背景图模块 |

---

## 四、实施改动点

### A. 后端：给模块增加"是否启用场景化"标志
- `BackgroundModuleEnum` 增加 `sceneEnabled` 字段（是否启用时段+节日场景化）。
- `/api/background/modules` 返回结构带上该标志（当前返回 `List<ValueLabelDto>` 仅 value/label，需扩字段或换结构）。
- 管理端据此判断哪些模块显示标签列。
- **赋值**：home = `true`；sign / join / feedback / messageboard = `false`；student 直接移除（或不再返回）。

### B. 用户端：注释 + 扩展点
- `backgroundApi.ts` 的 `pickBackgroundByScene` / `fetchModuleBackgrounds` 处加中文注释：
  - 说明"当前仅 home 启用场景化，其余模块纯随机"；
  - 说明扩展点：未来某模块要启用场景化，将它换成 `fetchModuleBackgroundItems` + `pickBackgroundByScene` 即可。
- `BackgroundModuleEnum` 同步注释上述规则。

### C. 管理端：标签列按模块显隐
- `BackgroundsListView.vue`：根据模块 `sceneEnabled` 决定是否渲染「时段」「节日」两列。
- 未启用模块：隐藏两列，表格区给出提示"该模块按启用顺序随机轮播，暂不支持时段/节日"。
- （student 项从模块列表移除后，管理端只显示 5 个模块）

---

## 五、规则说明（供 UI / 文档引用）

- 无时段图 = 参与所有时段候选；
- 有时段图 = 仅参与标注时段，并作为该时段兜底；
- 节日季 = 节日图优先，无节日图才动用通用图；非节日季 = 不使用节日图；
- 某时段既无匹配图也无无时段图 → 退化为该模块全池兜底。

---

## 六、涉及文件清单

### 后端改
- `BarcBackend/.../background/enumeration/BackgroundModuleEnum.java`（加 `sceneEnabled`、移除/标注 student）
- `BarcBackend/.../background/service/BackgroundImageService.java`（modules 接口返回 `sceneEnabled`）
- `BarcBackend/.../background/controller/BackgroundController.java`（若 modules 返回结构变更）
- 相关单元测试（`BackgroundModuleEnumTest`、`BackgroundImageServiceTest`）

### 管理端改
- `BarcManageFrontendV2/.../backgrounds/views/BackgroundsListView.vue`（标签列显隐 + 模块提示）
- `BarcManageFrontendV2/.../backgrounds/api/backgrounds.service.ts`（`BackgroundModuleOption` 增 `sceneEnabled`）

### 用户端改（注释为主，不动逻辑）
- `BarcFrontend/src/utils/backgroundApi.ts`（注释 + 扩展点说明）

### 已确认不改
- 候选池预览：不做
- student 用户端组件：不新增

---

## 七、实施结果（2026-09-07）

### 实际改动

| 文件 | 改动 |
|------|------|
| `BarcBackend/.../background/enumeration/BackgroundModuleEnum.java` | 新增 `sceneEnabled` 字段（仅 home=true）；**移除 STUDENT 成员**；`options()` 改返回 `List<BackgroundModuleOption>`；加扩展点注释 |
| `BarcBackend/.../background/model/BackgroundModuleOption.java` | **新增**：模块选项 DTO（value / label / sceneEnabled） |
| `BarcBackend/.../background/service/BackgroundImageService.java` | 无改动，`modulesService` 透传 `options()`（返回类型随之变化） |
| `BarcBackend/.../test/.../enumeration/BackgroundModuleEnumTest.java` | **新增**：覆盖 `fromCode`、student 已废弃、`options` 携带 `sceneEnabled` |
| `BarcBackend/.../test/.../service/BackgroundImageServiceTest.java` | 更新 `modules_options`：断言 structure 变更 + home 场景启用 + 不含 student |
| `BarcManageFrontendV2/.../backgrounds/api/backgrounds.service.ts` | `BackgroundModuleOption` 增加 `sceneEnabled: boolean` |
| `BarcManageFrontendV2/.../backgrounds/views/BackgroundsListView.vue` | 新增 `sceneEnabled` computed；未启用场景化模块隐藏「时段/节日」两列并显示提示 |
| `BarcManageFrontendV2/.../backgrounds/__tests__/BackgroundsListView.spec.ts` | mock 加 `sceneEnabled`；新增"隐藏场景列并提示"用例；场景用例改为切到首页后验证 |
| `BarcFrontend/src/utils/backgroundApi.ts` | **仅注释**：说明场景化生效范围（仅 home）与未来扩展点，逻辑零改动 |

### 验证结果

- 后端 `.\mvnw.cmd test`（JDK 17）：**Tests run: 151, Failures: 0, Errors: 0** → BUILD SUCCESS
  - `BackgroundModuleEnumTest`：5 项通过
  - `BackgroundImageServiceTest`：21 项通过
- 管理端 `npm run build`（type-check + vite build）：**成功**
  - `BackgroundsListView.spec.ts`：6 项通过（含新增场景用例）
- 用户端 `npm run build`（type-check + vite build）：**成功**
  - `backgroundApi.spec.ts`：9 项通过

### 环境备注（非代码问题）
- 前端 vitest/vite 在受限沙箱下 `esbuild` 子进程 spawn 报 `EPERM`，需完整进程权限方可运行；
- 后端 Maven 需写本地 `.m2` 仓库，且 `JAVA_HOME` 须指向 JDK 17（`D:\Java\jdk-17.0.13`），系统默认 `JAVA_HOME` 为 JRE 1.8 无 javac。

