# Work Content Freshness Timestamp Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Introduce a user-facing content freshness timestamp for works so only approved content edits refresh homepage/list/detail “更新时间”, while views, likes, status changes, cover/image changes, and other non-content writes do not.

**Architecture:** Add a nullable `content_updated_at` column to `work` and keep existing `updated_at` as the technical row-update timestamp. Public freshness reads must use `COALESCE(content_updated_at, updated_at)` because historical rows are not backfilled. Only creation and text-content edit paths should set `content_updated_at`; view/like/status/claim/cover/image paths must keep using raw `updated_at` only. Management-side APIs should expose both timestamps so admin tooling remains usable and no existing management workflow loses observability.

**Tech Stack:** Spring Boot 3.4, MyBatis, MySQL + Flyway, Vue 3 + Vite, Ant Design Vue, Element Plus, JUnit 5, Vitest

---

## File Map

**Create**
- `BarcBackend/src/main/resources/db/migration/V6__add_work_content_updated_at.sql`
- `BarcBackend/src/test/java/com/miaoyu/barc/api/work/mapper/WorkMapperContentUpdatedAtSqlTest.java`
- `BarcBackend/src/test/java/com/miaoyu/barc/api/work/service/WorkContentTimestampRoutingTest.java`
- `BarcManageFrontendV2/src/modules/works/__tests__/WorkDetailComp.spec.ts`

**Modify**
- `BarcBackend/src/main/resources/schema.sql`
- `BarcBackend/src/main/java/com/miaoyu/barc/api/work/model/WorkModel.java`
- `BarcBackend/src/main/java/com/miaoyu/barc/api/work/model/entity/WorkEntity.java`
- `BarcBackend/src/main/java/com/miaoyu/barc/api/work/mapper/WorkMapper.java`
- `BarcBackend/src/main/java/com/miaoyu/barc/api/work/mapper/WorkLikeMapper.java`
- `BarcBackend/src/main/resources/mappers/work/WorkMapper.xml`
- `BarcBackend/src/test/java/com/miaoyu/barc/api/work/mapper/WorkMapperXmlProjectionTest.java`
- `BarcBackend/src/test/java/com/miaoyu/barc/api/work/service/WorkServiceViewCountTest.java`
- `BarcFrontend/src/interfaces/WorkImpl.ts`
- `BarcFrontend/src/components/Work/WorkDetailComp.vue`
- `BarcManageFrontendV2/src/modules/works/api/workManage.ts`
- `BarcManageFrontendV2/src/modules/works/components/WorkDetailComp.vue`

**Maybe modify only if needed after implementation review**
- `BarcBackend/src/main/java/com/miaoyu/barc/api/work/service/WorkService.java`
- `BarcBackend/src/main/java/com/miaoyu/barc/api/work/service/WorkManageService.java`

## Business Rules Locked In

- Public/homepage/list/detail freshness uses the new `content_updated_at` concept.
- `updated_at` remains the technical row-update timestamp and may continue to change on views/likes/status updates.
- `content_updated_at` updates on:
  - work creation
  - owner text-content edits via `updateText`
  - admin text-content edits via `updateText`
- `content_updated_at` does **not** update on:
  - `incrementViewCount`
  - `incrementLikeCount`
  - `decrementLikeCount`
  - status changes (`PUBLIC` / `PRIVATE` / `OFF` / `BAN` / `DELETED` / restore)
  - claim/assign/revoke actions
  - cover replacement
  - content image table writes
- No historical backfill. Existing rows without `content_updated_at` continue to use `updated_at` as fallback in public reads.
- Management-side detail UI must keep working and should expose both content freshness and technical row freshness.

---

### Task 1: Lock the backend timestamp contract with failing tests

**Files:**
- Create: `BarcBackend/src/test/java/com/miaoyu/barc/api/work/mapper/WorkMapperContentUpdatedAtSqlTest.java`
- Modify: `BarcBackend/src/test/java/com/miaoyu/barc/api/work/mapper/WorkMapperXmlProjectionTest.java`

- [ ] **Step 1: Write failing SQL contract tests for the new column and fallback reads**

Test for all of the following:

```java
@Test
void selectByDay_ShouldFilterByCoalescedContentUpdatedAt() {}

@Test
void selectByPage_ShouldOrderByCoalescedContentUpdatedAt() {}

@Test
void selectByPageOnCategory_ShouldOrderByCoalescedContentUpdatedAt() {}

@Test
void selectByUuidWithFilters_ShouldOrderByCoalescedContentUpdatedAt() {}

@Test
void likedWorksProjection_ShouldExposeContentUpdatedAt() {}

@Test
void updateText_ShouldSetContentUpdatedAt() {}

@Test
void insert_ShouldSetContentUpdatedAt() {}

@Test
void viewAndLikeCounterSql_ShouldNotMentionContentUpdatedAt() {}
```

- [ ] **Step 2: Run the targeted backend tests and verify they fail first**

Run from `BarcBackend/`:

```bash
.\mvnw.cmd -q -Dtest=WorkMapperXmlProjectionTest,WorkMapperContentUpdatedAtSqlTest test
```

Expected: FAIL because `content_updated_at` does not exist in schema/model/queries yet.

- [ ] **Step 3: Implement the schema + mapper contract changes**

Update `schema.sql` and the new Flyway migration so `work` has a nullable content freshness column:

```sql
ALTER TABLE work
ADD COLUMN content_updated_at TIMESTAMP NULL DEFAULT NULL AFTER updated_at;
```

Update mapper SQL rules:

```sql
-- creation path
INSERT INTO work (..., created_at, updated_at, content_updated_at)
VALUES (..., CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)

-- content text edit path only
UPDATE work
SET title = #{title},
    description = #{description},
    content = #{content},
    author_nickname = #{author_nickname},
    is_claim = #{is_claim},
    student = #{student},
    content_updated_at = CURRENT_TIMESTAMP
WHERE id = #{id}

-- public freshness reads
WHERE COALESCE(content_updated_at, updated_at) >= DATE_SUB(now(), INTERVAL #{day} DAY)
ORDER BY COALESCE(w.content_updated_at, w.updated_at) DESC
```

Also update `WorkModel` / `WorkEntity` to carry `content_updated_at`.

- [ ] **Step 4: Re-run the targeted backend tests and verify they pass**

Run:

```bash
.\mvnw.cmd -q -Dtest=WorkMapperXmlProjectionTest,WorkMapperContentUpdatedAtSqlTest test
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/resources/schema.sql src/main/resources/db/migration/V6__add_work_content_updated_at.sql src/main/java/com/miaoyu/barc/api/work/model/WorkModel.java src/main/java/com/miaoyu/barc/api/work/model/entity/WorkEntity.java src/main/java/com/miaoyu/barc/api/work/mapper/WorkMapper.java src/main/java/com/miaoyu/barc/api/work/mapper/WorkLikeMapper.java src/main/resources/mappers/work/WorkMapper.xml src/test/java/com/miaoyu/barc/api/work/mapper/WorkMapperXmlProjectionTest.java src/test/java/com/miaoyu/barc/api/work/mapper/WorkMapperContentUpdatedAtSqlTest.java
git commit -m "feat(work): separate content freshness from row updates"
```

---

### Task 2: Lock write-path routing so only approved edits refresh content freshness

**Files:**
- Create: `BarcBackend/src/test/java/com/miaoyu/barc/api/work/service/WorkContentTimestampRoutingTest.java`
- Modify: `BarcBackend/src/test/java/com/miaoyu/barc/api/work/service/WorkServiceViewCountTest.java`

- [ ] **Step 1: Write failing service-level routing tests**

Cover these behaviors with mocks:

```java
@Test
void updateOwnerWorkContent_ShouldCallUpdateTextOnly() {}

@Test
void updateOwnerWorkVisibility_ShouldCallUpdateOnly() {}

@Test
void updateWorkContent_ForManage_ShouldCallUpdateTextOnly() {}

@Test
void updateWorkStatus_ForManage_ShouldCallUpdateOnly() {}

@Test
void getWorksByIdService_ShouldStillRecordViewWithoutTouchingFrontendContract() {}
```

Notes:
- Do not test DB timestamps here; test service-to-mapper routing.
- `WorkServiceViewCountTest` already proves public detail still records a view. Keep that behavior.

- [ ] **Step 2: Run the targeted backend service tests and verify they fail first**

Run from `BarcBackend/`:

```bash
.\mvnw.cmd -q -Dtest=WorkContentTimestampRoutingTest,WorkServiceViewCountTest test
```

Expected: FAIL until the new assertions and any small service wiring adjustments are in place.

- [ ] **Step 3: Make the smallest code changes required to satisfy the routing rules**

Implementation target:
- `WorkService.updateOwnerWorkContent(...)` must continue using `workMapper.updateText(...)`
- `WorkService.updateOwnerWorkVisibility(...)` must continue using `workMapper.update(...)`
- `WorkManageService.updateWorkContent(...)` must continue using `workMapper.updateText(...)`
- `WorkManageService.updateWorkStatus(...)`, `approveClaim(...)`, `revokeClaim(...)`, `assignAuthor(...)` must keep using `workMapper.update(...)`

If current code already satisfies the rule, only the tests need to change.

- [ ] **Step 4: Re-run the targeted backend service tests**

Run:

```bash
.\mvnw.cmd -q -Dtest=WorkContentTimestampRoutingTest,WorkServiceViewCountTest test
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/test/java/com/miaoyu/barc/api/work/service/WorkContentTimestampRoutingTest.java src/test/java/com/miaoyu/barc/api/work/service/WorkServiceViewCountTest.java src/main/java/com/miaoyu/barc/api/work/service/WorkService.java src/main/java/com/miaoyu/barc/api/work/service/WorkManageService.java
git commit -m "test(work): lock content timestamp write paths"
```

---

### Task 3: Switch public detail freshness display to the new field without breaking old rows

**Files:**
- Modify: `BarcFrontend/src/interfaces/WorkImpl.ts`
- Modify: `BarcFrontend/src/components/Work/WorkDetailComp.vue`

- [ ] **Step 1: Add the new frontend work type field**

Update the type contract:

```ts
export interface WorkImpl {
  // existing fields...
  updated_at: Date
  content_updated_at?: Date | null
}
```

- [ ] **Step 2: Change public detail “更新时间” to prefer content freshness**

Update the display logic to use fallback semantics explicitly in the component:

```ts
const effectiveUpdatedAt = work.value?.content_updated_at ?? work.value?.updated_at
```

Render:

```vue
<span><SyncOutlined />更新时间：{{ timestampToCn(effectiveUpdatedAt) }}</span>
```

- [ ] **Step 3: Run public frontend type-check**

Run from `BarcFrontend/`:

```bash
npm run type-check
```

Expected: PASS.

- [ ] **Step 4: Run the public frontend production build**

Run:

```bash
npm run build
```

Expected: build succeeds with no TypeScript/Vue compilation errors.

- [ ] **Step 5: Commit**

```bash
git add src/interfaces/WorkImpl.ts src/components/Work/WorkDetailComp.vue
git commit -m "feat(frontend): use content freshness for work detail"
```

---

### Task 4: Preserve management-side behavior and observability

**Files:**
- Modify: `BarcManageFrontendV2/src/modules/works/api/workManage.ts`
- Modify: `BarcManageFrontendV2/src/modules/works/components/WorkDetailComp.vue`
- Create: `BarcManageFrontendV2/src/modules/works/__tests__/WorkDetailComp.spec.ts`

- [ ] **Step 1: Write a failing manage-detail component test**

Test these expectations:

```ts
it('shows content update time using content_updated_at when present', () => {})
it('falls back to updated_at when content_updated_at is null', () => {})
it('still shows technical updated_at separately for admin observability', () => {})
```

- [ ] **Step 2: Run the manage frontend test and verify it fails first**

Run from `BarcManageFrontendV2/`:

```bash
npm run test:run -- src/modules/works/__tests__/WorkDetailComp.spec.ts
```

Expected: FAIL because the component/API type does not expose or render the new field yet.

- [ ] **Step 3: Update the manage API type and detail UI**

Add to `WorkRecord`:

```ts
content_updated_at?: string | null
```

Update the management detail header to show both timestamps, for example:

```vue
<span>上传：{{ detail.work.created_at }}</span>
<span>内容更新：{{ detail.work.content_updated_at || detail.work.updated_at }}</span>
<span>记录更新：{{ detail.work.updated_at }}</span>
```

Important:
- Do not remove the raw `updated_at` field from management views.
- Do not change management list default sorting unless there is an explicit product ask.
- Do not break existing `WorkEditDetail` response parsing.

- [ ] **Step 4: Re-run the manage frontend test**

Run:

```bash
npm run test:run -- src/modules/works/__tests__/WorkDetailComp.spec.ts
```

Expected: PASS.

- [ ] **Step 5: Run manage frontend type-check and build**

Run:

```bash
npm run type-check
npm run build
```

Expected: both PASS.

- [ ] **Step 6: Commit**

```bash
git add src/modules/works/api/workManage.ts src/modules/works/components/WorkDetailComp.vue src/modules/works/__tests__/WorkDetailComp.spec.ts
git commit -m "feat(manage): expose content freshness alongside row updates"
```

---

### Task 5: Full verification and regression sweep

**Files:**
- Review only; no planned new files

- [ ] **Step 1: Run the focused backend regression suite**

Run from `BarcBackend/`:

```bash
.\mvnw.cmd -q -Dtest=WorkMapperXmlProjectionTest,WorkMapperContentUpdatedAtSqlTest,WorkContentTimestampRoutingTest,WorkServiceViewCountTest test
```

Expected: PASS.

- [ ] **Step 2: Run the full backend test suite**

Run:

```bash
.\mvnw.cmd test
```

Expected: PASS, or document any pre-existing unrelated failures before proceeding.

- [ ] **Step 3: Run public frontend verification**

Run from `BarcFrontend/`:

```bash
npm run type-check
npm run build
```

Expected: PASS.

- [ ] **Step 4: Run manage frontend verification**

Run from `BarcManageFrontendV2/`:

```bash
npm run test:run -- src/modules/works/__tests__/WorkDetailComp.spec.ts
npm run type-check
npm run build
```

Expected: PASS.

- [ ] **Step 5: Manual data checks only if automated verification leaves a gap**

Use these as sanity checks, not as primary acceptance criteria:
- create a new work → `content_updated_at` populated
- edit title/description/content via owner flow → `content_updated_at` changes
- edit title/description/content via manage flow → `content_updated_at` changes
- view detail / like / unlike / status change / cover replace → `updated_at` may change but `content_updated_at` does not
- homepage `/api/work/new` and public lists follow `COALESCE(content_updated_at, updated_at)`
- old untouched rows still appear based on fallback `updated_at`

- [ ] **Step 6: Commit**

```bash
git add .
git commit -m "test(work): verify content freshness timestamp rollout"
```

---

## Implementation Notes

- Keep `content_updated_at` nullable to honor the “no historical backfill” decision.
- Prefer explicit timestamp assignment in mapper SQL over DB-side magic for `content_updated_at` so the business rule stays visible in code.
- Do not repurpose `updated_at` in management APIs; managers still need the technical row-update time.
- `HomeUpdatedComp.vue` does not need a code change if `/api/work/new` semantics change on the backend only.
- `WorkListComp.vue` in management currently shows `created_at`, not `updated_at`; leave that unchanged unless product asks otherwise.
- `WorkLikeMapper.selectPublicLikedWorksByUsername(...)` should project `content_updated_at` even though list ordering remains `wl.created_at DESC`, so public liked-work cards/details receive the new field consistently.

## Acceptance Criteria

- Public homepage “最新收录作品对应的学生” no longer changes because someone viewed or liked a work after rollout.
- Public list/category/author/uploader freshness ordering uses `COALESCE(content_updated_at, updated_at)`.
- Public work detail “更新时间” reflects `content_updated_at` when available.
- Management detail preserves normal behavior and shows both content freshness and technical row freshness.
- Owner/admin text edits update `content_updated_at`; view/like/status/claim/cover/image actions do not.
- No historical backfill occurs.
