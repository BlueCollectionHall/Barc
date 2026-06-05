# 个人中心“喜欢的作品”功能设计文档

> **日期**: 2026-06-04
> **状态**: 待用户审核
> **范围**: BarcBackend + BarcFrontend
> **前置依赖**: 已完成作品详情页点赞/取消点赞基础能力与 `work_like` 持久化

---

## 一、需求概述

在现有个人中心页面中，当前已经有：

- `作品集`
- `收录集`

本次需要在其右侧增加第三个选项：

- `喜欢`

该页面不是仅供本人查看的私有页面，而是**通用用户主页**。因此：

- 看自己的主页时，显示自己当前点赞的公开作品
- 看别人的主页时，显示别人当前点赞的公开作品

### 1.1 本次要做

1. 在个人中心中新增第三个分栏“喜欢”
2. 该分栏展示目标用户当前点赞的作品
3. 列表只显示当前仍然公开可访问的作品
4. 列表按点赞时间倒序排列
5. 继续复用个人中心现有作品列表 UI 与分页机制

### 1.2 本次明确不做

1. 不在该列表卡片中直接支持取消喜欢
2. 不新增喜欢历史能力
3. 不新增管理端“查看某人喜欢作品”能力
4. 不改作品列表页、收录集页、作品集页行为
5. 不新增点赞通知或推荐逻辑

---

## 二、已确认的产品边界

根据本轮澄清，需求边界如下：

### 2.1 列表语义

“喜欢的作品”表示：

- 某个用户**当前仍然点过心**的作品
- 不区分该作品是不是他自己的作品
- 只要当前点赞关系还存在，就出现在列表里

### 2.2 页面适用范围

个人中心是通用资料页，因此：

- 自己主页显示自己的喜欢作品
- 别人主页显示别人的喜欢作品

### 2.3 可见性规则

该分栏对访客公开：

- 任何能访问用户主页的人，都能看到该分栏

### 2.4 作品过滤规则

若某个用户曾点赞某作品，但该作品后来变为不可公开状态，则该作品不出现在该列表中。

本次列表只展示当前状态为：

- `PUBLIC`

不展示：

- `PRIVATE`
- `OFF`
- `BAN`
- `DELETED`

### 2.5 排序规则

列表按点赞时间倒序：

- 最近点赞的作品排最前

### 2.6 交互边界

该列表只负责展示，不负责取消点赞：

- 用户进入作品详情页后，仍可通过详情页心形取消喜欢
- 本次不在个人中心列表卡片中加入心形切换交互

---

## 三、现状结构分析

### 3.1 前台个人中心现状

当前个人中心前台结构已探明：

- 路由：`/account`
- 路由定义：`BarcFrontend/src/router/AccountRouter.ts`
- 页面入口：`BarcFrontend/src/views/AccountView.vue`

页面链路：

1. `HeaderComp.vue` 点击头像
2. 跳转 `{ name: "Account", query: { username } }`
3. `AccountView.vue` 组合以下组件：
   - `AccountHeaderComp.vue`
   - `AccountUserCardComp.vue`
   - `AccountItemComp.vue`
   - `AccountWorkListComp.vue`

### 3.2 当前分栏切换现状

`AccountItemComp.vue` 当前管理分栏切换：

- `作品集` → `works`
- `收录集` → `collections`

且代码中已经存在一个被注释的“喜欢”占位项，说明该位置本身就适合扩第三个选项。

### 3.3 当前列表与分页现状

列表与分页由以下部分驱动：

- `AccountWorkItemListPinia.ts`
- `AccountWorkListComp.vue`

现有逻辑：

- `type=works` 时，按 `author_username` 查作品集
- `type=collections` 时，按 `uploader_username` 查收录集
- 统一调用现有分页接口并将结果交给同一个列表组件渲染

因此，“喜欢”最适合继续沿用同一套切换与分页结构，而不是新开一整套列表组件。

---

## 四、方案对比

### 方案 A：在现有个人中心列表体系上扩 `type=likes`（推荐）

做法：

1. `AccountItemComp.vue` 增加第三个选项 `likes`
2. `AccountWorkItemListPinia.ts` 增加 `likes` 分支
3. 后端新增一个专门的“按用户名查询喜欢作品分页”接口
4. `AccountWorkListComp.vue` 继续复用

**优点：**

- 改动最小
- 和现有 `作品集 / 收录集` 结构完全一致
- 分页、路由 query、列表渲染逻辑都可复用

**缺点：**

- 需要补一个新后端分页接口

### 方案 B：新建独立“喜欢作品”组件和独立 store

做法：

- 为“喜欢”单独新建列表组件与状态管理

**优点：**

- 未来扩展空间更大

**缺点：**

- 对当前需求过重
- 和现有个人中心体系重复

### 方案 C：把 likes 查询硬塞进现有 `/api/work/works_by_page`

做法：

- 扩现有公共分页接口，让它同时承担点赞列表逻辑

**优点：**

- 表面上接口数量更少

**缺点：**

- 语义混乱
- 会把“用户喜欢关系”耦合进本来偏公共作品查询的主分页接口
- 后续维护最差

### 4.1 结论

本次采用 **方案 A**。

---

## 五、整体设计

### 5.1 前台设计

继续沿用个人中心现有结构：

- `AccountView.vue`：页面容器，不做大改
- `AccountItemComp.vue`：新增第三个选项 `喜欢`
- `AccountWorkItemListPinia.ts`：新增 `type=likes` 分支
- `AccountWorkListComp.vue`：列表 UI 继续复用

### 5.2 后端设计

新增一个**专门用于用户主页喜欢作品列表**的接口，建议挂在：

- `POST /api/work/like/list_by_username`

理由：

- 当前需求不是“查我自己的喜欢作品”而是“查某个主页用户的喜欢作品”
- 页面路由现有参数是 `username`
- likes 领域已经归属于 `/api/work/like/*`
- 能避免污染 `/api/work/works_by_page` 的语义

---

## 六、后端接口设计

### 6.1 接口定义

建议新增：

```http
POST /api/work/like/list_by_username?username={username}
Content-Type: application/json
```

该接口应保持为：

- **公开接口**
- 不依赖当前登录用户身份
- 不要求 `Authorization`

也就是说，它的查询目标只由：

- 主页目标 `username`

来决定，而不是由访问者是谁来决定。

请求体：

- `PageRequestDto`

返回：

- `PageResultDto<WorkModel>`

### 6.2 查询语义

查询对象不是当前登录用户，而是：

- 个人中心页面正在展示的目标用户

具体过程：

1. 用 `username` 查询目标用户 `uuid`
2. 按 `work_like.user_uuid = 目标用户uuid`
3. 联表查询 `work`
4. 仅保留 `work.status = PUBLIC`
5. 按 `work_like.created_at DESC`
6. 做分页
7. 对封面图继续做签名处理

这里的“喜欢关系”必须明确指向：

- **当前仍有效的点赞关系**

本项目当前点赞基础能力已确认是：

- 取消喜欢时直接删除 `work_like` 记录
- 不保留软删除/历史记录

因此本次 liked-list 查询只需要查询当前存在的 `work_like` 行，不需要额外处理历史状态过滤。

### 6.3 建议放置位置

新增或扩展以下位置：

- `WorkLikeController`
- `WorkLikeService`
- `WorkLikeMapper`

尽量保持 likes 的能力内聚在同一模块中。

### 6.4 Mapper 查询建议

建议增加：

1. 按用户名对应 uuid 查询点赞作品分页列表
2. 按同样条件查询总数

SQL 语义可概括为：

```sql
SELECT w.*
FROM work_like wl
JOIN work w ON wl.work_id = w.id
JOIN user_basic ub ON wl.user_uuid = ub.uuid
WHERE ub.username = #{username}
  AND w.status = 'PUBLIC'
ORDER BY wl.created_at DESC
LIMIT #{offset}, #{page_size}
```

统计总数同条件 `COUNT(*)` 即可。

### 6.5 返回数据要求

为了复用现有个人中心列表组件，后端返回的 `WorkModel` 必须满足该列表的现有字段需求，至少要保证：

- `id`
- `title`
- `cover_image`（签名后）
- `view_count`
- `like_count`
- 当前已有列表依赖的其他基础字段

不能只返回 `work_like` 关系行或最小字段集合，否则前台当前复用列表会出问题。

---

## 七、前台交互设计

### 7.1 分栏

在 `AccountItemComp.vue` 中，分栏顺序为：

1. `作品集`
2. `收录集`
3. `喜欢`

即“喜欢”明确放在右边第三个位置。

### 7.2 路由 query 约定

继续沿用当前 query 驱动模式：

- `type=works`
- `type=collections`
- `type=likes`

分页仍沿用：

- `page_num`

### 7.3 Store 行为

在 `AccountWorkItemListPinia.ts` 中新增分支：

- `type=likes` 时，调用新的 liked-list 分页接口

而不是复用 `/api/work/works_by_page`

原因：

- likes 的排序依据是 `work_like.created_at`
- 查询维度是“被某用户点赞的作品”
- 与 authored/uploader 两种分页条件不是同一类语义

### 7.4 列表交互

该列表只负责展示：

- 点击作品卡片，正常进入作品详情页
- 不在卡片上直接取消喜欢

取消喜欢的唯一入口仍然是：

- 作品详情页心形按钮

这能避免把交互复杂度扩散到个人中心共用列表中。

---

## 八、空态与体验细节

### 8.1 当前现状

当前个人中心列表：

- 没有明确空态 UI
- 没有明显 loading 态

### 8.2 本次建议

在不扩需求的前提下，建议仅在**极小改动即可复用现有列表逻辑**时，顺手补齐：

- 当 `likes` 分栏结果为空时，显示统一空态文案，例如：`暂无数据`

若复用现有作品模块里的空态样式成本低，则优先复用；若复用成本高，则本项应直接延期，不为了空态而新建较重的 UI 结构。

### 8.3 为什么这里允许顺手补空态

原因是：

- 新增第三栏后，空列表是高概率场景
- 没有空态时用户会误以为页面没加载出来
- 但该补充只能建立在“极小改动”前提上，不能演变成额外的页面重构

---

## 九、兼容性与风险控制

### 9.1 必须保持不变的内容

1. `作品集` 行为不变
2. `收录集` 行为不变
3. 现有详情页点赞 toggle 逻辑不变
4. 管理端接口与 UI 不做改动

### 9.2 风险点

#### 风险 1：username 查不到用户

处理建议：

- 返回空分页结果
- 不直接炸接口

#### 风险 2：列表结果字段不足

处理建议：

- 后端 liked-list 必须返回现有列表所需完整作品基础字段
- 尤其要保证 `cover_image`、`like_count`、`view_count` 可用

#### 风险 3：不可公开作品泄漏

处理建议：

- liked-list 查询时强制 `work.status = PUBLIC`
- 不因“主页主人就是点赞者”而放开私有内容

#### 风险 4：后续取消喜欢后列表同步

处理建议：

- 本次先接受“从详情页返回个人中心后，由现有重新进入/刷新流程更新”
- 不在本轮额外做跨页面即时同步总线

---

## 十、实现范围清单

### 10.1 后端新增/修改

预计涉及：

- `WorkLikeController`
- `WorkLikeService`
- `WorkLikeMapper`
- 可能需要补对应测试

### 10.2 前端新增/修改

预计涉及：

- `AccountItemComp.vue`
- `AccountWorkItemListPinia.ts`
- 可能新增一个 liked-list API helper
- 视情况轻量调整 `AccountWorkListComp.vue` 空态展示

### 10.3 不应触碰的范围

- 管理端页面逻辑
- 作品详情页点赞基础逻辑
- 非个人中心页面的作品卡片交互

---

## 十一、验证要点

1. 访问自己主页时，“喜欢”分栏可见
2. 访问别人主页时，“喜欢”分栏可见
3. 列表只展示公开作品
4. 排序按点赞时间倒序
5. 点进详情页后功能正常
6. 取消喜欢后，后续重新进入/刷新列表时不再出现该作品
7. `作品集` / `收录集` 原有行为不被破坏
8. 管理端构建与类型不受影响

---

## 十二、最终结论

本次“喜欢的作品”功能应当作为个人中心现有列表体系的第三种类型来实现，而不是单独重做一套页面结构。

最终方案为：

- 前台在个人中心新增第三个分栏 `喜欢`
- 后台新增 `POST /api/work/like/list_by_username`
- 按主页目标用户查询其当前点赞的公开作品
- 按点赞时间倒序分页返回
- 前台复用现有 `AccountWorkListComp.vue`
- 列表只展示，不支持直接取消喜欢

该方案在当前代码结构下改动最小、边界最清晰，也最不容易破坏现有个人中心与管理端逻辑。
