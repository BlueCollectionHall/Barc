# Barc 蔚蓝收录馆 - AI 开发指南

> 本文档面向 AI 开发者，补充实际操作层面的指南。结合「功能文档」和「结构文档」使用。

---

## 一、项目启动

### 1.1 前端启动

```bash
cd BarcFrontend
npm install
npm run dev
```

- 启动后访问：`http://localhost:5173`（Vite 默认端口）
- 环境变量文件：根目录 `.env` 或 `.env.local`

### 1.2 后端启动

```bash
cd BarcBackend
# 使用 Maven
./mvnw spring-boot:run

# 或打包后运行
./mvnw package
java -jar target/BarcBackend-0.1.14.jar
```

- 启动后访问：`http://localhost:51000`
- API 基础路径：`/api`

### 1.3 依赖服务

| 服务 | 地址 | 说明 |
|------|------|------|
| MySQL | jdbc:mysql://miaoyu.asia:51001/ba | 主数据库 |
| 腾讯云 COS | 需配置 | 统一文件存储（私有桶 + 签名 URL） |

---

## 二、新增后端 API

### 2.1 代码模板

假设要新增一个「公告」相关的 API：

#### Step 1: 创建 Model（数据模型）

```java
// 文件位置: src/main/java/com/miaoyu/barc/api/model/Notice.java
package com.miaoyu.barc.api.model;

import lombok.Data;

@Data
public class Notice {
    private String id;          // 主键 UUID
    private String title;       // 标题
    private String content;    // 内容
    private String author;      // 作者 UUID
    private String createdAt;   // 创建时间
    private String updatedAt;   // 更新时间
}
```

#### Step 2: 创建 Mapper（数据库操作）

```java
// 文件位置: src/main/java/com/miaoyu/barc/api/mapper/NoticeMapper.java
package com.miaoyu.barc.api.mapper;

import com.miaoyu.barc.api.model.Notice;
import org.apache.ibatis.annotations.*;

@Mapper
public interface NoticeMapper {
    
    @Select("SELECT * FROM notice WHERE id = #{id}")
    Notice findById(@Param("id") String id);
    
    @Select("SELECT * FROM notice ORDER BY created_at DESC")
    List<Notice> findAll();
    
    @Insert("INSERT INTO notice (id, title, content, author, created_at) " +
            "VALUES (#{id}, #{title}, #{content}, #{author}, NOW())")
    int insert(Notice notice);
    
    @Update("UPDATE notice SET title=#{title}, content=#{content}, updated_at=NOW() " +
            "WHERE id=#{id}")
    int update(Notice notice);
    
    @Delete("DELETE FROM notice WHERE id = #{id}")
    int deleteById(@Param("id") String id);
}
```

#### Step 3: 创建 Service（业务逻辑）

```java
// 文件位置: src/main/java/com/miaoyu/barc/api/service/NoticeService.java
package com.miaoyu.barc.api.service;

import com.miaoyu.barc.api.mapper.NoticeMapper;
import com.miaoyu.barc.api.model.Notice;
import com.miaoyu.barc.response.J;
import com.miaoyu.barc.response.ResourceR;
import com.miaoyu.barc.response.ChangeR;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

@Service
public class NoticeService {
    
    @Autowired
    private NoticeMapper noticeMapper;
    
    public ResponseEntity<J> getAllService() {
        List<Notice> list = noticeMapper.findAll();
        // ✅ 使用 ResourceR 获取资源
        return ResponseEntity.ok(new ResourceR().resourceSuch(true, list));
    }
    
    public ResponseEntity<J> getByIdService(String id) {
        Notice notice = noticeMapper.findById(id);
        if (notice == null) {
            return ResponseEntity.ok(new ResourceR().resourceSuch(false, null));
        }
        return ResponseEntity.ok(new ResourceR().resourceSuch(true, notice));
    }
    
    public ResponseEntity<J> insertService(Notice notice) {
        int result = noticeMapper.insert(notice);
        // ✅ 使用 ChangeR 表示变更操作，type=1 表示新增
        return ResponseEntity.ok(new ChangeR().udu(result > 0, 1));
    }
    
    public ResponseEntity<J> updateService(Notice notice) {
        int result = noticeMapper.update(notice);
        // ✅ type=3 表示修改
        return ResponseEntity.ok(new ChangeR().udu(result > 0, 3));
    }
    
    public ResponseEntity<J> deleteService(String id) {
        int result = noticeMapper.deleteById(id);
        // ✅ type=2 表示删除
        return ResponseEntity.ok(new ChangeR().udu(result > 0, 2));
    }
}
```

#### Step 4: 创建 Controller（接口）

```java
// 文件位置: src/main/java/com/miaoyu/barc/api/controller/NoticeController.java
package com.miaoyu.barc.api.controller;

import com.miaoyu.barc.annotation.IgnoreAuth;  // 不需要登录认证
import com.miaoyu.barc.api.service.NoticeService;
import com.miaoyu.barc.response.J;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/notice")
public class NoticeController {
    
    @Autowired
    private NoticeService noticeService;
    
    // 获取全部公告（公开接口）
    @IgnoreAuth
    @GetMapping("/all")
    public ResponseEntity<J> getAllControl() {
        return noticeService.getAllService();
    }
    
    // 获取单个公告（公开接口）
    @IgnoreAuth
    @GetMapping("/only")
    public ResponseEntity<J> getByIdControl(
            @RequestParam("id") String id) {
        return noticeService.getByIdService(id);
    }
    
    // 创建公告（需登录）
    @PostMapping("/create")
    public ResponseEntity<J> createControl(
            HttpServletRequest request,
            @RequestBody Notice notice) {
        // 从 JWT Token 中获取当前用户 UUID
        String uuid = request.getAttribute("uuid").toString();
        notice.setAuthor(uuid);
        return noticeService.insertService(notice);
    }
    
    // 修改公告（需登录）
    @PutMapping("/update")
    public ResponseEntity<J> updateControl(@RequestBody Notice notice) {
        return noticeService.updateService(notice);
    }
    
    // 删除公告（需登录）
    @DeleteMapping("/delete")
    public ResponseEntity<J> deleteControl(@RequestParam("id") String id) {
        return noticeService.deleteService(id);
    }
}
```

### 2.2 注解说明

| 注解 | 位置 | 说明 |
|------|------|------|
| `@IgnoreAuth` | Controller 方法 | 跳过 JWT 认证（公开接口） |
| `@ApiPath` | Controller 类 | 自定义 API 路径前缀 |
| `@RequireUserAndPermissionAnno` | Controller 方法 | 权限验证 |
| `@Mapper` | Mapper 接口 | MyBatis 扫描识别 |
| `@Service` | Service 类 | Spring 注入 |
| `@Autowired` | 注入点 | 依赖注入 |

### 2.3 响应格式（重要！）

**请使用预定义的语义化响应类，不要直接操作 J 类！**

```java
// ✅ 获取资源 - 使用 ResourceR
return ResponseEntity.ok(new ResourceR().resourceSuch(true, data));
return ResponseEntity.ok(new ResourceR().resourceSuch(false, null));

// ✅ 变更操作 - 使用 ChangeR (type: 1=新增, 2=删除, 3=修改)
return ResponseEntity.ok(new ChangeR().udu(true, 1));  // 新增成功
return ResponseEntity.ok(new ChangeR().udu(false, 2)); // 删除失败

// ✅ 用户错误 - 使用 UserR
return ResponseEntity.ok(new UserR().noSuchUser());  // 用户不存在
return ResponseEntity.ok(new UserR().insufficientAccountPermission()); // 权限不足

// ❌ 避免这样写（虽然 J 类支持链式调用）
return ResponseEntity.ok(new J().data(obj).success(true));
```

---

## 三、新增前端页面

### 3.1 代码模板

假设要新增一个「排行榜」页面：

#### Step 1: 创建路由文件

```typescript
// 文件位置: src/router/RankRouter.ts
import RankView from "@/views/RankView.vue";

export default {
  path: "/rank",
  component: RankView,
  children: [
    {
      path: "",
      name: "RankHome",
      component: () => import("@/components/Rank/RankHomeComp.vue")
    }
  ]
}
```

#### Step 2: 注册路由

```typescript
// src/router/index.ts
import RankRouter from "@/router/RankRouter.ts";

// 在 routes 数组中添加
const router = createRouter({
  routes: [
    // ... 其他路由
    RankRouter,
  ],
})
```

#### Step 3: 创建 View 页面

```vue
<!-- 文件位置: src/views/RankView.vue -->
<script setup lang="ts">
import HeaderComp from "@/components/HeaderFooter/HeaderComp.vue";
import FooterComp from "@/components/HeaderFooter/FooterComp.vue";
</script>

<template>
  <HeaderComp/>
  <RouterView/>
  <FooterComp/>
</template>
```

#### Step 4: 创建组件

```vue
<!-- 文件位置: src/components/Rank/RankHomeComp.vue -->
<script setup lang="ts">
import { ref, onMounted } from 'vue'
import axios from 'axios'

const rankList = ref([])

onMounted(async () => {
  const res = await axios.get('/api/rank/all')
  rankList.value = res.data.data
})
</script>

<template>
  <div class="rank-container">
    <h1>排行榜</h1>
    <div v-for="item in rankList" :key="item.id" class="rank-item">
      {{ item.title }}
    </div>
  </div>
</template>

<style scoped>
.rank-container {
  max-width: 1200px;
  margin: 0 auto;
  padding: 20px;
}
</style>
```

#### Step 5: 定义 TypeScript 接口

```typescript
// 文件位置: src/interfaces/RankImpl.ts
export interface RankImpl {
  id: string
  title: string
  score: number
  createdAt: Date
}
```

---

## 四、常见任务示例

### 4.1 新增数据库表

在 `schema.sql` 中添加：

```sql
CREATE TABLE IF NOT EXISTS rank (
    id VARCHAR(36) PRIMARY KEY NOT NULL,
    title VARCHAR(100) NOT NULL,
    score INT DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_score (score)
);
```

> **注意**：添加表后需要联系 DBA 执行，或使用数据库迁移工具。

### 4.2 修改现有 API

1. 找到对应的 Controller
2. 添加/修改方法
3. 在 Service 中添加业务逻辑
4. 在 Mapper 中添加 SQL

### 4.3 添加前端下拉菜单项

在导航组件中找到菜单配置，添加路由链接：

```vue
<template>
  <a-menu>
    <a-menu-item key="rank" @click="$router.push('/rank')">
      排行榜
    </a-menu-item>
  </a-menu>
</template>
```

---

## 五、up_type 使用说明

### 5.1 概念

`up_type` 是统一的「上传/修改」标识，用于同一个 API 处理新增和更新。

| up_type | 含义 | SQL 操作 |
|---------|------|----------|
| `upload` | 新增 | INSERT |
| `update` | 更新 | UPDATE |

### 5.2 使用示例

```java
@PostMapping("/up")
public ResponseEntity<J> upSchoolControl(
        @RequestParam("up_type") String upType,
        @RequestBody School school) {
    
    if ("upload".equals(upType)) {
        school.setId(UUID.randomUUID().toString());
        schoolMapper.insert(school);
    } else if ("update".equals(upType)) {
        schoolMapper.update(school);
    }
    
    return ResponseEntity.ok(new J().success(true));
}
```

---

## 六、JWT 认证流程

### 6.1 登录时获取 Token

```java
// 登录成功返回 Token
@PostMapping("/sign_in")
public ResponseEntity<J> signIn(@RequestBody LoginRequest request) {
    // 验证用户名密码...
    String token = JwtUtils.generateToken(userUuid);
    return ResponseEntity.ok(new J().data("token", token).success(true));
}
```

### 6.2 前端携带 Token

```typescript
// axios 请求拦截器
axios.interceptors.request.use(config => {
  const token = localStorage.getItem('token')
  if (token) {
    config.headers.Authorization = `Token ${token}`
  }
  return config
})
```

### 6.3 后端验证 Token

```java
// 在 WebConfig 中注册拦截器
@Configuration
public class WebConfig implements WebMvcConfigurer {
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new JwtInterceptor())
                .addPathPatterns("/**")
                .excludePathPatterns("/user/sign_in", "/user/sign_up", "/api/**");
    }
}
```

### 6.4 Controller 中获取当前用户

```java
// ✅ 从 Token 中解析用户 UUID
String uuid = request.getAttribute("uuid").toString();
```

---

## 七、文件上传流程

> **重要**：系统统一使用腾讯云 COS 私有桶存储，通过签名 URL 访问。

### 7.1 后端文件上传 API

```java
@Autowired
private CosService cosService;

@PostMapping("/upload")
public ResponseEntity<J> uploadFile(
        @RequestParam("file") MultipartFile file,
        HttpServletRequest request) {
    // 1. 验证文件类型
    // 2. 获取当前用户 UUID
    String uuid = request.getAttribute("uuid").toString();
    // 3. 生成 objectKey 并上传到 COS 私有桶
    String objectKey = "uploads/" + uuid + "/" + UUID.randomUUID() + "_" + file.getOriginalFilename();
    cosService.uploadFile(file.getInputStream(), objectKey);
    // 4. 返回签名 URL（前端可直接访问）
    String signedUrl = cosService.getSignedUrl(objectKey);
    return ResponseEntity.ok(new ResourceR().resourceSuch(true, signedUrl));
}
```

### 7.2 获取文件访问 URL

由于 COS 使用私有桶，访问文件需要签名 URL：

```java
// 当需要展示图片时，生成带签名的临时 URL
String signedUrl = cosService.getSignedUrl(objectKey);

// 返回给前端，前端直接用这个 URL 访问
return ResponseEntity.ok(new ResourceR().resourceSuch(true, signedUrl));
```

### 7.3 前端上传组件

```vue
<script setup>
import { ref } from 'vue'
import axios from 'axios'

const handleUpload = async (file) => {
  const formData = new FormData()
  formData.append('file', file)
  
  const res = await axios.post('/api/file/upload', formData, {
    headers: { 'Content-Type': 'multipart/form-data' }
  })
  
  return res.data.data.url
}
</script>
```

---

## 八、常见问题

### Q1: 怎么获取当前登录用户？

```java
// ✅ 正确方式：从 Request 属性中获取 uuid
String uuid = request.getAttribute("uuid").toString();

// ❌ 错误方式：userId 不是正确的属性名
// String userId = request.getAttribute("userId");  // 这是错的！
```

> **说明**：JWT 拦截器 `AuthInterceptor` 在验证 Token 成功后，会将用户 UUID 存入 `request.setAttribute("uuid", jwt.getData())`，因此所有需要获取当前用户的接口都应使用 `request.getAttribute("uuid")`。

### Q2: 怎么新增一个枚举值？

在对应的 `enumeration` 包下添加：

```java
public enum WorkStatus {
    NORMAL(1, "正常"),
    BANNED(2, "封禁"),
    REMOVED(3, "下架");
    
    private final int code;
    private final String desc;
    
    WorkStatus(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }
}
```

### Q3: 权限系统如何使用？

系统使用**位运算**实现权限控制，参考 `PermissionConst.java`：

```java
// 普通用户权限 (identity = USER)
USER = 1         // 0000 0001 会员
UPPER = 2       // 0000 0010 收录员
CREATOR = 4      // 0000 0100 创作者

// 管理者权限 (identity = MANAGER)
DISCIPLINARY_COMMITTEE = 1    // 风纪委员
FIR_MAINTAINER = 2           // 一级管理员
SEC_MAINTAINER = 4           // 二级管理员
THI_MAINTAINER = 8           // 三级管理员
ADMINISTRATOR = 16           // 副馆长
ADVANCED_ADMINISTRATOR = 32  // 馆长（超级管理员）
```

#### 使用鉴权注解

```java
// 需要 MANAGER 身份 + THI_MAINTAINER 权限
@RequireUserAndPermissionAnno({
    @RequireUserAndPermissionAnno.Check(
        identity = UserIdentityEnum.MANAGER,
        targetPermission = PermissionConst.THI_MAINTAINER
    )
})
public ResponseEntity<J> deleteUserControl(...) { }

// 只要用户存在即可
@RequireUserAndPermissionAnno({
    @RequireUserAndPermissionAnno.Check(uuidIndex = 0)
})
public ResponseEntity<J> getUserControl(...) { }
```

#### 权限对比工具

```java
@Autowired
private ComparePermission comparePermission;

// 判断权限是否足够
if (comparePermission.compare(userPermission, PermissionConst.ADMINISTRATOR)) {
    // 有副馆长及以上权限
}

// 判断是否有特定权限
if (comparePermission.has(userPermission, PermissionConst.SEC_MAINTAINER)) {
    // 有二级管理员权限（可处理作品举报）
}
```

### Q4: 如何开发管理端功能？

管理端需要调用带权限验证的接口：

```java
// 获取用户列表（需管理员权限）
POST /user/query/all_by_page

// 修改用户权限（需三级管理员及以上）
POST /user/permission/change_permission
Body: {
    "uuid": "xxx",
    "identity": "MANAGER",
    "permission": 8  // THI_MAINTAINER
}
```

### Q5: 数据库字段改了怎么办？

1. 联系 DBA 直接修改生产数据库
2. 或编写数据库迁移 SQL 脚本
3. **不要直接修改 schema.sql 后重新初始化**

### Q6: 前端 API 请求失败怎么办？

```typescript
// axios 响应拦截器
axios.interceptors.response.use(
  response => response,
  error => {
    if (error.response?.data?.code === 401) {
      // Token 过期，跳转登录
      router.push('/sign')
    }
    return Promise.reject(error)
  }
)
```

---

## 九、约束与禁忌

### 🚫 禁止事项

| 禁止 | 原因 |
|------|------|
| 不要修改 `application.yaml` 中的密钥 | 会导致线上服务不可用 |
| 不要直接删除用户数据 | 用户数据受保护，需走注销流程 |
| 不要在代码中硬编码密钥 | 使用配置文件管理 |
| 不要删除 `schema.sql` 中的表 | 可能有数据依赖 |
| 不要在生产环境测试 | 使用测试环境 |

### ⚠️ 谨慎操作

| 谨慎 | 说明 |
|------|------|
| 修改数据库表结构 | 可能影响已有数据 |
| 修改 JWT 配置 | 会导致所有用户重新登录 |
| 修改邮件配置 | 影响验证码发送 |
| 修改 Druid 连接池配置 | 影响服务稳定性 |

### ✅ 正确做法

1. **新增功能**：先在测试环境验证
2. **修改配置**：备份原配置
3. **数据库变更**：编写回滚 SQL
4. **提交代码**：写清楚改动内容

---

## 十、代码模板速查

### 后端 Controller 模板

```java
package com.miaoyu.barc.{module}.controller;

import com.miaoyu.barc.annotation.IgnoreAuth;
import com.miaoyu.barc.{module}.service.{Module}Service;
import com.miaoyu.barc.response.J;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/{prefix}")
public class {Module}Controller {
    
    @Autowired
    private {Module}Service {module}Service;
    
    @IgnoreAuth
    @GetMapping("/all")
    public ResponseEntity<J> getAllControl() {
        return {module}Service.getAllService();
    }
}
```

### 后端 Service 模板

```java
package com.miaoyu.barc.{module}.service;

import com.miaoyu.barc.{module}.mapper.{Module}Mapper;
import com.miaoyu.barc.response.J;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

@Service
public class {Module}Service {
    
    @Autowired
    private {Module}Mapper {module}Mapper;
    
    public ResponseEntity<J> getAllService() {
        var list = {module}Mapper.findAll();
        return ResponseEntity.ok(new J().data(list).success(true));
    }
}
```

### 前端路由模板

```typescript
import MainView from "@/views/MainView.vue";

export default {
  path: "/module",
  component: MainView,
  children: [
    {
      path: "",
      name: "ModuleHome",
      component: () => import("@/components/Module/ModuleHomeComp.vue")
    }
  ]
}
```

---

## 十一、调试技巧

### 后端调试

```java
// 添加日志
log.info("请求参数: {}", param);
log.error("错误: ", e);

// 断点调试
// 使用 IDE 的 Debug 模式启动 Spring Boot
```

### 前端调试

```typescript
// 添加日志
console.log('data:', data);

// Vue DevTools
// 使用 Chrome 插件 vue-devtools 查看组件状态
```

---

## 十二、相关文档

- [功能文档.md](./功能文档.md) - 系统功能说明
- [结构文档.md](./结构文档.md) - 代码结构说明
