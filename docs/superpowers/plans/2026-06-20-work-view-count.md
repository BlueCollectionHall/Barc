# Work View Count Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 为作品详情接口增加按日去重、可保留历史的浏览量入账能力，并继续通过 `work.view_count` 提供累计总浏览量。

**Architecture:** 后端新增 `work_view_log` 作为“成功入账浏览”明细表，使用 `dedupe_key + view_date + work_id` 唯一键完成日粒度去重。作品详情成功返回前同步调用 `WorkViewService` 尝试写入浏览明细与递增 `work.view_count`，写入失败仅记录日志，不影响详情响应。

**Tech Stack:** Java 17、Spring Boot、MyBatis、Flyway、JUnit 5、Mockito、Jakarta Servlet API

---

## 文件结构与职责

### 后端新增文件

- `BarcBackend/src/main/resources/db/migration/V5__add_work_view_log_table.sql`
  - 浏览明细表 migration，仅新增 `work_view_log`
- `BarcBackend/src/main/java/com/miaoyu/barc/api/work/model/WorkViewLogModel.java`
  - 浏览明细模型，映射 `work_view_log`
- `BarcBackend/src/main/java/com/miaoyu/barc/api/work/service/WorkViewService.java`
  - 浏览量业务入口：身份识别、去重写入、计数增长、失败降级
- `BarcBackend/src/main/java/com/miaoyu/barc/api/work/mapper/WorkViewLogMapper.java`
  - 浏览明细最小持久化接口
- `BarcBackend/src/main/java/com/miaoyu/barc/config/ClockConfig.java`
  - 提供可注入 `Clock`，让 UTC+8 日切逻辑可测
- `BarcBackend/src/main/java/com/miaoyu/barc/utils/web/ClientIpInfo.java`
  - 结构化的 IP 解析结果
- `BarcBackend/src/main/java/com/miaoyu/barc/utils/web/ClientIpResolver.java`
  - 真实客户端 IP 提取、规范化、哈希
- `BarcBackend/src/test/java/com/miaoyu/barc/api/work/service/WorkViewServiceTest.java`
  - 浏览量入账服务单元测试
- `BarcBackend/src/test/java/com/miaoyu/barc/utils/web/ClientIpResolverTest.java`
  - IP 解析与规范化测试
- `BarcBackend/src/test/java/com/miaoyu/barc/api/work/service/WorkServiceViewCountTest.java`
  - 详情服务接入浏览量后的行为测试

### 后端修改文件

- `BarcBackend/src/main/resources/schema.sql`
  - 补充 `work_view_log` 建表语句
- `BarcBackend/src/main/java/com/miaoyu/barc/api/work/mapper/WorkMapper.java`
  - 新增 `incrementViewCount(workId)`
- `BarcBackend/src/main/java/com/miaoyu/barc/api/work/service/WorkService.java`
  - 详情成功路径中接入 `WorkViewService`
- `BarcBackend/src/test/java/com/miaoyu/barc/api/work/service/WorkServiceLikeStateTest.java`
  - 为新增依赖补 `@Mock WorkViewService`，保持既有详情测试稳定

### 参考文件

- `BarcBackend/src/main/java/com/miaoyu/barc/api/work/service/WorkLikeService.java`
  - `DuplicateKeyException` 风格参考
- `BarcBackend/src/test/java/com/miaoyu/barc/api/work/service/WorkLikeServiceTest.java`
  - 作品领域服务测试风格参考
- `BarcBackend/src/test/java/com/miaoyu/barc/api/work/service/WorkServiceLikeStateTest.java`
  - 详情服务测试与 `MockHttpServletRequest` 用法参考

---

## Task 1: 建立浏览量 TDD 测试骨架

**Files:**
- Create: `BarcBackend/src/test/java/com/miaoyu/barc/api/work/service/WorkViewServiceTest.java`
- Create: `BarcBackend/src/test/java/com/miaoyu/barc/utils/web/ClientIpResolverTest.java`
- Create: `BarcBackend/src/test/java/com/miaoyu/barc/api/work/service/WorkServiceViewCountTest.java`
- Reference: `BarcBackend/src/test/java/com/miaoyu/barc/api/work/service/WorkLikeServiceTest.java`
- Reference: `BarcBackend/src/test/java/com/miaoyu/barc/api/work/service/WorkServiceLikeStateTest.java`

- [ ] **Step 1: 写 `ClientIpResolverTest` 的失败用例**

```java
@Test
@DisplayName("X-Real-IP 优先于 X-Forwarded-For")
void resolve_WhenXRealIpPresent_ShouldPreferIt() {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader("X-Real-IP", "203.0.113.9");
    request.addHeader("X-Forwarded-For", "198.51.100.10");

    ClientIpInfo info = resolver.resolve(request);

    assertNotNull(info);
    assertEquals("IPV4", info.viewerType());
    assertEquals("203.0.113.9", info.normalizedIp());
}

@Test
@DisplayName("IPv4-mapped IPv6 应折叠为 IPv4")
void resolve_WhenIpv4MappedIpv6_ShouldCollapseToIpv4() {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setRemoteAddr("::ffff:127.0.0.1");

    ClientIpInfo info = resolver.resolve(request);

    assertNotNull(info);
    assertEquals("IPV4", info.viewerType());
    assertEquals("127.0.0.1", info.normalizedIp());
}

@Test
@DisplayName("非法 IP 应返回空结果")
void resolve_WhenInvalidIp_ShouldReturnNull() {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader("X-Real-IP", "unknown");
    request.setRemoteAddr("not-an-ip");

    assertNull(resolver.resolve(request));
}
```

- [ ] **Step 2: 写 `WorkViewServiceTest` 的失败用例**

```java
@Test
@DisplayName("匿名首次访问应写入明细并增加浏览量")
void recordViewIfNeeded_WhenAnonymousFirstVisit_ShouldInsertLogAndIncrementCount() {
    when(clientIpResolver.resolve(request)).thenReturn(ipv4("198.51.100.8", HASH_A));
    when(workViewLogMapper.insert(any(WorkViewLogModel.class))).thenReturn(1);
    when(workMapper.incrementViewCount("work-1")).thenReturn(1);

    workViewService.recordViewIfNeeded(request, publicWork("work-1", "author-1"));

    verify(workViewLogMapper).insert(any(WorkViewLogModel.class));
    verify(workMapper).incrementViewCount("work-1");
}

@Test
@DisplayName("匿名重复访问命中唯一键时不应增加浏览量")
void recordViewIfNeeded_WhenDuplicateAnonymousVisit_ShouldSkipIncrement() {
    when(clientIpResolver.resolve(request)).thenReturn(ipv4("198.51.100.8", HASH_A));
    when(workViewLogMapper.insert(any(WorkViewLogModel.class)))
            .thenThrow(new DuplicateKeyException("duplicate"));

    workViewService.recordViewIfNeeded(request, publicWork("work-1", "author-1"));

    verify(workMapper, never()).incrementViewCount("work-1");
}

@Test
@DisplayName("登录首次访问应按 UUID 入账")
void recordViewIfNeeded_WhenLoggedInFirstVisit_ShouldInsertUserViewLog() {
    request.addHeader("Authorization", "valid-jwt");
    when(jwtService.jwtParser("valid-jwt")).thenReturn(new J(0, "ok", "user-1"));
    when(workViewLogMapper.insert(any(WorkViewLogModel.class))).thenReturn(1);
    when(workMapper.incrementViewCount("work-1")).thenReturn(1);

    workViewService.recordViewIfNeeded(request, publicWork("work-1", "author-1"));

    verify(workViewLogMapper).insert(argThat(model -> "USER:user-1".equals(model.getDedupe_key())));
    verifyNoInteractions(clientIpResolver);
}

@Test
@DisplayName("登录重复访问命中唯一键时不应增加浏览量")
void recordViewIfNeeded_WhenLoggedInDuplicateVisit_ShouldSkipIncrement() {
    request.addHeader("Authorization", "valid-jwt");
    when(jwtService.jwtParser("valid-jwt")).thenReturn(new J(0, "ok", "user-1"));
    when(workViewLogMapper.insert(any(WorkViewLogModel.class))).thenThrow(new DuplicateKeyException("duplicate"));

    workViewService.recordViewIfNeeded(request, publicWork("work-1", "author-1"));

    verify(workMapper, never()).incrementViewCount("work-1");
}

@Test
@DisplayName("同一匿名 IP 跨日访问应重新入账")
void recordViewIfNeeded_WhenAnonymousVisitsOnNextDay_ShouldUseNewViewDate() {
    when(clientIpResolver.resolve(request)).thenReturn(ipv4("198.51.100.8", HASH_A));
    when(workViewLogMapper.insert(any(WorkViewLogModel.class))).thenReturn(1);
    when(workMapper.incrementViewCount("work-1")).thenReturn(1);
    when(clock.instant()).thenReturn(Instant.parse("2026-06-20T15:59:59Z"), Instant.parse("2026-06-20T16:00:01Z"));
    when(clock.getZone()).thenReturn(ZoneOffset.UTC);

    workViewService.recordViewIfNeeded(request, publicWork("work-1", "author-1"));
    workViewService.recordViewIfNeeded(request, publicWork("work-1", "author-1"));

    verify(workViewLogMapper, times(2)).insert(any(WorkViewLogModel.class));
}

@Test
@DisplayName("匿名后登录同日访问应允许再次入账")
void recordViewIfNeeded_WhenAnonymousThenLoginSameDay_ShouldCountTwice() {
    when(clientIpResolver.resolve(anonymousRequest)).thenReturn(ipv4("198.51.100.8", HASH_A));
    when(workViewLogMapper.insert(any(WorkViewLogModel.class))).thenReturn(1);
    when(workMapper.incrementViewCount("work-1")).thenReturn(1);
    loggedInRequest.addHeader("Authorization", "valid-jwt");
    when(jwtService.jwtParser("valid-jwt")).thenReturn(new J(0, "ok", "user-1"));

    workViewService.recordViewIfNeeded(anonymousRequest, publicWork("work-1", "author-1"));
    workViewService.recordViewIfNeeded(loggedInRequest, publicWork("work-1", "author-1"));

    verify(workMapper, times(2)).incrementViewCount("work-1");
}

@Test
@DisplayName("匿名无合法 IP 时应跳过")
void recordViewIfNeeded_WhenAnonymousIpMissing_ShouldSkip() {
    when(clientIpResolver.resolve(request)).thenReturn(null);

    workViewService.recordViewIfNeeded(request, publicWork("work-1", "author-1"));

    verifyNoInteractions(workViewLogMapper);
    verify(workMapper, never()).incrementViewCount(anyString());
}

@Test
@DisplayName("作者本人访问自己的作品不应计数")
void recordViewIfNeeded_WhenAuthorViewsOwnWork_ShouldSkip() {
    when(jwtService.jwtParser("valid-jwt")).thenReturn(new J(0, "ok", "author-1"));
    request.addHeader("Authorization", "valid-jwt");

    workViewService.recordViewIfNeeded(request, publicWork("work-1", "author-1"));

    verifyNoInteractions(workViewLogMapper, clientIpResolver);
    verify(workMapper, never()).incrementViewCount(anyString());
}

@Test
@DisplayName("浏览量写入异常不应向外抛出")
void recordViewIfNeeded_WhenInsertFails_ShouldSwallowFailure() {
    when(clientIpResolver.resolve(request)).thenReturn(ipv4("198.51.100.8", HASH_A));
    when(workViewLogMapper.insert(any(WorkViewLogModel.class))).thenThrow(new RuntimeException("db down"));

    assertDoesNotThrow(() -> workViewService.recordViewIfNeeded(request, publicWork("work-1", "author-1")));
    verify(workMapper, never()).incrementViewCount("work-1");
}
```

- [ ] **Step 3: 写 `WorkServiceViewCountTest` 的失败用例**

```java
@Test
@DisplayName("公开详情成功返回时应尝试记录浏览")
void getWorksByIdService_WhenPublicWorkAccessible_ShouldRecordViewBeforeReturn() {
    when(workMapper.selectById("work-1")).thenReturn(publicWork);
    when(workCoverImageMapper.selectByWorkId("work-1")).thenReturn(coverImage);
    when(cosService.generateSignedUrl(anyString(), any(Date.class), eq(CosBucketConfigEnum.image)))
            .thenReturn("https://signed.example/cover.png");

    ResponseEntity<J> response = workService.getWorksByIdService(new MockHttpServletRequest(), "work-1");

    assertEquals(0, response.getBody().getCode());
    verify(workViewService).recordViewIfNeeded(any(HttpServletRequest.class), any(WorkModel.class));
}

@Test
@DisplayName("非公开作品被状态拦截时不应记录浏览")
void getWorksByIdService_WhenWorkBanned_ShouldNotRecordView() {
    when(workMapper.selectById("work-1")).thenReturn(bannedWork);

    ResponseEntity<J> response = workService.getWorksByIdService(new MockHttpServletRequest(), "work-1");

    assertEquals(1, response.getBody().getCode());
    verifyNoInteractions(workViewService);
}

@Test
@DisplayName("浏览量服务意外抛出异常时详情仍应成功返回")
void getWorksByIdService_WhenViewRecordingThrows_ShouldStillReturnSuccess() {
    when(workMapper.selectById("work-1")).thenReturn(publicWork);
    when(workCoverImageMapper.selectByWorkId("work-1")).thenReturn(coverImage);
    when(cosService.generateSignedUrl(anyString(), any(Date.class), eq(CosBucketConfigEnum.image)))
            .thenReturn("https://signed.example/cover.png");
    doThrow(new RuntimeException("unexpected")).when(workViewService)
            .recordViewIfNeeded(any(HttpServletRequest.class), any(WorkModel.class));

    ResponseEntity<J> response = workService.getWorksByIdService(new MockHttpServletRequest(), "work-1");

    assertEquals(0, response.getBody().getCode());
}
```

- [ ] **Step 4: 运行测试确认 RED**

Run: `./mvnw.cmd -Dtest=ClientIpResolverTest,WorkViewServiceTest,WorkServiceViewCountTest test`

Workdir: `BarcBackend`

Expected:
- 测试失败
- 报错缺少 `WorkViewService` / `ClientIpResolver` / `WorkViewLogModel` / `incrementViewCount` 等实现

---

## Task 2: 建立数据库契约与最小模型

**Files:**
- Create: `BarcBackend/src/main/resources/db/migration/V5__add_work_view_log_table.sql`
- Create: `BarcBackend/src/main/java/com/miaoyu/barc/api/work/model/WorkViewLogModel.java`
- Modify: `BarcBackend/src/main/resources/schema.sql`
- Test: `BarcBackend/src/test/java/com/miaoyu/barc/api/work/service/WorkViewServiceTest.java`

- [ ] **Step 1: 写 migration 与 schema**

```sql
CREATE TABLE IF NOT EXISTS work_view_log (
    id VARCHAR(36) PRIMARY KEY NOT NULL,
    work_id VARCHAR(100) NOT NULL,
    view_date DATE NOT NULL,
    viewer_type VARCHAR(16) NOT NULL,
    viewer_user_uuid VARCHAR(32) DEFAULT NULL,
    viewer_ipv4 VARCHAR(15) DEFAULT NULL,
    viewer_ipv6 VARCHAR(39) DEFAULT NULL,
    viewer_ip_hash CHAR(64) DEFAULT NULL,
    dedupe_key VARCHAR(80) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_work_view_log_daily (work_id, view_date, dedupe_key),
    INDEX idx_work_view_log_work_date (work_id, view_date),
    INDEX idx_work_view_log_date (view_date),
    INDEX idx_work_view_log_user_uuid (viewer_user_uuid),
    INDEX idx_work_view_log_ip_hash (viewer_ip_hash)
);
```

- [ ] **Step 2: 创建最小模型**

```java
@Getter
@Setter
public class WorkViewLogModel {
    private String id;
    private String work_id;
    private LocalDate view_date;
    private String viewer_type;
    private String viewer_user_uuid;
    private String viewer_ipv4;
    private String viewer_ipv6;
    private String viewer_ip_hash;
    private String dedupe_key;
    private String created_at;
}
```

- [ ] **Step 3: 自检数据库契约**

检查点：
- `work_id` 类型与 `work.id` 一致
- `viewer_user_uuid` 类型与 `user_basic.uuid` 一致但不加外键
- 唯一键为 `(work_id, view_date, dedupe_key)`
- 不修改既有 `work.view_count` 列
- 不增加历史回填 SQL

---

## Task 3: 实现 IP 提取与规范化

**Files:**
- Create: `BarcBackend/src/main/java/com/miaoyu/barc/config/ClockConfig.java`
- Create: `BarcBackend/src/main/java/com/miaoyu/barc/utils/web/ClientIpInfo.java`
- Create: `BarcBackend/src/main/java/com/miaoyu/barc/utils/web/ClientIpResolver.java`
- Test: `BarcBackend/src/test/java/com/miaoyu/barc/utils/web/ClientIpResolverTest.java`

- [ ] **Step 0: 提供可注入 `Clock` Bean**

```java
@Configuration
public class ClockConfig {
    @Bean
    public Clock systemClock() {
        return Clock.systemUTC();
    }
}
```

- [ ] **Step 1: 实现最小结果结构**

```java
public record ClientIpInfo(String viewerType, String normalizedIp, String ipHash) {}
```

- [ ] **Step 2: 实现优先级解析**

逻辑顺序：
1. 取 `X-Real-IP`
2. 为空再取 `X-Forwarded-For`
3. 再回退 `request.getRemoteAddr()`
4. 过滤空白值、`unknown`
5. 取多值头中的第一个地址

- [ ] **Step 3: 实现规范化与哈希**

```java
InetAddress address = InetAddress.getByName(rawIp);
String normalized = normalize(address);
String hash = sha256Hex(normalized);
```

要求：
- IPv4 原样规范化
- IPv6 使用标准库标准表示
- IPv4-mapped IPv6 折叠成 IPv4
- 哈希输出 64 位小写十六进制

- [ ] **Step 4: 运行解析测试确认 GREEN**

Run: `./mvnw.cmd -Dtest=ClientIpResolverTest test`

Workdir: `BarcBackend`

Expected: PASS

---

## Task 4: 实现浏览明细写入与主表累计增长

**Files:**
- Create: `BarcBackend/src/main/java/com/miaoyu/barc/api/work/mapper/WorkViewLogMapper.java`
- Create: `BarcBackend/src/main/java/com/miaoyu/barc/api/work/service/WorkViewService.java`
- Modify: `BarcBackend/src/main/java/com/miaoyu/barc/api/work/mapper/WorkMapper.java`
- Test: `BarcBackend/src/test/java/com/miaoyu/barc/api/work/service/WorkViewServiceTest.java`

- [ ] **Step 0: 给 `WorkViewServiceTest` 补 `@Mock Clock` 并固定时间**

```java
@Mock
private Clock clock;

when(clock.instant()).thenReturn(Instant.parse("2026-06-20T12:00:00Z"));
when(clock.getZone()).thenReturn(ZoneOffset.UTC);
```

- [ ] **Step 1: 先补最小 Mapper 接口**

```java
// WorkViewLogMapper
@Insert("""
INSERT INTO work_view_log
    (id, work_id, view_date, viewer_type, viewer_user_uuid, viewer_ipv4, viewer_ipv6, viewer_ip_hash, dedupe_key)
VALUES
    (#{id}, #{work_id}, #{view_date}, #{viewer_type}, #{viewer_user_uuid}, #{viewer_ipv4}, #{viewer_ipv6}, #{viewer_ip_hash}, #{dedupe_key})
""")
int insert(WorkViewLogModel model);

// WorkMapper
@Update("UPDATE work SET view_count = COALESCE(view_count, 0) + 1 WHERE id = #{work_id}")
int incrementViewCount(@Param("work_id") String workId);
```

- [ ] **Step 2: 实现 `WorkViewService.recordViewIfNeeded(...)` 的最小逻辑**

逻辑顺序：
1. `work == null` 直接返回
2. 尝试从 `Authorization` 解析 UUID
3. 若 `uuid == work.author`，直接返回
4. 有 UUID 时生成 `USER:<uuid>`
5. 无 UUID 时调用 `clientIpResolver.resolve(request)`
6. 匿名无合法 IP 时直接返回
7. 使用可注入 `Clock` 计算 `view_date`（UTC+8）
8. 插入 `work_view_log`
9. 插入成功后执行 `incrementViewCount(workId)`
10. `DuplicateKeyException` 视为正常重复访问
11. 其他异常只记日志，不向外抛

- [ ] **Step 3: 让插入与计数更新处于同一事务内**

```java
public void recordViewIfNeeded(...) {
    try {
        transactionTemplate.executeWithoutResult(status -> {
            workViewLogMapper.insert(model);
            int updated = workMapper.incrementViewCount(work.getId());
            if (updated <= 0) {
                throw new IllegalStateException("Failed to increment work view count");
            }
        });
    } catch (DuplicateKeyException ignored) {
        // duplicate daily view, skip silently
    } catch (Exception e) {
        log.error("Failed to record work view", e);
    }
}
```

这样可以避免同类自调用导致的事务失效问题。

- [ ] **Step 4: 运行浏览量服务测试确认 GREEN**

Run: `./mvnw.cmd -Dtest=WorkViewServiceTest test`

Workdir: `BarcBackend`

Expected: PASS

---

## Task 5: 将浏览量接入作品详情成功链路

**Files:**
- Modify: `BarcBackend/src/main/java/com/miaoyu/barc/api/work/service/WorkService.java`
- Modify: `BarcBackend/src/test/java/com/miaoyu/barc/api/work/service/WorkServiceLikeStateTest.java`
- Test: `BarcBackend/src/test/java/com/miaoyu/barc/api/work/service/WorkServiceViewCountTest.java`
- Reference: `BarcBackend/src/test/java/com/miaoyu/barc/api/work/service/WorkServiceLikeStateTest.java`

- [ ] **Step 0: 给既有 `WorkServiceLikeStateTest` 补 `@Mock WorkViewService`**

```java
@Mock
private WorkViewService workViewService;
```

- [ ] **Step 1: 在 `WorkService` 中注入 `WorkViewService`**

```java
@Autowired
private WorkViewService workViewService;
```

- [ ] **Step 2: 仅在详情成功返回前尝试记录浏览**

```java
work.setCover_image(signedCoverImageUrl);
work.setLiked_by_current_user(isLikedByCurrentUser(request, workId));
try {
    workViewService.recordViewIfNeeded(request, work);
} catch (Exception e) {
    log.error("Unexpected work view recording failure", e);
}
yield ResponseEntity.ok(new ResourceR().resourceSuch(true, work));
```

注意：
- 状态拦截分支不调用浏览量服务
- 封面缺失返回错误时不调用浏览量服务

- [ ] **Step 3: 运行详情接入测试确认 GREEN**

Run: `./mvnw.cmd -Dtest=WorkServiceViewCountTest test`

Workdir: `BarcBackend`

Expected: PASS

---

## Task 6: 扩大验证并清理实现

**Files:**
- Test: `BarcBackend/src/test/java/com/miaoyu/barc/api/work/service/WorkLikeServiceTest.java`
- Test: `BarcBackend/src/test/java/com/miaoyu/barc/api/work/service/WorkServiceLikeStateTest.java`
- Test: `BarcBackend/src/test/java/com/miaoyu/barc/api/work/service/WorkViewServiceTest.java`
- Test: `BarcBackend/src/test/java/com/miaoyu/barc/api/work/service/WorkServiceViewCountTest.java`
- Test: `BarcBackend/src/test/java/com/miaoyu/barc/utils/web/ClientIpResolverTest.java`

- [ ] **Step 1: 运行目标测试集**

Run: `./mvnw.cmd -Dtest=WorkLikeServiceTest,WorkServiceLikeStateTest,WorkViewServiceTest,WorkServiceViewCountTest,ClientIpResolverTest test`

Workdir: `BarcBackend`

Expected: PASS

- [ ] **Step 2: 运行编译验证**

Run: `./mvnw.cmd -q -DskipTests compile`

Workdir: `BarcBackend`

Expected: PASS

- [ ] **Step 3: 运行诊断并清理命名/重复代码**

检查点：
- 新增类命名与现有 `work_like` 风格一致
- 未引入 `as any` / 忽略错误式处理（Java 中对应无空 catch）
- 日志只在真正异常时输出
- 未改动无关接口或前端代码

- [ ] **Step 4: 记录未完成项（若有）**

仅在存在未做能力时记录，例如：
- 机器人过滤
- 浏览量看板接口
- 明细长期归档策略

---

## 交付检查清单

- [ ] `work_view_log` migration 与 `schema.sql` 一致
- [ ] 详情成功路径能触发浏览量记录
- [ ] 匿名按 IP 去重，登录按 UUID 去重
- [ ] 匿名后登录同日可以再次入账
- [ ] 作者本人访问不计数
- [ ] 浏览量写入失败不影响详情接口返回
- [ ] 目标测试全部通过
- [ ] 编译通过
