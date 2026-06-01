# 用户封号功能实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 实现用户封号功能，支持风险冻结、临时封号、违规封号，包含自动解封、审计记录、登录拦截

**Architecture:** 基于现有用户表扩展safe_level_before_ban字段，新建user_ban_record表记录封号历史，通过定时任务实现临时封号自动解封

**Tech Stack:** Java 17, Spring Boot, MyBatis, Flyway, MySQL

---

## 文件结构

### 新建文件
- `BarcBackend/src/main/resources/db/migration/V3__add_user_ban_support.sql` - 数据库迁移
- `BarcBackend/src/main/java/com/miaoyu/barc/user/model/UserBanRecordModel.java` - 封号记录模型
- `BarcBackend/src/main/java/com/miaoyu/barc/user/mapper/UserBanRecordMapper.java` - 封号记录映射器
- `BarcBackend/src/main/java/com/miaoyu/barc/user/mapper/UserBanRecordMapper.xml` - MyBatis映射文件
- `BarcBackend/src/main/java/com/miaoyu/barc/user/service/UserBanService.java` - 封号服务
- `BarcBackend/src/main/java/com/miaoyu/barc/user/controller/UserBanController.java` - 封号控制器
- `BarcBackend/src/main/java/com/miaoyu/barc/user/scheduler/UserBanScheduler.java` - 定时任务
- `BarcBackend/src/main/java/com/miaoyu/barc/user/enumeration/BanTypeEnum.java` - 封号类型枚举

### 修改文件
- `BarcBackend/src/main/java/com/miaoyu/barc/user/model/UserBasicModel.java` - 添加safe_level_before_ban字段
- `BarcBackend/src/main/java/com/miaoyu/barc/user/mapper/UserBasicMapper.java` - 添加更新safe_level方法
- `BarcBackend/src/main/java/com/miaoyu/barc/user/mapper/UserBasicMapper.xml` - 添加SQL映射
- `BarcBackend/src/main/java/com/miaoyu/barc/user/service/SignService.java` - 添加登录拦截逻辑

---

## Task 1: 数据库迁移

**Files:**
- Create: `BarcBackend/src/main/resources/db/migration/V3__add_user_ban_support.sql`

- [ ] **Step 1: 创建迁移文件**

```sql
-- V3__add_user_ban_support.sql
-- 为用户封号功能添加数据库支持

-- 1. 为student表添加safe_level_before_ban字段
ALTER TABLE student ADD COLUMN safe_level_before_ban INT DEFAULT NULL COMMENT '封号前的safe_level值，用于解封恢复';

-- 2. 创建封号记录表
CREATE TABLE user_ban_record (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    user_id VARCHAR(255) NOT NULL COMMENT '被封号用户ID',
    ban_type TINYINT NOT NULL COMMENT '封号类型：0-风险冻结，1-临时封号，2-违规封号',
    ban_reason VARCHAR(500) NOT NULL COMMENT '封号原因',
    ban_duration_days INT DEFAULT NULL COMMENT '封号天数（临时封号使用）',
    banned_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '封号时间',
    unbanned_at DATETIME DEFAULT NULL COMMENT '解封时间',
    unban_reason VARCHAR(500) DEFAULT NULL COMMENT '解封原因',
    operator_id VARCHAR(255) NOT NULL COMMENT '操作人ID（管理员或系统）',
    operator_type TINYINT NOT NULL DEFAULT 1 COMMENT '操作人类型：0-系统自动，1-管理员',
    safe_level_before_ban INT NOT NULL COMMENT '封号前的safe_level值',
    safe_level_after_ban INT NOT NULL COMMENT '封号后的safe_level值',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_user_id (user_id),
    INDEX idx_ban_type (ban_type),
    INDEX idx_banned_at (banned_at),
    INDEX idx_unbanned_at (unbanned_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户封号记录表';
```

- [ ] **Step 2: 验证迁移文件**

检查文件语法正确，符合Flyway命名规范（V3__前缀）

- [ ] **Step 3: 提交**

```bash
git add BarcBackend/src/main/resources/db/migration/V3__add_user_ban_support.sql
git commit -m "feat(数据库): 添加用户封号功能迁移文件

- 为student表添加safe_level_before_ban字段
- 创建user_ban_record封号记录表
- 添加必要索引优化查询性能"
```

---

## Task 2: 封号类型枚举

**Files:**
- Create: `BarcBackend/src/main/java/com/miaoyu/barc/user/enumeration/BanTypeEnum.java`

- [ ] **Step 1: 创建枚举类**

```java
package com.miaoyu.barc.user.enumeration;

/**
 * 封号类型枚举
 * 
 * 与safe_level值对应：
 * - RISK_FREEZE(0): 风险冻结，safe_level=0
 * - TEMPORARY_BAN(1): 临时封号，safe_level=-1
 * - VIOLATION_BAN(2): 违规封号，safe_level=-2
 * - SOFT_DELETE(3): 软删除，safe_level=-3
 */
public enum BanTypeEnum {
    RISK_FREEZE(0, "风险冻结", 0),
    TEMPORARY_BAN(1, "临时封号", -1),
    VIOLATION_BAN(2, "违规封号", -2),
    SOFT_DELETE(3, "软删除", -3);

    private final int code;
    private final String description;
    private final int safeLevelValue;

    BanTypeEnum(int code, String description, int safeLevelValue) {
        this.code = code;
        this.description = description;
        this.safeLevelValue = safeLevelValue;
    }

    public int getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }

    public int getSafeLevelValue() {
        return safeLevelValue;
    }

    /**
     * 根据code获取枚举
     */
    public static BanTypeEnum fromCode(int code) {
        for (BanTypeEnum type : values()) {
            if (type.code == code) {
                return type;
            }
        }
        throw new IllegalArgumentException("无效的封号类型代码: " + code);
    }

    /**
     * 根据safe_level值获取封号状态描述
     */
    public static String getStatusBySafeLevel(int safeLevel) {
        if (safeLevel >= 0) {
            return "正常";
        }
        for (BanTypeEnum type : values()) {
            if (type.safeLevelValue == safeLevel) {
                return type.description;
            }
        }
        return "未知状态";
    }
}
```

- [ ] **Step 2: 提交**

```bash
git add BarcBackend/src/main/java/com/miaoyu/barc/user/enumeration/BanTypeEnum.java
git commit -m "feat(枚举): 添加封号类型枚举

- 定义风险冻结、临时封号、违规封号、软删除四种类型
- 提供code到枚举的转换方法
- 提供safe_level到状态描述的转换方法"
```

---

## Task 3: 封号记录模型

**Files:**
- Create: `BarcBackend/src/main/java/com/miaoyu/barc/user/model/UserBanRecordModel.java`

- [ ] **Step 1: 创建模型类**

```java
package com.miaoyu.barc.user.model;

import java.time.LocalDateTime;

/**
 * 用户封号记录模型
 * 
 * 对应数据库表：user_ban_record
 * 记录所有封号/解封历史，用于审计追溯
 */
public class UserBanRecordModel {
    private Long id;
    private String userId;
    private Integer banType;
    private String banReason;
    private Integer banDurationDays;
    private LocalDateTime bannedAt;
    private LocalDateTime unbannedAt;
    private String unbanReason;
    private String operatorId;
    private Integer operatorType;
    private Integer safeLevelBeforeBan;
    private Integer safeLevelAfterBan;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public Integer getBanType() {
        return banType;
    }

    public void setBanType(Integer banType) {
        this.banType = banType;
    }

    public String getBanReason() {
        return banReason;
    }

    public void setBanReason(String banReason) {
        this.banReason = banReason;
    }

    public Integer getBanDurationDays() {
        return banDurationDays;
    }

    public void setBanDurationDays(Integer banDurationDays) {
        this.banDurationDays = banDurationDays;
    }

    public LocalDateTime getBannedAt() {
        return bannedAt;
    }

    public void setBannedAt(LocalDateTime bannedAt) {
        this.bannedAt = bannedAt;
    }

    public LocalDateTime getUnbannedAt() {
        return unbannedAt;
    }

    public void setUnbannedAt(LocalDateTime unbannedAt) {
        this.unbannedAt = unbannedAt;
    }

    public String getUnbanReason() {
        return unbanReason;
    }

    public void setUnbanReason(String unbanReason) {
        this.unbanReason = unbanReason;
    }

    public String getOperatorId() {
        return operatorId;
    }

    public void setOperatorId(String operatorId) {
        this.operatorId = operatorId;
    }

    public Integer getOperatorType() {
        return operatorType;
    }

    public void setOperatorType(Integer operatorType) {
        this.operatorType = operatorType;
    }

    public Integer getSafeLevelBeforeBan() {
        return safeLevelBeforeBan;
    }

    public void setSafeLevelBeforeBan(Integer safeLevelBeforeBan) {
        this.safeLevelBeforeBan = safeLevelBeforeBan;
    }

    public Integer getSafeLevelAfterBan() {
        return safeLevelAfterBan;
    }

    public void setSafeLevelAfterBan(Integer safeLevelAfterBan) {
        this.safeLevelAfterBan = safeLevelAfterBan;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
```

- [ ] **Step 2: 提交**

```bash
git add BarcBackend/src/main/java/com/miaoyu/barc/user/model/UserBanRecordModel.java
git commit -m "feat(模型): 添加封号记录模型

- 定义封号记录的所有字段
- 包含封号类型、原因、时长、操作人等信息
- 用于封号历史审计记录"
```

---

## Task 4: 修改用户模型

**Files:**
- Modify: `BarcBackend/src/main/java/com/miaoyu/barc/user/model/UserBasicModel.java`

- [ ] **Step 1: 添加safe_level_before_ban字段**

在UserBasicModel.java中添加以下内容：

```java
// 在safe_level字段后添加
private Integer safe_level_before_ban;

// 添加getter和setter
public Integer getSafe_level_before_ban() {
    return safe_level_before_ban;
}

public void setSafe_level_before_ban(Integer safe_level_before_ban) {
    this.safe_level_before_ban = safe_level_before_ban;
}
```

- [ ] **Step 2: 提交**

```bash
git add BarcBackend/src/main/java/com/miaoyu/barc/user/model/UserBasicModel.java
git commit -m "feat(模型): 为用户模型添加safe_level_before_ban字段

- 记录封号前的safe_level值
- 用于临时封号解封后恢复"
```

---

## Task 5: 封号记录映射器

**Files:**
- Create: `BarcBackend/src/main/java/com/miaoyu/barc/user/mapper/UserBanRecordMapper.java`
- Create: `BarcBackend/src/main/resources/mapper/UserBanRecordMapper.xml`

- [ ] **Step 1: 创建映射器接口**

```java
package com.miaoyu.barc.user.mapper;

import com.miaoyu.barc.user.model.UserBanRecordModel;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 封号记录映射器
 * 
 * 提供封号记录的CRUD操作
 */
@Mapper
public interface UserBanRecordMapper {
    
    /**
     * 插入封号记录
     */
    int insert(UserBanRecordModel record);
    
    /**
     * 根据用户ID查询封号记录（分页）
     */
    List<UserBanRecordModel> selectByUserId(@Param("userId") String userId, 
                                            @Param("offset") int offset, 
                                            @Param("size") int size);
    
    /**
     * 统计用户封号记录总数
     */
    long countByUserId(@Param("userId") String userId);
    
    /**
     * 查询用户最新的封号记录
     */
    UserBanRecordModel selectLatestByUserId(@Param("userId") String userId);
    
    /**
     * 查询已到期的临时封号记录
     */
    List<UserBanRecordModel> selectExpiredRecords();
    
    /**
     * 更新解封时间
     */
    int updateUnbannedAt(@Param("id") Long id, 
                         @Param("unbannedAt") LocalDateTime unbannedAt,
                         @Param("unbanReason") String unbanReason);
}
```

- [ ] **Step 2: 创建MyBatis映射文件**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE mapper PUBLIC "-//mybatis.org//DTD Mapper 3.0//EN" "http://mybatis.org/dtd/mybatis-3-mapper.dtd">
<mapper namespace="com.miaoyu.barc.user.mapper.UserBanRecordMapper">
    
    <resultMap id="UserBanRecordMap" type="com.miaoyu.barc.user.model.UserBanRecordModel">
        <id property="id" column="id"/>
        <result property="userId" column="user_id"/>
        <result property="banType" column="ban_type"/>
        <result property="banReason" column="ban_reason"/>
        <result property="banDurationDays" column="ban_duration_days"/>
        <result property="bannedAt" column="banned_at"/>
        <result property="unbannedAt" column="unbanned_at"/>
        <result property="unbanReason" column="unban_reason"/>
        <result property="operatorId" column="operator_id"/>
        <result property="operatorType" column="operator_type"/>
        <result property="safeLevelBeforeBan" column="safe_level_before_ban"/>
        <result property="safeLevelAfterBan" column="safe_level_after_ban"/>
        <result property="createdAt" column="created_at"/>
        <result property="updatedAt" column="updated_at"/>
    </resultMap>
    
    <insert id="insert" parameterType="com.miaoyu.barc.user.model.UserBanRecordModel" useGeneratedKeys="true" keyProperty="id">
        INSERT INTO user_ban_record (
            user_id, ban_type, ban_reason, ban_duration_days,
            banned_at, operator_id, operator_type,
            safe_level_before_ban, safe_level_after_ban
        ) VALUES (
            #{userId}, #{banType}, #{banReason}, #{banDurationDays},
            #{bannedAt}, #{operatorId}, #{operatorType},
            #{safeLevelBeforeBan}, #{safeLevelAfterBan}
        )
    </insert>
    
    <select id="selectByUserId" resultMap="UserBanRecordMap">
        SELECT * FROM user_ban_record
        WHERE user_id = #{userId}
        ORDER BY banned_at DESC
        LIMIT #{size} OFFSET #{offset}
    </select>
    
    <select id="countByUserId" resultType="long">
        SELECT COUNT(*) FROM user_ban_record
        WHERE user_id = #{userId}
    </select>
    
    <select id="selectLatestByUserId" resultMap="UserBanRecordMap">
        SELECT * FROM user_ban_record
        WHERE user_id = #{userId}
        ORDER BY banned_at DESC
        LIMIT 1
    </select>
    
    <select id="selectExpiredRecords" resultMap="UserBanRecordMap">
        SELECT * FROM user_ban_record
        WHERE ban_type = 1
        AND unbanned_at IS NULL
        AND DATE_ADD(banned_at, INTERVAL ban_duration_days DAY) <= NOW()
    </select>
    
    <update id="updateUnbannedAt">
        UPDATE user_ban_record
        SET unbanned_at = #{unbannedAt}, unban_reason = #{unbanReason}
        WHERE id = #{id}
    </update>
    
</mapper>
```

- [ ] **Step 3: 提交**

```bash
git add BarcBackend/src/main/java/com/miaoyu/barc/user/mapper/UserBanRecordMapper.java
git add BarcBackend/src/main/resources/mapper/UserBanRecordMapper.xml
git commit -m "feat(映射器): 添加封号记录映射器

- 提供封号记录的CRUD操作
- 支持按用户ID分页查询
- 支持查询已到期的临时封号记录
- 包含MyBatis映射文件"
```

---

## Task 6: 修改用户映射器

**Files:**
- Modify: `BarcBackend/src/main/java/com/miaoyu/barc/user/mapper/UserBasicMapper.java`
- Modify: `BarcBackend/src/main/resources/mapper/UserBasicMapper.xml`

- [ ] **Step 1: 添加更新safe_level的方法**

在UserBasicMapper.java中添加：

```java
/**
 * 更新用户safe_level
 */
int updateSafeLevel(@Param("uuid") String uuid, @Param("safeLevel") Integer safeLevel);

/**
 * 更新用户safe_level_before_ban
 */
int updateSafeLevelBeforeBan(@Param("uuid") String uuid, @Param("safeLevelBeforeBan") Integer safeLevelBeforeBan);

/**
 * 查询用户当前safe_level
 */
Integer selectSafeLevelByUuid(@Param("uuid") String uuid);
```

- [ ] **Step 2: 添加SQL映射**

在UserBasicMapper.xml中添加：

```xml
<update id="updateSafeLevel">
    UPDATE user_basic
    SET safe_level = #{safeLevel}
    WHERE uuid = #{uuid}
</update>

<update id="updateSafeLevelBeforeBan">
    UPDATE user_basic
    SET safe_level_before_ban = #{safeLevelBeforeBan}
    WHERE uuid = #{uuid}
</update>

<select id="selectSafeLevelByUuid" resultType="java.lang.Integer">
    SELECT safe_level FROM user_basic
    WHERE uuid = #{uuid}
</select>
```

- [ ] **Step 3: 提交**

```bash
git add BarcBackend/src/main/java/com/miaoyu/barc/user/mapper/UserBasicMapper.java
git add BarcBackend/src/main/resources/mapper/UserBasicMapper.xml
git commit -m "feat(映射器): 为用户映射器添加safe_level更新方法

- 添加updateSafeLevel方法
- 添加updateSafeLevelBeforeBan方法
- 添加selectSafeLevelByUuid方法"
```

---

## Task 7: 封号服务

**Files:**
- Create: `BarcBackend/src/main/java/com/miaoyu/barc/user/service/UserBanService.java`

- [ ] **Step 1: 创建封号服务**

```java
package com.miaoyu.barc.user.service;

import com.miaoyu.barc.permission.PermissionConst;
import com.miaoyu.barc.response.ErrorR;
import com.miaoyu.barc.response.ResourceR;
import com.miaoyu.barc.response.SuccessR;
import com.miaoyu.barc.user.enumeration.BanTypeEnum;
import com.miaoyu.barc.user.enumeration.UserIdentityEnum;
import com.miaoyu.barc.user.mapper.UserBanRecordMapper;
import com.miaoyu.barc.user.mapper.UserBasicMapper;
import com.miaoyu.barc.user.model.UserBanRecordModel;
import com.miaoyu.barc.user.model.UserBasicModel;
import com.miaoyu.barc.utils.J;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 用户封号服务
 * 
 * 提供封号、解封、查询封号历史等功能
 */
@Service
public class UserBanService {
    
    @Autowired
    private UserBanRecordMapper userBanRecordMapper;
    
    @Autowired
    private UserBasicMapper userBasicMapper;
    
    /**
     * 封号用户
     * 
     * @param operatorId 操作人ID
     * @param operatorType 操作人类型（0-系统自动，1-管理员）
     * @param userId 被封号用户ID
     * @param banType 封号类型
     * @param reason 封号原因
     * @param durationDays 封号天数（临时封号必填）
     * @return 封号结果
     */
    @Transactional
    public ResponseEntity<J> banUser(String operatorId, int operatorType, String userId, 
                                     int banType, String reason, Integer durationDays) {
        // 1. 验证封号类型
        BanTypeEnum banTypeEnum;
        try {
            banTypeEnum = BanTypeEnum.fromCode(banType);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.ok(new ErrorR().normal("无效的封号类型"));
        }
        
        // 2. 验证临时封号必须提供天数
        if (banTypeEnum == BanTypeEnum.TEMPORARY_BAN && (durationDays == null || durationDays <= 0)) {
            return ResponseEntity.ok(new ErrorR().normal("临时封号必须提供有效的封号天数"));
        }
        
        // 3. 查询用户当前状态
        UserBasicModel user = userBasicMapper.selectByUuid(userId);
        if (user == null) {
            return ResponseEntity.ok(new ErrorR().normal("用户不存在"));
        }
        
        // 4. 检查用户是否已被封号
        if (user.getSafe_level() != null && user.getSafe_level() < 0) {
            return ResponseEntity.ok(new ErrorR().normal("用户已被封号，不能重复封号"));
        }
        
        // 5. 记录封号前的safe_level
        int safeLevelBeforeBan = user.getSafe_level() != null ? user.getSafe_level() : 0;
        
        // 6. 更新用户表
        int targetSafeLevel = banTypeEnum.getSafeLevelValue();
        userBasicMapper.updateSafeLevelBeforeBan(userId, safeLevelBeforeBan);
        userBasicMapper.updateSafeLevel(userId, targetSafeLevel);
        
        // 7. 创建封号记录
        UserBanRecordModel record = new UserBanRecordModel();
        record.setUserId(userId);
        record.setBanType(banType);
        record.setBanReason(reason);
        record.setBanDurationDays(banTypeEnum == BanTypeEnum.TEMPORARY_BAN ? durationDays : null);
        record.setBannedAt(LocalDateTime.now());
        record.setOperatorId(operatorId);
        record.setOperatorType(operatorType);
        record.setSafeLevelBeforeBan(safeLevelBeforeBan);
        record.setSafeLevelAfterBan(targetSafeLevel);
        userBanRecordMapper.insert(record);
        
        // 8. 返回结果
        Map<String, Object> result = new HashMap<>();
        result.put("recordId", record.getId());
        if (banTypeEnum == BanTypeEnum.TEMPORARY_BAN) {
            LocalDateTime unbanTime = LocalDateTime.now().plusDays(durationDays);
            result.put("unbanTime", unbanTime.toString());
        }
        
        return ResponseEntity.ok(new SuccessR().normal("封号成功"));
    }
    
    /**
     * 解封用户
     * 
     * @param operatorId 操作人ID
     * @param userId 被解封用户ID
     * @param reason 解封原因
     * @return 解封结果
     */
    @Transactional
    public ResponseEntity<J> unbanUser(String operatorId, String userId, String reason) {
        // 1. 查询用户当前状态
        UserBasicModel user = userBasicMapper.selectByUuid(userId);
        if (user == null) {
            return ResponseEntity.ok(new ErrorR().normal("用户不存在"));
        }
        
        // 2. 检查用户是否被封号
        if (user.getSafe_level() == null || user.getSafe_level() >= 0) {
            return ResponseEntity.ok(new ErrorR().normal("用户未被封号"));
        }
        
        // 3. 获取封号前的safe_level
        Integer safeLevelBeforeBan = user.getSafe_level_before_ban();
        if (safeLevelBeforeBan == null) {
            safeLevelBeforeBan = 0; // 默认恢复到0
        }
        
        // 4. 恢复用户safe_level
        userBasicMapper.updateSafeLevel(userId, safeLevelBeforeBan);
        userBasicMapper.updateSafeLevelBeforeBan(userId, null);
        
        // 5. 更新封号记录
        UserBanRecordModel latestRecord = userBanRecordMapper.selectLatestByUserId(userId);
        if (latestRecord != null) {
            userBanRecordMapper.updateUnbannedAt(latestRecord.getId(), LocalDateTime.now(), reason);
        }
        
        // 6. 返回结果
        Map<String, Object> result = new HashMap<>();
        result.put("safeLevel", safeLevelBeforeBan);
        
        return ResponseEntity.ok(new SuccessR().normal("解封成功"));
    }
    
    /**
     * 查询用户封号历史
     * 
     * @param userId 用户ID
     * @param page 页码
     * @param size 每页大小
     * @return 封号历史列表
     */
    public ResponseEntity<J> getBanHistory(String userId, int page, int size) {
        int offset = (page - 1) * size;
        List<UserBanRecordModel> list = userBanRecordMapper.selectByUserId(userId, offset, size);
        long total = userBanRecordMapper.countByUserId(userId);
        
        Map<String, Object> result = new HashMap<>();
        result.put("list", list);
        result.put("total", total);
        
        return ResponseEntity.ok(new ResourceR().resourceSuch(true, result));
    }
    
    /**
     * 查询用户当前状态
     * 
     * @param userId 用户ID
     * @return 用户状态信息
     */
    public ResponseEntity<J> getUserStatus(String userId) {
        UserBasicModel user = userBasicMapper.selectByUuid(userId);
        if (user == null) {
            return ResponseEntity.ok(new ErrorR().normal("用户不存在"));
        }
        
        Map<String, Object> result = new HashMap<>();
        result.put("userId", userId);
        result.put("safeLevel", user.getSafe_level());
        result.put("safeLevelBeforeBan", user.getSafe_level_before_ban());
        result.put("banStatus", BanTypeEnum.getStatusBySafeLevel(user.getSafe_level()));
        
        // 查询最新封号记录
        UserBanRecordModel latestRecord = userBanRecordMapper.selectLatestByUserId(userId);
        if (latestRecord != null) {
            result.put("banReason", latestRecord.getBanReason());
            result.put("banTime", latestRecord.getBannedAt());
            if (latestRecord.getBanDurationDays() != null) {
                LocalDateTime unbanTime = latestRecord.getBannedAt().plusDays(latestRecord.getBanDurationDays());
                result.put("unbanTime", unbanTime);
            }
        }
        
        return ResponseEntity.ok(new ResourceR().resourceSuch(true, result));
    }
    
    /**
     * 自动解封已到期的临时封号用户
     * 
     * @return 解封数量
     */
    @Transactional
    public int unbanExpiredUsers() {
        List<UserBanRecordModel> expiredRecords = userBanRecordMapper.selectExpiredRecords();
        int count = 0;
        
        for (UserBanRecordModel record : expiredRecords) {
            // 恢复用户safe_level
            userBasicMapper.updateSafeLevel(record.getUserId(), record.getSafeLevelBeforeBan());
            userBasicMapper.updateSafeLevelBeforeBan(record.getUserId(), null);
            
            // 更新封号记录
            userBanRecordMapper.updateUnbannedAt(record.getId(), LocalDateTime.now(), "系统自动解封");
            
            count++;
        }
        
        return count;
    }
}
```

- [ ] **Step 2: 提交**

```bash
git add BarcBackend/src/main/java/com/miaoyu/barc/user/service/UserBanService.java
git commit -m "feat(服务): 实现用户封号服务

- 实现banUser封号方法
- 实现unbanUser解封方法
- 实现getBanHistory查询封号历史
- 实现getUserStatus查询用户状态
- 实现unbanExpiredUsers自动解封"
```

---

## Task 8: 封号控制器

**Files:**
- Create: `BarcBackend/src/main/java/com/miaoyu/barc/user/controller/UserBanController.java`

- [ ] **Step 1: 创建控制器**

```java
package com.miaoyu.barc.user.controller;

import com.miaoyu.barc.annotation.RequireUserAndPermissionAnno;
import com.miaoyu.barc.permission.PermissionConst;
import com.miaoyu.barc.user.enumeration.UserIdentityEnum;
import com.miaoyu.barc.user.service.UserBanService;
import com.miaoyu.barc.utils.J;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 用户封号控制器
 * 
 * 提供封号、解封、查询封号历史等API
 */
@RestController
@RequestMapping("/user/ban")
public class UserBanController {
    
    @Autowired
    private UserBanService userBanService;
    
    /**
     * 封号用户
     * 
     * 需要副馆长(ADMINISTRATOR=16)及以上权限
     */
    @RequireUserAndPermissionAnno({ @RequireUserAndPermissionAnno.Check(
        uuidIndex = 0,
        identity = UserIdentityEnum.MANAGER,
        targetPermission = PermissionConst.ADMINISTRATOR,
        isSuchElseRequire = false,
        isHasElseUpper = true
    )})
    @PostMapping("")
    public ResponseEntity<J> banUser(HttpServletRequest request, @RequestBody Map<String, Object> params) {
        String operatorId = (String) request.getAttribute("uuid");
        String userId = (String) params.get("userId");
        Integer banType = (Integer) params.get("banType");
        String reason = (String) params.get("reason");
        Integer durationDays = (Integer) params.get("durationDays");
        
        return userBanService.banUser(operatorId, 1, userId, banType, reason, durationDays);
    }
    
    /**
     * 解封用户
     * 
     * 需要副馆长(ADMINISTRATOR=16)及以上权限
     */
    @RequireUserAndPermissionAnno({ @RequireUserAndPermissionAnno.Check(
        uuidIndex = 0,
        identity = UserIdentityEnum.MANAGER,
        targetPermission = PermissionConst.ADMINISTRATOR,
        isSuchElseRequire = false,
        isHasElseUpper = true
    )})
    @PostMapping("/unban")
    public ResponseEntity<J> unbanUser(HttpServletRequest request, @RequestBody Map<String, Object> params) {
        String operatorId = (String) request.getAttribute("uuid");
        String userId = (String) params.get("userId");
        String reason = (String) params.get("reason");
        
        return userBanService.unbanUser(operatorId, userId, reason);
    }
    
    /**
     * 查询用户封号历史
     * 
     * 需要副馆长(ADMINISTRATOR=16)及以上权限
     */
    @RequireUserAndPermissionAnno({ @RequireUserAndPermissionAnno.Check(
        uuidIndex = 0,
        identity = UserIdentityEnum.MANAGER,
        targetPermission = PermissionConst.ADMINISTRATOR,
        isSuchElseRequire = false,
        isHasElseUpper = true
    )})
    @GetMapping("/history")
    public ResponseEntity<J> getBanHistory(
            HttpServletRequest request,
            @RequestParam("userId") String userId,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "10") int size) {
        return userBanService.getBanHistory(userId, page, size);
    }
    
    /**
     * 查询用户当前状态
     * 
     * 需要副馆长(ADMINISTRATOR=16)及以上权限
     */
    @RequireUserAndPermissionAnno({ @RequireUserAndPermissionAnno.Check(
        uuidIndex = 0,
        identity = UserIdentityEnum.MANAGER,
        targetPermission = PermissionConst.ADMINISTRATOR,
        isSuchElseRequire = false,
        isHasElseUpper = true
    )})
    @GetMapping("/status")
    public ResponseEntity<J> getUserStatus(
            HttpServletRequest request,
            @RequestParam("userId") String userId) {
        return userBanService.getUserStatus(userId);
    }
}
```

- [ ] **Step 2: 提交**

```bash
git add BarcBackend/src/main/java/com/miaoyu/barc/user/controller/UserBanController.java
git commit -m "feat(控制器): 实现用户封号控制器

- 实现POST /user/ban封号接口
- 实现POST /user/ban/unban解封接口
- 实现GET /user/ban/history查询封号历史
- 实现GET /user/ban/status查询用户状态
- 所有接口需要副馆长及以上权限"
```

---

## Task 9: 定时任务

**Files:**
- Create: `BarcBackend/src/main/java/com/miaoyu/barc/user/scheduler/UserBanScheduler.java`

- [ ] **Step 1: 创建定时任务**

```java
package com.miaoyu.barc.user.scheduler;

import com.miaoyu.barc.user.service.UserBanService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 用户封号定时任务
 * 
 * 负责自动解封已到期的临时封号用户
 */
@Component
public class UserBanScheduler {
    
    private static final Logger log = LoggerFactory.getLogger(UserBanScheduler.class);
    
    @Autowired
    private UserBanService userBanService;
    
    /**
     * 每小时检查并解封已到期的临时封号用户
     */
    @Scheduled(fixedRate = 3600000) // 每小时执行一次
    public void unbanExpiredUsers() {
        try {
            int count = userBanService.unbanExpiredUsers();
            if (count > 0) {
                log.info("自动解封了{}个已到期的临时封号用户", count);
            }
        } catch (Exception e) {
            log.error("自动解封任务执行失败", e);
        }
    }
}
```

- [ ] **Step 2: 提交**

```bash
git add BarcBackend/src/main/java/com/miaoyu/barc/user/scheduler/UserBanScheduler.java
git commit -m "feat(定时任务): 实现用户封号定时任务

- 每小时检查并解封已到期的临时封号用户
- 包含异常处理和日志记录"
```

---

## Task 10: 修改登录逻辑

**Files:**
- Modify: `BarcBackend/src/main/java/com/miaoyu/barc/user/service/SignService.java`

- [ ] **Step 1: 添加登录拦截逻辑**

在SignService.java中修改signInUserService方法：

```java
public ResponseEntity<J> signInUserService(String type, String account, String password) {
    UserBasicModel userBasic;
    switch (type) {
        case "username": {
            userBasic = userBasicMapper.selectByUsername(account);
            break;
        } case "email": {
            userBasic = userBasicMapper.selectByEmail(account);
            break;
        } default: {
            return ResponseEntity.ok(new ErrorR().normal("参数错误！"));
        }
    }
    if (userBasic == null) {
        return ResponseEntity.ok(new UserR().noSuchUser());
    }
    
    // 检查用户是否被封号
    if (userBasic.getSafe_level() != null && userBasic.getSafe_level() < 0) {
        return handleBannedUser(userBasic);
    }
    
    String s = passwordHash.passwordHash256(password);
    if (s == null) {
        return ResponseEntity.ok(new SignR().signIn(false));
    }
    if (s.equals(userBasic.getPassword())) {
        String token = jwtService.jwtSigned(userBasic.getUuid());
        return ResponseEntity.ok(new SuccessR().normal(token));
    } else {
        return ResponseEntity.ok(new ErrorR().normal("密码错误"));
    }
}

/**
 * 处理被封号用户的登录请求
 */
private ResponseEntity<J> handleBannedUser(UserBasicModel userBasic) {
    int safeLevel = userBasic.getSafe_level();
    String message;
    
    switch (safeLevel) {
        case 0:
            message = "账户风险冻结，请联系客服";
            break;
        case -1:
            // 查询临时封号的解封时间
            UserBanRecordModel latestRecord = userBanRecordMapper.selectLatestByUserId(userBasic.getUuid());
            if (latestRecord != null && latestRecord.getBanDurationDays() != null) {
                LocalDateTime unbanTime = latestRecord.getBannedAt().plusDays(latestRecord.getBanDurationDays());
                message = "账户临时封禁，解封时间：" + unbanTime.toString();
            } else {
                message = "账户临时封禁";
            }
            break;
        case -2:
            message = "账户因严重违规已被永久封禁";
            break;
        case -3:
            message = "账户不存在";
            break;
        default:
            message = "账户已被封禁";
    }
    
    return ResponseEntity.ok(new ErrorR().normal(message));
}
```

- [ ] **Step 2: 添加依赖注入**

在SignService类中添加：

```java
@Autowired
private UserBanRecordMapper userBanRecordMapper;
```

- [ ] **Step 3: 提交**

```bash
git add BarcBackend/src/main/java/com/miaoyu/barc/user/service/SignService.java
git commit -m "feat(登录): 添加封号用户登录拦截

- 登录时检查用户safe_level
- 根据封号类型返回不同提示信息
- 临时封号显示解封时间"
```

---

## Task 11: 测试用例

**Files:**
- Create: `BarcBackend/src/test/java/com/miaoyu/barc/user/service/UserBanServiceTest.java`

- [ ] **Step 1: 创建测试类**

```java
package com.miaoyu.barc.user.service;

import com.miaoyu.barc.user.enumeration.BanTypeEnum;
import com.miaoyu.barc.user.mapper.UserBanRecordMapper;
import com.miaoyu.barc.user.mapper.UserBasicMapper;
import com.miaoyu.barc.user.model.UserBanRecordModel;
import com.miaoyu.barc.user.model.UserBasicModel;
import com.miaoyu.barc.utils.J;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 用户封号服务测试
 */
@SpringBootTest
@Transactional
public class UserBanServiceTest {
    
    @Autowired
    private UserBanService userBanService;
    
    @Autowired
    private UserBasicMapper userBasicMapper;
    
    @Autowired
    private UserBanRecordMapper userBanRecordMapper;
    
    private UserBasicModel testUser;
    
    @BeforeEach
    void setUp() {
        // 创建测试用户
        testUser = new UserBasicModel();
        testUser.setUuid("test-user-001");
        testUser.setUsername("testuser");
        testUser.setEmail("test@example.com");
        testUser.setPassword("hashedpassword");
        testUser.setSafe_level(50);
        userBasicMapper.insert(testUser);
    }
    
    @Test
    void testBanUser_TemporaryBan() {
        // 测试临时封号
        ResponseEntity<J> response = userBanService.banUser(
            "admin-001", 1, testUser.getUuid(), 
            BanTypeEnum.TEMPORARY_BAN.getCode(), "违规发言", 7
        );
        
        J body = response.getBody();
        assertNotNull(body);
        assertEquals(0, body.getCode()); // 成功
        
        // 验证用户状态
        UserBasicModel updatedUser = userBasicMapper.selectByUuid(testUser.getUuid());
        assertEquals(-1, updatedUser.getSafe_level());
        assertEquals(50, updatedUser.getSafe_level_before_ban());
        
        // 验证封号记录
        UserBanRecordModel record = userBanRecordMapper.selectLatestByUserId(testUser.getUuid());
        assertNotNull(record);
        assertEquals(BanTypeEnum.TEMPORARY_BAN.getCode(), record.getBanType());
        assertEquals(7, record.getBanDurationDays());
    }
    
    @Test
    void testBanUser_ViolationBan() {
        // 测试违规封号
        ResponseEntity<J> response = userBanService.banUser(
            "admin-001", 1, testUser.getUuid(), 
            BanTypeEnum.VIOLATION_BAN.getCode(), "严重违规", null
        );
        
        J body = response.getBody();
        assertNotNull(body);
        assertEquals(0, body.getCode());
        
        // 验证用户状态
        UserBasicModel updatedUser = userBasicMapper.selectByUuid(testUser.getUuid());
        assertEquals(-2, updatedUser.getSafe_level());
    }
    
    @Test
    void testBanUser_DuplicateBan() {
        // 测试重复封号
        userBanService.banUser("admin-001", 1, testUser.getUuid(), 
            BanTypeEnum.TEMPORARY_BAN.getCode(), "违规发言", 7);
        
        ResponseEntity<J> response = userBanService.banUser(
            "admin-001", 1, testUser.getUuid(), 
            BanTypeEnum.TEMPORARY_BAN.getCode(), "再次违规", 7
        );
        
        J body = response.getBody();
        assertNotNull(body);
        assertEquals(1, body.getCode()); // 失败
    }
    
    @Test
    void testUnbanUser() {
        // 先封号
        userBanService.banUser("admin-001", 1, testUser.getUuid(), 
            BanTypeEnum.TEMPORARY_BAN.getCode(), "违规发言", 7);
        
        // 解封
        ResponseEntity<J> response = userBanService.unbanUser(
            "admin-001", testUser.getUuid(), "误封，已核实"
        );
        
        J body = response.getBody();
        assertNotNull(body);
        assertEquals(0, body.getCode());
        
        // 验证用户状态恢复
        UserBasicModel updatedUser = userBasicMapper.selectByUuid(testUser.getUuid());
        assertEquals(50, updatedUser.getSafe_level());
        assertNull(updatedUser.getSafe_level_before_ban());
    }
    
    @Test
    void testGetBanHistory() {
        // 创建封号记录
        userBanService.banUser("admin-001", 1, testUser.getUuid(), 
            BanTypeEnum.TEMPORARY_BAN.getCode(), "违规发言", 7);
        
        // 查询封号历史
        ResponseEntity<J> response = userBanService.getBanHistory(testUser.getUuid(), 1, 10);
        
        J body = response.getBody();
        assertNotNull(body);
        assertEquals(0, body.getCode());
    }
    
    @Test
    void testGetUserStatus() {
        // 封号用户
        userBanService.banUser("admin-001", 1, testUser.getUuid(), 
            BanTypeEnum.TEMPORARY_BAN.getCode(), "违规发言", 7);
        
        // 查询用户状态
        ResponseEntity<J> response = userBanService.getUserStatus(testUser.getUuid());
        
        J body = response.getBody();
        assertNotNull(body);
        assertEquals(0, body.getCode());
    }
}
```

- [ ] **Step 2: 提交**

```bash
git add BarcBackend/src/test/java/com/miaoyu/barc/user/service/UserBanServiceTest.java
git commit -m "test(测试): 添加用户封号服务测试用例

- 测试临时封号功能
- 测试违规封号功能
- 测试重复封号处理
- 测试解封功能
- 测试查询封号历史
- 测试查询用户状态"
```

---

## Task 12: 构建验证

- [ ] **Step 1: 运行后端构建**

```bash
cd BarcBackend
./mvnw clean package
```

Expected: BUILD SUCCESS

- [ ] **Step 2: 运行测试**

```bash
cd BarcBackend
./mvnw test
```

Expected: All tests pass

- [ ] **Step 3: 提交最终版本**

```bash
git add .
git commit -m "feat(系统): 完成用户封号功能

- 实现数据库迁移（V3）
- 实现封号类型枚举
- 实现封号记录模型和映射器
- 实现封号服务（封号、解封、查询）
- 实现封号控制器（RESTful API）
- 实现实时任务（自动解封）
- 实现登录拦截
- 添加单元测试
- 构建验证通过"
```

---

## 执行选项

**Plan complete and saved to `docs/superpowers/plans/2026-05-28-user-ban-plan.md`. Two execution options:**

**1. Subagent-Driven (recommended)** - I dispatch a fresh subagent per task, review between tasks, fast iteration

**2. Inline Execution** - Execute tasks in this session using executing-plans, batch execution with checkpoints

**Which approach?**
