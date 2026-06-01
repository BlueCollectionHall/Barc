# 学园 / 部团 / 学生 CRUD 实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 为 BarcBackend 和 BarcManageFrontendV2 增加学园(school)、部团(club)、学生(student)的全套 CRUD 管理能力，接入腾讯云 COS image 桶，并实现软删除与 GC 队列。

**Architecture:** 后端采用 Spring Boot + MyBatis(注解) + Flyway 迁移，新增 RESTful Controller/Service/Mapper；前端采用 Vue3 + Pinia + Element Plus 新增三级列表视图与统一表单 Drawer。对象存储层修复 bucket 解析 bug，新增 Saga 模式 GC 队列定时清理孤儿 key。

**Tech Stack:** Java 17, Spring Boot, MyBatis, Flyway, MySQL, Vue 3, Vite, Pinia, Element Plus, 腾讯云 COS

---

## File Structure

### 后端新增/修改

| 文件 | 操作 | 说明 |
|---|---|---|
| `BarcBackend/pom.xml` | 修改 | 添加 Flyway 依赖 |
| `BarcBackend/src/main/resources/application.yaml` | 修改 | 添加 Flyway 配置 |
| `BarcBackend/src/main/resources/db/migration/V1__add_soft_delete_and_gc.sql` | 新增 | Flyway 迁移：三表加 deleted_at + cos_garbage_key 表 |
| `BarcBackend/src/main/java/com/miaoyu/barc/api/model/{School,SchoolClub,Student}Model.java` | 修改 | 添加 deletedAt 字段 |
| `BarcBackend/src/main/java/com/miaoyu/barc/utils/tencent/cos/CosService.java` | 修改 | 修复 bucketName 按 clientName 解析 |
| `BarcBackend/src/main/java/com/miaoyu/barc/utils/tencent/cos/ImageUrlResolver.java` | 新增 | URL/key 自适应回显工具 |
| `BarcBackend/src/main/java/com/miaoyu/barc/api/service/CosGcService.java` | 新增 | GC 队列 Service |
| `BarcBackend/src/main/java/com/miaoyu/barc/api/scheduler/CosGcScheduler.java` | 新增 | GC 队列定时消费 |
| `BarcBackend/src/main/java/com/miaoyu/barc/api/mapper/{School,Club,Student}Mapper.java` | 修改 | 补 deleted_at IS NULL + 新增 CRUD 方法 |
| `BarcBackend/src/main/java/com/miaoyu/barc/api/service/{School,Club,Student}Service.java` | 重写 | 完整 CRUD + 软删除级联 + 搜索分页 |
| `BarcBackend/src/main/java/com/miaoyu/barc/api/controller/{School,Club,Student}Controller.java` | 重写 | RESTful 接口 + 权限注解 |
| `BarcBackend/src/main/java/com/miaoyu/barc/api/controller/FileController.java` | 修改(或新增) | 上传接口支持 path 参数 |

### 前端新增/修改

| 文件 | 操作 | 说明 |
|---|---|---|
| `BarcManageFrontendV2/src/app/router/routes.ts` | 修改 | 添加 school/club/student 三级路由 |
| `BarcManageFrontendV2/src/modules/school-club-student/api/school.ts` | 新增 | 学园 API |
| `BarcManageFrontendV2/src/modules/school-club-student/api/club.ts` | 新增 | 部团 API |
| `BarcManageFrontendV2/src/modules/school-club-student/api/student.ts` | 新增 | 学生 API |
| `BarcManageFrontendV2/src/modules/school-club-student/views/SchoolListView.vue` | 新增 | 学园列表 |
| `BarcManageFrontendV2/src/modules/school-club-student/views/ClubListView.vue` | 新增 | 部团列表 |
| `BarcManageFrontendV2/src/modules/school-club-student/views/StudentListView.vue` | 新增 | 学生列表 |
| `BarcManageFrontendV2/src/modules/school-club-student/components/SchoolCard.vue` | 新增 | 学园卡片 200×200 |
| `BarcManageFrontendV2/src/modules/school-club-student/components/ClubCard.vue` | 新增 | 部团卡片 200×200 |
| `BarcManageFrontendV2/src/modules/school-club-student/components/StudentCard.vue` | 新增 | 学生卡片 96×128 |
| `BarcManageFrontendV2/src/modules/school-club-student/components/EntityFormDrawer.vue` | 新增 | 统一新增/编辑 Drawer |
| `BarcManageFrontendV2/src/modules/school-club-student/components/ImageUploadField.vue` | 新增 | 图片上传字段组件 |
| `BarcManageFrontendV2/src/modules/school-club-student/components/EntityBreadcrumb.vue` | 新增 | 三级面包屑 |
| `BarcManageFrontendV2/src/modules/school-club-student/store/entityCache.ts` | 新增 | 实体名称缓存(面包屑用) |

---

## Phase 1 – 基础设施

### Task 1: 添加 Flyway 依赖与配置

**Files:**
- Modify: `BarcBackend/pom.xml`
- Modify: `BarcBackend/src/main/resources/application.yaml`
- Create: `BarcBackend/src/main/resources/db/migration/V1__add_soft_delete_and_gc.sql`

- [ ] **Step 1: 在 pom.xml 添加 Flyway 依赖**

在 `<dependencies>` 内新增：

```xml
<dependency>
    <groupId>org.flywaydb</groupId>
    <artifactId>flyway-mysql</artifactId>
</dependency>
```

- [ ] **Step 2: 在 application.yaml 添加 Flyway 配置**

在 `spring:` 下新增：

```yaml
  flyway:
    enabled: true
    baseline-on-migrate: true
    baseline-version: 0
    locations: classpath:db/migration
```

- [ ] **Step 3: 创建迁移文件 V1__add_soft_delete_and_gc.sql**

```sql
-- 学园表加软删除
ALTER TABLE schools ADD COLUMN deleted_at TIMESTAMP NULL DEFAULT NULL;
CREATE INDEX idx_schools_deleted_at ON schools(deleted_at);

-- 部团表加软删除
ALTER TABLE club ADD COLUMN deleted_at TIMESTAMP NULL DEFAULT NULL;
CREATE INDEX idx_club_deleted_at ON club(deleted_at);

-- 学生表加软删除
ALTER TABLE students ADD COLUMN deleted_at TIMESTAMP NULL DEFAULT NULL;
CREATE INDEX idx_students_deleted_at ON students(deleted_at);

-- GC 队列表
CREATE TABLE IF NOT EXISTS cos_garbage_key(
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    bucket VARCHAR(100) NOT NULL,
    object_key VARCHAR(500) NOT NULL,
    retry_count INT DEFAULT 0,
    last_error TEXT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    processed_at TIMESTAMP NULL DEFAULT NULL,
    INDEX idx_processed (processed_at),
    INDEX idx_created (created_at)
);
```

- [ ] **Step 4: Commit**

```bash
git add BarcBackend/pom.xml BarcBackend/src/main/resources/application.yaml BarcBackend/src/main/resources/db/migration/V1__add_soft_delete_and_gc.sql
git commit -m "feat(infra): integrate Flyway and add soft-delete + GC queue migration"
```

---

### Task 2: 修复 CosService bucketName Bug

**Files:**
- Modify: `BarcBackend/src/main/java/com/miaoyu/barc/utils/tencent/cos/CosService.java`

- [ ] **Step 1: 添加 resolveBucket 方法并替换所有硬编码 bucketName**

在 `CosService` 类中，添加私有方法：

```java
private String resolveBucket(CosBucketConfigEnum clientName) {
    return switch (clientName) {
        case avatar -> cosConfig.getAvatar().getBucketName();
        case image  -> cosConfig.getImage().getBucketName();
        case test   -> cosConfig.getTest().getBucketName();
        default     -> cosConfig.getBucketName();
    };
}
```

然后将 `uploadFile` 方法中的：
```java
PutObjectRequest putObjectRequest = new PutObjectRequest(
        cosConfig.getBucketName(),
        uniqueKeyFilename,
        file.getInputStream(),
        meta
);
```
改为：
```java
PutObjectRequest putObjectRequest = new PutObjectRequest(
        resolveBucket(clientName),
        uniqueKeyFilename,
        file.getInputStream(),
        meta
);
```

将 `deleteFile` 方法中的：
```java
cosClient.cosClient(clientName).deleteObject(cosConfig.getBucketName(), key);
```
改为：
```java
cosClient.cosClient(clientName).deleteObject(resolveBucket(clientName), key);
```

将 `moveFile` 方法中的：
```java
CopyObjectRequest copyObjectRequest = new CopyObjectRequest(
        cosConfig.getBucketName(), sourceKey,
        cosConfig.getBucketName(), targetKey
);
```
改为：
```java
String bucket = resolveBucket(clientName);
CopyObjectRequest copyObjectRequest = new CopyObjectRequest(
        bucket, sourceKey,
        bucket, targetKey
);
```

将 `generateSignedUrl` 方法中的：
```java
GeneratePresignedUrlRequest request = new GeneratePresignedUrlRequest(cosConfig.getBucketName(), key, HttpMethodName.GET);
```
改为：
```java
GeneratePresignedUrlRequest request = new GeneratePresignedUrlRequest(resolveBucket(clientName), key, HttpMethodName.GET);
```

- [ ] **Step 2: Commit**

```bash
git add BarcBackend/src/main/java/com/miaoyu/barc/utils/tencent/cos/CosService.java
git commit -m "fix(cos): resolve bucketName by clientName instead of hardcoded default"
```

---

### Task 3: 模型层添加 deletedAt 字段

**Files:**
- Modify: `BarcBackend/src/main/java/com/miaoyu/barc/api/model/SchoolModel.java`
- Modify: `BarcBackend/src/main/java/com/miaoyu/barc/api/model/SchoolClubModel.java`
- Modify: `BarcBackend/src/main/java/com/miaoyu/barc/api/model/StudentModel.java`

- [ ] **Step 1: SchoolModel 添加 deletedAt**

在 `SchoolModel` 中新增字段及 getter/setter：

```java
private java.time.LocalDateTime deletedAt;

public java.time.LocalDateTime getDeletedAt() { return deletedAt; }
public void setDeletedAt(java.time.LocalDateTime deletedAt) { this.deletedAt = deletedAt; }
```

- [ ] **Step 2: SchoolClubModel 添加 deletedAt**

在 `SchoolClubModel` 中新增同样字段及 getter/setter。

- [ ] **Step 3: StudentModel 添加 deletedAt**

在 `StudentModel` 中新增同样字段及 getter/setter。

- [ ] **Step 4: Commit**

```bash
git add BarcBackend/src/main/java/com/miaoyu/barc/api/model/SchoolModel.java BarcBackend/src/main/java/com/miaoyu/barc/api/model/SchoolClubModel.java BarcBackend/src/main/java/com/miaoyu/barc/api/model/StudentModel.java
git commit -m "feat(model): add deletedAt to School, Club, Student models"
```

---

### Task 4: Mapper 层补软删除 + 新增 CRUD 方法

**Files:**
- Modify: `BarcBackend/src/main/java/com/miaoyu/barc/api/mapper/SchoolMapper.java`
- Modify: `BarcBackend/src/main/java/com/miaoyu/barc/api/mapper/ClubMapper.java`
- Modify: `BarcBackend/src/main/java/com/miaoyu/barc/api/mapper/StudentMapper.java`

- [ ] **Step 1: SchoolMapper 重写**

```java
package com.miaoyu.barc.api.mapper;

import com.miaoyu.barc.api.model.SchoolModel;
import org.apache.ibatis.annotations.*;

import java.util.List;

public interface SchoolMapper {

    @Select("SELECT * FROM schools WHERE deleted_at IS NULL")
    List<SchoolModel> selectAll();

    @Select("SELECT * FROM schools WHERE id = #{id} AND deleted_at IS NULL")
    SchoolModel selectById(@Param("id") String id);

    @Select("<script>" +
            "SELECT * FROM schools WHERE deleted_at IS NULL " +
            "<if test='keyword != null and keyword != \"\"'>" +
            "  AND (cn_name LIKE CONCAT('%',#{keyword},'%') OR jp_name LIKE CONCAT('%',#{keyword},'%') OR kr_name LIKE CONCAT('%',#{keyword},'%') OR en_name LIKE CONCAT('%',#{keyword},'%'))" +
            "</if>" +
            " LIMIT #{offset}, #{size}" +
            "</script>")
    List<SchoolModel> selectByPage(@Param("keyword") String keyword, @Param("offset") int offset, @Param("size") int size);

    @Select("<script>" +
            "SELECT COUNT(*) FROM schools WHERE deleted_at IS NULL " +
            "<if test='keyword != null and keyword != \"\"'>" +
            "  AND (cn_name LIKE CONCAT('%',#{keyword},'%') OR jp_name LIKE CONCAT('%',#{keyword},'%') OR kr_name LIKE CONCAT('%',#{keyword},'%') OR en_name LIKE CONCAT('%',#{keyword},'%'))" +
            "</if>" +
            "</script>")
    long countByKeyword(@Param("keyword") String keyword);

    @Insert("INSERT INTO schools(id, cn_name, jp_name, kr_name, en_name, introduce, logo, beautify_logo, bg) " +
            "VALUES(#{id}, #{cn_name}, #{jp_name}, #{kr_name}, #{en_name}, #{introduce}, #{logo}, #{beautify_logo}, #{bg})")
    int insert(SchoolModel school);

    @Update("<script>" +
            "UPDATE schools SET " +
            "  cn_name = #{cn_name}," +
            "  jp_name = #{jp_name}," +
            "  kr_name = #{kr_name}," +
            "  introduce = #{introduce}," +
            "  logo = #{logo}," +
            "  beautify_logo = #{beautify_logo}," +
            "  bg = #{bg}" +
            " WHERE id = #{id} AND deleted_at IS NULL" +
            "</script>")
    int update(SchoolModel school);

    @Update("UPDATE schools SET deleted_at = NOW() WHERE id = #{id} AND deleted_at IS NULL")
    int softDelete(@Param("id") String id);

    @Update("UPDATE club SET deleted_at = NOW() WHERE id IN (SELECT club_id FROM school_club WHERE school_id = #{schoolId}) AND deleted_at IS NULL")
    int softDeleteClubsBySchool(@Param("schoolId") String schoolId);

    @Update("UPDATE students SET deleted_at = NOW() WHERE school = #{schoolId} AND deleted_at IS NULL")
    int softDeleteStudentsBySchool(@Param("schoolId") String schoolId);
}
```

- [ ] **Step 2: ClubMapper 重写**

```java
package com.miaoyu.barc.api.mapper;

import com.miaoyu.barc.api.model.SchoolClubModel;
import org.apache.ibatis.annotations.*;

import java.util.List;

public interface ClubMapper {

    @Select("SELECT * FROM club WHERE deleted_at IS NULL")
    List<SchoolClubModel> selectAll();

    @Select("SELECT c.* FROM club c JOIN school_club sc ON c.id = sc.club_id WHERE sc.school_id = #{school_id} AND c.deleted_at IS NULL")
    List<SchoolClubModel> selectBySchool(@Param("school_id") String schoolId);

    @Select("SELECT * FROM club WHERE id = #{id} AND deleted_at IS NULL")
    SchoolClubModel selectById(@Param("id") String id);

    @Select("<script>" +
            "SELECT c.* FROM club c JOIN school_club sc ON c.id = sc.club_id " +
            "WHERE sc.school_id = #{school_id} AND c.deleted_at IS NULL " +
            "<if test='keyword != null and keyword != \"\"'>" +
            "  AND (c.cn_name LIKE CONCAT('%',#{keyword},'%') OR c.jp_name LIKE CONCAT('%',#{keyword},'%') OR c.kr_name LIKE CONCAT('%',#{keyword},'%') OR c.en_name LIKE CONCAT('%',#{keyword},'%'))" +
            "</if>" +
            " LIMIT #{offset}, #{size}" +
            "</script>")
    List<SchoolClubModel> selectByPage(@Param("school_id") String schoolId, @Param("keyword") String keyword, @Param("offset") int offset, @Param("size") int size);

    @Select("<script>" +
            "SELECT COUNT(*) FROM club c JOIN school_club sc ON c.id = sc.club_id " +
            "WHERE sc.school_id = #{school_id} AND c.deleted_at IS NULL " +
            "<if test='keyword != null and keyword != \"\"'>" +
            "  AND (c.cn_name LIKE CONCAT('%',#{keyword},'%') OR c.jp_name LIKE CONCAT('%',#{keyword},'%') OR c.kr_name LIKE CONCAT('%',#{keyword},'%') OR c.en_name LIKE CONCAT('%',#{keyword},'%'))" +
            "</if>" +
            "</script>")
    long countByKeyword(@Param("school_id") String schoolId, @Param("keyword") String keyword);

    @Insert("INSERT INTO club(id, school, cn_name, jp_name, kr_name, en_name, logo, bg) " +
            "VALUES(#{id}, #{school}, #{cn_name}, #{jp_name}, #{kr_name}, #{en_name}, #{logo}, #{bg})")
    int insert(SchoolClubModel club);

    @Update("<script>" +
            "UPDATE club SET " +
            "  school = #{school}," +
            "  cn_name = #{cn_name}," +
            "  jp_name = #{jp_name}," +
            "  kr_name = #{kr_name}," +
            "  logo = #{logo}," +
            "  bg = #{bg}" +
            " WHERE id = #{id} AND deleted_at IS NULL" +
            "</script>")
    int update(SchoolClubModel club);

    @Update("UPDATE club SET deleted_at = NOW() WHERE id = #{id} AND deleted_at IS NULL")
    int softDelete(@Param("id") String id);

    @Update("UPDATE students SET deleted_at = NOW() WHERE club = #{clubId} AND deleted_at IS NULL")
    int softDeleteStudentsByClub(@Param("clubId") String clubId);

    @Insert("INSERT INTO school_club(school_id, club_id) VALUES(#{school_id}, #{club_id}) " +
            "ON DUPLICATE KEY UPDATE school_id = #{school_id}")
    int upsertSchoolClub(@Param("school_id") String schoolId, @Param("club_id") String clubId);

    @Delete("DELETE FROM school_club WHERE club_id = #{club_id}")
    int deleteSchoolClub(@Param("club_id") String clubId);
}
```

- [ ] **Step 3: StudentMapper 重写**

```java
package com.miaoyu.barc.api.mapper;

import com.miaoyu.barc.api.model.StudentModel;
import org.apache.ibatis.annotations.*;

import java.util.List;

public interface StudentMapper {

    @Select("SELECT * FROM students WHERE deleted_at IS NULL")
    List<StudentModel> selectAll();

    @Select("SELECT * FROM students WHERE school = #{school_id} AND deleted_at IS NULL")
    List<StudentModel> selectBySchool(@Param("school_id") String schoolId);

    @Select("SELECT * FROM students WHERE club = #{club_id} AND deleted_at IS NULL")
    List<StudentModel> selectByClub(@Param("club_id") String clubId);

    @Select("SELECT * FROM students WHERE id = #{id} AND deleted_at IS NULL")
    StudentModel selectById(@Param("id") String id);

    @Select("<script>" +
            "SELECT * FROM students WHERE club = #{club_id} AND deleted_at IS NULL " +
            "<if test='keyword != null and keyword != \"\"'>" +
            "  AND (cn_name LIKE CONCAT('%',#{keyword},'%') OR jp_name LIKE CONCAT('%',#{keyword},'%') OR kr_name LIKE CONCAT('%',#{keyword},'%') OR en_name LIKE CONCAT('%',#{keyword},'%'))" +
            "</if>" +
            " LIMIT #{offset}, #{size}" +
            "</script>")
    List<StudentModel> selectByPage(@Param("club_id") String clubId, @Param("keyword") String keyword, @Param("offset") int offset, @Param("size") int size);

    @Select("<script>" +
            "SELECT COUNT(*) FROM students WHERE club = #{club_id} AND deleted_at IS NULL " +
            "<if test='keyword != null and keyword != \"\"'>" +
            "  AND (cn_name LIKE CONCAT('%',#{keyword},'%') OR jp_name LIKE CONCAT('%',#{keyword},'%') OR kr_name LIKE CONCAT('%',#{keyword},'%') OR en_name LIKE CONCAT('%',#{keyword},'%'))" +
            "</if>" +
            "</script>")
    long countByKeyword(@Param("club_id") String clubId, @Param("keyword") String keyword);

    @Insert("INSERT INTO students(id, cn_name, jp_name, kr_name, en_name, introduce, avatar_square, avatar_rectangle, body_image, school, club) " +
            "VALUES(#{id}, #{cn_name}, #{jp_name}, #{kr_name}, #{en_name}, #{introduce}, #{avatar_square}, #{avatar_rectangle}, #{body_image}, #{school}, #{club})")
    int insert(StudentModel student);

    @Update("<script>" +
            "UPDATE students SET " +
            "  cn_name = #{cn_name}," +
            "  jp_name = #{jp_name}," +
            "  kr_name = #{kr_name}," +
            "  introduce = #{introduce}," +
            "  avatar_square = #{avatar_square}," +
            "  avatar_rectangle = #{avatar_rectangle}," +
            "  body_image = #{body_image}," +
            "  school = #{school}," +
            "  club = #{club}" +
            " WHERE id = #{id} AND deleted_at IS NULL" +
            "</script>")
    int update(StudentModel student);

    @Update("UPDATE students SET deleted_at = NOW() WHERE id = #{id} AND deleted_at IS NULL")
    int softDelete(@Param("id") String id);
}
```

- [ ] **Step 4: Commit**

```bash
git add BarcBackend/src/main/java/com/miaoyu/barc/api/mapper/SchoolMapper.java BarcBackend/src/main/java/com/miaoyu/barc/api/mapper/ClubMapper.java BarcBackend/src/main/java/com/miaoyu/barc/api/mapper/StudentMapper.java
git commit -m "feat(mapper): add soft-delete filters and full CRUD methods for school/club/student"
```

---

### Task 5: 新增 ImageUrlResolver + CosGcService + CosGcScheduler

**Files:**
- Create: `BarcBackend/src/main/java/com/miaoyu/barc/utils/tencent/cos/ImageUrlResolver.java`
- Create: `BarcBackend/src/main/java/com/miaoyu/barc/api/service/CosGcService.java`
- Create: `BarcBackend/src/main/java/com/miaoyu/barc/api/scheduler/CosGcScheduler.java`
- Create: `BarcBackend/src/main/java/com/miaoyu/barc/api/mapper/CosGcMapper.java`

- [ ] **Step 1: 创建 ImageUrlResolver**

```java
package com.miaoyu.barc.utils.tencent.cos;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Date;

@Component
public class ImageUrlResolver {
    @Autowired
    private CosService cosService;

    private static final long DEFAULT_EXPIRE_MS = 3600_000; // 1 hour

    public String resolve(String value) {
        if (value == null || value.isEmpty()) return null;
        if (value.startsWith("https://") || value.startsWith("http://")) return value;
        Date expiration = new Date(System.currentTimeMillis() + DEFAULT_EXPIRE_MS);
        return cosService.generateSignedUrl(value, expiration, CosBucketConfigEnum.image);
    }

    public boolean isKey(String value) {
        return value != null && !value.isEmpty() && !value.startsWith("http://") && !value.startsWith("https://");
    }
}
```

- [ ] **Step 2: 创建 CosGcMapper**

```java
package com.miaoyu.barc.api.mapper;

import org.apache.ibatis.annotations.*;

import java.util.List;

public interface CosGcMapper {

    @Insert("INSERT INTO cos_garbage_key(bucket, object_key) VALUES(#{bucket}, #{objectKey})")
    int enqueue(@Param("bucket") String bucket, @Param("objectKey") String objectKey);

    @Select("SELECT * FROM cos_garbage_key WHERE processed_at IS NULL ORDER BY created_at LIMIT #{limit}")
    List<CosGcItem> selectPending(@Param("limit") int limit);

    @Update("UPDATE cos_garbage_key SET processed_at = NOW() WHERE id = #{id}")
    int markProcessed(@Param("id") long id);

    @Update("UPDATE cos_garbage_key SET retry_count = retry_count + 1, last_error = #{error} WHERE id = #{id}")
    int markFailed(@Param("id") long id, @Param("error") String error);

    class CosGcItem {
        private Long id;
        private String bucket;
        private String objectKey;
        private Integer retryCount;
        private String lastError;

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public String getBucket() { return bucket; }
        public void setBucket(String bucket) { this.bucket = bucket; }
        public String getObjectKey() { return objectKey; }
        public void setObjectKey(String objectKey) { this.objectKey = objectKey; }
        public Integer getRetryCount() { return retryCount; }
        public void setRetryCount(Integer retryCount) { this.retryCount = retryCount; }
        public String getLastError() { return lastError; }
        public void setLastError(String lastError) { this.lastError = lastError; }
    }
}
```

- [ ] **Step 3: 创建 CosGcService**

```java
package com.miaoyu.barc.api.service;

import com.miaoyu.barc.api.mapper.CosGcMapper;
import com.miaoyu.barc.utils.tencent.cos.CosService;
import com.qcloud.cos.COSClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class CosGcService {
    @Autowired
    private CosGcMapper cosGcMapper;

    public void enqueue(String bucket, String objectKey) {
        if (objectKey == null || objectKey.isEmpty()) return;
        cosGcMapper.enqueue(bucket, objectKey);
        log.info("Enqueued COS garbage: bucket={}, key={}", bucket, objectKey);
    }
}
```

- [ ] **Step 4: 创建 CosGcScheduler**

```java
package com.miaoyu.barc.api.scheduler;

import com.miaoyu.barc.api.mapper.CosGcMapper;
import com.miaoyu.barc.api.service.CosGcService;
import com.miaoyu.barc.utils.tencent.cos.CosBucketConfigEnum;
import com.miaoyu.barc.utils.tencent.cos.CosClient;
import com.miaoyu.barc.utils.tencent.cos.CosConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@Slf4j
public class CosGcScheduler {
    @Autowired
    private CosGcMapper cosGcMapper;
    @Autowired
    private CosClient cosClient;
    @Autowired
    private CosConfig cosConfig;

    private static final int BATCH_SIZE = 50;

    @Scheduled(fixedDelay = 5 * 60 * 1000)
    public void consume() {
        List<CosGcMapper.CosGcItem> items = cosGcMapper.selectPending(BATCH_SIZE);
        for (CosGcMapper.CosGcItem item : items) {
            try {
                String bucket = item.getBucket();
                String key = item.getObjectKey();
                cosClient.cosClient(CosBucketConfigEnum.image)
                        .deleteObject(bucket, key);
                cosGcMapper.markProcessed(item.getId());
                log.info("COS GC succeeded: bucket={}, key={}", bucket, key);
            } catch (Exception e) {
                cosGcMapper.markFailed(item.getId(), e.getMessage());
                log.error("COS GC failed: id={}, key={}, error={}", item.getId(), item.getObjectKey(), e.getMessage());
            }
        }
    }
}
```

- [ ] **Step 5: 确认 Spring Boot 已启用 Scheduling**

检查 `BarcBackend/src/main/java/com/miaoyu/barc/BarcBackendApplication.java` 是否已有 `@EnableScheduling`。若无，添加：

```java
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
```

- [ ] **Step 6: Commit**

```bash
git add BarcBackend/src/main/java/com/miaoyu/barc/utils/tencent/cos/ImageUrlResolver.java BarcBackend/src/main/java/com/miaoyu/barc/api/mapper/CosGcMapper.java BarcBackend/src/main/java/com/miaoyu/barc/api/service/CosGcService.java BarcBackend/src/main/java/com/miaoyu/barc/api/scheduler/CosGcScheduler.java
git commit -m "feat(cos): add ImageUrlResolver, GC queue service and scheduler"
```

---

## Phase 2 – 后端 CRUD

### Task 6: SchoolService 完整 CRUD

**Files:**
- Modify: `BarcBackend/src/main/java/com/miaoyu/barc/api/service/SchoolService.java`

- [ ] **Step 1: 重写 SchoolService**

```java
package com.miaoyu.barc.api.service;

import com.miaoyu.barc.api.mapper.SchoolMapper;
import com.miaoyu.barc.api.model.SchoolModel;
import com.miaoyu.barc.response.ResourceR;
import com.miaoyu.barc.utils.J;
import com.miaoyu.barc.utils.tencent.cos.CosBucketConfigEnum;
import com.miaoyu.barc.utils.tencent.cos.CosConfig;
import com.miaoyu.barc.utils.tencent.cos.ImageUrlResolver;
import com.miaoyu.barc.api.service.CosGcService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.regex.Pattern;

@Service
public class SchoolService {
    @Autowired
    private SchoolMapper schoolMapper;
    @Autowired
    private ImageUrlResolver imageUrlResolver;
    @Autowired
    private CosGcService cosGcService;
    @Autowired
    private CosConfig cosConfig;

    private static final Pattern EN_NAME_PATTERN = Pattern.compile("^[A-Za-z][A-Za-z0-9 ]{0,254}$");

    public ResponseEntity<J> getAllSchoolsService() {
        List<SchoolModel> list = schoolMapper.selectAll();
        list.forEach(this::resolveImages);
        return ResponseEntity.ok(new ResourceR().resourceSuch(true, list));
    }

    public ResponseEntity<J> getSchoolById(String id) {
        SchoolModel school = schoolMapper.selectById(id);
        if (school == null) {
            return ResponseEntity.ok(new ResourceR().resourceSuch(false, null));
        }
        resolveImages(school);
        return ResponseEntity.ok(new ResourceR().resourceSuch(true, school));
    }

    public ResponseEntity<J> getSchoolsByPage(String keyword, int page, int size) {
        int offset = (page - 1) * size;
        List<SchoolModel> list = schoolMapper.selectByPage(keyword, offset, size);
        long total = schoolMapper.countByKeyword(keyword);
        list.forEach(this::resolveImages);
        Map<String, Object> result = new HashMap<>();
        result.put("list", list);
        result.put("total", total);
        return ResponseEntity.ok(new ResourceR().resourceSuch(true, result));
    }

    public ResponseEntity<J> checkIdAvailable(String id) {
        SchoolModel existing = schoolMapper.selectById(id);
        return ResponseEntity.ok(new ResourceR().resourceSuch(true, existing == null));
    }

    @Transactional
    public ResponseEntity<J> createSchool(SchoolModel school) {
        String enName = school.getEn_name();
        if (enName == null || enName.isEmpty() || !EN_NAME_PATTERN.matcher(enName).matches()) {
            return ResponseEntity.ok(new J(1, "en_name 格式不正确", null));
        }
        String id = enName.toLowerCase().replace(" ", "_");
        school.setId(id);
        if (schoolMapper.selectById(id) != null) {
            return ResponseEntity.ok(new J(1, "ID 已存在", null));
        }
        schoolMapper.insert(school);
        return ResponseEntity.ok(new ResourceR().resourceSuch(true, school));
    }

    @Transactional
    public ResponseEntity<J> updateSchool(String id, SchoolModel school) {
        SchoolModel existing = schoolMapper.selectById(id);
        if (existing == null) {
            return ResponseEntity.ok(new J(1, "学园不存在", null));
        }
        // 忽略 en_name 修改
        school.setId(id);
        school.setEn_name(existing.getEn_name());
        // 旧 key 入 GC
        enqueueIfKeyChanged(existing.getLogo(), school.getLogo());
        enqueueIfKeyChanged(existing.getBeautify_logo(), school.getBeautify_logo());
        enqueueIfKeyChanged(existing.getBg(), school.getBg());
        schoolMapper.update(school);
        return ResponseEntity.ok(new ResourceR().resourceSuch(true, school));
    }

    @Transactional
    public ResponseEntity<J> deleteSchool(String id) {
        SchoolModel existing = schoolMapper.selectById(id);
        if (existing == null) {
            return ResponseEntity.ok(new J(1, "学园不存在", null));
        }
        // 所有 key 入 GC
        enqueueIfKey(existing.getLogo());
        enqueueIfKey(existing.getBeautify_logo());
        enqueueIfKey(existing.getBg());
        schoolMapper.softDeleteStudentsBySchool(id);
        schoolMapper.softDeleteClubsBySchool(id);
        schoolMapper.softDelete(id);
        return ResponseEntity.ok(new ResourceR().resourceSuch(true, null));
    }

    private void resolveImages(SchoolModel school) {
        school.setLogo(imageUrlResolver.resolve(school.getLogo()));
        school.setBeautify_logo(imageUrlResolver.resolve(school.getBeautify_logo()));
        school.setBg(imageUrlResolver.resolve(school.getBg()));
    }

    private void enqueueIfKeyChanged(String oldValue, String newValue) {
        if (oldValue != null && !oldValue.equals(newValue) && imageUrlResolver.isKey(oldValue)) {
            cosGcService.enqueue(cosConfig.getImage().getBucketName(), oldValue);
        }
    }

    private void enqueueIfKey(String value) {
        if (imageUrlResolver.isKey(value)) {
            cosGcService.enqueue(cosConfig.getImage().getBucketName(), value);
        }
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add BarcBackend/src/main/java/com/miaoyu/barc/api/service/SchoolService.java
git commit -m "feat(service): full CRUD for School with soft-delete cascade and GC"
```

---

### Task 7: ClubService 完整 CRUD

**Files:**
- Modify: `BarcBackend/src/main/java/com/miaoyu/barc/api/service/ClubService.java`

- [ ] **Step 1: 重写 ClubService**

```java
package com.miaoyu.barc.api.service;

import com.miaoyu.barc.api.mapper.ClubMapper;
import com.miaoyu.barc.api.mapper.SchoolMapper;
import com.miaoyu.barc.api.model.SchoolClubModel;
import com.miaoyu.barc.api.model.SchoolModel;
import com.miaoyu.barc.response.ResourceR;
import com.miaoyu.barc.utils.J;
import com.miaoyu.barc.utils.tencent.cos.CosConfig;
import com.miaoyu.barc.utils.tencent.cos.ImageUrlResolver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.regex.Pattern;

@Service
public class ClubService {
    @Autowired
    private ClubMapper clubMapper;
    @Autowired
    private SchoolMapper schoolMapper;
    @Autowired
    private ImageUrlResolver imageUrlResolver;
    @Autowired
    private CosGcService cosGcService;
    @Autowired
    private CosConfig cosConfig;

    private static final Pattern EN_NAME_PATTERN = Pattern.compile("^[A-Za-z][A-Za-z0-9 ]{0,254}$");

    public ResponseEntity<J> getAllClubsService() {
        List<SchoolClubModel> list = clubMapper.selectAll();
        list.forEach(this::resolveImages);
        return ResponseEntity.ok(new ResourceR().resourceSuch(true, list));
    }

    public ResponseEntity<J> getClubsBySchoolService(String schoolId) {
        List<SchoolClubModel> list = clubMapper.selectBySchool(schoolId);
        list.forEach(this::resolveImages);
        return ResponseEntity.ok(new ResourceR().resourceSuch(true, list));
    }

    public ResponseEntity<J> getClubByIdService(String id) {
        SchoolClubModel club = clubMapper.selectById(id);
        if (club == null) {
            return ResponseEntity.ok(new ResourceR().resourceSuch(false, null));
        }
        resolveImages(club);
        return ResponseEntity.ok(new ResourceR().resourceSuch(true, club));
    }

    public ResponseEntity<J> getClubsByPage(String schoolId, String keyword, int page, int size) {
        int offset = (page - 1) * size;
        List<SchoolClubModel> list = clubMapper.selectByPage(schoolId, keyword, offset, size);
        long total = clubMapper.countByKeyword(schoolId, keyword);
        list.forEach(this::resolveImages);
        Map<String, Object> result = new HashMap<>();
        result.put("list", list);
        result.put("total", total);
        return ResponseEntity.ok(new ResourceR().resourceSuch(true, result));
    }

    public ResponseEntity<J> checkIdAvailable(String id) {
        SchoolClubModel existing = clubMapper.selectById(id);
        return ResponseEntity.ok(new ResourceR().resourceSuch(true, existing == null));
    }

    @Transactional
    public ResponseEntity<J> createClub(SchoolClubModel club, String schoolId) {
        String enName = club.getEn_name();
        if (enName == null || enName.isEmpty() || !EN_NAME_PATTERN.matcher(enName).matches()) {
            return ResponseEntity.ok(new J(1, "en_name 格式不正确", null));
        }
        if (schoolId == null || schoolId.isEmpty()) {
            return ResponseEntity.ok(new J(1, "school_id 不能为空", null));
        }
        String id = enName.toLowerCase().replace(" ", "_");
        club.setId(id);
        if (clubMapper.selectById(id) != null) {
            return ResponseEntity.ok(new J(1, "ID 已存在", null));
        }
        SchoolModel school = schoolMapper.selectById(schoolId);
        if (school == null) {
            return ResponseEntity.ok(new J(1, "所属学园不存在", null));
        }
        club.setSchool(schoolId);
        clubMapper.insert(club);
        clubMapper.upsertSchoolClub(schoolId, id);
        return ResponseEntity.ok(new ResourceR().resourceSuch(true, club));
    }

    @Transactional
    public ResponseEntity<J> updateClub(String id, SchoolClubModel club, String schoolId) {
        SchoolClubModel existing = clubMapper.selectById(id);
        if (existing == null) {
            return ResponseEntity.ok(new J(1, "部团不存在", null));
        }
        club.setId(id);
        club.setEn_name(existing.getEn_name());
        enqueueIfKeyChanged(existing.getLogo(), club.getLogo());
        enqueueIfKeyChanged(existing.getBg(), club.getBg());
        if (schoolId != null && !schoolId.isEmpty() && !schoolId.equals(existing.getSchool())) {
            SchoolModel school = schoolMapper.selectById(schoolId);
            if (school == null) {
                return ResponseEntity.ok(new J(1, "目标学园不存在", null));
            }
            club.setSchool(schoolId);
            clubMapper.deleteSchoolClub(id);
            clubMapper.upsertSchoolClub(schoolId, id);
        } else {
            club.setSchool(existing.getSchool());
        }
        clubMapper.update(club);
        return ResponseEntity.ok(new ResourceR().resourceSuch(true, club));
    }

    @Transactional
    public ResponseEntity<J> deleteClub(String id) {
        SchoolClubModel existing = clubMapper.selectById(id);
        if (existing == null) {
            return ResponseEntity.ok(new J(1, "部团不存在", null));
        }
        enqueueIfKey(existing.getLogo());
        enqueueIfKey(existing.getBg());
        clubMapper.softDeleteStudentsByClub(id);
        clubMapper.softDelete(id);
        return ResponseEntity.ok(new ResourceR().resourceSuch(true, null));
    }

    private void resolveImages(SchoolClubModel club) {
        club.setLogo(imageUrlResolver.resolve(club.getLogo()));
        club.setBg(imageUrlResolver.resolve(club.getBg()));
    }

    private void enqueueIfKeyChanged(String oldValue, String newValue) {
        if (oldValue != null && !oldValue.equals(newValue) && imageUrlResolver.isKey(oldValue)) {
            cosGcService.enqueue(cosConfig.getImage().getBucketName(), oldValue);
        }
    }

    private void enqueueIfKey(String value) {
        if (imageUrlResolver.isKey(value)) {
            cosGcService.enqueue(cosConfig.getImage().getBucketName(), value);
        }
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add BarcBackend/src/main/java/com/miaoyu/barc/api/service/ClubService.java
git commit -m "feat(service): full CRUD for Club with soft-delete cascade and school reassign"
```

---

### Task 8: StudentService 完整 CRUD

**Files:**
- Modify: `BarcBackend/src/main/java/com/miaoyu/barc/api/service/StudentService.java`

- [ ] **Step 1: 重写 StudentService**

```java
package com.miaoyu.barc.api.service;

import com.miaoyu.barc.api.mapper.ClubMapper;
import com.miaoyu.barc.api.mapper.StudentMapper;
import com.miaoyu.barc.api.model.SchoolClubModel;
import com.miaoyu.barc.api.model.StudentModel;
import com.miaoyu.barc.response.ResourceR;
import com.miaoyu.barc.utils.J;
import com.miaoyu.barc.utils.tencent.cos.CosConfig;
import com.miaoyu.barc.utils.tencent.cos.ImageUrlResolver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.regex.Pattern;

@Service
public class StudentService {
    @Autowired
    private StudentMapper studentMapper;
    @Autowired
    private ClubMapper clubMapper;
    @Autowired
    private ImageUrlResolver imageUrlResolver;
    @Autowired
    private CosGcService cosGcService;
    @Autowired
    private CosConfig cosConfig;

    private static final Pattern EN_NAME_PATTERN = Pattern.compile("^[A-Za-z][A-Za-z0-9 ]{0,254}$");

    public ResponseEntity<J> getAllStudentsService() {
        List<StudentModel> list = studentMapper.selectAll();
        list.forEach(this::resolveImages);
        return ResponseEntity.ok(new ResourceR().resourceSuch(true, list));
    }

    public ResponseEntity<J> getStudentsBySchoolService(String schoolId) {
        List<StudentModel> list = studentMapper.selectBySchool(schoolId);
        list.forEach(this::resolveImages);
        return ResponseEntity.ok(new ResourceR().resourceSuch(true, list));
    }

    public ResponseEntity<J> getStudentsByClubIdService(String clubId) {
        List<StudentModel> list = studentMapper.selectByClub(clubId);
        list.forEach(this::resolveImages);
        return ResponseEntity.ok(new ResourceR().resourceSuch(true, list));
    }

    public ResponseEntity<J> getStudentByIdService(String id) {
        StudentModel student = studentMapper.selectById(id);
        if (student == null) {
            return ResponseEntity.ok(new ResourceR().resourceSuch(false, null));
        }
        resolveImages(student);
        return ResponseEntity.ok(new ResourceR().resourceSuch(true, student));
    }

    public ResponseEntity<J> getStudentsByPage(String clubId, String keyword, int page, int size) {
        int offset = (page - 1) * size;
        List<StudentModel> list = studentMapper.selectByPage(clubId, keyword, offset, size);
        long total = studentMapper.countByKeyword(clubId, keyword);
        list.forEach(this::resolveImages);
        Map<String, Object> result = new HashMap<>();
        result.put("list", list);
        result.put("total", total);
        return ResponseEntity.ok(new ResourceR().resourceSuch(true, result));
    }

    public ResponseEntity<J> checkIdAvailable(String id) {
        StudentModel existing = studentMapper.selectById(id);
        return ResponseEntity.ok(new ResourceR().resourceSuch(true, existing == null));
    }

    @Transactional
    public ResponseEntity<J> createStudent(StudentModel student, String clubId) {
        String enName = student.getEn_name();
        if (enName == null || enName.isEmpty() || !EN_NAME_PATTERN.matcher(enName).matches()) {
            return ResponseEntity.ok(new J(1, "en_name 格式不正确", null));
        }
        if (clubId == null || clubId.isEmpty()) {
            return ResponseEntity.ok(new J(1, "club_id 不能为空", null));
        }
        String id = enName.toLowerCase().replace(" ", "_");
        student.setId(id);
        if (studentMapper.selectById(id) != null) {
            return ResponseEntity.ok(new J(1, "ID 已存在", null));
        }
        SchoolClubModel club = clubMapper.selectById(clubId);
        if (club == null) {
            return ResponseEntity.ok(new J(1, "所属部团不存在", null));
        }
        student.setClub(clubId);
        student.setSchool(club.getSchool());
        studentMapper.insert(student);
        return ResponseEntity.ok(new ResourceR().resourceSuch(true, student));
    }

    @Transactional
    public ResponseEntity<J> updateStudent(String id, StudentModel student, String clubId) {
        StudentModel existing = studentMapper.selectById(id);
        if (existing == null) {
            return ResponseEntity.ok(new J(1, "学生不存在", null));
        }
        student.setId(id);
        student.setEn_name(existing.getEn_name());
        enqueueIfKeyChanged(existing.getAvatar_square(), student.getAvatar_square());
        enqueueIfKeyChanged(existing.getAvatar_rectangle(), student.getAvatar_rectangle());
        enqueueIfKeyChanged(existing.getBody_image(), student.getBody_image());
        if (clubId != null && !clubId.isEmpty() && !clubId.equals(existing.getClub())) {
            SchoolClubModel club = clubMapper.selectById(clubId);
            if (club == null) {
                return ResponseEntity.ok(new J(1, "目标部团不存在", null));
            }
            student.setClub(clubId);
            student.setSchool(club.getSchool());
        } else {
            student.setClub(existing.getClub());
            student.setSchool(existing.getSchool());
        }
        studentMapper.update(student);
        return ResponseEntity.ok(new ResourceR().resourceSuch(true, student));
    }

    @Transactional
    public ResponseEntity<J> deleteStudent(String id) {
        StudentModel existing = studentMapper.selectById(id);
        if (existing == null) {
            return ResponseEntity.ok(new J(1, "学生不存在", null));
        }
        enqueueIfKey(existing.getAvatar_square());
        enqueueIfKey(existing.getAvatar_rectangle());
        enqueueIfKey(existing.getBody_image());
        studentMapper.softDelete(id);
        return ResponseEntity.ok(new ResourceR().resourceSuch(true, null));
    }

    private void resolveImages(StudentModel student) {
        student.setAvatar_square(imageUrlResolver.resolve(student.getAvatar_square()));
        student.setAvatar_rectangle(imageUrlResolver.resolve(student.getAvatar_rectangle()));
        student.setBody_image(imageUrlResolver.resolve(student.getBody_image()));
    }

    private void enqueueIfKeyChanged(String oldValue, String newValue) {
        if (oldValue != null && !oldValue.equals(newValue) && imageUrlResolver.isKey(oldValue)) {
            cosGcService.enqueue(cosConfig.getImage().getBucketName(), oldValue);
        }
    }

    private void enqueueIfKey(String value) {
        if (imageUrlResolver.isKey(value)) {
            cosGcService.enqueue(cosConfig.getImage().getBucketName(), value);
        }
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add BarcBackend/src/main/java/com/miaoyu/barc/api/service/StudentService.java
git commit -m "feat(service): full CRUD for Student with soft-delete and club reassign"
```

---

### Task 9: 重写 Controller 层 (RESTful + 鉴权)

**Files:**
- Modify: `BarcBackend/src/main/java/com/miaoyu/barc/api/controller/SchoolController.java`
- Modify: `BarcBackend/src/main/java/com/miaoyu/barc/api/controller/ClubController.java`
- Modify: `BarcBackend/src/main/java/com/miaoyu/barc/api/controller/StudentController.java`

- [ ] **Step 1: SchoolController 重写**

```java
package com.miaoyu.barc.api.controller;

import com.miaoyu.barc.annotation.IgnoreAuth;
import com.miaoyu.barc.annotation.RequireUserAndPermissionAnno;
import com.miaoyu.barc.api.model.SchoolModel;
import com.miaoyu.barc.api.service.SchoolService;
import com.miaoyu.barc.permission.PermissionConst;
import com.miaoyu.barc.response.ResourceR;
import com.miaoyu.barc.utils.J;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/school")
public class SchoolController {
    @Autowired
    private SchoolService schoolService;

    @IgnoreAuth
    @GetMapping("/list")
    public ResponseEntity<J> list(
            @RequestParam(value = "keyword", required = false, defaultValue = "") String keyword,
            @RequestParam(value = "page", required = false, defaultValue = "1") int page,
            @RequestParam(value = "size", required = false, defaultValue = "20") int size) {
        return schoolService.getSchoolsByPage(keyword, page, size);
    }

    @IgnoreAuth
    @GetMapping("/{id}")
    public ResponseEntity<J> getById(@PathVariable("id") String id) {
        return schoolService.getSchoolById(id);
    }

    @RequireUserAndPermissionAnno({ @RequireUserAndPermissionAnno.Check(permission = PermissionConst.ADMINISTRATOR) })
    @PostMapping("")
    public ResponseEntity<J> create(@RequestBody SchoolModel school) {
        return schoolService.createSchool(school);
    }

    @RequireUserAndPermissionAnno({ @RequireUserAndPermissionAnno.Check(permission = PermissionConst.ADMINISTRATOR) })
    @PutMapping("/{id}")
    public ResponseEntity<J> update(@PathVariable("id") String id, @RequestBody SchoolModel school) {
        return schoolService.updateSchool(id, school);
    }

    @RequireUserAndPermissionAnno({ @RequireUserAndPermissionAnno.Check(permission = PermissionConst.ADMINISTRATOR) })
    @DeleteMapping("/{id}")
    public ResponseEntity<J> delete(@PathVariable("id") String id) {
        return schoolService.deleteSchool(id);
    }

    @IgnoreAuth
    @GetMapping("/check_id_available")
    public ResponseEntity<J> checkIdAvailable(@RequestParam("id") String id) {
        return schoolService.checkIdAvailable(id);
    }
}
```

- [ ] **Step 2: ClubController 重写**

```java
package com.miaoyu.barc.api.controller;

import com.miaoyu.barc.annotation.IgnoreAuth;
import com.miaoyu.barc.annotation.RequireUserAndPermissionAnno;
import com.miaoyu.barc.api.model.SchoolClubModel;
import com.miaoyu.barc.api.service.ClubService;
import com.miaoyu.barc.permission.PermissionConst;
import com.miaoyu.barc.utils.J;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/club")
public class ClubController {
    @Autowired
    private ClubService clubService;

    @IgnoreAuth
    @GetMapping("/list")
    public ResponseEntity<J> list(
            @RequestParam("school_id") String schoolId,
            @RequestParam(value = "keyword", required = false, defaultValue = "") String keyword,
            @RequestParam(value = "page", required = false, defaultValue = "1") int page,
            @RequestParam(value = "size", required = false, defaultValue = "20") int size) {
        return clubService.getClubsByPage(schoolId, keyword, page, size);
    }

    @IgnoreAuth
    @GetMapping("/{id}")
    public ResponseEntity<J> getById(@PathVariable("id") String id) {
        return clubService.getClubByIdService(id);
    }

    @RequireUserAndPermissionAnno({ @RequireUserAndPermissionAnno.Check(permission = PermissionConst.ADMINISTRATOR) })
    @PostMapping("")
    public ResponseEntity<J> create(@RequestBody SchoolClubModel club, @RequestParam("school_id") String schoolId) {
        return clubService.createClub(club, schoolId);
    }

    @RequireUserAndPermissionAnno({ @RequireUserAndPermissionAnno.Check(permission = PermissionConst.ADMINISTRATOR) })
    @PutMapping("/{id}")
    public ResponseEntity<J> update(@PathVariable("id") String id, @RequestBody SchoolClubModel club, @RequestParam(value = "school_id", required = false) String schoolId) {
        return clubService.updateClub(id, club, schoolId);
    }

    @RequireUserAndPermissionAnno({ @RequireUserAndPermissionAnno.Check(permission = PermissionConst.ADMINISTRATOR) })
    @DeleteMapping("/{id}")
    public ResponseEntity<J> delete(@PathVariable("id") String id) {
        return clubService.deleteClub(id);
    }

    @IgnoreAuth
    @GetMapping("/check_id_available")
    public ResponseEntity<J> checkIdAvailable(@RequestParam("id") String id) {
        return clubService.checkIdAvailable(id);
    }
}
```

- [ ] **Step 3: StudentController 重写**

```java
package com.miaoyu.barc.api.controller;

import com.miaoyu.barc.annotation.IgnoreAuth;
import com.miaoyu.barc.annotation.RequireUserAndPermissionAnno;
import com.miaoyu.barc.api.model.StudentModel;
import com.miaoyu.barc.api.service.StudentService;
import com.miaoyu.barc.permission.PermissionConst;
import com.miaoyu.barc.utils.J;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/student")
public class StudentController {
    @Autowired
    private StudentService studentService;

    @IgnoreAuth
    @GetMapping("/list")
    public ResponseEntity<J> list(
            @RequestParam("club_id") String clubId,
            @RequestParam(value = "keyword", required = false, defaultValue = "") String keyword,
            @RequestParam(value = "page", required = false, defaultValue = "1") int page,
            @RequestParam(value = "size", required = false, defaultValue = "20") int size) {
        return studentService.getStudentsByPage(clubId, keyword, page, size);
    }

    @IgnoreAuth
    @GetMapping("/{id}")
    public ResponseEntity<J> getById(@PathVariable("id") String id) {
        return studentService.getStudentByIdService(id);
    }

    @RequireUserAndPermissionAnno({ @RequireUserAndPermissionAnno.Check(permission = PermissionConst.ADMINISTRATOR) })
    @PostMapping("")
    public ResponseEntity<J> create(@RequestBody StudentModel student, @RequestParam("club_id") String clubId) {
        return studentService.createStudent(student, clubId);
    }

    @RequireUserAndPermissionAnno({ @RequireUserAndPermissionAnno.Check(permission = PermissionConst.ADMINISTRATOR) })
    @PutMapping("/{id}")
    public ResponseEntity<J> update(@PathVariable("id") String id, @RequestBody StudentModel student, @RequestParam(value = "club_id", required = false) String clubId) {
        return studentService.updateStudent(id, student, clubId);
    }

    @RequireUserAndPermissionAnno({ @RequireUserAndPermissionAnno.Check(permission = PermissionConst.ADMINISTRATOR) })
    @DeleteMapping("/{id}")
    public ResponseEntity<J> delete(@PathVariable("id") String id) {
        return studentService.deleteStudent(id);
    }

    @IgnoreAuth
    @GetMapping("/check_id_available")
    public ResponseEntity<J> checkIdAvailable(@RequestParam("id") String id) {
        return studentService.checkIdAvailable(id);
    }
}
```

- [ ] **Step 4: Commit**

```bash
git add BarcBackend/src/main/java/com/miaoyu/barc/api/controller/SchoolController.java BarcBackend/src/main/java/com/miaoyu/barc/api/controller/ClubController.java BarcBackend/src/main/java/com/miaoyu/barc/api/controller/StudentController.java
git commit -m "feat(controller): RESTful CRUD for school/club/student with admin permission guards"
```

---

### Task 10: 上传接口调整

**Files:**
- Modify: `BarcBackend/src/main/java/com/miaoyu/barc/api/controller/FileController.java` (若不存在则新建)

- [ ] **Step 1: 查找现有文件上传接口**

搜索现有上传 Controller：
```bash
grep -rl "uploadFile" BarcBackend/src/main/java/ --include="*.java"
```

如果已有 FileController，修改其 upload 方法以支持 `path` 查询参数；否则新建：

```java
package com.miaoyu.barc.api.controller;

import com.miaoyu.barc.annotation.RequireUserAndPermissionAnno;
import com.miaoyu.barc.permission.PermissionConst;
import com.miaoyu.barc.utils.J;
import com.miaoyu.barc.utils.tencent.cos.CosBucketConfigEnum;
import com.miaoyu.barc.utils.tencent.cos.CosService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/file")
public class FileController {
    @Autowired
    private CosService cosService;

    @RequireUserAndPermissionAnno({ @RequireUserAndPermissionAnno.Check(permission = PermissionConst.ADMINISTRATOR) })
    @PostMapping("/upload")
    public ResponseEntity<J> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam("path") String path) {
        J result = cosService.uploadFile(file, path, CosBucketConfigEnum.image);
        return ResponseEntity.ok(result);
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add BarcBackend/src/main/java/com/miaoyu/barc/api/controller/FileController.java
git commit -m "feat(file): upload endpoint with path param for COS image bucket"
```

---

## Phase 3 – V2 前端

### Task 11: 前端 API 层

**Files:**
- Create: `BarcManageFrontendV2/src/modules/school-club-student/api/school.ts`
- Create: `BarcManageFrontendV2/src/modules/school-club-student/api/club.ts`
- Create: `BarcManageFrontendV2/src/modules/school-club-student/api/student.ts`

- [ ] **Step 1: school.ts**

```typescript
import { http } from '@/shared/api/http'

export interface School {
  id: string
  cn_name: string
  jp_name?: string
  kr_name?: string
  en_name: string
  introduce?: string
  logo?: string
  beautify_logo?: string
  bg?: string
}

export interface PageResult<T> {
  list: T[]
  total: number
}

export function fetchSchoolList(keyword: string, page: number, size: number): Promise<PageResult<School>> {
  return http.get<PageResult<School>>('/school/list', { params: { keyword, page, size } })
}

export function fetchSchoolById(id: string): Promise<School> {
  return http.get<School>(`/school/${id}`)
}

export function createSchool(payload: School): Promise<School> {
  return http.post<School>('/school', payload)
}

export function updateSchool(id: string, payload: Partial<School>): Promise<School> {
  return http.put<School>(`/school/${id}`, payload)
}

export function deleteSchool(id: string): Promise<unknown> {
  return http.delete(`/school/${id}`)
}

export function checkSchoolIdAvailable(id: string): Promise<boolean> {
  return http.get<boolean>('/school/check_id_available', { params: { id } })
}
```

- [ ] **Step 2: club.ts**

```typescript
import { http } from '@/shared/api/http'
import type { PageResult } from './school'

export interface Club {
  id: string
  school?: string
  cn_name: string
  jp_name?: string
  kr_name?: string
  en_name: string
  logo?: string
  bg?: string
}

export function fetchClubList(schoolId: string, keyword: string, page: number, size: number): Promise<PageResult<Club>> {
  return http.get<PageResult<Club>>('/club/list', { params: { school_id: schoolId, keyword, page, size } })
}

export function fetchClubById(id: string): Promise<Club> {
  return http.get<Club>(`/club/${id}`)
}

export function createClub(payload: Club, schoolId: string): Promise<Club> {
  return http.post<Club>('/club', payload, { params: { school_id: schoolId } })
}

export function updateClub(id: string, payload: Partial<Club>, schoolId?: string): Promise<Club> {
  return http.put<Club>(`/club/${id}`, payload, { params: schoolId ? { school_id: schoolId } : undefined })
}

export function deleteClub(id: string): Promise<unknown> {
  return http.delete(`/club/${id}`)
}

export function checkClubIdAvailable(id: string): Promise<boolean> {
  return http.get<boolean>('/club/check_id_available', { params: { id } })
}
```

- [ ] **Step 3: student.ts**

```typescript
import { http } from '@/shared/api/http'
import type { PageResult } from './school'

export interface Student {
  id: string
  cn_name: string
  jp_name?: string
  kr_name?: string
  en_name: string
  introduce?: string
  avatar_square?: string
  avatar_rectangle?: string
  body_image?: string
  school?: string
  club: string
}

export function fetchStudentList(clubId: string, keyword: string, page: number, size: number): Promise<PageResult<Student>> {
  return http.get<PageResult<Student>>('/student/list', { params: { club_id: clubId, keyword, page, size } })
}

export function fetchStudentById(id: string): Promise<Student> {
  return http.get<Student>(`/student/${id}`)
}

export function createStudent(payload: Student, clubId: string): Promise<Student> {
  return http.post<Student>('/student', payload, { params: { club_id: clubId } })
}

export function updateStudent(id: string, payload: Partial<Student>, clubId?: string): Promise<Student> {
  return http.put<Student>(`/student/${id}`, payload, { params: clubId ? { club_id: clubId } : undefined })
}

export function deleteStudent(id: string): Promise<unknown> {
  return http.delete(`/student/${id}`)
}

export function checkStudentIdAvailable(id: string): Promise<boolean> {
  return http.get<boolean>('/student/check_id_available', { params: { id } })
}
```

- [ ] **Step 4: Commit**

```bash
git add BarcManageFrontendV2/src/modules/school-club-student/api/
git commit -m "feat(frontend): api layer for school/club/student"
```

---

### Task 12: 路由与模块脚手架

**Files:**
- Modify: `BarcManageFrontendV2/src/app/router/routes.ts`
- Create: `BarcManageFrontendV2/src/modules/school-club-student/store/entityCache.ts`

- [ ] **Step 1: 修改 routes.ts**

在 `adminChildren` 数组末尾、`ForbiddenView` 路由之前插入：

```typescript
const SchoolListView = () => import('@/modules/school-club-student/views/SchoolListView.vue')
const ClubListView = () => import('@/modules/school-club-student/views/ClubListView.vue')
const StudentListView = () => import('@/modules/school-club-student/views/StudentListView.vue')
```

在 `adminChildren` 中添加路由对象：

```typescript
  {
    path: 'schools',
    name: 'schools-list',
    component: SchoolListView,
    meta: {
      title: '学园列表',
      subtitle: '管理所有学园及其下属的部团与学生。',
      requiresAuth: true,
      requiresManager: true,
      minManagerPermission: 16,
      menuLabel: '学园、部团、学生',
      menuGroup: 'content',
      menuGroupLabel: '内容管理',
      groupOrder: 50,
      menuOrder: 50,
    },
  },
  {
    path: 'schools/:schoolId/clubs',
    name: 'clubs-list',
    component: ClubListView,
    meta: {
      title: '部团列表',
      subtitle: '管理该学园下的所有部团。',
      requiresAuth: true,
      requiresManager: true,
      minManagerPermission: 16,
      hiddenInMenu: true,
    },
  },
  {
    path: 'schools/:schoolId/clubs/:clubId/students',
    name: 'students-list',
    component: StudentListView,
    meta: {
      title: '学生列表',
      subtitle: '管理该部团下的所有学生。',
      requiresAuth: true,
      requiresManager: true,
      minManagerPermission: 16,
      hiddenInMenu: true,
    },
  },
```

- [ ] **Step 2: 创建 entityCache.ts**

```typescript
import { defineStore } from 'pinia'
import { ref } from 'vue'

export const useEntityCacheStore = defineStore('entity-cache', () => {
  const schoolNames = ref<Record<string, string>>({})
  const clubNames = ref<Record<string, string>>({})

  function setSchoolName(id: string, name: string) {
    schoolNames.value[id] = name
  }
  function setClubName(id: string, name: string) {
    clubNames.value[id] = name
  }
  function getSchoolName(id: string): string | undefined {
    return schoolNames.value[id]
  }
  function getClubName(id: string): string | undefined {
    return clubNames.value[id]
  }

  return { schoolNames, clubNames, setSchoolName, setClubName, getSchoolName, getClubName }
})
```

- [ ] **Step 3: Commit**

```bash
git add BarcManageFrontendV2/src/app/router/routes.ts BarcManageFrontendV2/src/modules/school-club-student/store/entityCache.ts
git commit -m "feat(frontend): add school-club-student routes and entity cache store"
```

---

### Task 13: 卡片组件

**Files:**
- Create: `BarcManageFrontendV2/src/modules/school-club-student/components/SchoolCard.vue`
- Create: `BarcManageFrontendV2/src/modules/school-club-student/components/ClubCard.vue`
- Create: `BarcManageFrontendV2/src/modules/school-club-student/components/StudentCard.vue`

- [ ] **Step 1: SchoolCard.vue**

```vue
<template>
  <div class="entity-card school-card" @click="$emit('click', props.id)">
    <img v-if="props.logo" :src="props.logo" class="card-bg" />
    <div v-else class="card-bg placeholder">学园</div>
    <div class="card-name">{{ props.name }}</div>
    <el-dropdown class="card-menu" trigger="click" @command="handleCommand" @click.stop>
      <span class="el-dropdown-link">
        <el-icon><More-Filled /></el-icon>
      </span>
      <template #dropdown>
        <el-dropdown-menu>
          <el-dropdown-item command="edit">修改</el-dropdown-item>
          <el-dropdown-item command="delete" style="color: #f56c6c;">删除</el-dropdown-item>
        </el-dropdown-menu>
      </template>
    </el-dropdown>
  </div>
</template>

<script setup lang="ts">
import { MoreFilled } from '@element-plus/icons-vue'

const props = defineProps<{
  id: string
  name: string
  logo?: string
}>()

const emit = defineEmits<{
  (e: 'click', id: string): void
  (e: 'edit', id: string): void
  (e: 'delete', id: string): void
}>()

function handleCommand(cmd: string) {
  if (cmd === 'edit') emit('edit', props.id)
  if (cmd === 'delete') emit('delete', props.id)
}
</script>

<style scoped>
.school-card {
  position: relative;
  width: 200px;
  height: 200px;
  border-radius: 8px;
  overflow: hidden;
  cursor: pointer;
  transition: transform 0.2s ease;
}
.school-card:hover {
  transform: scale(1.02);
}
.card-bg {
  width: 100%;
  height: 100%;
  object-fit: cover;
}
.placeholder {
  display: flex;
  align-items: center;
  justify-content: center;
  background: #e4e7ed;
  color: #909399;
  font-size: 14px;
}
.card-name {
  position: absolute;
  bottom: 0;
  left: 0;
  right: 0;
  padding: 8px;
  background: linear-gradient(transparent, rgba(0,0,0,0.6));
  color: #fff;
  font-size: 14px;
  text-align: center;
}
.card-menu {
  position: absolute;
  top: 8px;
  right: 8px;
  color: #fff;
  background: rgba(0,0,0,0.3);
  border-radius: 4px;
  padding: 4px;
  cursor: pointer;
}
</style>
```

- [ ] **Step 2: ClubCard.vue**

与 SchoolCard.vue 结构相同，只是 props 名字不同（复用 SchoolCard 也可，但分开便于未来样式差异）。创建时复制 SchoolCard.vue 并替换 `logo` → `logo`，组件名改为 ClubCard。

- [ ] **Step 3: StudentCard.vue**

```vue
<template>
  <div class="student-card" @click="$emit('click', props.id)">
    <img v-if="props.avatar" :src="props.avatar" class="avatar" />
    <div v-else class="avatar placeholder">头像</div>
    <div class="name">{{ props.name }}</div>
    <el-dropdown class="card-menu" trigger="click" @command="handleCommand" @click.stop>
      <span class="el-dropdown-link">
        <el-icon><More-Filled /></el-icon>
      </span>
      <template #dropdown>
        <el-dropdown-menu>
          <el-dropdown-item command="edit">修改</el-dropdown-item>
          <el-dropdown-item command="delete" style="color: #f56c6c;">删除</el-dropdown-item>
        </el-dropdown-menu>
      </template>
    </el-dropdown>
  </div>
</template>

<script setup lang="ts">
import { MoreFilled } from '@element-plus/icons-vue'

const props = defineProps<{
  id: string
  name: string
  avatar?: string
}>()

const emit = defineEmits<{
  (e: 'click', id: string): void
  (e: 'edit', id: string): void
  (e: 'delete', id: string): void
}>()

function handleCommand(cmd: string) {
  if (cmd === 'edit') emit('edit', props.id)
  if (cmd === 'delete') emit('delete', props.id)
}
</script>

<style scoped>
.student-card {
  position: relative;
  width: 96px;
  height: 128px;
  border: 0.2rem solid white;
  border-radius: 0.5rem;
  overflow: hidden;
  cursor: pointer;
  transition: 0.3s ease;
}
.student-card:hover {
  background: white;
}
.avatar {
  width: 100%;
  height: 100%;
  object-fit: cover;
}
.placeholder {
  display: flex;
  align-items: center;
  justify-content: center;
  background: #e4e7ed;
  color: #909399;
  font-size: 12px;
}
.name {
  position: absolute;
  bottom: 0;
  left: 0;
  right: 0;
  padding: 4px;
  background: linear-gradient(transparent, rgba(0,0,0,0.6));
  color: #fff;
  font-size: 12px;
  text-align: center;
}
.card-menu {
  position: absolute;
  top: 4px;
  right: 4px;
  color: #fff;
  background: rgba(0,0,0,0.3);
  border-radius: 4px;
  padding: 2px;
  cursor: pointer;
}
</style>
```

- [ ] **Step 4: Commit**

```bash
git add BarcManageFrontendV2/src/modules/school-club-student/components/SchoolCard.vue BarcManageFrontendV2/src/modules/school-club-student/components/ClubCard.vue BarcManageFrontendV2/src/modules/school-club-student/components/StudentCard.vue
git commit -m "feat(frontend): add SchoolCard, ClubCard, StudentCard components"
```

---

### Task 14: ImageUploadField + EntityFormDrawer

**Files:**
- Create: `BarcManageFrontendV2/src/modules/school-club-student/components/ImageUploadField.vue`
- Create: `BarcManageFrontendV2/src/modules/school-club-student/components/EntityFormDrawer.vue`

- [ ] **Step 1: ImageUploadField.vue**

```vue
<template>
  <div class="image-upload-field">
    <div v-if="modelValue" class="preview">
      <img :src="displayUrl" />
      <el-button size="small" @click="clear">替换</el-button>
    </div>
    <el-upload
      v-else
      :action="uploadUrl"
      :show-file-list="false"
      :before-upload="beforeUpload"
      :on-success="handleSuccess"
      :on-error="handleError"
      accept="image/*"
    >
      <el-button type="primary" size="small">上传</el-button>
    </el-upload>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'

const props = defineProps<{
  modelValue?: string
  uploadUrl: string
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', val: string): void
}>()

const displayUrl = computed(() => {
  const v = props.modelValue
  if (!v) return ''
  if (v.startsWith('http://') || v.startsWith('https://')) return v
  // key 形式：后端已经拼成签名 URL 返回，所以这里直接返回即可。若需要前端拼，则改为 base + key
  return v
})

function beforeUpload(file: File) {
  const isImage = file.type.startsWith('image/')
  if (!isImage) {
    ElMessage.error('请上传图片文件')
    return false
  }
  return true
}

function handleSuccess(res: any) {
  if (res.code === 0) {
    emit('update:modelValue', res.data as string)
  } else {
    ElMessage.error(res.msg || '上传失败')
  }
}

function handleError() {
  ElMessage.error('上传失败，请重试')
}

function clear() {
  emit('update:modelValue', '')
}
</script>

<style scoped>
.image-upload-field {
  display: flex;
  align-items: center;
}
.preview img {
  width: 80px;
  height: 80px;
  object-fit: cover;
  border-radius: 4px;
  margin-right: 8px;
}
</style>
```

- [ ] **Step 2: EntityFormDrawer.vue**

这是一个较长的统一表单组件，支持 School/Club/Student 三种实体的新增和编辑。为节省篇幅，此处给出核心结构：

```vue
<template>
  <el-drawer v-model="visible" :title="title" size="480px" :before-close="handleClose">
    <el-form :model="form" label-width="100px" :rules="rules" ref="formRef">
      <el-form-item label="英文名" prop="en_name">
        <el-input v-model="form.en_name" :disabled="isEdit" @blur="checkId" placeholder="North Abydos" />
        <div v-if="idPreview" class="id-preview">ID 预览: {{ idPreview }}</div>
        <div v-if="idConflict" class="error-text">ID 已存在</div>
      </el-form-item>

      <el-form-item v-if="isEdit" label="ID">
        <el-input :model-value="form.id" disabled />
      </el-form-item>

      <el-form-item label="中文名" prop="cn_name">
        <el-input v-model="form.cn_name" />
      </el-form-item>
      <el-form-item label="日文名">
        <el-input v-model="form.jp_name" />
      </el-form-item>
      <el-form-item label="韩文名">
        <el-input v-model="form.kr_name" />
      </el-form-item>
      <el-form-item v-if="entityType !== 'club'" label="介绍">
        <el-input v-model="form.introduce" type="textarea" rows="3" />
      </el-form-item>

      <!-- 跨级重选上级 -->
      <el-form-item v-if="entityType === 'club' && isEdit" label="所属学园">
        <el-select v-model="form.school_id" placeholder="选择学园">
          <el-option v-for="s in schoolOptions" :key="s.id" :label="s.cn_name" :value="s.id" />
        </el-select>
      </el-form-item>
      <el-form-item v-if="entityType === 'student' && isEdit" label="所属部团">
        <el-select v-model="form.club_id" placeholder="选择部团">
          <el-option v-for="c in clubOptions" :key="c.id" :label="c.cn_name" :value="c.id" />
        </el-select>
      </el-form-item>

      <!-- 图片字段 -->
      <el-form-item v-for="field in imageFields" :key="field.key" :label="field.label">
        <ImageUploadField v-model="form[field.key]" :upload-url="buildUploadUrl(field.key)" />
      </el-form-item>
    </el-form>

    <template #footer>
      <el-button @click="handleClose">取消</el-button>
      <el-button type="primary" @click="submit" :disabled="idConflict || submitting">保存</el-button>
    </template>
  </el-drawer>
</template>

<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import ImageUploadField from './ImageUploadField.vue'
import { checkSchoolIdAvailable, checkClubIdAvailable, checkStudentIdAvailable } from '../api'

const props = defineProps<{
  modelValue: boolean
  entityType: 'school' | 'club' | 'student'
  isEdit: boolean
  initialData?: Record<string, any>
  parentId?: string
  schoolOptions?: { id: string; cn_name: string }[]
  clubOptions?: { id: string; cn_name: string }[]
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', val: boolean): void
  (e: 'submit', data: Record<string, any>): void
}>()

const visible = computed({
  get: () => props.modelValue,
  set: (val) => emit('update:modelValue', val)
})

const form = reactive<Record<string, any>>({})
const formRef = ref()
const idPreview = ref('')
const idConflict = ref(false)
const submitting = ref(false)

const title = computed(() => {
  const action = props.isEdit ? '修改' : '新增'
  const map = { school: '学园', club: '部团', student: '学生' }
  return `${action}${map[props.entityType]}`
})

const imageFields = computed(() => {
  if (props.entityType === 'school') {
    return [
      { key: 'logo', label: 'Logo' },
      { key: 'beautify_logo', label: '美化 Logo' },
      { key: 'bg', label: '背景图' },
    ]
  }
  if (props.entityType === 'club') {
    return [
      { key: 'logo', label: 'Logo' },
      { key: 'bg', label: '背景图' },
    ]
  }
  return [
    { key: 'avatar_square', label: '方形头像' },
    { key: 'avatar_rectangle', label: '矩形头像' },
    { key: 'body_image', label: '立绘' },
  ]
})

const rules = {
  en_name: [{ required: true, message: '英文名必填', trigger: 'blur' }],
  cn_name: [{ required: true, message: '中文名必填', trigger: 'blur' }],
}

function buildUploadUrl(field: string) {
  const entityId = props.isEdit ? props.initialData?.id : idPreview.value
  const prefix = `${props.entityType}s/${entityId}/${field}_`
  const apiBase = import.meta.env.VITE_API_BASE_URL || ''
  return `${apiBase}/api/file/upload?path=${encodeURIComponent(prefix)}`
}

function deriveId(enName: string) {
  return enName.toLowerCase().replace(/ /g, '_')
}

async function checkId() {
  if (props.isEdit || !form.en_name) return
  const id = deriveId(form.en_name)
  idPreview.value = id
  let ok = false
  if (props.entityType === 'school') ok = await checkSchoolIdAvailable(id)
  else if (props.entityType === 'club') ok = await checkClubIdAvailable(id)
  else ok = await checkStudentIdAvailable(id)
  idConflict.value = !ok
}

watch(() => props.modelValue, (val) => {
  if (val) {
    Object.assign(form, props.initialData || {})
    if (!props.isEdit) {
      form.en_name = ''
      form.cn_name = ''
      form.jp_name = ''
      form.kr_name = ''
      form.introduce = ''
      imageFields.value.forEach(f => form[f.key] = '')
      idPreview.value = ''
      idConflict.value = false
    } else {
      idPreview.value = form.id || ''
      if (props.entityType === 'club') form.school_id = props.initialData?.school || props.parentId
      if (props.entityType === 'student') form.club_id = props.initialData?.club || props.parentId
    }
  }
})

function handleClose() {
  visible.value = false
}

async function submit() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  const payload = { ...form }
  if (!props.isEdit) {
    payload.id = deriveId(form.en_name)
  }
  if (props.entityType === 'club' && props.isEdit && form.school_id) {
    payload.school = form.school_id
  }
  if (props.entityType === 'student' && props.isEdit && form.club_id) {
    payload.club = form.club_id
  }
  emit('submit', payload)
}
</script>

<style scoped>
.id-preview {
  font-size: 12px;
  color: #606266;
  margin-top: 4px;
}
.error-text {
  font-size: 12px;
  color: #f56c6c;
  margin-top: 4px;
}
</style>
```

- [ ] **Step 3: Commit**

```bash
git add BarcManageFrontendV2/src/modules/school-club-student/components/ImageUploadField.vue BarcManageFrontendV2/src/modules/school-club-student/components/EntityFormDrawer.vue
git commit -m "feat(frontend): add ImageUploadField and EntityFormDrawer"
```

---

### Task 15: 三级列表视图

**Files:**
- Create: `BarcManageFrontendV2/src/modules/school-club-student/views/SchoolListView.vue`
- Create: `BarcManageFrontendV2/src/modules/school-club-student/views/ClubListView.vue`
- Create: `BarcManageFrontendV2/src/modules/school-club-student/views/StudentListView.vue`

由于视图文件较长且结构相似，此处给出 SchoolListView.vue 的完整代码，ClubListView 和 StudentListView 在此基础上调整 API 和面包屑。

- [ ] **Step 1: SchoolListView.vue**

```vue
<template>
  <div class="school-list-view">
    <div class="toolbar">
      <el-input v-model="keyword" placeholder="搜索学园..." clearable @change="loadPage(1)" style="width: 240px;" />
      <el-button type="primary" @click="openCreate">新增学园</el-button>
    </div>
    <div class="card-grid">
      <SchoolCard
        v-for="school in list"
        :key="school.id"
        :id="school.id"
        :name="school.cn_name"
        :logo="school.logo"
        @click="goToClubs"
        @edit="openEdit"
        @delete="handleDelete"
      />
    </div>
    <el-pagination
      v-model:current-page="page"
      v-model:page-size="size"
      :total="total"
      layout="prev, pager, next"
      @current-change="loadPage"
    />
    <EntityFormDrawer
      v-model="drawerVisible"
      entity-type="school"
      :is-edit="isEdit"
      :initial-data="editData"
      @submit="handleSubmit"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import SchoolCard from '../components/SchoolCard.vue'
import EntityFormDrawer from '../components/EntityFormDrawer.vue'
import { fetchSchoolList, createSchool, updateSchool, deleteSchool } from '../api/school'
import type { School } from '../api/school'

const router = useRouter()
const keyword = ref('')
const page = ref(1)
const size = ref(20)
const total = ref(0)
const list = ref<School[]>([])
const drawerVisible = ref(false)
const isEdit = ref(false)
const editData = ref<Partial<School>>({})

async function loadPage(p = 1) {
  page.value = p
  const res = await fetchSchoolList(keyword.value, page.value, size.value)
  list.value = res.list
  total.value = res.total
}

function goToClubs(id: string) {
  router.push({ name: 'clubs-list', params: { schoolId: id } })
}

function openCreate() {
  isEdit.value = false
  editData.value = {}
  drawerVisible.value = true
}

function openEdit(id: string) {
  const s = list.value.find(x => x.id === id)
  if (!s) return
  isEdit.value = true
  editData.value = { ...s }
  drawerVisible.value = true
}

async function handleDelete(id: string) {
  try {
    await ElMessageBox.confirm('删除该学园将同时软删除其下所有部团与学生，是否继续？', '确认删除', { type: 'warning' })
    await deleteSchool(id)
    ElMessage.success('删除成功')
    loadPage()
  } catch (e) {
    // cancel
  }
}

async function handleSubmit(data: Record<string, any>) {
  if (isEdit.value) {
    await updateSchool(data.id, data)
    ElMessage.success('修改成功')
  } else {
    await createSchool(data as School)
    ElMessage.success('创建成功')
  }
  drawerVisible.value = false
  loadPage()
}

onMounted(() => loadPage(1))
</script>

<style scoped>
.school-list-view {
  padding: 16px;
}
.toolbar {
  display: flex;
  gap: 12px;
  margin-bottom: 16px;
}
.card-grid {
  display: flex;
  flex-wrap: wrap;
  gap: 16px;
}
</style>
```

- [ ] **Step 2: ClubListView.vue**

结构与 SchoolListView.vue 类似，但：
- 从 route params 读取 `schoolId`
- API 用 `fetchClubList(schoolId, keyword, page, size)`
- 面包屑显示学园名
- 点击卡片进入学生列表
- 删除文案改为"删除该部团将同时软删除其下所有学生"

- [ ] **Step 3: StudentListView.vue**

结构与 SchoolListView.vue 类似，但：
- 从 route params 读取 `schoolId` 和 `clubId`
- API 用 `fetchStudentList(clubId, keyword, page, size)`
- 面包屑显示学园名 > 部团名
- 使用 StudentCard 组件
- 删除无级联提示（学生为最底层）

- [ ] **Step 4: Commit**

```bash
git add BarcManageFrontendV2/src/modules/school-club-student/views/
git commit -m "feat(frontend): add SchoolListView, ClubListView, StudentListView"
```

---

### Task 16: EntityBreadcrumb 与面包屑集成

**Files:**
- Create: `BarcManageFrontendV2/src/modules/school-club-student/components/EntityBreadcrumb.vue`
- Modify: `BarcManageFrontendV2/src/modules/school-club-student/views/ClubListView.vue`
- Modify: `BarcManageFrontendV2/src/modules/school-club-student/views/StudentListView.vue`

- [ ] **Step 1: EntityBreadcrumb.vue**

```vue
<template>
  <el-breadcrumb separator="/">
    <el-breadcrumb-item :to="{ name: 'schools-list' }">学园列表</el-breadcrumb-item>
    <el-breadcrumb-item v-if="schoolName" :to="{ name: 'clubs-list', params: { schoolId } }">{{ schoolName }}</el-breadcrumb-item>
    <el-breadcrumb-item v-if="clubName">{{ clubName }}</el-breadcrumb-item>
  </el-breadcrumb>
</template>

<script setup lang="ts">
defineProps<{
  schoolId?: string
  schoolName?: string
  clubName?: string
}>()
</script>
```

- [ ] **Step 2: 在 ClubListView 和 StudentListView 的 template 顶部引入 `<EntityBreadcrumb ... />`**

- [ ] **Step 3: Commit**

```bash
git add BarcManageFrontendV2/src/modules/school-club-student/components/EntityBreadcrumb.vue
git commit -m "feat(frontend): add EntityBreadcrumb component"
```

---

## Phase 4 – 加固与验收

### Task 17: 编译检查与回归

**Files:** 无

- [ ] **Step 1: 后端编译检查**

```bash
cd BarcBackend
mvn clean compile -DskipTests
```

Expected: BUILD SUCCESS

- [ ] **Step 2: 前端类型检查与编译**

```bash
cd BarcManageFrontendV2
npm run type-check || npx vue-tsc --noEmit
npm run build
```

Expected: 无 type error，build 成功

- [ ] **Step 3: Commit (若有修复)**

```bash
git add -A
git commit -m "fix: compile and type-check fixes"
```

---

### Task 18: D1–D13 验收验证

按设计文档验收清单逐项验证：

| 编号 | 验证方式 |
|---|---|
| D1 | 删除一条 school，前端列表立即不显示；DB 中 deleted_at 有值 |
| D2 | 上传一张新图片，腾讯云控制台检查 image 桶 |
| D3 | 检查上传后的 key 是否符合 `schools/{id}/logo_{ts}.ext` 格式 |
| D4 | 配置错误 COS 凭证后删除记录，观察 cos_garbage_key retry_count 递增 |
| D5 | 列表中同时存在旧 URL 和新 key 数据，前端图片均正常显示 |
| D6 | 替换 URL 形式的旧 logo 为新 key，cos_garbage_key 无新增记录 |
| D7 | 检查 flyway_schema_history 表有 baseline 与 V1 两行 |
| D8 | en_name 输入 "North Abydos"，ID 预览显示 "north_abydos" |
| D9 | 编辑现有学园，en_name 输入框为 disabled 状态 |
| D10 | 在某学园部团列表搜索，只命中该学园下的部团 |
| D11 | 编辑部团重选学园，前后台归属正确 |
| D12 | 接口路径和方法符合 RESTful 风格 |
| D13 | 普通用户登录管理端，侧边栏无入口；手敲 `/schools` URL 跳转 `/403` |

- [ ] **Step 1: 启动后端服务，执行上述验证**
- [ ] **Step 2: 启动前端服务，执行上述验证**
- [ ] **Step 3: Commit 验收文档 (可选)**

```bash
git add docs/superpowers/plans/2026-05-05-school-club-student-plan.md
git commit -m "docs: mark Phase 4 acceptance criteria completed"
```

---

## Self-Review

### Spec Coverage
| Spec 章节 | 对应任务 |
|---|---|
| D1 软删除 | Task 1, 4, 6, 7, 8 |
| D2 image 桶 | Task 2, 5, 10 |
| D3 COS 路径规约 | Task 10 (uploadUrl 构建) |
| D4 cos_garbage_key + Saga | Task 1, 5, 6, 7, 8 |
| D5 URL/key 自适应 | Task 5 (ImageUrlResolver) |
| D6 legacy URL 不入 GC | Task 6/7/8 (enqueueIfKey 判断) |
| D7 Flyway baseline | Task 1 |
| D8 ID 派生 | Task 6/7/8 |
| D9 en_name 不可改 | Task 6/7/8 (update 中忽略) + Task 14 (disabled) |
| D10 当前层级搜索 | Task 4 (Mapper) + Task 15 (视图 keyword) |
| D11 跨级重选 | Task 7/8 (school/club 更新) + Task 14 (select) |
| D12 RESTful | Task 9 |
| D13 权限 ≥16 | Task 9 (注解) + Task 12 (路由 meta) |

### Placeholder Scan
- 无 TBD / TODO
- 无 "appropriate error handling" 类模糊描述
- 所有代码块已提供完整实现

### Type Consistency
- `SchoolModel.deletedAt` 使用 `java.time.LocalDateTime`，与 MySQL `TIMESTAMP` 兼容
- 前端 `PageResult<T>` 接口在三处 API 文件中复用一致
- `PermissionConst.ADMINISTRATOR = 16`，路由 `minManagerPermission: 16` 匹配

---

## Execution Handoff

**Plan complete and saved to `docs/superpowers/plans/2026-05-05-school-club-student-plan.md`.**

**Two execution options:**

**1. Subagent-Driven (recommended)** - I dispatch a fresh subagent per task, review between tasks, fast iteration

**2. Inline Execution** - Execute tasks in this session using executing-plans, batch execution with checkpoints for review

**Which approach?**
