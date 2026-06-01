# 用户封号功能设计

- **创建日期**: 2026-05-28
- **作用范围**: BarcBackend(后端 API + 数据库迁移)
- **不影响**: BarcFrontend(前台门户)、BarcManageFrontendV2(管理端 V2 界面)
- **状态**: 设计已确认,等待实施计划(writing-plans)

---

## 1. 背景与目标

用户安全等级(safe_level)字段已存在于用户表中，范围为-100到100。当前需要实现完整的封号功能，包括：
- 根据safe_level值判断用户状态
- 支持多种封号类型（风险冻结、临时封号、违规封号）
- 临时封号支持自动解封
- 封号记录审计功能
- 登录拦截与提示

### 受众
- **副馆长(ADMINISTRATOR=16)** 和 **馆长(ADVANCED_ADMINISTRATOR=32)** 可以使用封号功能
- 系统自动封号功能由后端定时任务执行
- 其他权限值不能执行封号操作

### 数据库环境
- 当前使用的数据库 **就是生产环境数据库**,严禁清库或破坏现有数据
- 新增封号记录表，不影响现有用户表数据

---

## 2. 关键决策(D1–D10)

按确认顺序登记,后续实施计划严格遵循。

| 编号 | 决策 | 备注 |
|---|---|---|
| **D1** | **safe_level范围** | -100到100，0及以下为封号状态 |
| **D2** | **封号类型映射** | 0=风险冻结，-1=临时封号，-2=违规封号，-3=软删除 |
| **D3** | **临时封号解封机制** | 恢复到封号前的safe_level值（方案A1） |
| **D4** | **封号记录表** | 新建user_ban_record表，记录所有封号/解封历史 |
| **D5** | **封号操作方式** | 支持管理员手动和系统自动两种方式 |
| **D6** | **权限控制** | 不是所有管理员都能封号，需检查权限等级 |
| **D7** | **登录拦截** | 封号后不能登录，提示封号类型 |
| **D8** | **解封机制** | 定时任务扫描临时封号记录，到期自动解封 |
| **D9** | **封号天数设置** | 支持预设选项（1天、3天、7天、30天）和自由输入（1-365天），前端下拉选择+自定义输入框 |
| **D10** | **Flyway迁移** | 使用Flyway管理数据库变更 |

---

## 3. 数据层设计

### 3.1 用户表扩展（student表）

```sql
-- 添加字段
ALTER TABLE student ADD COLUMN safe_level_before_ban INT DEFAULT NULL COMMENT '封号前的safe_level值，用于解封恢复';
```

**字段说明：**
- `safe_level_before_ban`: 记录封号前的safe_level值，解封时恢复使用
- 默认值为NULL，表示用户从未被封号

### 3.2 封号记录表（新建）

```sql
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

**字段说明：**
- `ban_type`: 封号类型，与safe_level值对应
- `ban_duration_days`: 临时封号的天数，其他类型为NULL
- `banned_at`: 封号时间
- `unbanned_at`: 解封时间，临时封号到期后自动填充
- `operator_id`: 操作人ID，系统自动封号时为"system"
- `operator_type`: 操作人类型，0=系统自动，1=管理员
- `safe_level_before_ban`: 封号前的safe_level值
- `safe_level_after_ban`: 封号后的safe_level值

---

## 4. 业务逻辑设计

### 4.1 封号类型与safe_level映射

| 封号类型 | ban_type | safe_level值 | 说明 |
|----------|----------|--------------|------|
| 风险冻结 | 0 | 0 | 账户存在风险，暂时冻结 |
| 临时封号 | 1 | -1 | 违规行为，临时封禁 |
| 违规封号 | 2 | -2 | 严重违规，永久封禁 |
| 软删除 | 3 | -3 | 账号已被删除 |

### 4.2 封号操作流程

```
1. 管理员/系统发起封号请求
   ↓
2. 验证操作权限（检查管理员职位权限）
   ↓
3. 查询用户当前safe_level
   ↓
4. 记录safe_level_before_ban
   ↓
5. 更新用户表：
   - safe_level_before_ban = 当前safe_level
   - safe_level = 目标封号类型对应的值
   ↓
6. 创建封号记录（user_ban_record）
   ↓
7. 如果是临时封号，计算解封时间
   ↓
8. 返回封号结果
```

### 4.3 解封流程（定时任务）

```
1. 定时任务每小时扫描user_ban_record表
   ↓
2. 查询条件：
   - ban_type = 1（临时封号）
   - unbanned_at IS NULL（未解封）
   - banned_at + ban_duration_days <= 当前时间（已到期）
   ↓
3. 对于每个到期记录：
   - 更新用户表：safe_level = safe_level_before_ban
   - 更新封号记录：unbanned_at = 当前时间
   ↓
4. 记录解封日志
```

### 4.4 系统自动封号规则

| 触发条件 | 封号类型 | 说明 |
|----------|----------|------|
| safe_level <= 0 | 风险冻结 | 自动冻结 |
| 连续输错密码5次 | 临时封号（1天） | 防止暴力破解 |
| 检测到异常登录 | 临时封号（3天） | 安全防护 |

**异常登录检测规则：**
- 短时间内（5分钟内）从不同IP地址登录
- 短时间内（10分钟内）从不同设备（User-Agent）登录
- 登录地点与常用地点距离超过500公里（基于IP地理位置）
- 使用已知的恶意IP地址登录（基于黑名单）

**检测实现方式：**
- 在登录成功后记录登录日志（IP、设备、时间）
- 定时任务扫描登录日志，检测异常模式
- 发现异常时自动触发临时封号

---

## 5. 权限控制设计

### 5.1 管理员权限等级

参考现有后端权限系统，封号操作需要以下权限：

| 权限等级 | 可执行操作 | 说明 |
|----------|------------|------|
| 超级管理员 | 所有封号操作 | 可封号、解封、查看历史 |
| 高级管理员 | 风险冻结、临时封号 | 不能执行违规封号 |
| 普通管理员 | 仅查看 | 不能执行封号操作 |

### 5.2 权限检查逻辑

```java
// 检查管理员是否有封号权限
public boolean hasBanPermission(String adminId, int banType) {
    // 查询管理员权限等级
    int adminLevel = getAdminLevel(adminId);
    
    // 根据封号类型检查权限
    switch (banType) {
        case 0: // 风险冻结
            return adminLevel >= 16; // 副馆长及以上
        case 1: // 临时封号
            return adminLevel >= 16; // 副馆长及以上
        case 2: // 违规封号
            return adminLevel >= 32; // 仅馆长
        default:
            return false;
    }
}
```

---

## 6. API设计

### 6.1 封号接口

```java
// POST /admin/ban/user
// 请求头：Authorization: Token {jwt}
// 请求体
{
    "userId": "用户ID",
    "banType": 1,  // 封号类型：0-风险冻结，1-临时封号，2-违规封号
    "reason": "封号原因",
    "durationDays": 7  // 封号天数（临时封号必填，其他类型忽略）
}

// 响应
{
    "code": 200,
    "message": "封号成功",
    "data": {
        "recordId": "封号记录ID",
        "unbanTime": "2026-06-04 10:00:00"  // 临时封号返回解封时间
    }
}
```

### 6.2 解封接口

```java
// POST /admin/unban/user
// 请求头：Authorization: Token {jwt}
// 请求体
{
    "userId": "用户ID",
    "reason": "解封原因"
}

// 响应
{
    "code": 200,
    "message": "解封成功",
    "data": {
        "safeLevel": 50  // 恢复后的safe_level值
    }
}
```

### 6.3 查询封号历史接口

```java
// GET /admin/ban/history?userId=xxx&page=1&size=10
// 请求头：Authorization: Token {jwt}

// 响应
{
    "code": 200,
    "data": {
        "total": 100,
        "list": [
            {
                "id": 1,
                "banType": 1,
                "banReason": "违规发言",
                "banDurationDays": 7,
                "bannedAt": "2026-05-28 10:00:00",
                "unbannedAt": "2026-06-04 10:00:00",
                "operatorId": "admin001",
                "operatorType": 1
            }
        ]
    }
}
```

### 6.4 查询用户当前状态接口

```java
// GET /admin/user/status?userId=xxx
// 请求头：Authorization: Token {jwt}

// 响应
{
    "code": 200,
    "data": {
        "userId": "用户ID",
        "safeLevel": -1,
        "safeLevelBeforeBan": 50,
        "banStatus": "临时封号",
        "banReason": "违规发言",
        "banTime": "2026-05-28 10:00:00",
        "unbanTime": "2026-06-04 10:00:00"
    }
}
```

---

## 7. 登录拦截设计

### 7.1 登录流程修改

```
1. 用户提交登录请求
   ↓
2. 验证账号密码
   ↓
3. 查询用户safe_level
   ↓
4. 判断safe_level值：
   - safe_level >= 0：正常登录
   - safe_level = 0：返回"账户风险冻结，请联系客服"
   - safe_level = -1：返回"账户临时封禁，解封时间：{unban_time}"
   - safe_level = -2：返回"账户因严重违规已被永久封禁"
   - safe_level = -3：返回"账户不存在"
   ↓
5. 如果被封号，查询user_ban_record获取封号详情
   ↓
6. 返回封号提示信息
```

### 7.2 登录响应示例

```java
// 正常登录响应
{
    "code": 200,
    "message": "登录成功",
    "data": {
        "token": "jwt_token",
        "user": { ... }
    }
}

// 封号登录响应
{
    "code": 403,
    "message": "账户临时封禁，解封时间：2026-06-04 10:00:00",
    "data": {
        "banType": 1,
        "banReason": "违规发言",
        "banTime": "2026-05-28 10:00:00",
        "unbanTime": "2026-06-04 10:00:00"
    }
}
```

---

## 8. 定时任务设计

### 8.1 解封定时任务

```java
@Scheduled(fixedRate = 3600000) // 每小时执行一次
public void unbanExpiredUsers() {
    // 查询已到期的临时封号记录
    List<UserBanRecord> expiredRecords = userBanRecordMapper.selectExpiredRecords();
    
    for (UserBanRecord record : expiredRecords) {
        // 恢复用户safe_level
        studentMapper.updateSafeLevel(record.getUserId(), record.getSafeLevelBeforeBan());
        
        // 更新封号记录
        userBanRecordMapper.updateUnbannedAt(record.getId(), new Date());
        
        // 记录日志
        log.info("用户{}已自动解封，恢复safe_level为{}", record.getUserId(), record.getSafeLevelBeforeBan());
    }
}
```

### 8.2 查询SQL

```xml
<!-- 查询已到期的临时封号记录 -->
<select id="selectExpiredRecords" resultType="UserBanRecord">
    SELECT * FROM user_ban_record
    WHERE ban_type = 1
    AND unbanned_at IS NULL
    AND DATE_ADD(banned_at, INTERVAL ban_duration_days DAY) <= NOW()
</select>
```

---

## 9. 测试用例

### 9.1 封号功能测试

| 测试场景 | 输入 | 预期输出 |
|----------|------|----------|
| 管理员封号 | banType=1, durationDays=7 | 封号成功，返回unbanTime |
| 权限不足 | 普通管理员封号 | 返回权限不足错误 |
| 临时封号解封 | 到期自动解封 | safe_level恢复到封号前值 |
| 违规封号 | banType=2 | 永久封禁，无解封时间 |
| 重复封号 | 已封号用户再次封号 | 返回用户已被封号错误 |

### 9.2 登录拦截测试

| 测试场景 | 用户safe_level | 预期输出 |
|----------|----------------|----------|
| 正常用户 | 50 | 登录成功 |
| 风险冻结 | 0 | 返回"账户风险冻结，请联系客服" |
| 临时封号 | -1 | 返回"账户临时封禁，解封时间：xxx" |
| 违规封号 | -2 | 返回"账户因严重违规已被永久封禁" |
| 软删除 | -3 | 返回"账户不存在" |

---

## 10. 风险与注意事项

### 10.1 数据安全
- 严禁直接删除用户数据
- 封号操作必须记录审计日志
- 定时任务必须有异常处理和重试机制

### 10.2 性能考虑
- 封号记录表需要合理索引
- 定时任务查询需要分批处理
- 登录拦截查询需要缓存优化

### 10.3 兼容性
- 现有用户表结构不变，只新增字段
- 新增封号记录表不影响现有功能
- 登录接口响应格式保持兼容

---

**设计完成日期**: 2026-05-28
**设计状态**: 设计已确认,等待实施计划
