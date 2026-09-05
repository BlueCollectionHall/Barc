# Barc 蔚蓝收录馆 - Agent 行为约束文档

> 本文档用于约束所有 Agent 智能体在本项目中的行为规范。
> 所有 Agent 在工作前必须阅读并遵守本文档。

---

## 一、项目概述

**项目名称：** Barc 蔚蓝收录馆

**主要模块：**
| 模块 | 说明 | 状态 |
|------|------|------|
| BarcBackend | 后端服务（Java Spring Boot） | ✅ 活跃 |
| BarcFrontend | 用户前端（Vue 3 + TypeScript） | ✅ 活跃 |
| BarcManageFrontendV2 | 管理前端（Vue 3 + TypeScript） | ✅ 活跃 |
| BarcManageFrontend | 旧管理前端 | ❌ 已弃用 |

---

## 二、Agent 角色与职责

| 角色 | 职责 |
|------|------|
| 开发Agent | 实现新功能、修复bug |
| 测试Agent | 编写和执行测试 |
| 审查Agent | 代码审查 |
| 文档Agent | 更新文档 |

---

## 三、行为准则

### 3.1 基本准则

- **代码风格**：遵循现有项目约定
- **提交信息**：使用 Conventional Commits 格式
- **沟通原则**：使用中文（包括 todo 列表）
- **测试要求**：每次修改必须测试
- **审查要求**：每次修改必须经过审查
- **注释要求**：所有修改必须添加清晰注释，确保人类和 Agent 都能理解

### 3.2 工作流程

```
理解需求 → 探明关系 → 确定边界 → 实现 → 测试 → 构建验证 → 审查 → 提交
```

- **任务获取**：从 issue 或任务板获取
- **进度报告**：使用中文 todo 工具
- **完成标准**：测试通过、构建成功、代码审查通过、文档更新

---

## 四、技术栈与规范

| 层级 | 技术 | 规范 |
|------|------|------|
| 后端 | Java 17, Spring Boot, MyBatis, Maven | 遵循 Java 规范 |
| 前端 | Vue 3, Vite, TypeScript, Axios | **必须使用 `<script setup>` 语法糖**<br>**必须符合 TypeScript 规范和 ESLint 规范** |
| 数据库 | MySQL | 见「数据库表状态」章节 |
| 文件存储 | 腾讯云 COS（私有桶） | 数据库存储对象 key，取出时通过签名方法转为带签名 URL<br>**COS client 代码已完善，修改需兼容所有关联代码，尽量不动** |
| 认证 | JWT | Token 格式：`Token {jwt}` |

---

## 五、项目结构

```
Barc/
├── BarcBackend/          # 后端服务（Java Spring Boot）
│   └── src/test/java/    # 后端测试目录（按 Java 规范）
├── BarcFrontend/         # 用户前端（Vue 3 + TypeScript）
│   └── tests/            # 前端测试目录
├── BarcManageFrontendV2/ # 管理前端（活跃，Vue 3 + TypeScript）
│   └── tests/            # 管理前端测试目录
├── BarcManageFrontend/   # 已弃用（不要使用）
└── docs/                 # 文档目录
```

---

## 六、数据库表状态

| 状态 | 表名 | 说明 |
|------|------|------|
| ✅ 活跃 | `student`, `club`, `school`, `school_club` | 当前使用（单数形式） |
| ❌ 弃用 | `students`, `clubs`, `schools`, `school_clubs` | 已弃用，不要使用（复数形式） |

**禁止使用弃用的复数形式表名！**

### 6.2 字段命名与索引规范

**字段命名规则：**
| 表类型 | 主键字段名 | 说明 |
|--------|-----------|------|
| 用户相关表（student, club, school 等） | `uuid` | 用户表使用 `uuid` 作为主键字段名 |
| 其他实体表 | `id` | 其他表使用 `id` 作为主键字段名（值通常也是 uuid 格式） |

**用户索引原则：**

在需要索引或关联用户时，**尽量使用 `uuid` 字段**，避免使用 `username`、`email` 等字段。

| 场景 | 推荐做法 | 避免做法 |
|------|----------|----------|
| **关联用户表** | 使用 `uuid` 字段关联 | 使用 `username` 关联 |
| **查询用户** | 使用 `uuid` 查询 | 使用 `email` 查询 |
| **外键引用** | 存储 `uuid` 值 | 存储 `username` 值 |

**示例：**
```sql
-- ✅ 推荐：使用 uuid 关联用户
SELECT s.*, sc.* 
FROM student s 
JOIN school_club sc ON s.uuid = sc.student_uuid

-- ⚠️ 尽量避免：使用 username 关联
SELECT * FROM student WHERE username = 'xxx'
```

**说明：**
- 仅在业务必须时（如登录、注册校验）才使用 `username`/`email` 查询用户
- 其他场景一律使用 `uuid` 进行索引和关联

---

## 七、测试规范

### 7.1 测试目录

| 模块 | 测试目录 | 覆盖率要求 |
|------|----------|------------|
| BarcBackend | `src/test/java/`（按 Java 包结构） | **≥90%** |
| BarcFrontend | `tests/`（前端根目录下） | **≥90%** |
| BarcManageFrontendV2 | `tests/`（前端根目录下） | **≥90%** |

### 7.2 测试要求

- 每次修改必须编写对应测试用例
- 测试覆盖率必须达到 90% 以上
- 前端测试放在各自目录的 `tests/` 文件夹
- 后端测试放在 `src/test/java/` 下，按 Java 包结构组织

---

## 八、构建验证规范

### 8.1 构建命令

每次代码修改后必须执行：

```bash
# 前端构建验证（BarcFrontend）
cd BarcFrontend
npm run build

# 前端构建验证（BarcManageFrontendV2）
cd BarcManageFrontendV2
npm run build

# 后端构建验证（BarcBackend）
cd BarcBackend
./mvnw clean package
```

### 8.2 验证要求

- 前端代码必须符合 TypeScript 规范，无类型错误
- 前端代码必须符合 ESLint 规范，无 lint 错误
- 后端代码必须通过 Maven 构建，无编译错误
- **所有构建必须成功通过，确保代码能正常运行**

---

## 九、复杂修改处理规范

### 9.1 复杂修改识别标准

| 类型 | 特征 | 处理要求 |
|------|------|----------|
| **复杂调用链路** | A 调用 B 调用 C，修改 C 影响 A 和 B | 必须探明完整调用链 |
| **复杂关联关系** | 多表关联、多模块依赖 | 必须确定影响边界 |
| **跨模块修改** | 修改涉及多个功能模块 | 必须评估全局影响 |
| **核心组件修改** | 修改基础工具类、公共组件 | 必须检查所有使用点 |

### 9.2 处理流程

```
1. 探明关系
   ├── 分析调用链路（上游调用方、下游被调用方）
   ├── 分析数据关联（表关系、字段依赖）
   ├── 分析模块依赖（哪些模块依赖此代码）
   └── 分析前端使用（双端哪些功能使用）

2. 确定边界
   ├── 明确修改范围（哪些文件需要改）
   ├── 明确影响范围（哪些功能会受影响）
   ├── 明确测试范围（需要测试哪些场景）
   └── 明确回滚方案（出问题如何回退）

3. 执行修改
   ├── 按边界范围逐步修改
   ├── 每步修改后验证
   ├── 添加详细注释说明
   └── 保持双端兼容性

4. 验证结果
   ├── 单元测试通过
   ├── 集成测试通过
   ├── 双端功能验证
   └── 构建验证通过
```

### 9.3 检查清单

- [ ] 是否探明了完整调用链路？
- [ ] 是否确定了影响边界？
- [ ] 是否检查了所有调用方？
- [ ] 是否检查了双端兼容性？
- [ ] 是否添加了详细注释？
- [ ] 是否有回滚方案？

---

## 十、代码注释规范

### 10.1 注释原则

| 原则 | 说明 |
|------|------|
| **可读性** | 注释要让人类和 Agent 都能理解 |
| **完整性** | 解释代码的目的、逻辑和注意事项 |
| **时效性** | 修改代码时同步更新注释 |
| **语言** | 使用中文注释，确保团队理解 |

### 10.2 后端 Java 注释示例

```java
/**
 * 用户注册接口
 * 
 * @param request 注册请求参数
 * @return 注册结果，包含用户ID
 * @throws BusinessException 用户名已存在时抛出异常
 */
@PostMapping("/register")
public ResponseEntity<J> register(@RequestBody RegisterRequest request) {
    // 验证用户名是否已存在
    if (userService.existsByUsername(request.getUsername())) {
        throw new BusinessException("用户名已存在");
    }
    
    // 创建用户并返回ID
    String userId = userService.createUser(request);
    return ResponseEntity.ok(new ResourceR().resourceSuch(true, userId));
}
```

### 10.3 前端 Vue 注释示例

```vue
<script setup lang="ts">
/**
 * 用户注册表单组件
 * 
 * 功能：
 * 1. 收集用户注册信息
 * 2. 表单验证
 * 3. 调用注册API
 * 4. 处理注册结果
 */

import { ref } from 'vue'

// 表单数据
const formData = ref({
  username: '',    // 用户名
  password: '',    // 密码
  email: ''        // 邮箱
})

// 表单验证规则
const rules = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    { min: 3, max: 20, message: '用户名长度在3-20个字符', trigger: 'blur' }
  ]
}

/**
 * 提交注册表单
 * 
 * 处理逻辑：
 * 1. 验证表单数据
 * 2. 调用注册API
 * 3. 成功后跳转到登录页
 * 4. 失败显示错误信息
 */
const handleSubmit = async () => {
  // TODO: 实现注册逻辑
}
</script>
```

### 10.4 注释检查清单

- [ ] 函数/方法有 JSDoc 或 JavaDoc 注释
- [ ] 复杂逻辑有行内注释解释
- [ ] 修改的代码有变更说明注释
- [ ] 注释使用中文，确保团队理解
- [ ] 注释与代码保持同步更新

---

## 十一、后端修改兼容性规范

### 11.1 双端兼容性要求

修改后端 API 时必须同时考虑双端兼容性：

| 检查项 | 说明 |
|--------|------|
| **BarcFrontend 兼容性** | 修改后端返回值时，检查用户前端是否兼容 |
| **BarcManageFrontendV2 兼容性** | 修改后端返回值时，检查管理端前端是否兼容 |
| **双端测试** | 后端修改后，必须同时测试两个前端的功能正常 |
| **返回值格式** | 新增字段用可选类型，删除字段先废弃再移除 |

### 11.2 兼容性检查清单

1. 修改 API 返回值结构 → 检查两个前端的数据解析
2. 新增必填字段 → 确保两个前端都能正确提交
3. 删除返回字段 → 确保两个前端都不依赖该字段
4. 修改字段类型 → 确保两个前端都能正确处理
5. 修改错误码 → 确保两个前端的错误处理都能兼容

---

## 十二、文件存储规范

- **数据库字段**：存储 COS 对象 key（如：`uploads/{uuid}/{filename}`）
- **获取 URL**：调用签名方法 `cosService.getSignedUrl(objectKey)` 生成带签名的临时 URL
- **返回前端**：前端直接使用签名 URL 访问文件

⚠️ **COS client 代码已完善，修改需兼容所有关联代码，尽量不要动！**

---

## 十三、Git 提交规范（宪法级强制约定）

### 13.1 强制提交时机（最高优先级）

- **每次对话结束时**，若本次对话对代码或必要文件（配置、脚本、文档等）产生了任何改动，**必须**执行 `git commit` 完成本地提交。
- **只提交，不 Push**：未经用户明确许可，**禁止**执行 `git push`。
- 若本次对话没有任何文件改动，则**禁止**创建空提交。
- 提交前必须先通过 §八 构建验证流程（前端 `npm run build`、后端 `./mvnw clean package`）与相关测试，未通过验证的代码不得提交。

### 13.2 提交标题格式

```
<英文Type>(<中文Scope>): <中文Title>
```

**示例**：`feat(用户): 新增用户注册API`

**英文 Type（必选其一）**：

| 前缀 | 含义 |
|------|------|
| `feat` | 新功能 |
| `fix` | 修复 bug |
| `docs` | 文档更新 |
| `style` | 代码风格调整（不影响逻辑） |
| `refactor` | 重构（无新功能、无缺陷修复） |
| `perf` | 性能优化 |
| `test` | 测试相关 |
| `build` | 构建系统/依赖变更 |
| `ci` | CI/CD 配置变更 |
| `chore` | 其它工具链/杂项变更 |

**中文 Scope 参考（可按业务模块扩展）**：

| 模块 | 说明 |
|------|------|
| `用户` | 用户相关功能（注册、登录、个人信息等） |
| `作品` | 作品相关功能（上传、展示、管理等） |
| `公告` | 公告相关功能 |
| `学校` | 学校相关功能 |
| `社团` | 社团相关功能 |
| `权限` | 权限管理功能 |
| `文件` | 文件上传/存储功能 |
| `系统` | 系统配置、工具类等 |

### 13.3 描述（Body）

- 必须使用**中文**撰写。
- 采用中文数字编号逐条列举，格式为 `1、xxx；2、xxx；3、xxx；`。
- **每条独占一行**，每条必须对应本次提交的一项具体工作细节（改了什么、为什么、影响哪里）。
- **禁止**把多条细节挤在同一行，**禁止**使用 `-` 无序列表代替编号。

### 13.4 完整示例

```
feat(用户): 新增用户注册API

1、添加 UserController 注册接口；
2、添加 UserService 注册逻辑与 UserMapper 插入方法；
3、编写单元测试，覆盖率达到 92%；
4、已验证 BarcFrontend 和 BarcManageFrontendV2 双端兼容性；
```

```
fix(作品): 修复作品列表显示问题

1、修复作品封面图加载失败；
2、修复分页参数传递错误；
3、补充加载状态提示并验证双端兼容性；
```

### 13.5 禁止事项

- **禁止**使用 `--no-verify` 跳过钩子。
- **禁止**未经用户许可执行 `git push`、`git reset --hard`、`git push --force`、`git checkout --`、`git clean -fd`。
- **禁止**自动 amend 已推送的 commit。
- **禁止**在无任何文件改动时创建空提交。

---

## 十四、操作限制

### 🚫 严格禁止

| 禁止操作 | 说明 |
|----------|------|
| **擅自打开浏览器** | 未经用户明确允许，禁止自行启动浏览器进行测试 |
| **擅自清理数据库** | 禁止未经用户确认就清理、删除、重置数据库数据 |

### ⚠️ 必须请求用户许可

- 需要打开浏览器进行测试时 → 先询问用户
- 需要清理测试数据时 → 先询问用户
- 需要执行可能影响数据的操作时 → 先询问用户

---

## 十五、关键约束与禁忌

### 🚫 绝对禁止

- 禁止修改 `application.yaml` 中的密钥
- 禁止直接删除用户数据
- 禁止硬编码密钥
- 禁止删除 `schema.sql` 中的表
- 禁止在生产环境测试

### ⚠️ 谨慎操作

- 谨慎修改数据库表结构
- 谨慎修改 JWT 配置
- 谨慎修改邮件配置
- 谨慎修改 Druid 连接池配置

### 🚫 代码质量禁止

- **禁止使用弃用的复数形式表名（students, clubs, schools, school_clubs）**
- **禁止使用弃用的 BarcManageFrontend 模块**
- **禁止提交有 TypeScript 类型错误的代码**
- **禁止提交有 ESLint 错误的代码**
- **禁止提交构建失败的代码**
- **禁止未经允许擅自打开浏览器测试**
- **禁止未经允许擅自清理数据库**
- **禁止只顾一端修改后端，忽略双端兼容性**
- **禁止提交没有注释的代码修改**
- **禁止未探明关系就直接修改复杂代码**

---

## 十六、测试与审查流程

### 16.1 测试要求

- 每次修改必须进行单元测试和集成测试
- 测试环境：使用测试环境，不要在生产环境测试
- **覆盖率要求：代码覆盖率必须达到 90% 以上**
- **构建要求：前端 build 和后端 package 必须成功**

### 16.2 审查要求

- 每次修改必须经过代码审查
- 审查内容：代码质量、测试覆盖、注释完整、双端兼容

---

## 十七、常见问题处理

参考现有文档：
- `docs/AI开发指南.md` - 开发指南与常见问题
- `docs/功能文档.md` - 系统功能说明
- `docs/结构文档.md` - 代码结构说明

---

## 十八、权限鉴权规范（宪法级定义）

> 本章统一定义本项目的权限鉴权模式。所有 Agent 在实现、修改涉及权限控制的接口前，必须先阅读本章并确认目标功能所需的鉴权模式，避免误用。

### 18.1 权限管理位置

- 项目权限管理统一位于后端 `com.miaoyu.barc.permission` 包：
  - `PermissionConst`：定义所有权限常量（各身份的权限位）。
  - `ComparePermission`：提供两种鉴权方式的判定方法 `compare`（比大小）与 `has`（1匹配）。
- AOP 鉴权实现位于 `com.miaoyu.barc.aspect` 包，通过注解驱动：
  - `@RequireUserAndPermissionAnno`（切面 `RequireUserAndPermissionAspect`）：校验用户身份与权限。
  - `@RequireSelfOrPermissionAnno`（切面 `RequireSelfOrPermissionAspect`）：校验用户为"本人"或具备权限。
- 两种鉴权方式由注解字段 `isHasElseUpper` 切换，**本项目统一命名如下**：

| `isHasElseUpper` | 鉴权模式名词 | 判定方法 | 代码位置 |
|---|---|---|---|
| `false`（默认） | **比大小** | `comparePermission.compare(your, target)` | `ComparePermission.compare()` |
| `true` | **1匹配** | `comparePermission.has(your, target)` | `ComparePermission.has()` |

**AOP 统一鉴权流程：**

1. 通过 `uuidIndex` 从方法形参中取出被校验用户的 uuid，查询 `UserArchiveModel`；
2. 校验身份匹配（`userArchive.getIdentity() == check.identity()`），身份不匹配直接拒绝；
3. 身份匹配后，依据 `isHasElseUpper` 选择「比大小」或「1匹配」判定权限，不通过时返回 `insufficientAccountPermission`。

**注解常用字段说明：**

| 字段 | 含义 |
|---|---|
| `uuidIndex` | 需要校验的 uuid 实参在方法形参中的索引位置 |
| `authorUuidIndex` | 作者 uuid 实参索引（仅 `@RequireSelfOrPermissionAnno`） |
| `identity` | 要求匹配的身份（`UserIdentityEnum`） |
| `targetPermission` | 目标权限常量（`PermissionConst` 中的值） |
| `isSuchElseRequire` | `true`：仅校验用户存在性，不校验身份权限（直接放行）；`false`：执行完整身份+权限校验 |
| `isHasElseUpper` | `true`：1匹配；`false`：比大小（默认） |

### 18.2 鉴权模式名词定义

**①「比大小」**

- **定义**：将账号权限值与所需权限值按数值大小直接比较，**账号权限值 ≥ 所需权限值**（`your >= target`）即鉴权通过。
- **适用场景**：权限严格按层级排列、数值越大权限越高的场景。例如 MANAGER 身份中 `ADMINISTRATOR`（16，副馆长）与 `ADVANCED_ADMINISTRATOR`（32，馆长）的判定。
- **代码依据**：`ComparePermission.compare(int your, int target)` 返回 `your >= target`。

**②「1匹配」**

- **定义**：将账号权限值与所需权限值按二进制位比较，要求两者在**至少一个相同位上均为 1**（即按位与结果非 0，`(your & target) != 0`）即鉴权通过。
- **适用场景**：账号权限可能同时持有多个权限位（按位或 `|` 组合，如 `FIR_MAINTAINER | SEC_MAINTAINER`）时，校验其是否持有"所需角色位中的任意一个"。例如管理端处理举报类接口。
- **⚠️ 注意**：「1匹配」的判定是"至少存在一个公共位为 1"（`!= 0`），**不是**"账号权限完全包含所需权限"（后者的判定为 `(your & target) == target`）。新增或修改权限判定时必须确认所需语义，防止误判。
- **代码依据**：`ComparePermission.has(int your, int target)` 返回 `(your & target) != 0`。

### 18.3 权限常量说明

`PermissionConst` 中的权限均为二进制单一位（2 的幂）：

| 身份 | 常量 | 值 | 二进制 | 角色 |
|------|------|----|--------|------|
| USER | `USER` | 1 | 0000 0001 | 会员 |
| USER | `UPPER` | 2 | 0000 0010 | 收录员 |
| USER | `CREATOR` | 4 | 0000 0100 | 创作者 |
| MANAGER | `DISCIPLINARY_COMMITTEE` | 1 | 0000 0001 | 风纪委员 |
| MANAGER | `FIR_MAINTAINER` | 2 | 0000 0010 | 一级管理员 |
| MANAGER | `SEC_MAINTAINER` | 4 | 0000 0100 | 二级管理员 |
| MANAGER | `THI_MAINTAINER` | 8 | 0000 1000 | 三级管理员 |
| MANAGER | `ADMINISTRATOR` | 16 | 0001 0000 | 副馆长 |
| MANAGER | `ADVANCED_ADMINISTRATOR` | 32 | 0010 0000 | 馆长 |

### 18.4 使用示例（取自项目现有代码）

- **比大小示例**：`UserBanController` 的封号 / 解封 / 查询接口使用 `isHasElseUpper = false` 且 `targetPermission = PermissionConst.ADMINISTRATOR`，即要求操作者权限 ≥ 副馆长。
- **1匹配示例**：`UserManageService` 使用 `isHasElseUpper = true` 且 `targetPermission = PermissionConst.THI_MAINTAINER`；`WorkManageService` 大量接口使用 `isHasElseUpper = true` 且 `targetPermission = PermissionConst.SEC_MAINTAINER`，即要求账号持有对应角色位。
- **组合判定示例**：`FeedbackService` 在代码内组合两种方式，如 `comparePermission.has(permission, PermissionConst.FIR_MAINTAINER) || comparePermission.compare(permission, PermissionConst.ADMINISTRATOR)`：持有对应角色位（1匹配）或权限达到副馆长及以上（比大小）即可处理该类举报。

---

## 十九、附录

### 相关文档

- [功能文档.md](./docs/功能文档.md) - 系统功能说明
- [结构文档.md](./docs/结构文档.md) - 代码结构说明
- [AI开发指南.md](./docs/AI开发指南.md) - 开发指南与常见问题

---

**最后更新：** 2026-09-05

**维护者：** 项目团队
