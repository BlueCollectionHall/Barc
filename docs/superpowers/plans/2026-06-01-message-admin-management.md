# 留言管理后台 实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在管理后台(V2)新增留言管理模块，支持分页列表+搜索过滤、编辑内容、单条删除+批量删除，并异步邮件通知被操作留言的发布者。同时修复现有留言删除权限检查（从 `>=` 改为 `&` 二进制匹配），确保只有持有 FIR_MAINTAINER(2) bit 的管理员才能管理留言。

**Architecture:** 后端扩展 `MessageBoardController/Service/Mapper`，新增分页查询、更新、批量删除端点，注入 `UserBasicMapper` 获取发布者邮箱，通过 `@Async` 异步发送通知邮件。前端 V2 新增 `modules/messages/` 模块，遵循 `notices` 模块的 RoutePageShell + service + 列表视图模式。

**Tech Stack:** Java 17 / Spring Boot / MyBatis / JavaMailSender · Vue 3 / TypeScript / Element Plus / Axios

**Permission Rule:** `(userArchive.permission & PermissionConst.FIR_MAINTAINER) != 0` → 二进制 & 检查。馆长(32)不可管理留言，因为 `32 & 2 = 0`。

---

## 前置约束

1. **不动前台公开端点**: `GET /comment/message_board/all`、`GET /comment/message_board/new`、`GET /comment/message_board/upload` 签名和返回格式完全不变
2. **现有删除端点**: 修复权限检查但不改变调用签名（控制器不变）
3. **V1 管理前端**: 不动，V1 的 `MessageView.vue` 保持空壳
4. **前端 V2 不做路由缓存反馈** (不用 sessionStorage route feedback)，直接用 `ElMessage` 提示

---

## Task 1: 后端 — 开启异步支持 + 新增 Mapper XML

**Files:**
- Modify: `BarcBackend/src/main/java/com/miaoyu/barc/BarcBackendApplication.java` (add @EnableAsync)
- Create: `BarcBackend/src/main/resources/mappers/Comment/MessageBoardMapper.xml` (new XML mapper)
- Modify: `BarcBackend/src/main/java/com/miaoyu/barc/comment/mapper/MessageBoardMapper.java` (add method signatures)

- [ ] **Step 1: 主类添加 `@EnableAsync`**

```java
// BarcBackendApplication.java
package com.miaoyu.barc;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@MapperScan("com.miaoyu.barc.**.mapper")
@EnableScheduling
@EnableAsync
@SpringBootApplication
public class BarcBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(BarcBackendApplication.class, args);
    }

}
```

- [ ] **Step 2: 创建 MessageBoardMapper.xml**

```xml
<!-- BarcBackend/src/main/resources/mappers/Comment/MessageBoardMapper.xml -->
<!DOCTYPE mapper
        PUBLIC "-//mybatis.org//DTD Mapper 3.0//EN" "http://mybatis.org/dtd/mybatis-3-mapper.dtd">
<mapper namespace="com.miaoyu.barc.comment.mapper.MessageBoardMapper">

    <select id="selectAdminByPage" resultType="com.miaoyu.barc.comment.model.MessageBoardModel">
        SELECT m.id, m.author, m.content, m.created_at
        FROM message_board m
        <where>
            <if test="condition.author != null and condition.author != ''">
                AND m.author = #{condition.author}
            </if>
            <if test="condition.content != null and condition.content != ''">
                AND m.content LIKE CONCAT('%', #{condition.content}, '%')
            </if>
            <if test="condition.date_from != null and condition.date_from != ''">
                AND m.created_at &gt;= #{condition.date_from}
            </if>
            <if test="condition.date_to != null and condition.date_to != ''">
                AND m.created_at &lt;= #{condition.date_to}
            </if>
        </where>
        ORDER BY m.created_at DESC
        LIMIT #{offset}, #{pageSize}
    </select>

    <select id="countAdminByPage" resultType="long">
        SELECT COUNT(*)
        FROM message_board m
        <where>
            <if test="condition.author != null and condition.author != ''">
                AND m.author = #{condition.author}
            </if>
            <if test="condition.content != null and condition.content != ''">
                AND m.content LIKE CONCAT('%', #{condition.content}, '%')
            </if>
            <if test="condition.date_from != null and condition.date_from != ''">
                AND m.created_at &gt;= #{condition.date_from}
            </if>
            <if test="condition.date_to != null and condition.date_to != ''">
                AND m.created_at &lt;= #{condition.date_to}
            </if>
        </where>
    </select>

    <update id="updateById">
        UPDATE message_board SET content = #{content} WHERE id = #{id}
    </update>

    <delete id="batchDelete">
        DELETE FROM message_board WHERE id IN
        <foreach collection="ids" item="id" open="(" separator="," close=")">
            #{id}
        </foreach>
    </delete>

</mapper>
```

- [ ] **Step 3: Mapper 接口新增方法签名**

```java
// MessageBoardMapper.java — 在现有方法后追加:

List<MessageBoardModel> selectAdminByPage(
    @Param("offset") Integer offset,
    @Param("pageSize") Integer pageSize,
    @Param("condition") Map<String, Object> condition
);

Long countAdminByPage(@Param("condition") Map<String, Object> condition);

boolean updateById(@Param("id") String id, @Param("content") String content);

boolean batchDelete(@Param("ids") List<String> ids);
```

**验证**: `mvn compile -f BarcBackend/pom.xml` 编译通过。

---

## Task 2: 后端 — 新增邮件通知方法

**Files:**
- Modify: `BarcBackend/src/main/java/com/miaoyu/barc/email/utils/SendEmailUtils.java`

- [ ] **Step 1: 新增管理员删除/编辑通知邮件方法**

```java
// SendEmailUtils.java — 在现有方法后追加:

/**
 * 管理员删除留言通知
 */
public boolean messageDeletedByAdminEmail(String to, String originalContent, String reason, String adminUsername) {
    try {
        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message);
        helper.setFrom(new InternetAddress("admin@barc.work", "蔚蓝收录馆"));
        helper.setTo(to);
        helper.setSubject("您的蔚蓝收录馆留言已被管理员删除");
        helper.setText(
            "Sensei！您在蔚蓝收录馆留言板的留言已被管理员删除。\n\n" +
            "被删除的留言内容：" + originalContent + "\n" +
            "操作管理员：" + adminUsername + "\n" +
            "处理原因：" + reason + "\n" +
            "如有疑问，请联系蔚蓝收录馆管理团队。"
        );
        mailSender.send(message);
    } catch (Exception e) {
        return false;
    }
    return true;
}

/**
 * 管理员修改留言通知
 */
public boolean messageEditedByAdminEmail(String to, String oldContent, String newContent, String reason, String adminUsername) {
    try {
        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message);
        helper.setFrom(new InternetAddress("admin@barc.work", "蔚蓝收录馆"));
        helper.setTo(to);
        helper.setSubject("您的蔚蓝收录馆留言已被管理员修改");
        helper.setText(
            "Sensei！您在蔚蓝收录馆留言板的留言已被管理员修改。\n\n" +
            "原留言内容：" + oldContent + "\n" +
            "修改后内容：" + newContent + "\n" +
            "操作管理员：" + adminUsername + "\n" +
            "处理原因：" + reason + "\n" +
            "如有疑问，请联系蔚蓝收录馆管理团队。"
        );
        mailSender.send(message);
    } catch (Exception e) {
        return false;
    }
    return true;
}
```

**验证**: `mvn compile -f BarcBackend/pom.xml` 编译通过。

---

## Task 3: 后端 — 修复 + 扩展 MessageBoardService

**Files:**
- Modify: `BarcBackend/src/main/java/com/miaoyu/barc/comment/service/MessageBoardService.java`

- [ ] **Step 1: 注入新依赖**

在类的 `@Autowired` 区域追加：

```java
@Autowired
private UserBasicMapper userBasicMapper;
@Autowired
private SendEmailUtils sendEmailUtils;
```

以及新增 import:
```java
import com.miaoyu.barc.email.utils.SendEmailUtils;
import com.miaoyu.barc.user.enumeration.UserIdentityEnum;
import com.miaoyu.barc.user.mapper.UserBasicMapper;
import com.miaoyu.barc.user.model.UserBasicModel;
import com.miaoyu.barc.utils.dto.PageRequestDto;
import com.miaoyu.barc.utils.dto.PageResultDto;
import com.miaoyu.barc.utils.pojo.PageInitPojo;
import com.miaoyu.barc.response.SuccessR;
import org.springframework.scheduling.annotation.Async;
import java.util.List;
import java.util.Map;
```

- [ ] **Step 2: 修复 deleteBoardMessageService（去掉注解，手动检查 + 二进制匹配）**

替换整个方法：

```java
public ResponseEntity<J> deleteBoardMessageService(String uuid, String messageId) {
    MessageBoardModel messageBoard = messageBoardMapper.selectById(messageId);
    if (messageBoard == null) {
        return ResponseEntity.ok(new ChangeR().udu(false, 1));
    }
    UserArchiveModel userArchive = userArchiveMapper.selectByUuid(uuid);
    boolean isAuthor = messageBoard.getAuthor().equals(uuid);
    boolean isAdmin = userArchive != null
            && userArchive.getIdentity().equals(UserIdentityEnum.MANAGER)
            && (userArchive.getPermission() & PermissionConst.FIR_MAINTAINER) != 0;
    if (!isAuthor && !isAdmin) {
        return ResponseEntity.ok(new ChangeR().udu(false, 1));
    }
    boolean deleted = messageBoardMapper.delete(messageId);
    if (deleted) {
        // 管理员删除时发送邮件通知（非作者自己删除）
        if (isAdmin && !isAuthor) {
            notifyMessageDeleted(messageBoard, uuid);
        }
        return ResponseEntity.ok(new ChangeR().udu(true, 1));
    }
    return ResponseEntity.ok(new ChangeR().udu(false, 1));
}
```

注意：去掉该方法上原有的 `@RequireSelfOrPermissionAnno` 注解。

- [ ] **Step 3: 新增分页管理查询方法**

```java
@RequireUserAndPermissionAnno({@RequireUserAndPermissionAnno.Check(
    isSuchElseRequire = false,
    identity = UserIdentityEnum.MANAGER,
    targetPermission = PermissionConst.FIR_MAINTAINER,
    isHasElseUpper = true
)})
public ResponseEntity<J> adminQueryBoardMessagesService(String uuid, PageRequestDto dto) {
    PageInitPojo init = new PageInitPojo(dto);
    List<MessageBoardModel> models = messageBoardMapper.selectAdminByPage(
        init.getOffset(), init.getPageSize(), dto.getParams()
    );
    Long total = messageBoardMapper.countAdminByPage(dto.getParams());
    int mathTotalPage = (int) Math.ceil((double) total / init.getPageSize());
    Integer totalPage = mathTotalPage == 0 ? 1 : mathTotalPage;
    return ResponseEntity.ok(
        new SuccessR().normal(
            new PageResultDto<>(total, models, init.getPageNum(), init.getPageSize(), totalPage)
        )
    );
}
```

- [ ] **Step 4: 新增编辑留言方法**

```java
@RequireUserAndPermissionAnno({@RequireUserAndPermissionAnno.Check(
    isSuchElseRequire = false,
    identity = UserIdentityEnum.MANAGER,
    targetPermission = PermissionConst.FIR_MAINTAINER,
    isHasElseUpper = true
)})
public ResponseEntity<J> adminUpdateBoardMessageService(String uuid, String messageId, String content, String reason) {
    MessageBoardModel messageBoard = messageBoardMapper.selectById(messageId);
    if (messageBoard == null) {
        return ResponseEntity.ok(new ChangeR().udu(false, 3));
    }
    boolean updated = messageBoardMapper.updateById(messageId, content);
    if (updated) {
        notifyMessageEdited(messageBoard, content, reason, uuid);
        return ResponseEntity.ok(new ChangeR().udu(true, 3));
    }
    return ResponseEntity.ok(new ChangeR().udu(false, 3));
}
```

- [ ] **Step 5: 新增批量删除方法**

```java
@RequireUserAndPermissionAnno({@RequireUserAndPermissionAnno.Check(
    isSuchElseRequire = false,
    identity = UserIdentityEnum.MANAGER,
    targetPermission = PermissionConst.FIR_MAINTAINER,
    isHasElseUpper = true
)})
public ResponseEntity<J> adminBatchDeleteBoardMessagesService(String uuid, List<String> messageIds, String reason) {
    // 先查出所有要被删除的留言（用于邮件通知）
    List<MessageBoardModel> messages = new ArrayList<>();
    for (String id : messageIds) {
        MessageBoardModel msg = messageBoardMapper.selectById(id);
        if (msg != null) {
            messages.add(msg);
        }
    }
    boolean deleted = messageBoardMapper.batchDelete(messageIds);
    if (deleted) {
        for (MessageBoardModel msg : messages) {
            if (!msg.getAuthor().equals(uuid)) {
                notifyMessageDeleted(msg, uuid);
            }
        }
        return ResponseEntity.ok(new ChangeR().udu(true, 2));
    }
    return ResponseEntity.ok(new ChangeR().udu(false, 2));
}
```

- [ ] **Step 6: 新增异步邮件通知私有方法**

```java
@Async
private void notifyMessageDeleted(MessageBoardModel messageBoard, String adminUuid) {
    UserBasicModel authorBasic = userBasicMapper.selectByUuid(messageBoard.getAuthor());
    if (authorBasic == null || authorBasic.getEmail() == null) {
        return;
    }
    UserBasicModel adminBasic = userBasicMapper.selectByUuid(adminUuid);
    String adminName = adminBasic != null ? adminBasic.getUsername() : adminUuid;
    sendEmailUtils.messageDeletedByAdminEmail(
        authorBasic.getEmail(),
        messageBoard.getContent(),
        "违反社区规定",  // 默认原因，后续可扩展
        adminName
    );
}

@Async
private void notifyMessageEdited(MessageBoardModel messageBoard, String newContent, String reason, String adminUuid) {
    UserBasicModel authorBasic = userBasicMapper.selectByUuid(messageBoard.getAuthor());
    if (authorBasic == null || authorBasic.getEmail() == null) {
        return;
    }
    UserBasicModel adminBasic = userBasicMapper.selectByUuid(adminUuid);
    String adminName = adminBasic != null ? adminBasic.getUsername() : adminUuid;
    String useReason = (reason != null && !reason.isBlank()) ? reason : "内容违规修改";
    sendEmailUtils.messageEditedByAdminEmail(
        authorBasic.getEmail(),
        messageBoard.getContent(),
        newContent,
        useReason,
        adminName
    );
}
```

注意: `@Async` 要求方法必须是 `public` 且从外部调用才能触发代理。但这里是 `private` 方法在同 Service 内调用，Spring AOP 代理不会拦截。需要在同一类中通过 `self` 注入或提取到独立的 `@Component`。

修正方案：创建独立的 `MessageNotificationService`。

**验证**: `mvn compile -f BarcBackend/pom.xml` 编译通过。

---

## Task 4: 后端 — 创建 MessageNotificationService（异步邮件）

**Files:**
- Create: `BarcBackend/src/main/java/com/miaoyu/barc/comment/service/MessageNotificationService.java`

- [ ] **Step 1: 创建独立的异步通知服务**

```java
package com.miaoyu.barc.comment.service;

import com.miaoyu.barc.comment.model.MessageBoardModel;
import com.miaoyu.barc.email.utils.SendEmailUtils;
import com.miaoyu.barc.user.mapper.UserBasicMapper;
import com.miaoyu.barc.user.model.UserBasicModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class MessageNotificationService {

    @Autowired
    private UserBasicMapper userBasicMapper;
    @Autowired
    private SendEmailUtils sendEmailUtils;

    @Async
    public void notifyMessageDeleted(MessageBoardModel messageBoard, String adminUuid) {
        UserBasicModel authorBasic = userBasicMapper.selectByUuid(messageBoard.getAuthor());
        if (authorBasic == null || authorBasic.getEmail() == null) {
            return;
        }
        UserBasicModel adminBasic = userBasicMapper.selectByUuid(adminUuid);
        String adminName = adminBasic != null ? adminBasic.getUsername() : adminUuid;
        sendEmailUtils.messageDeletedByAdminEmail(
            authorBasic.getEmail(),
            messageBoard.getContent(),
            "违反社区规定",
            adminName
        );
    }

    @Async
    public void notifyMessageEdited(MessageBoardModel messageBoard, String newContent, String reason, String adminUuid) {
        UserBasicModel authorBasic = userBasicMapper.selectByUuid(messageBoard.getAuthor());
        if (authorBasic == null || authorBasic.getEmail() == null) {
            return;
        }
        UserBasicModel adminBasic = userBasicMapper.selectByUuid(adminUuid);
        String adminName = adminBasic != null ? adminBasic.getUsername() : adminUuid;
        String useReason = (reason != null && !reason.isBlank()) ? reason : "内容违规修改";
        sendEmailUtils.messageEditedByAdminEmail(
            authorBasic.getEmail(),
            messageBoard.getContent(),
            newContent,
            useReason,
            adminName
        );
    }
}
```

- [ ] **Step 2: 回改 MessageBoardService，注入 MessageNotificationService 替换私有异步方法**

在 `MessageBoardService` 中:
- 注入 `@Autowired private MessageNotificationService notificationService;`
- 将 `notifyMessageDeleted(...)` 调用改为 `notificationService.notifyMessageDeleted(...)`
- 将 `notifyMessageEdited(...)` 调用改为 `notificationService.notifyMessageEdited(...)`
- 删除步骤 6 中新增的私有 `@Async` 方法

**验证**: `mvn compile -f BarcBackend/pom.xml` 编译通过。

---

## Task 5: 后端 — 扩展 MessageBoardController 管理端点

**Files:**
- Modify: `BarcBackend/src/main/java/com/miaoyu/barc/comment/controller/MessageBoardController.java`

- [ ] **Step 1: 新增管理员端点**

在现有 Controller 末尾（`}` 之前）追加：

```java
/**
 * 管理员分页查询留言
 */
@PostMapping("/admin/list")
public ResponseEntity<J> adminListControl(
        HttpServletRequest request,
        @RequestBody PageRequestDto dto
) {
    return messageBoardService.adminQueryBoardMessagesService(
        request.getAttribute("uuid").toString(), dto
    );
}

/**
 * 管理员编辑留言
 */
@PutMapping("/admin/update")
public ResponseEntity<J> adminUpdateControl(
        HttpServletRequest request,
        @RequestBody Map<String, Object> body
) {
    String messageId = (String) body.get("message_id");
    String content = (String) body.get("content");
    String reason = (String) body.getOrDefault("reason", "");
    return messageBoardService.adminUpdateBoardMessageService(
        request.getAttribute("uuid").toString(), messageId, content, reason
    );
}

/**
 * 管理员批量删除留言
 */
@DeleteMapping("/admin/batch_delete")
public ResponseEntity<J> adminBatchDeleteControl(
        HttpServletRequest request,
        @RequestBody Map<String, Object> body
) {
    @SuppressWarnings("unchecked")
    List<String> messageIds = (List<String>) body.get("message_ids");
    String reason = (String) body.getOrDefault("reason", "");
    return messageBoardService.adminBatchDeleteBoardMessagesService(
        request.getAttribute("uuid").toString(), messageIds, reason
    );
}
```

新增 import:
```java
import com.miaoyu.barc.utils.dto.PageRequestDto;
import java.util.List;
import java.util.Map;
```

**验证**: 启动后端，用 curl/Postman 测试：
```bash
curl -X POST http://localhost:51000/comment/message_board/admin/list \
  -H "Authorization: <token>" \
  -H "Content-Type: application/json" \
  -d '{"page_num":1,"page_size":10,"params":{}}'
```
期望返回 `{ "code": 0, "data": { "total": N, "list": [...] } }`

---

## Task 6: 前端 V2 — 创建留言类型定义

**Files:**
- Create: `BarcManageFrontendV2/src/shared/types/message.ts`

- [ ] **Step 1: 创建消息类型接口**

```typescript
// BarcManageFrontendV2/src/shared/types/message.ts

export interface MessageRecord {
  id: string
  author: string
  content: string
  created_at: string | null
}

export interface MessageUpdatePayload {
  message_id: string
  content: string
  reason: string
}

export interface MessageBatchDeletePayload {
  message_ids: string[]
  reason: string
}
```

**验证**: TypeScript 编译通过 `npx vue-tsc --noEmit`。

---

## Task 7: 前端 V2 — 创建留言 API 服务

**Files:**
- Create: `BarcManageFrontendV2/src/modules/messages/api/messages.service.ts`

- [ ] **Step 1: 创建 API service**

```typescript
import { http } from '@/shared/api/http'
import type { PageRequest, PageResult } from '@/shared/types/api'
import type { MessageBatchDeletePayload, MessageRecord, MessageUpdatePayload } from '@/shared/types/message'

export function fetchMessagesByPage(payload: PageRequest): Promise<PageResult<MessageRecord>> {
  return http.post<PageResult<MessageRecord>>('/comment/message_board/admin/list', payload)
}

export function updateMessage(payload: MessageUpdatePayload): Promise<string> {
  return http.put<string>('/comment/message_board/admin/update', payload)
}

export function batchDeleteMessages(payload: MessageBatchDeletePayload): Promise<string> {
  return http.delete<string>('/comment/message_board/admin/batch_delete', { data: payload })
}

export function deleteMessage(messageId: string): Promise<string> {
  return http.delete<string>('/comment/message_board/delete', {
    params: { message_id: messageId },
  })
}
```

- **注意**: 单条删除复用现有公开 `/comment/message_board/delete` 端点，权限已在后端修复。
- **注意**: `http.delete` 在 axios 中 `data` 需通过 `config.data` 传递（如批量删除 body）

**验证**: TypeScript 编译通过。

---

## Task 8: 前端 V2 — 创建留言管理列表视图

**Files:**
- Create: `BarcManageFrontendV2/src/modules/messages/views/MessagesListView.vue`

- [ ] **Step 1: 创建列表视图页面**

```vue
<script setup lang="ts">
import { ElMessageBox } from 'element-plus'
import { computed, onMounted, reactive, ref } from 'vue'

import RoutePageShell from '@/app/components/RoutePageShell.vue'
import {
  batchDeleteMessages,
  deleteMessage,
  fetchMessagesByPage,
  updateMessage,
} from '@/modules/messages/api/messages.service'
import type { MessageRecord } from '@/shared/types/message'
import { getErrorMessage, type PageResult } from '@/shared/types/api'
import { formatDateTime } from '@/shared/utils/date'
import { showError, showSuccess } from '@/shared/utils/message'

const loading = ref(false)
const pageResult = ref<PageResult<MessageRecord> | null>(null)
const deletingIds = ref<string[]>([])
const selectedIds = ref<string[]>([])

const pagination = reactive({
  page_num: 1,
  page_size: 15,
})

const filters = reactive({
  author: '',
  content: '',
  date_from: '',
  date_to: '',
})

// 编辑弹窗
const editDialogVisible = ref(false)
const editingMessage = ref<MessageRecord | null>(null)
const editContent = ref('')
const editReason = ref('')
const editSubmitting = ref(false)

// 批量删除弹窗
const batchDeleteVisible = ref(false)
const batchDeleteReason = ref('')
const batchDeleteSubmitting = ref(false)

const hasSelection = computed(() => selectedIds.value.length > 0)

function buildParams(): Record<string, unknown> {
  const params: Record<string, unknown> = {}
  if (filters.author.trim()) params.author = filters.author.trim()
  if (filters.content.trim()) params.content = filters.content.trim()
  if (filters.date_from) params.date_from = filters.date_from
  if (filters.date_to) params.date_to = filters.date_to
  return params
}

async function loadMessages(): Promise<void> {
  loading.value = true
  try {
    pageResult.value = await fetchMessagesByPage({
      page_num: pagination.page_num,
      page_size: pagination.page_size,
      params: buildParams(),
    })
  } catch (error) {
    showError(getErrorMessage(error))
  } finally {
    loading.value = false
  }
}

function handlePageChange(page: number): void {
  pagination.page_num = page
  void loadMessages()
}

function handleSearch(): void {
  pagination.page_num = 1
  selectedIds.value = []
  void loadMessages()
}

function handleReset(): void {
  filters.author = ''
  filters.content = ''
  filters.date_from = ''
  filters.date_to = ''
  pagination.page_num = 1
  selectedIds.value = []
  void loadMessages()
}

function handleSelectionChange(selection: MessageRecord[]): void {
  selectedIds.value = selection.map((m) => m.id)
}

function truncateContent(content: string, maxLen = 80): string {
  return content.length > maxLen ? content.slice(0, maxLen) + '…' : content
}

// === 单条删除 ===
async function removeMessage(message: MessageRecord): Promise<void> {
  try {
    await ElMessageBox.confirm(
      `确认删除该留言？\n内容：${truncateContent(message.content, 50)}`,
      '确认删除',
      {
        type: 'warning',
        confirmButtonText: '确认删除',
        cancelButtonText: '取消',
      },
    )

    deletingIds.value = [...deletingIds.value, message.id]
    const msg = await deleteMessage(message.id)
    showSuccess(msg || '留言已删除。')
    await loadMessages()
  } catch (error) {
    if (error === 'cancel' || error === 'close') return
    showError(getErrorMessage(error))
  } finally {
    deletingIds.value = deletingIds.value.filter((id) => id !== message.id)
  }
}

function isDeleting(id: string): boolean {
  return deletingIds.value.includes(id)
}

// === 编辑 ===
function openEditDialog(message: MessageRecord): void {
  editingMessage.value = message
  editContent.value = message.content
  editReason.value = ''
  editDialogVisible.value = true
}

async function submitEdit(): Promise<void> {
  if (!editingMessage.value) return
  if (!editContent.value.trim()) {
    showError('留言内容不能为空')
    return
  }
  editSubmitting.value = true
  try {
    const msg = await updateMessage({
      message_id: editingMessage.value.id,
      content: editContent.value.trim(),
      reason: editReason.value.trim(),
    })
    showSuccess(msg || '留言已修改。邮件通知已发送。')
    editDialogVisible.value = false
    await loadMessages()
  } catch (error) {
    showError(getErrorMessage(error))
  } finally {
    editSubmitting.value = false
  }
}

// === 批量删除 ===
function openBatchDelete(): void {
  batchDeleteReason.value = ''
  batchDeleteVisible.value = true
}

async function submitBatchDelete(): Promise<void> {
  if (selectedIds.value.length === 0) return
  batchDeleteSubmitting.value = true
  try {
    const msg = await batchDeleteMessages({
      message_ids: [...selectedIds.value],
      reason: batchDeleteReason.value.trim(),
    })
    showSuccess(msg || `已删除 ${selectedIds.value.length} 条留言。`)
    batchDeleteVisible.value = false
    selectedIds.value = []
    await loadMessages()
  } catch (error) {
    showError(getErrorMessage(error))
  } finally {
    batchDeleteSubmitting.value = false
  }
}

onMounted(() => {
  void loadMessages()
})
</script>

<template>
  <RoutePageShell
    title="留言管理"
    eyebrow="Message Management"
    subtitle="查看、搜索、编辑和删除用户留言板内容。删除和修改将通过邮件通知发布者。"
  >
    <template #actions>
      <el-button plain @click="loadMessages">刷新</el-button>
      <el-button
        type="danger"
        plain
        :disabled="!hasSelection"
        @click="openBatchDelete"
      >
        批量删除 ({{ selectedIds.length }})
      </el-button>
    </template>

    <!-- 搜索过滤 -->
    <section class="glass-panel filter-section">
      <el-form :inline="true" :model="filters" class="filter-form">
        <el-form-item label="作者 UUID">
          <el-input
            v-model="filters.author"
            placeholder="精确匹配"
            clearable
            style="width: 260px"
          />
        </el-form-item>
        <el-form-item label="内容关键词">
          <el-input
            v-model="filters.content"
            placeholder="模糊搜索"
            clearable
            style="width: 220px"
          />
        </el-form-item>
        <el-form-item label="起始日期">
          <el-date-picker
            v-model="filters.date_from"
            type="date"
            placeholder="选择起始日期"
            value-format="YYYY-MM-DD"
            style="width: 160px"
          />
        </el-form-item>
        <el-form-item label="截止日期">
          <el-date-picker
            v-model="filters.date_to"
            type="date"
            placeholder="选择截止日期"
            value-format="YYYY-MM-DD"
            style="width: 160px"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">搜索</el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>
    </section>

    <!-- 数据表格 -->
    <section class="glass-panel table-section" v-loading="loading">
      <el-table
        :data="pageResult?.list ?? []"
        style="width: 100%"
        @selection-change="handleSelectionChange"
      >
        <el-table-column type="selection" width="45" />
        <el-table-column prop="author" label="发布者" min-width="240" show-overflow-tooltip />
        <el-table-column label="留言内容" min-width="360">
          <template #default="{ row }">
            <span class="content-cell">{{ truncateContent(row.content, 100) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="发布时间" width="180">
          <template #default="{ row }">
            {{ formatDateTime(row.created_at) }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button size="small" plain @click="openEditDialog(row)">
              编辑
            </el-button>
            <el-button
              size="small"
              type="danger"
              plain
              :loading="isDeleting(row.id)"
              :disabled="isDeleting(row.id)"
              @click="removeMessage(row)"
            >
              删除
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-empty
        v-if="!loading && (pageResult?.list.length ?? 0) === 0"
        description="暂无留言数据"
      />
    </section>

    <!-- 分页 -->
    <section class="glass-panel pagination-section" v-if="pageResult">
      <el-pagination
        background
        layout="prev, pager, next"
        :current-page="pagination.page_num"
        :page-size="pagination.page_size"
        :total="pageResult.total"
        @current-change="handlePageChange"
      />
    </section>

    <!-- 编辑弹窗 -->
    <el-dialog
      v-model="editDialogVisible"
      title="编辑留言"
      width="560px"
      :close-on-click-modal="false"
    >
      <el-form label-position="top">
        <el-form-item label="留言内容">
          <el-input
            v-model="editContent"
            type="textarea"
            :rows="5"
            maxlength="500"
            show-word-limit
          />
        </el-form-item>
        <el-form-item label="修改原因（将包含在邮件通知中）">
          <el-input
            v-model="editReason"
            placeholder="例：内容包含不当言论，已修改为合规内容"
            maxlength="200"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="editSubmitting" @click="submitEdit">
          确认修改并发送通知
        </el-button>
      </template>
    </el-dialog>

    <!-- 批量删除确认弹窗 -->
    <el-dialog
      v-model="batchDeleteVisible"
      title="批量删除留言"
      width="480px"
      :close-on-click-modal="false"
    >
      <p>确认删除选中的 <strong>{{ selectedIds.length }}</strong> 条留言？此操作不可撤销。</p>
      <el-form label-position="top" style="margin-top: 1rem">
        <el-form-item label="删除原因（将包含在邮件通知中）">
          <el-input
            v-model="batchDeleteReason"
            type="textarea"
            :rows="2"
            placeholder="例：批量清理违规留言"
            maxlength="200"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="batchDeleteVisible = false">取消</el-button>
        <el-button
          type="danger"
          :loading="batchDeleteSubmitting"
          @click="submitBatchDelete"
        >
          确认批量删除
        </el-button>
      </template>
    </el-dialog>
  </RoutePageShell>
</template>

<style scoped>
.filter-section {
  padding: 1rem 1.25rem 0;
  margin-bottom: 1rem;
}

.filter-form {
  display: flex;
  flex-wrap: wrap;
  gap: 0;
}

.table-section {
  padding: 1.25rem;
  margin-bottom: 1rem;
}

.content-cell {
  color: var(--barc-text-soft);
  line-height: 1.5;
}

.pagination-section {
  display: flex;
  justify-content: center;
  padding: 1rem 1.25rem;
}
</style>
```

**验证**: TypeScript 编译通过 `npx vue-tsc --noEmit`（在 BarcManageFrontendV2 目录下）。

---

## Task 9: 前端 V2 — 注册路由

**Files:**
- Modify: `BarcManageFrontendV2/src/app/router/routes.ts`

- [ ] **Step 1: 添加路由条目**

在文件顶部 import 区域追加：
```typescript
const MessagesListView = () => import('@/modules/messages/views/MessagesListView.vue')
```

在 `adminChildren` 数组中，在 `works/logs` 路由之后（`menuOrder: 43` 之后）、`403` 路由之前插入：

```typescript
{
  path: 'messages/list',
  name: 'messages-list',
  component: MessagesListView,
  meta: {
    title: '留言管理',
    subtitle: '查看、搜索、编辑和删除用户留言板内容。',
    requiresAuth: true,
    requiresManager: true,
    minManagerPermissionBit: MANAGER_PERMISSION.FIR_MAINTAINER,
    menuLabel: '留言管理',
    menuGroup: 'content',
    menuGroupLabel: '内容管理',
    groupOrder: 50,
    menuOrder: 44,
  },
},
```

- [ ] **Step 2: 确认权限常量**

`MANAGER_PERMISSION.FIR_MAINTAINER` 在 `src/shared/constants/permissions.ts` 已定义为 `1 << 1 = 2`。路由守卫使用 `minManagerPermissionBit`，会调用 `hasManagerPermissionBit()` → `(currentPermission & requiredBit) !== 0`。与后端二进制 & 检查一致。

**验证**: 启动 V2 前端，以 FIR_MAINTAINER 权限账号登录，侧边栏应出现「留言管理」，跳转 `/messages/list` 页面正常显示。以 馆长(32) 账号登录，侧边栏不显示留言管理，直接访问 URL 被路由守卫拦截。

---

## Task 10: 集成验证

- [ ] **Step 1: 编译后端**

```bash
cd BarcBackend
mvn compile
```
期望: BUILD SUCCESS

- [ ] **Step 2: 编译前端 V2**

```bash
cd BarcManageFrontendV2
npm run build
```
期望: 无 TypeScript 错误，构建成功

- [ ] **Step 3: 对比检查 — 确保未破坏公开前端**

确认 `BarcFrontend` 中仍可正常调用：
- `GET /comment/message_board/new?limit=50`
- `GET /comment/message_board/upload?content=xxx`

`MbContainerComp.vue` 和 `HomeMessageComp.vue` 无需任何修改。

- [ ] **Step 4: 权限测试**

| 账号 | 权限值 | 能否看到留言管理菜单 | 能否调 admin API |
|------|--------|---------------------|-----------------|
| 风纪委员 | 1 | ❌ | ❌ (1&2=0) |
| 一级管理员 | 2 | ✅ | ✅ |
| 二级管理员 | 4 | ❌ | ❌ (4&2=0) |
| 副馆长 | 16 | ❌ | ❌ (16&2=0) |
| 馆长 | 32 | ❌ | ❌ (32&2=0) |
| 一级+二级管理员 | 6 | ✅ | ✅ (6&2=2≠0) |
| 馆长+一级管理员 | 34 | ✅ | ✅ (34&2=2≠0) |

---

## 文件变更清单

| 操作 | 文件 |
|------|------|
| 修改 | `BarcBackend/src/main/java/com/miaoyu/barc/BarcBackendApplication.java` |
| 创建 | `BarcBackend/src/main/resources/mappers/Comment/MessageBoardMapper.xml` |
| 修改 | `BarcBackend/src/main/java/com/miaoyu/barc/comment/mapper/MessageBoardMapper.java` |
| 修改 | `BarcBackend/src/main/java/com/miaoyu/barc/email/utils/SendEmailUtils.java` |
| 修改 | `BarcBackend/src/main/java/com/miaoyu/barc/comment/service/MessageBoardService.java` |
| 创建 | `BarcBackend/src/main/java/com/miaoyu/barc/comment/service/MessageNotificationService.java` |
| 修改 | `BarcBackend/src/main/java/com/miaoyu/barc/comment/controller/MessageBoardController.java` |
| 创建 | `BarcManageFrontendV2/src/shared/types/message.ts` |
| 创建 | `BarcManageFrontendV2/src/modules/messages/api/messages.service.ts` |
| 创建 | `BarcManageFrontendV2/src/modules/messages/views/MessagesListView.vue` |
| 修改 | `BarcManageFrontendV2/src/app/router/routes.ts` |

**总计: 5 个新文件, 6 个修改文件。0 个公开前端文件被修改。**
