# 管理端作品管理功能 实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在管理端 BarcManageFrontendV2 中实现完整的作品管理功能，包括状态管理（封禁/下架/删除/恢复）、认领管理（审批/撤销/指派/历史）、内容修改、投诉处理和操作日志。

**Architecture:** 扩展现有 `api/work/` 后端模块，新增 `WorkManageController` 和 `WorkManageService`；前端 BarcManageFrontendV2 新增 `modules/works/` 模块。权限使用 SEC_MAINTAINER(4) 位运算检查。

**Tech Stack:** Java 17 + Spring Boot 3 + MyBatis（后端），Vue 3 + TypeScript + Ant Design Vue 4 + Element Plus（管理前端）

**Design Doc:** `docs/superpowers/specs/2026-05-29-work-management-design.md`

---

## 文件结构

```
# === 后端新增 ===
BarcBackend/src/main/java/com/miaoyu/barc/api/work/
├── controller/WorkManageController.java     # 管理端API
├── service/WorkManageService.java           # 管理端业务逻辑
├── mapper/WorkOperationLogMapper.java       # 操作日志数据访问
└── model/WorkOperationLogModel.java         # 操作日志模型

# === 后端修改 ===
BarcBackend/src/main/java/com/miaoyu/barc/api/work/
├── enumeration/WorkStatusEnum.java          # +DELETED状态
├── service/WorkService.java                 # +DELETED状态处理
├── mapper/WorkMapper.java                   # +管理查询方法
└── (XML) resources/mappers/work/WorkMapper.xml  # +动态SQL

# === 数据库 ===
BarcBackend/src/main/resources/schema.sql    # +work_operation_log表

# === 前端新增 ===
BarcManageFrontendV2/src/modules/works/
├── api/workManage.ts                        # 管理端API封装
├── views/
│   ├── WorksListView.vue                    # 作品列表页
│   ├── WorkEditView.vue                     # 作品编辑页
│   ├── ClaimsListView.vue                   # 认领管理页
│   ├── ComplaintsListView.vue               # 投诉处理页
│   └── OperationLogView.vue                 # 操作日志页
├── components/
│   ├── WorkListComp.vue                     # 作品列表组件
│   ├── WorkDetailComp.vue                   # 作品编辑表单组件
│   ├── WorkStatusBadge.vue                  # 状态标签组件
│   ├── ClaimListComp.vue                    # 认领列表组件
│   ├── ClaimHistoryComp.vue                 # 认领历史组件
│   ├── ComplaintListComp.vue                # 投诉列表组件
│   └── OperationLogComp.vue                 # 操作日志组件
└── __tests__/

# === 前端修改 ===
BarcManageFrontendV2/src/app/router/routes.ts   # +works路由
BarcManageFrontendV2/src/shared/constants/permissions.ts  # 确认SEC_MAINTAINER
```

---

### Task 1: 修改 WorkStatusEnum 新增 DELETED 状态

**Files:**
- Modify: `BarcBackend/src/main/java/com/miaoyu/barc/api/work/enumeration/WorkStatusEnum.java`

- [ ] **Step 1: 新增 DELETED 枚举值**

在 `WorkStatusEnum.java` 中，在 `BAN("封禁")` 后新增 `DELETED("已删除")`：

```java
package com.miaoyu.barc.api.work.enumeration;

import com.miaoyu.barc.utils.dto.ValueLabelDto;

import java.util.Arrays;
import java.util.List;

public enum WorkStatusEnum {
    PUBLIC("公开"),
    PRIVATE("私有"),
    OFF("下架"),
    BAN("封禁"),
    DELETED("已删除");  // 新增：软删除状态，仅管理员可见

    private static final List<ValueLabelDto> CACHED_OPTIONS = Arrays.stream(WorkStatusEnum.values())
            .map(e -> new ValueLabelDto(e.name(), e.getName()))
            .toList();
    public static List<ValueLabelDto> getOptions() {
        return CACHED_OPTIONS;
    }

    private final String name;

    WorkStatusEnum(String name) {
        this.name = name;
    }
    public String getName() {
        return name;
    }
}
```

- [ ] **Step 2: 构建验证**

```bash
cd BarcBackend && ./mvnw clean compile
```
Expected: BUILD SUCCESS

- [ ] **Step 3: 提交**

```bash
git add BarcBackend/src/main/java/com/miaoyu/barc/api/work/enumeration/WorkStatusEnum.java
git commit -m "feat(作品): WorkStatusEnum新增DELETED状态"
```

---

### Task 2: 新增 work_operation_log 数据库表

**Files:**
- Modify: `BarcBackend/src/main/resources/schema.sql`

- [ ] **Step 1: 在 schema.sql 末尾新增表定义**

```sql
CREATE TABLE IF NOT EXISTS work_operation_log (
    id VARCHAR(36) PRIMARY KEY NOT NULL,
    work_id VARCHAR(100) NOT NULL,
    operator_uuid VARCHAR(32) NOT NULL,
    operation_type VARCHAR(30) NOT NULL COMMENT '操作类型：BAN/OFF/DELETE/RESTORE/EDIT/CLAIM_APPROVE/CLAIM_REVOKE/CLAIM_ASSIGN/COMPLAINT_PROCESS',
    detail TEXT NULL COMMENT '操作详情JSON，如{"remark":"违规内容","old_status":"PUBLIC","new_status":"BAN"}',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_work_id (work_id),
    INDEX idx_operator (operator_uuid),
    INDEX idx_operation_type (operation_type),
    INDEX idx_created_at (created_at),
    FOREIGN KEY (work_id) REFERENCES work(id) ON DELETE CASCADE,
    FOREIGN KEY (operator_uuid) REFERENCES user_basic(uuid) ON DELETE CASCADE
);
```

- [ ] **Step 2: 验证 SQL 语法**

```bash
cd BarcBackend && ./mvnw clean compile
```
Expected: BUILD SUCCESS（表定义在 schema.sql 中，编译不报错即可）

- [ ] **Step 3: 提交**

```bash
git add BarcBackend/src/main/resources/schema.sql
git commit -m "feat(作品): 新增work_operation_log操作日志表"
```

---

### Task 3: 创建 WorkOperationLogModel

**Files:**
- Create: `BarcBackend/src/main/java/com/miaoyu/barc/api/work/model/WorkOperationLogModel.java`

- [ ] **Step 1: 创建模型类**

```java
package com.miaoyu.barc.api.work.model;

import lombok.Getter;
import lombok.Setter;

/**
 * 作品操作日志模型
 * 记录管理员对作品的所有管理操作
 */
@Setter
@Getter
public class WorkOperationLogModel {
    private String id;              // 主键UUID
    private String work_id;         // 关联作品ID
    private String operator_uuid;   // 操作人UUID
    private String operation_type;  // 操作类型
    private String detail;          // 操作详情JSON
    private String created_at;      // 创建时间
}
```

- [ ] **Step 2: 构建验证**

```bash
cd BarcBackend && ./mvnw clean compile
```
Expected: BUILD SUCCESS

- [ ] **Step 3: 提交**

```bash
git add BarcBackend/src/main/java/com/miaoyu/barc/api/work/model/WorkOperationLogModel.java
git commit -m "feat(作品): 新增WorkOperationLogModel操作日志模型"
```

---

### Task 4: 创建 WorkOperationLogMapper

**Files:**
- Create: `BarcBackend/src/main/java/com/miaoyu/barc/api/work/mapper/WorkOperationLogMapper.java`

- [ ] **Step 1: 创建 Mapper 接口**

```java
package com.miaoyu.barc.api.work.mapper;

import com.miaoyu.barc.api.work.model.WorkOperationLogModel;
import org.apache.ibatis.annotations.*;

import java.util.List;

/**
 * 作品操作日志数据访问层
 * 记录管理员对作品的所有管理操作，仅插入和查询，不提供删除
 */
@Mapper
public interface WorkOperationLogMapper {

    /** 插入操作日志 */
    @Insert("INSERT INTO work_operation_log (id, work_id, operator_uuid, operation_type, detail) " +
            "VALUES (#{id}, #{work_id}, #{operator_uuid}, #{operation_type}, #{detail})")
    int insert(WorkOperationLogModel log);

    /** 根据作品ID查询所有操作日志，按时间倒序 */
    @Select("SELECT * FROM work_operation_log WHERE work_id = #{workId} ORDER BY created_at DESC")
    List<WorkOperationLogModel> selectByWorkId(String workId);

    /** 分页查询所有操作日志，按时间倒序 */
    @Select("SELECT * FROM work_operation_log ORDER BY created_at DESC LIMIT #{offset}, #{limit}")
    List<WorkOperationLogModel> selectByPage(@Param("offset") int offset, @Param("limit") int limit);

    /** 统计操作日志总数 */
    @Select("SELECT COUNT(*) FROM work_operation_log")
    Long countAll();

    /** 按操作类型筛选分页查询 */
    @Select("SELECT * FROM work_operation_log WHERE operation_type = #{operationType} " +
            "ORDER BY created_at DESC LIMIT #{offset}, #{limit}")
    List<WorkOperationLogModel> selectByTypeAndPage(
            @Param("operationType") String operationType,
            @Param("offset") int offset,
            @Param("limit") int limit);

    /** 按操作类型统计总数 */
    @Select("SELECT COUNT(*) FROM work_operation_log WHERE operation_type = #{operationType}")
    Long countByType(@Param("operationType") String operationType);
}
```

- [ ] **Step 2: 构建验证**

```bash
cd BarcBackend && ./mvnw clean compile
```
Expected: BUILD SUCCESS

- [ ] **Step 3: 提交**

```bash
git add BarcBackend/src/main/java/com/miaoyu/barc/api/work/mapper/WorkOperationLogMapper.java
git commit -m "feat(作品): 新增WorkOperationLogMapper操作日志数据访问层"
```

---

### Task 5: 在 WorkMapper 中新增管理查询方法

**Files:**
- Modify: `BarcBackend/src/main/java/com/miaoyu/barc/api/work/mapper/WorkMapper.java`
- Modify: `BarcBackend/src/main/resources/mappers/work/WorkMapper.xml`

- [ ] **Step 1: 读取现有 WorkMapper.java**

先读取文件确认当前内容，在文件末尾 `}` 之前新增以下方法声明：

```java
// ===== 管理端专用查询方法 =====

/** 管理端分页查询作品（含所有状态，含已删除） */
List<WorkModel> selectByPageForManage(@Param("status") String status,
                                       @Param("keyword") String keyword,
                                       @Param("offset") int offset,
                                       @Param("limit") int limit,
                                       @Param("sortField") String sortField,
                                       @Param("sortOrder") String sortOrder);

/** 管理端统计作品总数 */
Long countByPageForManage(@Param("status") String status,
                           @Param("keyword") String keyword);
```

- [ ] **Step 2: 在 WorkMapper.xml 中新增 SQL**

在 `</mapper>` 标签之前新增：

```xml
<!-- 管理端分页查询作品（含所有状态） -->
<select id="selectByPageForManage" resultType="com.miaoyu.barc.api.work.model.WorkModel">
    SELECT w.*
    FROM work w
    <where>
        <if test="status != null and status != ''">
            AND w.status = #{status}
        </if>
        <if test="keyword != null and keyword != ''">
            AND (w.title LIKE CONCAT('%', #{keyword}, '%')
                 OR w.author_nickname LIKE CONCAT('%', #{keyword}, '%'))
        </if>
    </where>
    <if test="sortField != null and sortField != ''">
        ORDER BY w.${sortField} ${sortOrder}
    </if>
    <if test="sortField == null or sortField == ''">
        ORDER BY w.created_at DESC
    </if>
    LIMIT #{offset}, #{limit}
</select>

<!-- 管理端统计作品总数 -->
<select id="countByPageForManage" resultType="java.lang.Long">
    SELECT COUNT(*)
    FROM work w
    <where>
        <if test="status != null and status != ''">
            AND w.status = #{status}
        </if>
        <if test="keyword != null and keyword != ''">
            AND (w.title LIKE CONCAT('%', #{keyword}, '%')
                 OR w.author_nickname LIKE CONCAT('%', #{keyword}, '%'))
        </if>
    </where>
</select>
```

- [ ] **Step 3: 构建验证**

```bash
cd BarcBackend && ./mvnw clean compile
```
Expected: BUILD SUCCESS

- [ ] **Step 4: 提交**

```bash
git add BarcBackend/src/main/java/com/miaoyu/barc/api/work/mapper/WorkMapper.java
git add BarcBackend/src/main/resources/mappers/work/WorkMapper.xml
git commit -m "feat(作品): WorkMapper新增管理端分页查询方法"
```

---

### Task 6: 创建 WorkManageService 核心业务逻辑

**Files:**
- Create: `BarcBackend/src/main/java/com/miaoyu/barc/api/work/service/WorkManageService.java`

- [ ] **Step 1: 创建 WorkManageService**

```java
package com.miaoyu.barc.api.work.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.miaoyu.barc.annotation.RequireUserAndPermissionAnno;
import com.miaoyu.barc.api.work.enumeration.WorkStatusEnum;
import com.miaoyu.barc.api.work.mapper.*;
import com.miaoyu.barc.api.work.model.*;
import com.miaoyu.barc.email.utils.SendEmailUtils;
import com.miaoyu.barc.feedback.mapper.WorkFeedbackMapper;
import com.miaoyu.barc.feedback.model.WorkFeedbackModel;
import com.miaoyu.barc.permission.PermissionConst;
import com.miaoyu.barc.response.*;
import com.miaoyu.barc.user.enumeration.UserIdentityEnum;
import com.miaoyu.barc.user.mapper.UserArchiveMapper;
import com.miaoyu.barc.user.mapper.UserBasicMapper;
import com.miaoyu.barc.user.model.UserArchiveModel;
import com.miaoyu.barc.user.model.UserBasicModel;
import com.miaoyu.barc.utils.GenerateUUID;
import com.miaoyu.barc.utils.J;
import com.miaoyu.barc.utils.dto.PageRequestDto;
import com.miaoyu.barc.utils.dto.PageResultDto;
import com.miaoyu.barc.utils.pojo.PageInitPojo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Slf4j
@Service
public class WorkManageService {

    // 操作类型常量
    public static final String OP_BAN = "BAN";
    public static final String OP_OFF = "OFF";
    public static final String OP_DELETE = "DELETE";
    public static final String OP_RESTORE = "RESTORE";
    public static final String OP_EDIT = "EDIT";
    public static final String OP_CLAIM_APPROVE = "CLAIM_APPROVE";
    public static final String OP_CLAIM_REVOKE = "CLAIM_REVOKE";
    public static final String OP_CLAIM_ASSIGN = "CLAIM_ASSIGN";
    public static final String OP_COMPLAINT_PROCESS = "COMPLAINT_PROCESS";

    // 认领撤销后的默认作者UUID（与WorkService.uploadWorkService中的默认值一致）
    private static final String DEFAULT_AUTHOR_UUID = "707B0FBF6AAA35B788069B07AEFEA12B";

    @Autowired private WorkMapper workMapper;
    @Autowired private WorkClaimMapper workClaimMapper;
    @Autowired private WorkOperationLogMapper workOperationLogMapper;
    @Autowired private WorkFeedbackMapper workFeedbackMapper;
    @Autowired private UserBasicMapper userBasicMapper;
    @Autowired private UserArchiveMapper userArchiveMapper;
    @Autowired private SendEmailUtils sendEmailUtils;

    private final ObjectMapper objectMapper = new ObjectMapper();

    // ==================== 作品列表与详情 ====================

    /** 管理端分页获取作品列表，包含所有状态 */
    @RequireUserAndPermissionAnno({
        @RequireUserAndPermissionAnno.Check(
            identity = UserIdentityEnum.MANAGER,
            targetPermission = PermissionConst.SEC_MAINTAINER,
            isHasElseUpper = true
        )})
    public ResponseEntity<J> getWorkListForManage(PageRequestDto dto) {
        PageInitPojo pageInit = new PageInitPojo(dto);
        int offset = pageInit.getOffset();
        int pageSize = pageInit.getPageSize();
        int pageNum = pageInit.getPageNum();

        Map<String, Object> params = dto.getParams() != null ? dto.getParams() : new HashMap<>();
        String status = params.get("status") != null ? params.get("status").toString() : null;
        String keyword = params.get("keyword") != null ? params.get("keyword").toString() : null;
        String sortField = dto.getSort_field();
        String sortOrder = dto.getSort_order();

        List<WorkModel> works = workMapper.selectByPageForManage(
                status, keyword, offset, pageSize, sortField, sortOrder);
        Long total = workMapper.countByPageForManage(status, keyword);
        int totalPage = (int) Math.ceil((double) total / pageSize);

        return ResponseEntity.ok(
            new ResourceR().resourceSuch(true,
                new PageResultDto<>(total, works, pageNum, pageSize,
                    totalPage == 0 ? 1 : totalPage)));
    }

    /** 管理端获取作品详情（含已删除） */
    @RequireUserAndPermissionAnno({
        @RequireUserAndPermissionAnno.Check(
            identity = UserIdentityEnum.MANAGER,
            targetPermission = PermissionConst.SEC_MAINTAINER,
            isHasElseUpper = true
        )})
    public ResponseEntity<J> getWorkDetailForManage(String workId) {
        WorkModel work = workMapper.selectById(workId);
        if (work == null) {
            return ResponseEntity.ok(new ResourceR().resourceSuch(false, null));
        }
        return ResponseEntity.ok(new ResourceR().resourceSuch(true, work));
    }

    // ==================== 作品状态管理 ====================

    /**
     * 修改作品状态（封禁/下架/删除/恢复）
     * 状态转换规则：
     *   PUBLIC/PRIVATE → BAN/OFF
     *   任意状态 → DELETED（软删除）
     *   BAN/OFF/DELETED → PUBLIC（恢复）
     * BAN和OFF之间不能直接转换
     */
    @RequireUserAndPermissionAnno({
        @RequireUserAndPermissionAnno.Check(
            identity = UserIdentityEnum.MANAGER,
            targetPermission = PermissionConst.SEC_MAINTAINER,
            isHasElseUpper = true
        )})
    @Transactional
    public ResponseEntity<J> updateWorkStatus(String operatorUuid, String workId,
                                               WorkStatusEnum newStatus, String remark) {
        WorkModel work = workMapper.selectById(workId);
        if (work == null) {
            return ResponseEntity.ok(new ResourceR().resourceSuch(false, null));
        }

        WorkStatusEnum oldStatus = work.getStatus();
        String operationType;

        // 状态转换校验与操作类型确定
        if (newStatus == WorkStatusEnum.PUBLIC) {
            // 恢复操作：BAN/OFF/DELETED → PUBLIC
            if (oldStatus != WorkStatusEnum.BAN && oldStatus != WorkStatusEnum.OFF
                    && oldStatus != WorkStatusEnum.DELETED) {
                return ResponseEntity.ok(new ErrorR().normal("只有封禁/下架/已删除的作品才能恢复"));
            }
            operationType = OP_RESTORE;
        } else if (newStatus == WorkStatusEnum.BAN) {
            if (oldStatus != WorkStatusEnum.PUBLIC && oldStatus != WorkStatusEnum.PRIVATE) {
                return ResponseEntity.ok(new ErrorR().normal("只能封禁公开或私有的作品"));
            }
            operationType = OP_BAN;
        } else if (newStatus == WorkStatusEnum.OFF) {
            if (oldStatus != WorkStatusEnum.PUBLIC && oldStatus != WorkStatusEnum.PRIVATE) {
                return ResponseEntity.ok(new ErrorR().normal("只能下架公开或私有的作品"));
            }
            operationType = OP_OFF;
        } else if (newStatus == WorkStatusEnum.DELETED) {
            // 软删除：任意状态 → DELETED
            operationType = OP_DELETE;
        } else {
            return ResponseEntity.ok(new ErrorR().normal("不支持的状态转换"));
        }

        work.setStatus(newStatus);
        boolean updated = workMapper.update(work);
        if (!updated) {
            return ResponseEntity.ok(new ChangeR().udu(false, 3));
        }

        // 记录操作日志
        this.recordOperationLog(workId, operatorUuid, operationType,
                Map.of("remark", remark != null ? remark : "",
                       "old_status", oldStatus.name(),
                       "new_status", newStatus.name()));

        // 发送邮件通知作品作者
        this.notifyWorkAuthor(work, operationType, remark);

        return ResponseEntity.ok(new ChangeR().udu(true, 3));
    }

    // ==================== 作品内容修改 ====================

    /** 管理员修改作品内容（全部字段可编辑） */
    @RequireUserAndPermissionAnno({
        @RequireUserAndPermissionAnno.Check(
            identity = UserIdentityEnum.MANAGER,
            targetPermission = PermissionConst.SEC_MAINTAINER,
            isHasElseUpper = true
        )})
    @Transactional
    public ResponseEntity<J> updateWorkContent(String operatorUuid, WorkModel requestModel) {
        WorkModel existing = workMapper.selectById(requestModel.getId());
        if (existing == null) {
            return ResponseEntity.ok(new ResourceR().resourceSuch(false, null));
        }

        boolean updated = workMapper.update(requestModel);
        if (!updated) {
            return ResponseEntity.ok(new ChangeR().udu(false, 3));
        }

        // 记录操作日志
        this.recordOperationLog(requestModel.getId(), operatorUuid, OP_EDIT,
                Map.of("remark", "管理员修改作品内容"));

        return ResponseEntity.ok(new ChangeR().udu(true, 3));
    }

    // ==================== 认领管理 ====================

    /** 获取认领列表（支持筛选） */
    @RequireUserAndPermissionAnno({
        @RequireUserAndPermissionAnno.Check(
            identity = UserIdentityEnum.MANAGER,
            targetPermission = PermissionConst.SEC_MAINTAINER,
            isHasElseUpper = true
        )})
    public ResponseEntity<J> getClaimsList(String status) {
        List<WorkClaimModel> claims;
        if ("all".equals(status)) {
            claims = workClaimMapper.selectAll();
        } else {
            claims = workClaimMapper.selectAll(); // MyBatis无直接按status筛选，在service层过滤
            // 如需要按work的is_claim状态筛选，在后续优化
        }
        return ResponseEntity.ok(new ResourceR().resourceSuch(true, claims));
    }

    /** 审批认领：通过或拒绝
     *  权限：DISCIPLINARY_COMMITTEE(风纪委员) 或 SEC_MAINTAINER(二级管理员)
     */
    @RequireUserAndPermissionAnno({
        @RequireUserAndPermissionAnno.Check(
            identity = UserIdentityEnum.MANAGER,
            targetPermission = PermissionConst.DISCIPLINARY_COMMITTEE,
            isHasElseUpper = true,
            isSuchElseRequire = false  // OR逻辑：任一满足即可
        ),
        @RequireUserAndPermissionAnno.Check(
            identity = UserIdentityEnum.MANAGER,
            targetPermission = PermissionConst.SEC_MAINTAINER,
            isHasElseUpper = true,
            isSuchElseRequire = false
        )})
    @Transactional
    public ResponseEntity<J> approveClaim(String operatorUuid, String claimId, boolean approved) {
        WorkClaimModel claim = workClaimMapper.selectById(claimId);
        if (claim == null) {
            return ResponseEntity.ok(new ErrorR().normal("认领申请不存在"));
        }

        if (approved) {
            WorkModel work = workMapper.selectById(claim.getWork_id());
            if (work == null) {
                return ResponseEntity.ok(new ErrorR().normal("作品不存在"));
            }
            if (work.getIs_claim()) {
                return ResponseEntity.ok(new ErrorR().normal("作品已经被认领"));
            }
            work.setIs_claim(true);
            work.setAuthor(claim.getApplicant_uuid());
            workMapper.update(work);

            // 认领通过后删除该作品的所有认领申请
            workClaimMapper.deleteByWorkId(claim.getWork_id());

            // 记录日志
            this.recordOperationLog(claim.getWork_id(), operatorUuid, OP_CLAIM_APPROVE,
                    Map.of("applicant_uuid", claim.getApplicant_uuid(), "result", "approved"));

            // 通知申请人
            UserBasicModel applicant = userBasicMapper.selectByUuid(claim.getApplicant_uuid());
            if (applicant != null) {
                sendEmailUtils.customEmail(applicant.getEmail(),
                    "作品认领申请已通过",
                    "您对作品《" + work.getTitle() + "》的认领申请已通过审核！");
            }
        } else {
            // 拒绝：仅删除该申请
            workClaimMapper.deleteById(claimId);
            this.recordOperationLog(claim.getWork_id(), operatorUuid, OP_CLAIM_APPROVE,
                    Map.of("applicant_uuid", claim.getApplicant_uuid(), "result", "rejected"));
        }

        return ResponseEntity.ok(new ChangeR().udu(true, 3));
    }

    /** 撤销认领 */
    @RequireUserAndPermissionAnno({
        @RequireUserAndPermissionAnno.Check(
            identity = UserIdentityEnum.MANAGER,
            targetPermission = PermissionConst.SEC_MAINTAINER,
            isHasElseUpper = true
        )})
    @Transactional
    public ResponseEntity<J> revokeClaim(String operatorUuid, String workId) {
        WorkModel work = workMapper.selectById(workId);
        if (work == null) {
            return ResponseEntity.ok(new ResourceR().resourceSuch(false, null));
        }
        if (!work.getIs_claim()) {
            return ResponseEntity.ok(new ErrorR().normal("该作品未被认领"));
        }

        work.setIs_claim(false);
        work.setAuthor(DEFAULT_AUTHOR_UUID);
        workMapper.update(work);

        this.recordOperationLog(workId, operatorUuid, OP_CLAIM_REVOKE,
                Map.of("remark", "管理员撤销认领"));
        return ResponseEntity.ok(new ChangeR().udu(true, 3));
    }

    /** 管理员直接指派作者 */
    @RequireUserAndPermissionAnno({
        @RequireUserAndPermissionAnno.Check(
            identity = UserIdentityEnum.MANAGER,
            targetPermission = PermissionConst.SEC_MAINTAINER,
            isHasElseUpper = true
        )})
    @Transactional
    public ResponseEntity<J> assignAuthor(String operatorUuid, String workId, String authorUuid) {
        WorkModel work = workMapper.selectById(workId);
        if (work == null) {
            return ResponseEntity.ok(new ResourceR().resourceSuch(false, null));
        }

        UserArchiveModel authorArchive = userArchiveMapper.selectByUuid(authorUuid);
        if (authorArchive == null) {
            return ResponseEntity.ok(new UserR().noSuchUser());
        }

        // 如果已有认领，先撤销
        if (work.getIs_claim()) {
            work.setIs_claim(false);
        }

        work.setIs_claim(true);
        work.setAuthor(authorUuid);
        workMapper.update(work);

        // 删除该作品的所有认领申请
        workClaimMapper.deleteByWorkId(workId);

        this.recordOperationLog(workId, operatorUuid, OP_CLAIM_ASSIGN,
                Map.of("assigned_author_uuid", authorUuid));
        return ResponseEntity.ok(new ChangeR().udu(true, 3));
    }

    /** 获取认领历史 */
    @RequireUserAndPermissionAnno({
        @RequireUserAndPermissionAnno.Check(
            identity = UserIdentityEnum.MANAGER,
            targetPermission = PermissionConst.SEC_MAINTAINER,
            isHasElseUpper = true
        )})
    public ResponseEntity<J> getClaimHistory(String workId) {
        List<WorkClaimModel> claims = workClaimMapper.selectByWorkId(workId);
        return ResponseEntity.ok(new ResourceR().resourceSuch(true, claims));
    }

    // ==================== 投诉处理 ====================

    /** 获取投诉列表 */
    @RequireUserAndPermissionAnno({
        @RequireUserAndPermissionAnno.Check(
            identity = UserIdentityEnum.MANAGER,
            targetPermission = PermissionConst.SEC_MAINTAINER,
            isHasElseUpper = true
        )})
    public ResponseEntity<J> getComplaintsList() {
        List<WorkFeedbackModel> complaints = workFeedbackMapper.selectAll();
        return ResponseEntity.ok(new ResourceR().resourceSuch(true, complaints));
    }

    /**
     * 处理投诉（联动操作）
     * action: BAN（封禁）/ OFF（下架）/ DELETE（删除）/ IGNORE（忽略）
     */
    @RequireUserAndPermissionAnno({
        @RequireUserAndPermissionAnno.Check(
            identity = UserIdentityEnum.MANAGER,
            targetPermission = PermissionConst.SEC_MAINTAINER,
            isHasElseUpper = true
        )})
    @Transactional
    public ResponseEntity<J> processComplaint(String operatorUuid, String complaintId,
                                               String action, String remark) {
        WorkFeedbackModel complaint = workFeedbackMapper.selectById(complaintId);
        if (complaint == null) {
            return ResponseEntity.ok(new ErrorR().normal("投诉不存在"));
        }
        if (complaint.getStatus()) {
            return ResponseEntity.ok(new ErrorR().normal("投诉已处理"));
        }

        // 标记投诉为已处理
        complaint.setStatus(true);
        complaint.setNote(remark);
        workFeedbackMapper.update(complaint);

        // 联动操作
        if (!"IGNORE".equals(action)) {
            WorkStatusEnum newStatus = switch (action) {
                case "BAN" -> WorkStatusEnum.BAN;
                case "OFF" -> WorkStatusEnum.OFF;
                case "DELETE" -> WorkStatusEnum.DELETED;
                default -> null;
            };

            if (newStatus != null) {
                WorkModel work = workMapper.selectById(complaint.getWork_id());
                if (work != null) {
                    work.setStatus(newStatus);
                    workMapper.update(work);

                    // 通知作品作者
                    this.notifyWorkAuthor(work,
                            action.equals("BAN") ? OP_BAN :
                            action.equals("OFF") ? OP_OFF : OP_DELETE,
                            "投诉处理：" + remark);
                }
            }
        }

        // 记录操作日志
        this.recordOperationLog(complaint.getWork_id(), operatorUuid, OP_COMPLAINT_PROCESS,
                Map.of("complaint_id", complaintId, "action", action, "remark", remark));

        // 通知投诉人
        sendEmailUtils.customEmail(complaint.getEmail(),
                "投诉反馈已处理完成",
                "您对作品的投诉反馈已经完成处理。处理结果：" + action + "。处理备注：" +
                (remark != null ? remark : "无"));

        return ResponseEntity.ok(new ChangeR().udu(true, 3));
    }

    // ==================== 操作日志 ====================

    /** 获取操作日志列表（分页） */
    @RequireUserAndPermissionAnno({
        @RequireUserAndPermissionAnno.Check(
            identity = UserIdentityEnum.MANAGER,
            targetPermission = PermissionConst.SEC_MAINTAINER,
            isHasElseUpper = true
        )})
    public ResponseEntity<J> getOperationLogs(PageRequestDto dto) {
        PageInitPojo pageInit = new PageInitPojo(dto);
        int offset = pageInit.getOffset();
        int pageSize = pageInit.getPageSize();
        int pageNum = pageInit.getPageNum();

        Map<String, Object> params = dto.getParams() != null ? dto.getParams() : new HashMap<>();
        String operationType = params.get("operation_type") != null
                ? params.get("operation_type").toString() : null;

        List<WorkOperationLogModel> logs;
        Long total;

        if (operationType != null && !operationType.isEmpty()) {
            logs = workOperationLogMapper.selectByTypeAndPage(operationType, offset, pageSize);
            total = workOperationLogMapper.countByType(operationType);
        } else {
            logs = workOperationLogMapper.selectByPage(offset, pageSize);
            total = workOperationLogMapper.countAll();
        }

        int totalPage = (int) Math.ceil((double) total / pageSize);
        return ResponseEntity.ok(
            new ResourceR().resourceSuch(true,
                new PageResultDto<>(total, logs, pageNum, pageSize,
                    totalPage == 0 ? 1 : totalPage)));
    }

    // ==================== 内部工具方法 ====================

    /** 记录操作日志 */
    private void recordOperationLog(String workId, String operatorUuid,
                                     String operationType, Map<String, String> detail) {
        try {
            WorkOperationLogModel log = new WorkOperationLogModel();
            log.setId(new GenerateUUID().getUuid36l());
            log.setWork_id(workId);
            log.setOperator_uuid(operatorUuid);
            log.setOperation_type(operationType);
            log.setDetail(objectMapper.writeValueAsString(detail));
            workOperationLogMapper.insert(log);
        } catch (JsonProcessingException e) {
            log.error("操作日志JSON序列化失败", e);
        }
    }

    /** 通知作品作者 */
    private void notifyWorkAuthor(WorkModel work, String operationType, String remark) {
        try {
            UserBasicModel author = userBasicMapper.selectByUuid(work.getAuthor());
            if (author != null) {
                String opName = switch (operationType) {
                    case OP_BAN -> "封禁";
                    case OP_OFF -> "下架";
                    case OP_DELETE -> "删除";
                    case OP_RESTORE -> "恢复";
                    default -> operationType;
                };
                sendEmailUtils.customEmail(author.getEmail(),
                    "您的作品已被" + opName,
                    "您在蔚蓝收录馆中收录的作品《" + work.getTitle() + "》已被" + opName +
                    "。处理备注：" + (remark != null ? remark : "无"));
            }
        } catch (Exception e) {
            log.error("通知作品作者邮件发送失败", e);
        }
    }
}
```

- [ ] **Step 2: 构建验证**

```bash
cd BarcBackend && ./mvnw clean compile
```
Expected: BUILD SUCCESS

- [ ] **Step 3: 提交**

```bash
git add BarcBackend/src/main/java/com/miaoyu/barc/api/work/service/WorkManageService.java
git commit -m "feat(作品): 新增WorkManageService管理端业务逻辑"
```

---

### Task 7: 创建 WorkManageController

**Files:**
- Create: `BarcBackend/src/main/java/com/miaoyu/barc/api/work/controller/WorkManageController.java`

- [ ] **Step 1: 创建控制器**

```java
package com.miaoyu.barc.api.work.controller;

import com.miaoyu.barc.api.work.enumeration.WorkStatusEnum;
import com.miaoyu.barc.api.work.model.WorkModel;
import com.miaoyu.barc.api.work.service.WorkManageService;
import com.miaoyu.barc.utils.J;
import com.miaoyu.barc.utils.dto.PageRequestDto;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 管理端作品管理控制器
 * 所有端点需要 SEC_MAINTAINER(二级管理员) 权限
 * 路径前缀：/api/work/manage
 */
@RestController
@RequestMapping("/api/work/manage")
public class WorkManageController {

    @Autowired
    private WorkManageService workManageService;

    /** 分页获取作品列表（含所有状态） */
    @PostMapping("/list")
    public ResponseEntity<J> getWorkListManage(@RequestBody PageRequestDto dto) {
        return workManageService.getWorkListForManage(dto);
    }

    /** 获取作品详情（含已删除） */
    @GetMapping("/detail")
    public ResponseEntity<J> getWorkDetailManage(@RequestParam("work_id") String workId) {
        return workManageService.getWorkDetailForManage(workId);
    }

    /** 修改作品状态（封禁/下架/删除/恢复） */
    @PutMapping("/status")
    public ResponseEntity<J> updateWorkStatusManage(
            HttpServletRequest request,
            @RequestBody Map<String, Object> body) {
        String workId = body.get("work_id").toString();
        WorkStatusEnum newStatus = WorkStatusEnum.valueOf(body.get("status").toString());
        String remark = body.get("remark") != null ? body.get("remark").toString() : null;
        return workManageService.updateWorkStatus(
                request.getAttribute("uuid").toString(), workId, newStatus, remark);
    }

    /** 修改作品内容（全部字段） */
    @PutMapping("/update")
    public ResponseEntity<J> updateWorkContentManage(
            HttpServletRequest request,
            @RequestBody WorkModel workModel) {
        return workManageService.updateWorkContent(
                request.getAttribute("uuid").toString(), workModel);
    }

    /** 获取认领列表 */
    @GetMapping("/claims")
    public ResponseEntity<J> getClaimsManage(
            @RequestParam(value = "status", defaultValue = "all") String status) {
        return workManageService.getClaimsList(status);
    }

    /** 审批认领（通过或拒绝） */
    @PostMapping("/claim/approve")
    public ResponseEntity<J> approveClaimManage(
            HttpServletRequest request,
            @RequestBody Map<String, Object> body) {
        String claimId = body.get("claim_id").toString();
        boolean approved = Boolean.parseBoolean(body.get("approved").toString());
        return workManageService.approveClaim(
                request.getAttribute("uuid").toString(), claimId, approved);
    }

    /** 撤销认领 */
    @PostMapping("/claim/revoke")
    public ResponseEntity<J> revokeClaimManage(
            HttpServletRequest request,
            @RequestBody Map<String, String> body) {
        return workManageService.revokeClaim(
                request.getAttribute("uuid").toString(), body.get("work_id"));
    }

    /** 管理员直接指派作者 */
    @PostMapping("/claim/assign")
    public ResponseEntity<J> assignAuthorManage(
            HttpServletRequest request,
            @RequestBody Map<String, String> body) {
        return workManageService.assignAuthor(
                request.getAttribute("uuid").toString(),
                body.get("work_id"),
                body.get("author_uuid"));
    }

    /** 获取认领历史 */
    @GetMapping("/claim/history")
    public ResponseEntity<J> getClaimHistoryManage(@RequestParam("work_id") String workId) {
        return workManageService.getClaimHistory(workId);
    }

    /** 获取投诉列表 */
    @GetMapping("/complaints")
    public ResponseEntity<J> getComplaintsManage() {
        return workManageService.getComplaintsList();
    }

    /** 处理投诉（联动操作） */
    @PostMapping("/complaint/process")
    public ResponseEntity<J> processComplaintManage(
            HttpServletRequest request,
            @RequestBody Map<String, Object> body) {
        String complaintId = body.get("complaint_id").toString();
        String action = body.get("action").toString();
        String remark = body.get("remark") != null ? body.get("remark").toString() : null;
        return workManageService.processComplaint(
                request.getAttribute("uuid").toString(), complaintId, action, remark);
    }

    /** 获取操作日志列表 */
    @PostMapping("/logs")
    public ResponseEntity<J> getOperationLogsManage(@RequestBody PageRequestDto dto) {
        return workManageService.getOperationLogs(dto);
    }
}
```

- [ ] **Step 2: 构建验证**

```bash
cd BarcBackend && ./mvnw clean compile
```
Expected: BUILD SUCCESS

- [ ] **Step 3: 提交**

```bash
git add BarcBackend/src/main/java/com/miaoyu/barc/api/work/controller/WorkManageController.java
git commit -m "feat(作品): 新增WorkManageController管理端API"
```

---

### Task 8: 修改 WorkMapper.update 包含 status 字段

**Files:**
- Read first: `BarcBackend/src/main/java/com/miaoyu/barc/api/work/mapper/WorkMapper.java`

- [ ] **Step 1: 检查现有 update 方法的 SQL**

读取 WorkMapper.java 中的 update 方法，确认其是否包含 status 字段更新。如不包含，需要修改：

在 `@Update` 注解的 SQL 中添加 `status = #{status},`：

```java
@Update("UPDATE work SET title = #{title}, description = #{description}, content = #{content}, " +
        "banner_image = #{banner_image}, cover_image = #{cover_image}, " +
        "author = #{author}, author_nickname = #{author_nickname}, " +
        "uploader = #{uploader}, is_claim = #{is_claim}, " +
        "status = #{status}, student = #{student}, " +  // 确保 status 和 student 字段存在
        "updated_at = CURRENT_TIMESTAMP WHERE id = #{id}")
int update(WorkModel workModel);
```

- [ ] **Step 2: 构建验证**

```bash
cd BarcBackend && ./mvnw clean compile
```
Expected: BUILD SUCCESS

- [ ] **Step 3: 提交**

```bash
git add BarcBackend/src/main/java/com/miaoyu/barc/api/work/mapper/WorkMapper.java
git commit -m "fix(作品): WorkMapper.update SQL补充status字段更新"
```

---

### Task 9: 修改 WorkService 前端兼容性

**Files:**
- Modify: `BarcBackend/src/main/java/com/miaoyu/barc/api/work/service/WorkService.java`

- [ ] **Step 1: 在 getWorksByIdService 中添加 DELETED 状态处理**

在 `getWorksByIdService` 方法的 switch 语句中 `default` 之前新增 DELETED case：

找到：
```java
case OFF -> ResponseEntity.ok(new ErrorR().normal("作品已被下架"));
default -> {
```

修改为：
```java
case OFF -> ResponseEntity.ok(new ErrorR().normal("作品已被下架"));
case DELETED -> ResponseEntity.ok(new ErrorR().normal("作品已被删除"));
default -> {
```

- [ ] **Step 2: 构建验证**

```bash
cd BarcBackend && ./mvnw clean compile
```
Expected: BUILD SUCCESS

- [ ] **Step 3: 提交**

```bash
git add BarcBackend/src/main/java/com/miaoyu/barc/api/work/service/WorkService.java
git commit -m "fix(作品): getWorksByIdService补充DELETED状态处理"
```

---

### Task 10: 确认前端权限常量

**Files:**
- Read: `BarcManageFrontendV2/src/shared/constants/permissions.ts`

- [ ] **Step 1: 确认 SEC_MAINTAINER 常量存在**

读取 permissions.ts，确认 `SEC_MAINTAINER = 4` 已定义。如未定义，添加：

```typescript
// 管理者权限常量（位运算）
export const MANAGER_PERMISSION = {
  DISCIPLINARY_COMMITTEE: 1,  // 风纪委员
  FIR_MAINTAINER: 2,          // 一级管理员
  SEC_MAINTAINER: 4,          // 二级管理员 - 作品管理
  THI_MAINTAINER: 8,          // 三级管理员 - 用户管理
  ADMINISTRATOR: 16,          // 副馆长
  ADVANCED_ADMINISTRATOR: 32, // 馆长
} as const
```

- [ ] **Step 2: 提交**

```bash
git add BarcManageFrontendV2/src/shared/constants/permissions.ts
git commit -m "chore(权限): 确认SEC_MAINTAINER权限常量"
```

---

### Task 11: 创建前端 API 层

**Files:**
- Create: `BarcManageFrontendV2/src/modules/works/api/workManage.ts`

- [ ] **Step 1: 创建 API 封装**

```typescript
import { baseHttp } from '@/shared/api/https'
import type { PageRequestImpl, PageResultImpl, ResponseImpl } from '@/shared/types'

/**
 * 管理端作品管理 API
 * 所有接口需要 SEC_MAINTAINER 权限
 */

// 作品列表（分页）
export function getWorkListManage(dto: PageRequestImpl) {
  return baseHttp.post<ResponseImpl<PageResultImpl<any>>>('/api/work/manage/list', dto)
}

// 作品详情
export function getWorkDetailManage(workId: string) {
  return baseHttp.get<ResponseImpl<any>>('/api/work/manage/detail', { params: { work_id: workId } })
}

// 修改作品状态
export function updateWorkStatus(workId: string, status: string, remark?: string) {
  return baseHttp.put<ResponseImpl>('/api/work/manage/status', { work_id: workId, status, remark })
}

// 修改作品内容
export function updateWorkContent(model: any) {
  return baseHttp.put<ResponseImpl>('/api/work/manage/update', model)
}

// 获取认领列表
export function getClaimsList(status: string = 'all') {
  return baseHttp.get<ResponseImpl<any[]>>('/api/work/manage/claims', { params: { status } })
}

// 审批认领
export function approveClaim(claimId: string, approved: boolean) {
  return baseHttp.post<ResponseImpl>('/api/work/manage/claim/approve', { claim_id: claimId, approved })
}

// 撤销认领
export function revokeClaim(workId: string) {
  return baseHttp.post<ResponseImpl>('/api/work/manage/claim/revoke', { work_id: workId })
}

// 管理员指派作者
export function assignAuthor(workId: string, authorUuid: string) {
  return baseHttp.post<ResponseImpl>('/api/work/manage/claim/assign', { work_id: workId, author_uuid: authorUuid })
}

// 获取认领历史
export function getClaimHistory(workId: string) {
  return baseHttp.get<ResponseImpl<any[]>>('/api/work/manage/claim/history', { params: { work_id: workId } })
}

// 获取投诉列表
export function getComplaintsList() {
  return baseHttp.get<ResponseImpl<any[]>>('/api/work/manage/complaints')
}

// 处理投诉
export function processComplaint(complaintId: string, action: string, remark?: string) {
  return baseHttp.post<ResponseImpl>('/api/work/manage/complaint/process', { complaint_id: complaintId, action, remark })
}

// 获取操作日志
export function getOperationLogs(dto: PageRequestImpl) {
  return baseHttp.post<ResponseImpl<PageResultImpl<any>>>('/api/work/manage/logs', dto)
}
```

- [ ] **Step 2: 构建验证**

```bash
cd BarcManageFrontendV2 && npm run build
```
Expected: 无 TypeScript 类型错误

- [ ] **Step 3: 提交**

```bash
git add BarcManageFrontendV2/src/modules/works/api/workManage.ts
git commit -m "feat(管理端): 新增作品管理API封装层"
```

---

### Task 12: 创建作品列表页

**Files:**
- Create: `BarcManageFrontendV2/src/modules/works/components/WorkStatusBadge.vue`
- Create: `BarcManageFrontendV2/src/modules/works/components/WorkListComp.vue`
- Create: `BarcManageFrontendV2/src/modules/works/views/WorksListView.vue`

- [ ] **Step 1: 创建 WorkStatusBadge 状态标签组件**

```vue
<template>
  <el-tag :type="tagType" size="small">{{ label }}</el-tag>
</template>

<script setup lang="ts">
import { computed } from 'vue'

const props = defineProps<{ status: string }>()

const label = computed(() => {
  const map: Record<string, string> = {
    PUBLIC: '公开', PRIVATE: '私有', OFF: '下架', BAN: '封禁', DELETED: '已删除'
  }
  return map[props.status] || props.status
})

const tagType = computed(() => {
  const map: Record<string, string> = {
    PUBLIC: 'success', PRIVATE: 'info', OFF: 'warning', BAN: 'danger', DELETED: 'danger'
  }
  return map[props.status] || 'info'
})
</script>
```

- [ ] **Step 2: 创建 WorkListComp 作品列表组件**

```vue
<template>
  <div class="work-list">
    <!-- 筛选栏 -->
    <el-form :inline="true" :model="filterForm" class="filter-bar">
      <el-form-item label="状态">
        <el-select v-model="filterForm.status" placeholder="全部" clearable @change="handleFilter">
          <el-option label="全部" value="" />
          <el-option label="公开" value="PUBLIC" />
          <el-option label="私有" value="PRIVATE" />
          <el-option label="下架" value="OFF" />
          <el-option label="封禁" value="BAN" />
          <el-option label="已删除" value="DELETED" />
        </el-select>
      </el-form-item>
      <el-form-item label="关键词">
        <el-input v-model="filterForm.keyword" placeholder="搜索标题或作者" clearable @keyup.enter="handleFilter" />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" @click="handleFilter">搜索</el-button>
      </el-form-item>
    </el-form>

    <!-- 表格 -->
    <el-table :data="workList" border stripe v-loading="loading" style="width: 100%">
      <el-table-column prop="id" label="ID" width="120" show-overflow-tooltip />
      <el-table-column label="封面" width="80">
        <template #default="{ row }">
          <el-image :src="row.cover_image" style="width: 50px; height: 50px" fit="cover" />
        </template>
      </el-table-column>
      <el-table-column prop="title" label="标题" min-width="150" show-overflow-tooltip />
      <el-table-column prop="author_nickname" label="作者" width="120" />
      <el-table-column label="状态" width="90">
        <template #default="{ row }">
          <WorkStatusBadge :status="row.status" />
        </template>
      </el-table-column>
      <el-table-column prop="created_at" label="创建时间" width="170" />
      <el-table-column label="操作" width="280" fixed="right">
        <template #default="{ row }">
          <el-button size="small" @click="$emit('edit', row)">编辑</el-button>
          <template v-if="row.status !== 'DELETED'">
            <el-button v-if="row.status === 'PUBLIC' || row.status === 'PRIVATE'" size="small" type="warning" @click="$emit('ban', row)">封禁</el-button>
            <el-button v-if="row.status === 'PUBLIC' || row.status === 'PRIVATE'" size="small" type="warning" @click="$emit('off', row)">下架</el-button>
            <el-button v-if="row.status === 'BAN' || row.status === 'OFF'" size="small" type="success" @click="$emit('restore', row)">恢复</el-button>
          </template>
          <el-button v-if="row.status !== 'DELETED'" size="small" type="danger" @click="$emit('delete', row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <!-- 分页 -->
    <el-pagination
      v-model:current-page="currentPage"
      :page-size="10"
      :total="total"
      layout="total, prev, pager, next"
      @current-change="handlePageChange"
      style="margin-top: 16px; justify-content: flex-end"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { getWorkListManage } from '../api/workManage'
import WorkStatusBadge from './WorkStatusBadge.vue'

defineEmits<{
  edit: [row: any]
  ban: [row: any]
  off: [row: any]
  restore: [row: any]
  delete: [row: any]
}>()

const workList = ref<any[]>([])
const total = ref(0)
const currentPage = ref(1)
const loading = ref(false)
const filterForm = ref({ status: '', keyword: '' })

const fetchData = async () => {
  loading.value = true
  try {
    const { data } = await getWorkListManage({
      page_num: currentPage.value,
      page_size: 10,
      params: {
        status: filterForm.value.status || null,
        keyword: filterForm.value.keyword || null
      }
    })
    if (data.code === 0) {
      workList.value = data.data.records || []
      total.value = data.data.total || 0
    }
  } finally {
    loading.value = false
  }
}

const handleFilter = () => {
  currentPage.value = 1
  fetchData()
}

const handlePageChange = (page: number) => {
  currentPage.value = page
  fetchData()
}

onMounted(() => fetchData())
</script>
```

- [ ] **Step 3: 创建 WorksListView 页面容器**

```vue
<template>
  <div class="works-list-view">
    <WorkListComp
      @edit="handleEdit"
      @ban="handleBan"
      @off="handleOff"
      @restore="handleRestore"
      @delete="handleDelete"
    />

    <!-- 状态修改确认弹窗 -->
    <el-dialog v-model="statusDialogVisible" :title="statusDialogTitle" width="400px">
      <el-input v-model="statusRemark" type="textarea" :rows="3" placeholder="操作备注（选填）" />
      <template #footer>
        <el-button @click="statusDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="confirmStatusChange">确认</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import WorkListComp from '../components/WorkListComp.vue'
import { updateWorkStatus } from '../api/workManage'

const router = useRouter()

// 状态变更弹窗
const statusDialogVisible = ref(false)
const statusDialogTitle = ref('')
const statusRemark = ref('')
let pendingAction: { workId: string; status: string } | null = null

const handleEdit = (row: any) => {
  router.push({ name: 'works-edit', params: { workId: row.id } })
}

const confirmStatusChange = async () => {
  if (!pendingAction) return
  await updateWorkStatus(pendingAction.workId, pendingAction.status, statusRemark.value)
  ElMessage.success(statusDialogTitle.value + '成功')
  statusDialogVisible.value = false
  statusRemark.value = ''
  window.location.reload()
}

const handleBan = (row: any) => {
  pendingAction = { workId: row.id, status: 'BAN' }
  statusDialogTitle.value = '封禁作品'
  statusDialogVisible.value = true
}

const handleOff = (row: any) => {
  pendingAction = { workId: row.id, status: 'OFF' }
  statusDialogTitle.value = '下架作品'
  statusDialogVisible.value = true
}

const handleRestore = (row: any) => {
  pendingAction = { workId: row.id, status: 'PUBLIC' }
  statusDialogTitle.value = '恢复作品'
  statusDialogVisible.value = true
}

const handleDelete = (row: any) => {
  ElMessageBox.confirm(`确定要删除作品《${row.title}》吗？此为软删除，可恢复。`, '确认删除', {
    confirmButtonText: '确定', cancelButtonText: '取消', type: 'warning'
  }).then(async () => {
    await updateWorkStatus(row.id, 'DELETED', '管理员删除')
    ElMessage.success('删除成功')
    window.location.reload()
  })
}
</script>
```

- [ ] **Step 4: 构建验证**

```bash
cd BarcManageFrontendV2 && npm run build
```
Expected: BUILD SUCCESS，无类型错误

- [ ] **Step 5: 提交**

```bash
git add BarcManageFrontendV2/src/modules/works/components/WorkStatusBadge.vue
git add BarcManageFrontendV2/src/modules/works/components/WorkListComp.vue
git add BarcManageFrontendV2/src/modules/works/views/WorksListView.vue
git commit -m "feat(管理端): 新增作品列表页"
```

---

### Task 13: 创建作品编辑页

**Files:**
- Create: `BarcManageFrontendV2/src/modules/works/components/WorkDetailComp.vue`
- Create: `BarcManageFrontendV2/src/modules/works/views/WorkEditView.vue`

- [ ] **Step 1: 创建 WorkDetailComp 编辑表单组件**

```vue
<template>
  <el-form :model="form" label-width="100px" style="max-width: 800px">
    <el-form-item label="标题" required>
      <el-input v-model="form.title" placeholder="作品标题" />
    </el-form-item>

    <el-form-item label="简介">
      <el-input v-model="form.description" type="textarea" :rows="3" placeholder="作品简介" />
    </el-form-item>

    <el-form-item label="富文本内容">
      <el-input v-model="form.content" type="textarea" :rows="10" placeholder="作品内容" />
    </el-form-item>

    <el-form-item label="作者昵称">
      <el-input v-model="form.author_nickname" placeholder="作者昵称" />
    </el-form-item>

    <el-form-item label="关联学生">
      <el-input v-model="form.student" placeholder="学生ID" />
    </el-form-item>

    <el-form-item label="是否认领">
      <el-switch v-model="form.is_claim" />
    </el-form-item>

    <el-form-item>
      <el-button type="primary" @click="$emit('save', form)" :loading="saving">保存</el-button>
      <el-button @click="$emit('cancel')">取消</el-button>
    </el-form-item>
  </el-form>
</template>

<script setup lang="ts">
import { ref, onMounted, defineEmits, defineProps } from 'vue'
import { getWorkDetailManage } from '../api/workManage'

const props = defineProps<{ workId: string }>()
const emit = defineEmits<{ save: [form: any]; cancel: [] }>()

const saving = ref(false)
const form = ref<any>({ id: '', title: '', description: '', content: '', author_nickname: '', student: '', is_claim: false })

onMounted(async () => {
  const { data } = await getWorkDetailManage(props.workId)
  if (data.code === 0 && data.data) {
    form.value = { ...data.data }
  }
})

defineExpose({ setSaving: (v: boolean) => { saving.value = v } })
</script>
```

- [ ] **Step 2: 创建 WorkEditView 页面容器**

```vue
<template>
  <div class="work-edit-view">
    <el-page-header @back="$router.back()" title="返回">
      <template #content>
        <span>编辑作品</span>
      </template>
    </el-page-header>
    <WorkDetailComp :workId="workId" ref="detailComp" @save="handleSave" @cancel="$router.back()" />
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import WorkDetailComp from '../components/WorkDetailComp.vue'
import { updateWorkContent } from '../api/workManage'

const route = useRoute()
const router = useRouter()
const workId = route.params.workId as string
const detailComp = ref()

const handleSave = async (form: any) => {
  detailComp.value?.setSaving(true)
  try {
    await updateWorkContent(form)
    ElMessage.success('保存成功')
    router.back()
  } catch {
    ElMessage.error('保存失败')
  } finally {
    detailComp.value?.setSaving(false)
  }
}
</script>
```

- [ ] **Step 3: 构建验证**

```bash
cd BarcManageFrontendV2 && npm run build
```
Expected: BUILD SUCCESS

- [ ] **Step 4: 提交**

```bash
git add BarcManageFrontendV2/src/modules/works/components/WorkDetailComp.vue
git add BarcManageFrontendV2/src/modules/works/views/WorkEditView.vue
git commit -m "feat(管理端): 新增作品编辑页"
```

---

### Task 14: 创建认领管理页

**Files:**
- Create: `BarcManageFrontendV2/src/modules/works/components/ClaimListComp.vue`
- Create: `BarcManageFrontendV2/src/modules/works/views/ClaimsListView.vue`

- [ ] **Step 1: 创建 ClaimListComp 认领列表组件**

```vue
<template>
  <div class="claim-list">
    <el-table :data="claims" border stripe v-loading="loading">
      <el-table-column prop="id" label="申请ID" width="120" show-overflow-tooltip />
      <el-table-column prop="work_id" label="作品ID" width="120" show-overflow-tooltip />
      <el-table-column prop="applicant_uuid" label="申请人UUID" width="150" show-overflow-tooltip />
      <el-table-column prop="created_at" label="申请时间" width="170" />
      <el-table-column label="操作" width="200">
        <template #default="{ row }">
          <el-button size="small" type="success" @click="$emit('approve', row)">通过</el-button>
          <el-button size="small" type="danger" @click="$emit('reject', row)">拒绝</el-button>
        </template>
      </el-table-column>
    </el-table>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, defineEmits } from 'vue'
import { getClaimsList } from '../api/workManage'

defineEmits<{ approve: [row: any]; reject: [row: any] }>()

const claims = ref<any[]>([])
const loading = ref(false)

const fetchData = async () => {
  loading.value = true
  try {
    const { data } = await getClaimsList()
    if (data.code === 0) claims.value = data.data || []
  } finally { loading.value = false }
}

onMounted(() => fetchData())

defineExpose({ refresh: fetchData })
</script>
```

- [ ] **Step 2: 创建 ClaimsListView 页面容器**

```vue
<template>
  <div class="claims-list-view">
    <el-tabs v-model="activeTab">
      <el-tab-pane label="待审批" name="pending">
        <ClaimListComp ref="claimList" @approve="handleApprove" @reject="handleReject" />
      </el-tab-pane>
      <el-tab-pane label="指派作者" name="assign">
        <el-form :inline="true">
          <el-form-item label="作品ID">
            <el-input v-model="assignForm.workId" placeholder="输入作品ID" />
          </el-form-item>
          <el-form-item label="作者UUID">
            <el-input v-model="assignForm.authorUuid" placeholder="输入作者UUID" />
          </el-form-item>
          <el-form-item>
            <el-button type="primary" @click="handleAssign">指派</el-button>
          </el-form-item>
        </el-form>
      </el-tab-pane>
      <el-tab-pane label="撤销认领" name="revoke">
        <el-form :inline="true">
          <el-form-item label="作品ID">
            <el-input v-model="revokeWorkId" placeholder="输入作品ID" />
          </el-form-item>
          <el-form-item>
            <el-button type="danger" @click="handleRevoke">撤销认领</el-button>
          </el-form-item>
        </el-form>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import ClaimListComp from '../components/ClaimListComp.vue'
import { approveClaim, assignAuthor, revokeClaim } from '../api/workManage'

const activeTab = ref('pending')
const claimList = ref()
const revokeWorkId = ref('')
const assignForm = ref({ workId: '', authorUuid: '' })

const handleApprove = async (row: any) => {
  await approveClaim(row.id, true)
  ElMessage.success('认领已通过')
  claimList.value?.refresh()
}

const handleReject = async (row: any) => {
  await approveClaim(row.id, false)
  ElMessage.success('认领已拒绝')
  claimList.value?.refresh()
}

const handleRevoke = async () => {
  await revokeClaim(revokeWorkId.value)
  ElMessage.success('认领已撤销')
}

const handleAssign = async () => {
  await assignAuthor(assignForm.value.workId, assignForm.value.authorUuid)
  ElMessage.success('作者指派成功')
}
</script>
```

- [ ] **Step 3: 构建验证 + 提交**

```bash
cd BarcManageFrontendV2 && npm run build
git add BarcManageFrontendV2/src/modules/works/components/ClaimListComp.vue
git add BarcManageFrontendV2/src/modules/works/views/ClaimsListView.vue
git commit -m "feat(管理端): 新增认领管理页"
```

---

### Task 15: 创建投诉处理页和操作日志页

**Files:**
- Create: `BarcManageFrontendV2/src/modules/works/components/ComplaintListComp.vue`
- Create: `BarcManageFrontendV2/src/modules/works/views/ComplaintsListView.vue`
- Create: `BarcManageFrontendV2/src/modules/works/components/OperationLogComp.vue`
- Create: `BarcManageFrontendV2/src/modules/works/views/OperationLogView.vue`

- [ ] **Step 1: 创建 ComplaintListComp**

```vue
<template>
  <el-table :data="complaints" border stripe v-loading="loading">
    <el-table-column prop="work_id" label="作品ID" width="120" />
    <el-table-column prop="reason_option" label="投诉原因" width="120" />
    <el-table-column prop="content" label="详细描述" min-width="200" show-overflow-tooltip />
    <el-table-column prop="email" label="投诉人邮箱" width="180" />
    <el-table-column label="状态" width="100">
      <template #default="{ row }">
        <el-tag :type="row.status ? 'success' : 'warning'" size="small">{{ row.status ? '已处理' : '待处理' }}</el-tag>
      </template>
    </el-table-column>
    <el-table-column prop="created_at" label="投诉时间" width="170" />
    <el-table-column label="操作" width="120" fixed="right">
      <template #default="{ row }">
        <el-button v-if="!row.status" size="small" type="primary" @click="$emit('process', row)">处理</el-button>
        <span v-else style="color: #999">已处理</span>
      </template>
    </el-table-column>
  </el-table>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { getComplaintsList } from '../api/workManage'

defineEmits<{ process: [row: any] }>()

const complaints = ref<any[]>([])
const loading = ref(false)

const fetchData = async () => {
  loading.value = true
  try {
    const { data } = await getComplaintsList()
    if (data.code === 0) complaints.value = data.data || []
  } finally { loading.value = false }
}

onMounted(() => fetchData())
defineExpose({ refresh: fetchData })
</script>
```

- [ ] **Step 2: 创建 ComplaintsListView 页面容器（含处理弹窗）**

```vue
<template>
  <div class="complaints-list-view">
    <ComplaintListComp ref="complaintList" @process="openProcessDialog" />

    <el-dialog v-model="dialogVisible" title="处理投诉" width="450px">
      <el-form label-width="80px">
        <el-form-item label="操作">
          <el-select v-model="processAction" placeholder="选择操作">
            <el-option label="封禁作品" value="BAN" />
            <el-option label="下架作品" value="OFF" />
            <el-option label="删除作品" value="DELETE" />
            <el-option label="忽略投诉" value="IGNORE" />
          </el-select>
        </el-form-item>
        <el-form-item label="处理备注">
          <el-input v-model="processRemark" type="textarea" :rows="3" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="confirmProcess">确认处理</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import ComplaintListComp from '../components/ComplaintListComp.vue'
import { processComplaint } from '../api/workManage'

const complaintList = ref()
const dialogVisible = ref(false)
const processAction = ref('BAN')
const processRemark = ref('')
let pendingRow: any = null

const openProcessDialog = (row: any) => {
  pendingRow = row
  dialogVisible.value = true
}

const confirmProcess = async () => {
  if (!pendingRow) return
  await processComplaint(pendingRow.id, processAction.value, processRemark.value)
  ElMessage.success('投诉处理完成')
  dialogVisible.value = false
  complaintList.value?.refresh()
}
</script>
```

- [ ] **Step 3: 创建 OperationLogComp**

```vue
<template>
  <div class="operation-log">
    <el-form :inline="true" class="filter-bar">
      <el-form-item label="操作类型">
        <el-select v-model="filterType" placeholder="全部" clearable @change="fetchData">
          <el-option label="全部" value="" />
          <el-option label="封禁" value="BAN" />
          <el-option label="下架" value="OFF" />
          <el-option label="删除" value="DELETE" />
          <el-option label="恢复" value="RESTORE" />
          <el-option label="编辑" value="EDIT" />
          <el-option label="认领审批" value="CLAIM_APPROVE" />
          <el-option label="认领撤销" value="CLAIM_REVOKE" />
          <el-option label="指派作者" value="CLAIM_ASSIGN" />
          <el-option label="投诉处理" value="COMPLAINT_PROCESS" />
        </el-select>
      </el-form-item>
    </el-form>

    <el-table :data="logs" border stripe v-loading="loading">
      <el-table-column prop="created_at" label="时间" width="170" />
      <el-table-column prop="operator_uuid" label="操作人" width="150" show-overflow-tooltip />
      <el-table-column label="操作类型" width="100">
        <template #default="{ row }">
          <el-tag size="small">{{ typeLabel(row.operation_type) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="work_id" label="作品ID" width="120" show-overflow-tooltip />
      <el-table-column prop="detail" label="详情" min-width="200" show-overflow-tooltip />
    </el-table>

    <el-pagination
      v-model:current-page="currentPage"
      :page-size="20"
      :total="total"
      layout="total, prev, pager, next"
      @current-change="fetchData"
      style="margin-top: 16px; justify-content: flex-end"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { getOperationLogs } from '../api/workManage'

const logs = ref<any[]>([])
const total = ref(0)
const currentPage = ref(1)
const loading = ref(false)
const filterType = ref('')

const typeLabel = (type: string) => {
  const map: Record<string, string> = {
    BAN: '封禁', OFF: '下架', DELETE: '删除', RESTORE: '恢复',
    EDIT: '编辑', CLAIM_APPROVE: '认领审批', CLAIM_REVOKE: '认领撤销',
    CLAIM_ASSIGN: '指派作者', COMPLAINT_PROCESS: '投诉处理'
  }
  return map[type] || type
}

const fetchData = async () => {
  loading.value = true
  try {
    const { data } = await getOperationLogs({
      page_num: currentPage.value, page_size: 20,
      params: { operation_type: filterType.value || null }
    })
    if (data.code === 0) { logs.value = data.data.records || []; total.value = data.data.total || 0 }
  } finally { loading.value = false }
}

onMounted(() => fetchData())
</script>
```

- [ ] **Step 4: 创建 OperationLogView 页面容器**

```vue
<template><OperationLogComp /></template>
<script setup lang="ts">
import OperationLogComp from '../components/OperationLogComp.vue'
</script>
```

- [ ] **Step 5: 构建验证 + 提交**

```bash
cd BarcManageFrontendV2 && npm run build
git add BarcManageFrontendV2/src/modules/works/components/ComplaintListComp.vue
git add BarcManageFrontendV2/src/modules/works/views/ComplaintsListView.vue
git add BarcManageFrontendV2/src/modules/works/components/OperationLogComp.vue
git add BarcManageFrontendV2/src/modules/works/views/OperationLogView.vue
git commit -m "feat(管理端): 新增投诉处理页和操作日志页"
```

---

### Task 16: 配置前端路由

**Files:**
- Modify: `BarcManageFrontendV2/src/app/router/routes.ts`

- [ ] **Step 1: 在 routes.ts 中添加 works 路由**

在 `adminChildren` 数组合适位置（内容管理 groupOrder: 50 附近）新增路由：

```typescript
// 在现有的 import 区域新增 lazyload
const WorksListView = () => import('@/modules/works/views/WorksListView.vue')
const WorkEditView = () => import('@/modules/works/views/WorkEditView.vue')
const ClaimsListView = () => import('@/modules/works/views/ClaimsListView.vue')
const ComplaintsListView = () => import('@/modules/works/views/ComplaintsListView.vue')
const OperationLogView = () => import('@/modules/works/views/OperationLogView.vue')

// 在 adminChildren 数组中，content 分组区域新增：
{
  path: 'works/list',
  name: 'works-list',
  component: WorksListView,
  meta: {
    title: '作品列表',
    subtitle: '管理全部作品，支持封禁、下架、删除、恢复和内容修改。',
    requiresAuth: true,
    requiresManager: true,
    minManagerPermission: MANAGER_PERMISSION.SEC_MAINTAINER,
    menuLabel: '作品管理',
    menuGroup: 'content',
    menuGroupLabel: '内容管理',
    groupOrder: 50,
    menuOrder: 40,
  },
},
{
  path: 'works/:workId/edit',
  name: 'works-edit',
  component: WorkEditView,
  meta: {
    title: '编辑作品',
    subtitle: '编辑作品的全部内容字段。',
    requiresAuth: true,
    requiresManager: true,
    minManagerPermission: MANAGER_PERMISSION.SEC_MAINTAINER,
    hiddenInMenu: true,
  },
},
{
  path: 'works/claims',
  name: 'works-claims',
  component: ClaimsListView,
  meta: {
    title: '认领管理',
    subtitle: '审批认领申请、撤销认领、指派作者。',
    requiresAuth: true,
    requiresManager: true,
    minManagerPermission: MANAGER_PERMISSION.SEC_MAINTAINER,
    menuLabel: '认领管理',
    menuGroup: 'content',
    menuGroupLabel: '内容管理',
    groupOrder: 50,
    menuOrder: 41,
  },
},
{
  path: 'works/complaints',
  name: 'works-complaints',
  component: ComplaintsListView,
  meta: {
    title: '投诉处理',
    subtitle: '查看和处理作品投诉，处理时可联动封禁/下架/删除操作。',
    requiresAuth: true,
    requiresManager: true,
    minManagerPermission: MANAGER_PERMISSION.SEC_MAINTAINER,
    menuLabel: '投诉处理',
    menuGroup: 'content',
    menuGroupLabel: '内容管理',
    groupOrder: 50,
    menuOrder: 42,
  },
},
{
  path: 'works/logs',
  name: 'works-logs',
  component: OperationLogView,
  meta: {
    title: '操作日志',
    subtitle: '查看所有作品管理操作的审计日志。',
    requiresAuth: true,
    requiresManager: true,
    minManagerPermission: MANAGER_PERMISSION.SEC_MAINTAINER,
    menuLabel: '操作日志',
    menuGroup: 'content',
    menuGroupLabel: '内容管理',
    groupOrder: 50,
    menuOrder: 43,
  },
},
```

- [ ] **Step 2: 构建验证**

```bash
cd BarcManageFrontendV2 && npm run build
```
Expected: BUILD SUCCESS

- [ ] **Step 3: 提交**

```bash
git add BarcManageFrontendV2/src/app/router/routes.ts
git commit -m "feat(管理端): 配置works模块路由"
```

---

## 规格自检

1. **Spec coverage**: 
   - ✅ 作品状态管理（封禁/下架/删除/恢复）→ Task 6, 7, 12
   - ✅ 作品认领管理（审批/撤销/指派/历史）→ Task 6, 7, 14
   - ✅ 作品内容修改（全部字段）→ Task 6, 7, 13
   - ✅ 投诉反馈处理（联动操作）→ Task 6, 7, 15
   - ✅ 操作日志记录 → Task 2, 3, 4, 15
   - ✅ DELETED状态 → Task 1, 9
   - ✅ 前台兼容性 → Task 9
   - ✅ 前端路由 → Task 16

2. **Placeholder scan**: 无 TBD/TODO/占位符

3. **Type consistency**: 
   - `WorkOperationLogModel` 的字段名与 Mapper SQL 一致
   - `WorkManageService` 返回的 `ResponseEntity<J>` 格式与现有响应类一致
   - 前端 API 函数参数名与后端 Controller 的 `@RequestBody` 字段一致
   - `minManagerPermission: MANAGER_PERMISSION.SEC_MAINTAINER` 值与 `permissions.ts` 常量一致

4. **Missing pieces**: 
   - 现有 WorkClaimMapper 可能缺少 `selectById`, `deleteById` 方法 → 实现时需先读取确认
   - WorkFeedbackMapper 可能缺少 `selectAll`, `selectById`, `update` 方法 → 实现时需先确认
   - 前端 baseHttp 的路径需确认 → 已在 shared/api 中定义

---

## 实现计划总结

| 阶段 | Task | 内容 | 预估工作量 |
|------|------|------|-----------|
| 后端基础 | 1-4 | WorkStatusEnum、数据库表、Model、Mapper | 30min |
| 后端逻辑 | 5-6 | WorkMapper管理查询、WorkManageService | 45min |
| 后端API | 7 | WorkManageController | 15min |
| 后端修复 | 8-9 | Update SQL补充、DELETED状态处理 | 15min |
| 前端基础 | 10-11 | 权限常量确认、API封装 | 15min |
| 前端页面 | 12-15 | 列表页、编辑页、认领页、投诉页、日志页 | 90min |
| 前端集成 | 16 | 路由配置 | 10min |

总预估：约 3.5 小时
