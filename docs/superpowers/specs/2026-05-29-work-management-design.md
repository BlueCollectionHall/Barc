# 管理端作品管理功能设计文档

> **日期**: 2026-05-29
> **状态**: 待审批
> **范围**: BarcBackend + BarcManageFrontendV2

---

## 一、需求概述

管理端需要新增作品管理功能，包括：
1. 作品状态管理（封禁、下架、删除、恢复）
2. 作品认领管理（审批、撤销、指派、历史）
3. 作品内容修改（全部字段可编辑）
4. 投诉反馈处理（处理时联动操作）
5. 操作日志记录

### 权限要求

使用 `SEC_MAINTAINER`（二级管理员，值=4）权限位，通过位运算 `(permission & 4) != 0` 检查。

**重要**：馆长(32)和副馆长(16)不天然拥有此权限，必须显式包含SEC_MAINTAINER位（如36=32+4，20=16+4）。

注解配置：
```java
@RequireUserAndPermissionAnno({
    @RequireUserAndPermissionAnno.Check(
        identity = UserIdentityEnum.MANAGER,
        targetPermission = PermissionConst.SEC_MAINTAINER,
        isHasElseUpper = true  // 位运算检查
    )
})
```

---

## 二、后端设计

### 2.1 架构方案

扩展现有 `api/work/` 模块，新增 `WorkManageController` 和相关Service。

### 2.2 新增Controller

**`WorkManageController`** - 路径前缀：`/api/work/manage`

**认领审批权限说明**：审批认领端点使用OR逻辑，风纪委员(1)或二级管理员(4)任一权限即可。其他端点仅需二级管理员(4)。

| 端点 | 方法 | 功能 | 权限注解 |
|------|------|------|---------|
| `/list` | POST | 分页获取作品列表（含所有状态） | SEC_MAINTAINER |
| `/detail` | GET | 获取作品详情（含已删除） | SEC_MAINTAINER |
| `/status` | PUT | 修改作品状态 | SEC_MAINTAINER |
| `/update` | PUT | 修改作品内容 | SEC_MAINTAINER |
| `/claims` | GET | 获取认领列表 | SEC_MAINTAINER |
| `/claim/approve` | POST | 审批认领 | DISCIPLINARY_COMMITTEE **或** SEC_MAINTAINER |
| `/claim/revoke` | POST | 撤销认领 | SEC_MAINTAINER |
| `/claim/assign` | POST | 管理员指派作者 | SEC_MAINTAINER |
| `/claim/history` | GET | 认领历史记录 | SEC_MAINTAINER |
| `/complaints` | GET | 获取投诉列表 | SEC_MAINTAINER |
| `/complaint/process` | POST | 处理投诉（联动操作） | SEC_MAINTAINER |
| `/logs` | GET | 操作日志列表 | SEC_MAINTAINER |

### 2.3 新增Service

**`WorkManageService`** - 核心业务逻辑

### 2.4 数据库变更

#### 2.4.1 修改 `WorkStatusEnum`

```java
public enum WorkStatusEnum {
    PUBLIC("公开"),
    PRIVATE("私有"),
    OFF("下架"),
    BAN("封禁"),
    DELETED("已删除");  // 新增
}
```

#### 2.4.2 新增表 `work_operation_log`

```sql
CREATE TABLE IF NOT EXISTS work_operation_log (
    id VARCHAR(36) PRIMARY KEY NOT NULL,
    work_id VARCHAR(100) NOT NULL,
    operator_uuid VARCHAR(32) NOT NULL,
    operation_type VARCHAR(30) NOT NULL COMMENT 'BAN/OFF/DELETE/RESTORE/EDIT/CLAIM_APPROVE/CLAIM_REVOKE/CLAIM_ASSIGN/COMPLAINT_PROCESS',
    detail TEXT NULL COMMENT '操作详情JSON',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_work_id (work_id),
    INDEX idx_operator (operator_uuid),
    INDEX idx_operation_type (operation_type),
    INDEX idx_created_at (created_at),
    FOREIGN KEY (work_id) REFERENCES work(id) ON DELETE CASCADE,
    FOREIGN KEY (operator_uuid) REFERENCES user_basic(uuid) ON DELETE CASCADE
);
```

### 2.5 状态转换规则

```
PUBLIC ←→ PRIVATE （用户自己切换，现有逻辑）
PUBLIC/PRIVATE → BAN （管理员封禁）
PUBLIC/PRIVATE → OFF （管理员下架）
PUBLIC/PRIVATE/BAN/OFF → DELETED （管理员删除）
BAN/OFF/DELETED → PUBLIC （管理员恢复）
```

BAN和OFF之间不能直接转换，需先恢复为PUBLIC。

### 2.6 API契约

#### 修改作品状态
```
PUT /api/work/manage/status
Body: { "work_id": "xxx", "status": "BAN", "remark": "违规内容" }
Response: { "code": 0, "msg": "状态修改成功", "data": null }
```

#### 处理投诉联动操作
```
POST /api/work/manage/complaint/process
Body: { "complaint_id": "xxx", "action": "BAN", "remark": "确认违规" }
Response: { "code": 0, "msg": "处理成功", "data": null }
```

处理成功后自动：
1. 更新投诉状态为已处理
2. 如果action不是IGNORE，同时修改作品状态
3. 记录操作日志
4. 发送邮件通知投诉人和作品作者

#### 撤销认领
```
POST /api/work/manage/claim/revoke
Body: { "work_id": "xxx" }
Response: { "code": 0, "msg": "撤销成功", "data": null }
```
执行：`work.is_claim = false, work.author = "707B0FBF6AAA35B788069B07AEFEA12B"`（默认UUID）

#### 管理员指派作者
```
POST /api/work/manage/claim/assign
Body: { "work_id": "xxx", "author_uuid": "yyy" }
Response: { "code": 0, "msg": "指派成功", "data": null }
```
执行：`work.is_claim = true, work.author = author_uuid`，删除该作品的所有待审批认领申请

### 2.7 前台兼容性

- 前台查询接口传入 `PUBLIC` 状态时不会返回DELETED作品
- 前台 `getWorksByIdService` 需补充DELETED状态返回"作品已被删除"
- **不需要修改前台其他代码**

---

## 三、前端设计

### 3.1 模块结构

```
BarcManageFrontendV2/src/modules/works/
├── api/
│   └── workManage.ts
├── components/
│   ├── WorkListComp.vue
│   ├── WorkDetailComp.vue
│   ├── WorkStatusBadge.vue
│   ├── ClaimListComp.vue
│   ├── ClaimHistoryComp.vue
│   ├── ComplaintListComp.vue
│   └── OperationLogComp.vue
├── views/
│   ├── WorksListView.vue
│   ├── WorkEditView.vue
│   ├── ClaimsListView.vue
│   ├── ComplaintsListView.vue
│   └── OperationLogView.vue
└── __tests__/
```

### 3.2 路由配置

| 路由 | 页面 | 菜单分组 | 权限 |
|------|------|---------|------|
| `works/list` | 作品列表 | 内容管理 | SEC_MAINTAINER(4) |
| `works/:workId/edit` | 作品编辑 | (隐藏) | SEC_MAINTAINER(4) |
| `works/claims` | 认领管理 | 内容管理 | SEC_MAINTAINER(4) |
| `works/complaints` | 投诉处理 | 内容管理 | SEC_MAINTAINER(4) |
| `works/logs` | 操作日志 | 内容管理 | SEC_MAINTAINER(4) |

### 3.3 页面功能

#### 作品列表页
- 顶部筛选：状态（全部/公开/私有/下架/封禁/已删除）、关键词搜索
- 表格列：封面缩略图、标题、作者、状态、创建时间、操作
- 操作按钮：查看详情、修改状态、编辑内容
- 批量操作：批量封禁、批量下架、批量删除

#### 作品编辑页
- 表单字段：标题、简介、富文本内容、封面图上传、Banner图上传、分类选择、关联学生选择
- 保存按钮

#### 认领管理页
- Tab切换：待审批 / 已认领 / 全部
- 待审批：审批通过/拒绝
- 已认领：撤销认领
- 管理员指派：选择作品→选择用户→指派

#### 投诉处理页
- 列表：投诉时间、作品标题、投诉原因、投诉人邮箱、状态
- 处理弹窗：选择操作（封禁/下架/删除/忽略）、填写处理备注、确认执行

#### 操作日志页
- 列表：操作时间、操作人、操作类型、作品标题、详情
- 筛选：按操作类型、操作人、时间范围

---

## 四、安全与边界

### 4.1 安全考虑
- 所有管理端点强制要求SEC_MAINTAINER权限位
- 操作日志不可删除，保证审计追溯
- 删除为软删除，数据可恢复

### 4.2 边界情况
- 已删除作品的评论、认领申请如何处理？→ 保留，恢复时可见
- 同一作品被多人同时投诉？→ 各投诉独立处理
- 认领撤销后，原认领申请是否保留？→ 保留历史记录

### 4.3 邮件通知
- 投诉处理后通知投诉人
- 作品被封禁/下架/删除时通知作者
- 认领审批结果通知申请人

---

## 五、实现范围

### 后端新增
- `WorkManageController.java`
- `WorkManageService.java`
- `WorkOperationLogMapper.java`
- `WorkOperationLogModel.java`
- `schema.sql` 新增 `work_operation_log` 表

### 后端修改
- `WorkStatusEnum.java` 新增 `DELETED`
- `WorkService.getWorksByIdService()` 补充DELETED状态处理
- `WorkMapper.java` 新增管理相关查询方法
- `WorkMapper.xml` 新增动态SQL

### 前端新增
- `modules/works/` 整个模块

### 前端修改
- `routes.ts` 新增works路由
- `shared/constants/permissions.ts` 确认SEC_MAINTAINER常量存在

---

## 六、测试要点

1. 权限校验：无SEC_MAINTAINER位的用户无法访问管理端点
2. 状态转换：各状态之间转换的正确性
3. 软删除：DELETED状态的作品不出现在前台查询中
4. 投诉联动：处理投诉时正确执行封禁/下架/删除
5. 认领管理：审批、撤销、指派的正确性
6. 操作日志：每次操作正确记录
7. 邮件通知：投诉处理和状态变更时发送邮件
