# Public Work Metric Hide Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Hide artwork view counts on the public frontend, keep like counts visible, and show comment counts after likes on public list cards and the public detail page without changing backend APIs.

**Architecture:** Public list APIs do not expose `comment_count`, so the frontend must fetch comment counts separately for visible work IDs and cache/dedupe those requests in a small utility/composable. Existing list-card components will switch from `view_count + like_count` to `like_count + comment_count`, while the detail page will drop the view-count chip and keep the existing like/comment interactions.

**Tech Stack:** Vue 3, TypeScript, Pinia, Ant Design Vue icons, Axios, Vitest, vue-tsc, Vite

---

## File map

- Create: `BarcFrontend/src/utils/workCommentCountCache.ts`
- Create: `BarcFrontend/src/utils/__tests__/workCommentCountCache.spec.ts`
- Modify: `BarcFrontend/src/components/Work/WorkItemListComp.vue`
- Modify: `BarcFrontend/src/components/Work/Category/WorkCategoryGeneralComp.vue`
- Modify: `BarcFrontend/src/components/Account/AccountWorkListComp.vue`
- Modify: `BarcFrontend/src/components/Student/StudentContainerComp.vue`
- Modify: `BarcFrontend/src/components/Work/WorkDetailComp.vue`

## Implementation decisions

- Public scope only: do **not** touch `BarcManageFrontend*` or `ManageWorkComp.vue`.
- No backend changes.
- For list/card surfaces, fetch comment counts lazily per visible work ID via the existing `fetchCommentCountByWork(workId)` helper.
- Cache comment-count requests by work ID so repeated renders/page transitions do not refetch the same counts in one session.
- On comment-count request failure, display `0` and avoid breaking card rendering.
- Keep the detail page’s existing comment modal and click behavior intact.

### Task 1: Add a reusable public comment-count cache helper

**Files:**
- Create: `BarcFrontend/src/utils/workCommentCountCache.ts`
- Test: `BarcFrontend/src/utils/__tests__/workCommentCountCache.spec.ts`

- [ ] **Step 1: Write the failing utility tests**

Cover these behaviors:
- resolves a fetched count for one work ID
- dedupes concurrent requests for the same work ID
- caches resolved counts for later reads
- falls back to `0` when the comment-count request throws
- supports batch loading for multiple work IDs

- [ ] **Step 2: Run the new test file and confirm failure**

Run: `npm run test:run -- src/utils/__tests__/workCommentCountCache.spec.ts`
Expected: FAIL because the helper does not exist yet.

- [ ] **Step 3: Implement the minimal helper**

Add a small utility that:
- imports `fetchCommentCountByWork` from `src/utils/commentApi.ts`
- stores resolved counts in an internal `Map<string, number>`
- stores in-flight promises in an internal `Map<string, Promise<number>>`
- exports:
  - `loadWorkCommentCount(workId: string): Promise<number>`
  - `loadWorkCommentCounts(workIds: Array<string>): Promise<Record<string, number>>`
  - `getCachedWorkCommentCount(workId: string): number | undefined`
  - `resetWorkCommentCountCache(): void` for tests

- [ ] **Step 4: Re-run the utility tests**

Run: `npm run test:run -- src/utils/__tests__/workCommentCountCache.spec.ts`
Expected: PASS.

### Task 2: Update the shared public work list card metrics

**Files:**
- Modify: `BarcFrontend/src/components/Work/WorkItemListComp.vue`

- [ ] **Step 1: Add comment-count state wiring**

In `WorkItemListComp.vue`:
- remove the `EyeOutlined` import
- add a comment icon import (`MessageOutlined`)
- add reactive state for `commentCounts` keyed by work ID
- watch `workList` and batch-load comment counts for current items via the new helper

- [ ] **Step 2: Change the card metric row**

Update the cover overlay from:
- `view_count + like_count`

to:
- `like_count + comment_count`

Display `commentCounts[item.id] ?? 0`.

- [ ] **Step 3: Verify no behavior regressions in this component**

Confirm:
- clicking cards still routes to detail
- author nickname enrichment still runs
- no loading placeholder is required; `0` is acceptable until the async count resolves

### Task 3: Update duplicated public list/card components

**Files:**
- Modify: `BarcFrontend/src/components/Work/Category/WorkCategoryGeneralComp.vue`
- Modify: `BarcFrontend/src/components/Account/AccountWorkListComp.vue`
- Modify: `BarcFrontend/src/components/Student/StudentContainerComp.vue`

- [ ] **Step 1: Mirror the shared metric behavior in each duplicated card component**

For each file:
- remove `EyeOutlined`
- add `MessageOutlined`
- add local comment-count state keyed by work ID
- batch-load counts for the current list after data is present / whenever the list changes
- swap the displayed metrics from `view_count + like_count` to `like_count + comment_count`

- [ ] **Step 2: Preserve each file’s existing data-loading pattern**

Follow the local pattern instead of refactoring across files:
- `WorkCategoryGeneralComp.vue` loads directly in the component
- `AccountWorkListComp.vue` renders Pinia-provided `pageResult.list`
- `StudentContainerComp.vue` owns its own `works` array

- [ ] **Step 3: Sanity-check fallback rendering**

Each card should render even if comment-count fetches fail; use `0` as the fallback.

### Task 4: Update the public detail page metric strip

**Files:**
- Modify: `BarcFrontend/src/components/Work/WorkDetailComp.vue`

- [ ] **Step 1: Remove the view-count chip from the template**

Delete the `view_box` metric block and the unused `EyeOutlined` import.

- [ ] **Step 2: Keep like + comment as the remaining metric chips**

Ensure the metric order becomes:
- like count chip (existing toggle-like behavior unchanged)
- comment count chip (existing click-to-open-comment-modal behavior unchanged)

- [ ] **Step 3: Clean up style rules tied only to the removed view chip**

Remove or adjust any now-dead `.view_box`-specific styling while preserving layout and spacing for the remaining chips and action buttons.

### Task 5: Verify types, tests, and build health

**Files:**
- Test: `BarcFrontend/src/utils/__tests__/workCommentCountCache.spec.ts`
- Verify changed Vue files under `BarcFrontend/src/components/...`

- [ ] **Step 1: Run targeted tests**

Run: `npm run test:run -- src/utils/__tests__/workCommentCountCache.spec.ts`
Expected: PASS.

- [ ] **Step 2: Run type checking**

Run: `npm run type-check`
Expected: PASS.

- [ ] **Step 3: Run the production build**

Run: `npm run build`
Expected: PASS.

- [ ] **Step 4: Manual verification checklist**

Confirm on the public frontend:
- work search/general list cards show like + comment, no view count
- category cards show like + comment, no view count
- account work/collection/likes cards show like + comment, no view count
- student-page cards show like + comment, no view count
- work detail shows like + comment, no view count
- opening the detail comment modal still refreshes comment count correctly after comment/reply actions
