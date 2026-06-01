# 学园 / 部团 / 学生 增删改查功能设计

- **创建日期**: 2026-05-05
- **作用范围**: BarcBackend(后端 API + 数据库迁移)、BarcManageFrontendV2(管理端 V2 界面)
- **不影响**: BarcFrontend(前台门户)、Naigos 联合体系
- **状态**: 设计已确认,等待实施计划(writing-plans)

---

## 1. 背景与目标

学园(school)、部团(club)、学生(student)目前只在前台门户展示,管理端缺少增删改查能力。
本期为 V2 管理端增加这三个实体的全套 CRUD,并把图片资源接入腾讯云 COS 私有桶,纳入既有的鉴权 / 签名 URL 体系。

### 受众
- **副馆长(ADMINISTRATOR=16)** 和 **馆长(ADVANCED_ADMINISTRATOR=32)** 可以使用
- 其他权限值(普通用户、初级馆员等)既看不到入口,也无法通过 URL 强行进入

### 数据库环境
- 当前使用的数据库 **就是生产环境数据库**,严禁清库或破坏现有数据
- 现有图片仍以完整 URL 形式保存,本期对新写入的数据切换为 **COS object key**,旧数据保持兼容显示

---

## 2. 关键决策(D1–D13)

按确认顺序登记,后续实施计划严格遵循。

| 编号 | 决策 | 备注 |
|---|---|---|
| **D1** | **软删除** | 三表加 `deleted_at TIMESTAMP NULL DEFAULT NULL`,`NULL` 视为存活 |
| **D2** | **使用 image 桶**(`miaoyu-barc-image-1309572720`) | 自定义域名 `https://image-cos-9c98ec3e.barc.work` |
| **D3** | **COS 路径规约** | `schools/{id}/{field}_{ts}.{ext}`、`clubs/{id}/{field}_{ts}.{ext}`、`students/{id}/{field}_{ts}.{ext}` |
| **D4** | **主表存 key + cos_garbage_key 队列** | Saga 模式,DB 成功后再异步清理 COS,失败重试 |
| **D5** | **URL/key 自适应回显** | 字段以 `https://` 开头时直接返给前端;否则当 key 处理,后端拼签名 URL |
| **D6** | **legacy URL 不入 GC 队列** | 旧的 URL 形式被替换/删除时不进 cos_garbage_key,留给未来统一治理 |
| **D7** | **Flyway 迁移** | `baseline-on-migrate=true` + `baseline-version=0`,保护生产库不被 DDL 重建 |
| **D8** | **ID 派生规则** | `en_name` 大写转小写、空格转下划线 → `id`;例:`North Abydos` → `north_abydos` |
| **D9** | **en_name 创建后不可修改** | 否则 ID 漂移,COS 路径与 DB 主键脱钩 |
| **D10** | **搜索按当前层级** | 学园列表只搜学园名;部团列表只搜该学园下的部团名;学生列表只搜该部团下的学生名 |
| **D11** | **修改时允许重选上级** | 修改部团可改属于哪个学园;修改学生可改属于哪个部团并同步 school 冗余字段 |
| **D12** | **RESTful API**(POST/PUT/DELETE) | 与 `work` 模块一致 |
| **D13** | **侧边栏 / 路由 / API 写权限 ≥ 16** | `minManagerPermission: 16`;前后端双重校验 |

---

## 3. 数据层设计

### 3.1 现有表(对照 mapper 实际查询的物理名)
| 实体 | 物理表名(权威) | schema.sql 中的 DDL(stale,未执行) |
|---|---|---|
| 学园 | **`schools`**(复数) | `school`(单数,过期) |
| 部团 | **`club`**(单数) | `club`(单数) |
| 学生 | **`students`**(复数) | `student`(单数,过期) |
| 学园-部团关联 | `school_club`(已存在) | `school_club` |

> **关键事实**:`schema.sql` 与 mapper 查询不一致是历史遗留。生产库实际表名以 mapper 的 `FROM xxx` 为准。本期不修复 schema.sql,只新增 Flyway 迁移,迁移文件操作 **schools / club / students** 三个真实表名。

### 3.2 字段映射

#### schools(学园)
| 列 | 类型 | 用途 |
|---|---|---|
| id | VARCHAR(200) PK | 由 en_name 派生(D8) |
| cn_name | VARCHAR(100) NOT NULL | 列表/卡片显示 |
| jp_name / kr_name | VARCHAR(100) NULL | 多语言 |
| en_name | VARCHAR(255) NULL | **本期改为必填** + 驱动 ID |
| introduce | TEXT NULL | 详情页 |
| logo | VARCHAR(255) NULL | **URL 或 key**(D5) |
| beautify_logo | VARCHAR(255) NULL | **URL 或 key** |
| bg | VARCHAR(255) NULL | **URL 或 key** |
| **deleted_at** | TIMESTAMP NULL | **新增,软删除标记**(D1) |

#### club(部团)
| 列 | 类型 | 用途 |
|---|---|---|
| id | VARCHAR(200) PK | 由 en_name 派生 |
| school | VARCHAR(200) NULL | 冗余字段;主关系走 `school_club` 表 |
| cn_name / jp_name / kr_name / en_name | 同上 | en_name 必填 |
| logo / bg | VARCHAR(255) NULL | URL 或 key |
| **deleted_at** | TIMESTAMP NULL | **新增** |

#### students(学生)
| 列 | 类型 | 用途 |
|---|---|---|
| id | VARCHAR(255) PK | 由 en_name 派生 |
| cn_name / jp_name / kr_name / en_name | 同上 | en_name 必填 |
| introduce | TEXT NULL | 详情 |
| avatar_square / avatar_rectangle / body_image | VARCHAR(255) | URL 或 key |
| school | VARCHAR(100) NULL | 冗余字段 |
| club | VARCHAR(100) NOT NULL | FK → club.id |
| **deleted_at** | TIMESTAMP NULL | **新增** |

#### cos_garbage_key(新表,Saga 队列)
```sql
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

### 3.3 Flyway 迁移文件
- **位置**: `BarcBackend/src/main/resources/db/migration/`
- **文件名**: `V1__add_soft_delete_and_gc.sql`
- **配置**(`application.yaml`):
  ```yaml
  spring:
    flyway:
      enabled: true
      baseline-on-migrate: true
      baseline-version: 0
      locations: classpath:db/migration
  ```
- **首次启动**: Flyway 把当前生产库标为 baseline,然后跑 V1 → 仅 ALTER + CREATE,不 DROP,不重建。

### 3.4 软删除级联(事务内)
- 删除一个 school → 同事务内将其下所有 club 与 student 的 `deleted_at` 一并设置
- 删除一个 club → 同事务内将其下所有 student 的 `deleted_at` 一并设置
- 不动 `school_club` 关联表(保留还原可能)
- 所有现有 SELECT 全部补 `WHERE deleted_at IS NULL`
- **影响范围**:`SchoolMapper`、`ClubMapper`、`StudentMapper`,以及前台门户的相关查询

---

## 4. 对象存储层设计

### 4.1 必须先修的 Bug
**`CosService.uploadFile / deleteFile / generateSignedUrl` 现状**:无视 `clientName` 参数,统一用 `cosConfig.getBucketName()`(test 桶)。
**修复**:按 `clientName` 取对应 `CosBucketPojo.bucketName`:
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
此修复是本期所有新功能的前置依赖。

### 4.2 路径规约(D3)
| 实体 | 字段 | 路径模板 |
|---|---|---|
| school | logo | `schools/{id}/logo_{ts}.{ext}` |
| school | beautify_logo | `schools/{id}/beautify_logo_{ts}.{ext}` |
| school | bg | `schools/{id}/bg_{ts}.{ext}` |
| club | logo | `clubs/{id}/logo_{ts}.{ext}` |
| club | bg | `clubs/{id}/bg_{ts}.{ext}` |
| student | avatar_square | `students/{id}/avatar_square_{ts}.{ext}` |
| student | avatar_rectangle | `students/{id}/avatar_rectangle_{ts}.{ext}` |
| student | body_image | `students/{id}/body_image_{ts}.{ext}` |

`{ts}` = `System.currentTimeMillis()`,`{ext}` = 原始扩展名。

### 4.3 上传流程
1. 前端在 Drawer 里填 en_name → 立即派生 id 预览
2. 选图 → 调 `POST /api/file/upload?path=schools/{id}/logo_` (后端拼 ts + ext) → 返回 object key
3. 前端将 key 暂存到表单 state
4. 提交表单 → 后端落库 key

### 4.4 GC 队列(D4 / Saga)
- **入队场景**:
  - 修改图片字段时,旧值若是 key(非 URL,D6) → 旧 key 入队
  - 软删除实体时,所有 key 字段全部入队
  - 硬删除(本期不开 UI)同上
- **不入队场景**:旧值是 `https://` 开头的 URL(D6,留作未来统一治理)
- **消费**: `CosGcScheduler` `@Scheduled(fixedDelay = 5 * 60 * 1000)`
  - 查 `processed_at IS NULL ORDER BY created_at LIMIT N`
  - 调 COS 删除
  - 成功 → `UPDATE processed_at = NOW()`
  - 失败 → `retry_count++`,`last_error = e.getMessage()`,下次再试

### 4.5 URL/key 自适应回显(D5)
后端工具方法:
```java
public String resolve(String value) {
    if (value == null || value.isEmpty()) return null;
    if (value.startsWith("https://") || value.startsWith("http://")) return value;
    return cosService.generateSignedUrl(value, expireDate, CosBucketConfigEnum.image);
}
```
所有列表 / 详情接口在序列化前对图片字段过一遍 `resolve()`,前端不感知差异。

---

## 5. 后端 API 设计

### 5.1 路由表(全部 RESTful,D12)
| 方法 | 路径 | 说明 | 权限 |
|---|---|---|---|
| GET | `/api/school/list?keyword=&page=&size=` | 分页 + 关键词搜索(模糊匹配 cn/jp/kr/en_name) | 公开(只读) |
| GET | `/api/school/{id}` | 详情 | 公开 |
| POST | `/api/school` | 新建 | ≥16 |
| PUT | `/api/school/{id}` | 修改(请求体若含 en_name 一律忽略,见 5.2) | ≥16 |
| DELETE | `/api/school/{id}` | 软删除(级联) | ≥16 |
| GET | `/api/school/check_id_available?id=xxx` | 检查派生 ID 是否冲突 | ≥16 |
| GET | `/api/club/list?school_id=xxx&keyword=&page=&size=` | 列出某学园下的部团 + 当前层关键词搜索 | 公开 |
| GET | `/api/club/{id}` | 详情 | 公开 |
| POST | `/api/club` | 新建(请求体必带 school_id) | ≥16 |
| PUT | `/api/club/{id}` | 修改(可改 school_id;en_name 忽略) | ≥16 |
| DELETE | `/api/club/{id}` | 软删除(级联到 student) | ≥16 |
| GET | `/api/club/check_id_available?id=xxx` | 检查派生 ID 是否冲突 | ≥16 |
| GET | `/api/student/list?club_id=xxx&keyword=&page=&size=` | 列出某部团下的学生 + 当前层关键词搜索 | 公开 |
| GET | `/api/student/{id}` | 详情 | 公开 |
| POST | `/api/student` | 新建(请求体必带 club_id) | ≥16 |
| PUT | `/api/student/{id}` | 修改(可改 club_id;en_name 忽略;后端自动同步 school 冗余) | ≥16 |
| DELETE | `/api/student/{id}` | 软删除 | ≥16 |
| GET | `/api/student/check_id_available?id=xxx` | 检查派生 ID 是否冲突 | ≥16 |

### 5.2 写接口校验
- `en_name` 必填、非空、正则 `^[A-Za-z][A-Za-z0-9 ]{0,254}$`
- 派生 ID `name.toLowerCase().replace(' ', '_')`
- 创建时 ID 唯一(主键冲突 → 409)
- 修改时不接受 en_name 字段:**后端静默忽略**(不返回错误),与前端 readonly 的双重约束(D9)
- club POST/PUT 必须带 school_id 参数;同时写 school_club 关联表
- student POST/PUT 必须带 club_id;后端反查 club.school 同步到 student.school 冗余字段

### 5.3 切面 / 鉴权
所有写接口加注解:
```java
@RequireUserAndPermissionAnno({ @Check(permission = PermissionConst.ADMINISTRATOR) })
```
读接口保持公开(沿用现有前台对接习惯)。

---

## 6. 前端模块设计(BarcManageFrontendV2)

### 6.1 模块布局
```
src/modules/school-club-student/
├─ views/
│  ├─ SchoolListView.vue         /schools
│  ├─ ClubListView.vue            /schools/:schoolId/clubs
│  └─ StudentListView.vue         /schools/:schoolId/clubs/:clubId/students
├─ components/
│  ├─ SchoolCard.vue              200×200 卡片
│  ├─ ClubCard.vue                200×200 卡片
│  ├─ StudentCard.vue             96×128 卡片(对齐前台 HomeUpdatedComp)
│  ├─ EntityFormDrawer.vue        统一新增/修改弹窗
│  ├─ ImageUploadField.vue        URL/key 自适应上传字段
│  └─ EntityBreadcrumb.vue        三级面包屑
├─ api/
│  ├─ school.ts
│  ├─ club.ts
│  └─ student.ts
└─ store/
   └─ entityCache.ts              缓存 entity 名,供面包屑读取
```

### 6.2 路由配置
```ts
{
  path: '/schools',
  meta: {
    requiresAuth: true,
    requiresManager: true,
    minManagerPermission: 16,    // D13
    menuLabel: '学园、部团、学生',
    menuGroup: '内容管理',
    menuOrder: 50,
  },
  component: SchoolListView,
}
```
权限不足时:
- 侧边栏 `permissionStore.canAccessRoute()` 过滤掉,不显示
- 强行 URL 进入 → router `beforeEach` 拦截 → `ElMessage.error('权限不足')` + `router.replace('/403')`

### 6.3 卡片样式
- **学园 / 部团卡片**:200×200,1:1,显示 logo + cn_name 浮层
- **学生卡片**:96×128,严格按前台 `BarcFrontend/src/components/Home/HomeUpdatedComp.vue`:
  - `border: white .2rem solid; border-radius: .5rem; transition: .3s ease`
  - hover → 白底
  - `.name` 绝对定位底部
- 每张卡片右上角 `el-dropdown` 触发 ⋯ → "修改" / "删除"
- 删除 → `ElMessageBox.confirm`,文案体现级联("将同时软删除其下所有部团与学生")

### 6.4 表单 Drawer
- 宽 480px
- 字段顺序:en_name(创建时必填,编辑时只读)→ ID 实时预览 → cn_name → jp_name → kr_name → introduce → 图片字段(每个字段一个 ImageUploadField)
- en_name blur → 调 `check_id_available` 实时校验冲突
- 非法 / 冲突 → 提交按钮禁用 + 红色提示

### 6.5 ImageUploadField 行为
| 输入值类型 | 预览方式 | 替换图片时旧值处理 |
|---|---|---|
| `https://...`(legacy URL) | 直接当 `<img src>` | 提交后端,后端不入 GC 队列(D6) |
| 纯 key(如 `schools/abc/logo_1234.png`) | 已由后端拼成签名 URL 显示 | 提交后端,后端入 GC 队列 |
| 空 | 显示 "上传" 按钮 | — |

### 6.6 搜索(D10)
- 每个 List 视图顶部一个搜索框
- 学园列表:输入串 → `GET /api/school/list?keyword=`,模糊匹配 cn/jp/kr/en_name
- 部团列表:`?school_id=xx&keyword=`,只在该学园内搜
- 学生列表:`?club_id=xx&keyword=`,只在该部团内搜

### 6.7 跨级重选(D11)
- 编辑 club 时表单内有 `<el-select>` "所属学园",可重选
- 编辑 student 时有 `<el-select>` "所属部团",可重选;后端自动同步 school 冗余

---

## 7. 错误处理

| 场景 | HTTP / code | 前端表现 |
|---|---|---|
| 业务失败(参数、唯一冲突等) | 200 + code:1 | 提示文案,表单不清空 |
| 未登录 / token 过期 | 401 | 跳 /login |
| 已登录但权限不足 | 403 | toast + 跳 /403 |
| 资源不存在 | 404 | 列表自动刷新 |
| en_name 重复 / ID 冲突 | 409 | 输入框红框 + 提示 |
| 服务器异常 | 500 | "服务异常,请稍后重试" |
| COS 上传失败 | 200 + code:1 | 表单状态保留,允许重试 |
| COS 删除失败(改图 / 删除时) | 不影响 DB 落库 | 进 GC 队列,日志记录,用户无感 |

---

## 8. ✅ 明确做(In Scope,本期交付)

### 8.1 数据层
- [x] 集成 Flyway,baseline-on-migrate=true,baseline-version=0
- [x] 新增 `V1__add_soft_delete_and_gc.sql`
- [x] schools / club / students 三表加 `deleted_at`
- [x] 新增 `cos_garbage_key` 表
- [x] 所有现有 SELECT 补 `WHERE deleted_at IS NULL`(影响前台门户与管理端列表查询)

### 8.2 对象存储层
- [x] 修复 CosService 的 bucketName 不按 clientName 解析的 Bug
- [x] 新增 `CosGcService.enqueue(bucket, key)`
- [x] 新增 `CosGcScheduler`,5 分钟定时消费
- [x] 路径规约 schools/{id}/{field}_{ts}.{ext} 等
- [x] 上传走 image 桶

### 8.3 后端 API
- [x] SchoolController / ClubController / StudentController(REST 风格)
- [x] 写接口加 `@RequireUserAndPermissionAnno(permission=16)`
- [x] en_name 必填 + 派生 ID + 唯一校验
- [x] `check_id_available` 端点
- [x] URL/key 自适应回显工具(`ImageUrlResolver`)
- [x] 软删除事务级联(school → club → student)
- [x] 软删除时所有 key 字段入 GC 队列(URL 形式不入)
- [x] 列表分页 + 当前层级搜索
- [x] club PUT 支持改 school;student PUT 支持改 club 并同步 school 冗余

### 8.4 V2 前端
- [x] 新模块 `school-club-student`
- [x] 三级路由 + 自动侧边栏
- [x] 权限 ≥16 才能看到入口;强行 URL 进入会被弹回 + toast
- [x] 学园 / 部团卡片 200×200,学生卡片 96×128(对齐前台 HomeUpdatedComp)
- [x] 三点 ⋯ 菜单 → 修改 / 删除
- [x] EntityFormDrawer 统一新增/编辑
- [x] ImageUploadField 组件,URL/key 自适应回显
- [x] en_name 创建时必填、blur 实时检查唯一;编辑时只读
- [x] ID 实时预览
- [x] 当前层级搜索框
- [x] 跨级重选上级
- [x] 删除二次确认,文案体现级联软删

---

## 9. ❌ 明确不做(Out of Scope,留待未来)

> 这些条目是 **本期主动决定不做** 的事项,记录在此以便未来需要时能回查决策依据。
> 列入此节不代表"以后不能做",只代表"本期不做"。

### 9.1 历史数据治理(留作专项工单)
- **legacy URL 形式数据的反向迁移**:不批量将旧 `https://` 字段下载并搬到 image 桶,等所有图片自然迭代为 key 后再做(决策依据 D6)。
- **legacy URL 不入 GC 队列**:本期编辑或删除一个仍是 URL 形式的字段时,旧 URL 不会进入 cos_garbage_key,因为它根本不在 image 桶里(决策依据 D6)。

### 9.2 孤儿文件治理(留作"上传即入 GC,提交摘除"工单)
- **用户在 Drawer 里上传后中途关闭弹窗**:此时已上传到 COS 的新文件就成为孤儿。本期接受。
- **未来方案**(占位):上传时立即写一行 cos_garbage_key,但 `created_at + 24h` 才允许被消费;提交表单成功时摘掉那行;过期未摘除的自动清理。

### 9.3 删除能力
- **硬删除 UI**:本期只提供软删除。如需恢复或彻底物理删除,直接在数据库手动操作:
  - 恢复:`UPDATE schools SET deleted_at = NULL WHERE id = ?;`(级联恢复需手动逐表)
  - 硬删:DBA 手动 `DELETE`,key 手动塞入 cos_garbage_key
- **批量删除 / 批量编辑**:本期不做,只支持单条操作。

### 9.4 监控与告警
- **GC 队列失败告警**(邮件、钉钉、企业微信等):只写日志,不外发通知。
- **GC 队列后台监控页**:不在管理端做可视化页面,DBA 直查 `cos_garbage_key` 表。

### 9.5 学生跨学园批量调整
- 不做"把整个部团从 A 学园搬到 B 学园"的批量操作。
- 不做"把多个学生一次性挪到另一个部团"的批量操作。
- 单条修改足够覆盖本期需求。

### 9.6 多图同时上传的进度条
- 学生一张张上传 avatar_square / avatar_rectangle / body_image,逐张转圈即可。
- 不做"3 张图同时上传 + 总进度条"。

### 9.7 schema.sql 同步
- **不修复 `schema.sql` 与 mapper 实际表名(schools / club / students)不一致的问题**。
- 原因:`schema.sql` 因 `IF NOT EXISTS` 在生产库已无实际作用,本期不动它降低风险。
- 未来若彻底切换到 Flyway,再统一清理 schema.sql。

### 9.8 自定义域名 / CDN 加速调优
- 沿用现有 `image-cos-9c98ec3e.barc.work`,不做新的 CDN 接入。

### 9.9 前台门户改造
- 不修改前台 BarcFrontend 任何代码。
- 软删除后前台自动看不到该数据(因为现有 SELECT 已加 `WHERE deleted_at IS NULL`)。

---

## 10. 验收清单

按 D1–D13 逐项映射,实施完成时逐条勾选。

- [ ] **D1**: 软删除一条 school,前端列表立即不显示;DB 中数据保留;手动改 deleted_at=NULL 后恢复
- [ ] **D2**: 上传一张新图片,在腾讯云控制台能看到文件出现在 image 桶下
- [ ] **D3**: 上传的 key 形如 `schools/north_abydos/logo_1714912345678.png`
- [ ] **D4**: 故意配置错的 COS 凭证 → 删除一条记录 → DB 删除成功 + cos_garbage_key 记录新增 + retry_count 在每 5 分钟递增
- [ ] **D5**: 列表中既存在旧 URL 数据又存在新 key 数据,前端表现一致(都正常显示)
- [ ] **D6**: 替换一个 URL 形式的旧 logo 为新 key → cos_garbage_key 没有新增记录
- [ ] **D7**: 重启后端,查 `flyway_schema_history` 表有 baseline 与 V1 两行;生产数据 0 丢失
- [ ] **D8**: en_name 输入 "North Abydos" → ID 预览 "north_abydos"
- [ ] **D9**: 编辑现有学园,en_name 字段是 readonly,无法修改
- [ ] **D10**: 在某学园的部团列表里搜索关键字,只命中该学园下的部团,不会跨学园
- [ ] **D11**: 编辑一个部团,把"所属学园"从 A 改为 B → 前后台都正确归属 B
- [ ] **D12**: 接口路径 / 方法符合 work 模块的 RESTful 风格
- [ ] **D13**: 普通用户(权限 1)登录管理端 → 侧边栏看不到入口 → 手敲 `/schools` URL → 弹"权限不足" + 跳 /403

---

## 11. 实施顺序(4 阶段)

**Phase 1 – 基础设施**(无业务可见变化,但风险最高,需要先充分回归)
1. 集成 Flyway + baseline 配置
2. `V1__add_soft_delete_and_gc.sql`(deleted_at + cos_garbage_key)
3. **修复 CosService 的 bucketName Bug**
4. 现有 Mapper 全量补 `WHERE deleted_at IS NULL`(grep 全仓 `FROM schools|FROM club|FROM students`)
5. CosGcService + CosGcScheduler

**Phase 2 – 后端 CRUD**
1. ImageUrlResolver
2. SchoolController/Service/Mapper(增/删/改/查/搜索/check_id)
3. ClubController/Service/Mapper(同上 + school 重选)
4. StudentController/Service/Mapper(同上 + club 重选 + school 冗余同步)
5. 上传接口路径调整

**Phase 3 – V2 前端**
1. 模块脚手架 + 路由 + 侧边栏 + 权限守卫
2. 三个 ListView + Card 组件
3. EntityFormDrawer + ImageUploadField + ID 预览
4. ⋯ 菜单 + ElMessageBox 删除
5. 面包屑 + 搜索 + 跨级重选

**Phase 4 – 加固**
1. 浏览器全功能回归(用户自测)
2. (可选)GC 队列后台页 — 列入 9.4,本期不做

---

## 12. 风险与缓解

| 风险 | 缓解 |
|---|---|
| Flyway baseline 误清生产库 | `baseline-on-migrate=true` + `baseline-version=0`,DBA 备份后开启 |
| Mapper 漏补 `deleted_at IS NULL` 导致前台显示已删数据 | grep 全仓所有 `FROM schools|FROM club|FROM students` 逐个核对 |
| 上传后用户取消 → 孤儿文件 | 列入 9.2,本期接受 |
| en_name 历史数据冲突 | 上线前 SQL 抽样,人工迁移 |
| CosService bug 修复影响其他模块 | bug 修完先在 dev 环境跑既有上传 / 头像 / 删除路径回归 |

---

## 附录 A:相关文件路径

### 后端
- 实体模型:`BarcBackend/src/main/java/com/miaoyu/barc/api/model/{School,SchoolClub,Student}Model.java`
- Mapper:`BarcBackend/src/main/java/com/miaoyu/barc/api/mapper/{School,Club,Student}Mapper.java`
- COS 工具:`BarcBackend/src/main/java/com/miaoyu/barc/utils/tencent/cos/{CosService,CosClient,CosConfig,CosBucketConfigEnum}.java`
- 权限切面:`BarcBackend/src/main/java/com/miaoyu/barc/annotation/RequireUserAndPermissionAnno.java`
- 权限常量:`BarcBackend/src/main/java/com/miaoyu/barc/permission/PermissionConst.java`
- 配置:`BarcBackend/src/main/resources/application.yaml`
- (新增)迁移目录:`BarcBackend/src/main/resources/db/migration/`

### 前端
- 路由:`BarcManageFrontendV2/src/app/router/{routes,index}.ts`
- 侧边栏:`BarcManageFrontendV2/src/app/components/AppSidebar.vue`
- 页面壳:`BarcManageFrontendV2/src/app/components/RoutePageShell.vue`
- HTTP 层:`BarcManageFrontendV2/src/shared/api/http.ts`
- 参考列表页:`BarcManageFrontendV2/src/modules/users/views/UsersListView.vue`
- 参考 Drawer:`BarcManageFrontendV2/src/modules/users/components/UserQuickCreateDrawer.vue`
- 学生卡参考:`BarcFrontend/src/components/Home/HomeUpdatedComp.vue`

### Bucket
- image 桶:`miaoyu-barc-image-1309572720`
- 自定义域名:`https://image-cos-9c98ec3e.barc.work`
