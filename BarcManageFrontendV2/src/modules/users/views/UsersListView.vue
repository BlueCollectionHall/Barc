<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'

import RoutePageShell from '@/app/components/RoutePageShell.vue'
import { usePermissionStore } from '@/app/stores/permissions'
import UserQuickCreateDrawer from '@/modules/users/components/UserQuickCreateDrawer.vue'
import { queryUsersByPage } from '@/modules/users/api/users.service'
import { getErrorMessage, type PageResult } from '@/shared/types/api'
import type { UserIdentity, UserListFilters, UserListItem } from '@/shared/types/user'
import { showError } from '@/shared/utils/message'
import { useMouseTooltip } from '@/shared/utils/mouse-tooltip'

interface CreateFeedback {
  description: string
  hints: string[]
  outcome: string
  title: string
  tone: 'info' | 'success' | 'warning'
}

interface QuickCreateSubmittedPayload {
  email_verified: boolean
  safe_level: number
  telephone_present: boolean
  username: string
}

const permissionStore = usePermissionStore()
const tooltip = useMouseTooltip()

const filters = reactive<UserListFilters>({
  identity: 'USER',
})

const pagination = reactive({
  page_num: 1,
  page_size: 12,
})

const loading = ref(false)
const createSubmitting = ref(false)
const createDrawerVisible = ref(false)
const pageResult = ref<PageResult<UserListItem> | null>(null)
const createFeedback = ref<CreateFeedback | null>(null)

const permissionOptions = computed(() => permissionStore.optionsForIdentity((filters.identity ?? 'USER') as UserIdentity))
const managerCount = computed(() => pageResult.value?.list.filter((item) => item.identity === 'MANAGER').length ?? 0)
const hasActiveFilters = computed(
  () =>
    Boolean(filters.username?.trim()) ||
    Boolean(filters.nickname?.trim()) ||
    filters.permission !== undefined ||
    filters.identity !== 'USER',
)
const showEmptyState = computed(() => !loading.value && pageResult.value !== null && pageResult.value.list.length === 0)
const emptyStateTitle = computed(() => {
  if (hasActiveFilters.value) {
    return '当前筛选下没有匹配用户'
  }

  if (pagination.page_num > 1) {
    return '这一页暂时没有用户'
  }

  return '暂时没有用户记录'
})
const emptyStateDescription = computed(() => {
  if (hasActiveFilters.value) {
    return '用户名、昵称、身份或权限筛选仍在生效，可以调整条件后再试。'
  }

  if (pagination.page_num > 1) {
    return '当前页没有可显示的数据，可以返回第一页或重新刷新列表。'
  }

  return '当新的用户进入系统后，这里会继续按当前视图展示。'
})

watch(
  () => filters.identity,
  (identity) => {
    const options = permissionStore.optionsForIdentity((identity ?? 'USER') as UserIdentity)
    if (!options.some((option) => option.value === filters.permission)) {
      filters.permission = undefined
    }
  },
)

async function loadUsers(): Promise<boolean> {
  loading.value = true

  try {
    pageResult.value = await queryUsersByPage({
      page_num: pagination.page_num,
      page_size: pagination.page_size,
      params: {
        ...(filters.username ? { username: filters.username.trim() } : {}),
        ...(filters.nickname ? { nickname: filters.nickname.trim() } : {}),
        ...(filters.identity ? { identity: filters.identity } : {}),
        ...(filters.permission !== undefined ? { permission: filters.permission } : {}),
      },
    })

    return true
  } catch (error) {
    showError(getErrorMessage(error))
    return false
  } finally {
    loading.value = false
  }
}

function submitFilters(): void {
  pagination.page_num = 1
  void loadUsers()
}

function resetFilters(): void {
  filters.username = ''
  filters.nickname = ''
  filters.identity = 'USER'
  filters.permission = undefined
  pagination.page_num = 1
  void loadUsers()
}

function handlePageChange(page: number): void {
  pagination.page_num = page
  void loadUsers()
}

function permissionLabel(item: UserListItem): string {
  return permissionStore.labelFor(item.identity, item.permission)
}

function createFollowUpHints(payload: QuickCreateSubmittedPayload): string[] {
  const hints: string[] = []

  if (!payload.email_verified) {
    hints.push('邮箱仍处于待验证状态，建议提醒用户尽快完成验证。')
  }

  if (!payload.telephone_present) {
    hints.push('当前没有登记电话，后续如果需要人工联络，建议补录联系方式。')
  }

  if (payload.safe_level > 0) {
    hints.push(`安全等级已设为 ${payload.safe_level}，建议确认是否还需要额外审核或线下跟进。`)
  }

  return hints
}

function setCreateFeedback(
  payload: QuickCreateSubmittedPayload,
  feedback: Omit<CreateFeedback, 'hints'>,
): void {
  createFeedback.value = {
    ...feedback,
    hints: createFollowUpHints(payload),
  }
}

async function handleQuickCreateSubmitted(payload: QuickCreateSubmittedPayload): Promise<void> {
  const reloaded = await loadUsers()
  const createdUserVisible = pageResult.value?.list.some((item) => item.username === payload.username) ?? false

  if (!reloaded) {
    setCreateFeedback(payload, {
      tone: 'warning',
      outcome: '列表刷新失败',
      title: `已创建用户 ${payload.username}`,
      description: '新用户已经创建成功，但当前列表刷新失败了，请稍后手动刷新页面确认结果。',
    })
    return
  }

  if (createdUserVisible) {
    setCreateFeedback(payload, {
      tone: 'success',
      outcome: '当前页已显示',
      title: `已创建用户 ${payload.username}`,
      description: '列表已刷新，而且这个新用户已经出现在当前页，你可以继续核对资料或处理下一位。',
    })
    return
  }

  if (hasActiveFilters.value) {
    setCreateFeedback(payload, {
      tone: 'info',
      outcome: '被当前筛选隐藏',
      title: `已创建用户 ${payload.username}`,
      description: '列表已按当前筛选条件刷新，当前筛选条件仍在生效；如果暂时没看到新用户，请调整筛选后再查看。',
    })
    return
  }

  if (pagination.page_num > 1) {
    setCreateFeedback(payload, {
      tone: 'info',
      outcome: '当前停留在后续页',
      title: `已创建用户 ${payload.username}`,
      description: '列表已按当前页刷新；如果暂时没看到新用户，可以翻回第一页继续确认。',
    })
    return
  }

  setCreateFeedback(payload, {
    tone: 'info',
    outcome: '当前页暂未显示',
    title: `已创建用户 ${payload.username}`,
    description: '列表已经刷新，但当前页暂时还没有显示这个新用户；可以再次刷新或稍后回到第一页确认。',
  })
}

onMounted(async () => {
  await permissionStore.ensureCatalog()
  await loadUsers()
})
</script>

<template>
  <RoutePageShell title="用户列表" eyebrow="User List">
    <template #actions>
      <span class="header-meta">
        当前页 {{ pageResult?.list.length ?? 0 }} 条 · 管理员 {{ managerCount }} 条 · 总计 {{ pageResult?.total ?? 0 }} 条
      </span>
      <el-button plain @click="loadUsers">刷新</el-button>
      <el-button type="primary" @click="createDrawerVisible = true">新增用户</el-button>
    </template>

    <section class="glass-panel page-card">
      <el-form class="filters" label-position="top" @submit.prevent="submitFilters">
        <el-form-item label="用户名">
          <el-input v-model="filters.username" clearable />
        </el-form-item>
        <el-form-item label="昵称">
          <el-input v-model="filters.nickname" clearable />
        </el-form-item>
        <el-form-item label="身份">
          <el-select v-model="filters.identity" clearable placeholder="全部身份">
            <el-option label="用户" value="USER" />
            <el-option label="管理者" value="MANAGER" />
          </el-select>
        </el-form-item>
        <el-form-item label="权限">
          <el-select v-model="filters.permission" clearable placeholder="全部权限">
            <el-option v-for="option in permissionOptions" :key="option.value" :label="option.label" :value="option.value" />
          </el-select>
        </el-form-item>
        <div class="filters__actions">
          <el-button @click="resetFilters">重置</el-button>
          <el-button type="primary" native-type="submit" :loading="loading">搜索</el-button>
        </div>
      </el-form>
    </section>

    <section class="glass-panel page-card page-card--table">
      <transition name="fade-slide">
        <article
          v-if="createFeedback"
          class="create-feedback"
          :class="`create-feedback--${createFeedback.tone}`"
        >
          <div class="create-feedback__header">
            <div>
              <div class="create-feedback__eyebrow">Create feedback</div>
              <div class="create-feedback__outcome">{{ createFeedback.outcome }}</div>
              <h2 class="create-feedback__title">{{ createFeedback.title }}</h2>
              <p class="create-feedback__description">{{ createFeedback.description }}</p>
            </div>

            <el-button text @click="createFeedback = null">收起</el-button>
          </div>

          <ul v-if="createFeedback.hints.length > 0" class="create-feedback__hints">
            <li v-for="hint in createFeedback.hints" :key="hint" class="create-feedback__hint">
              {{ hint }}
            </li>
          </ul>
        </article>
      </transition>

      <div v-if="showEmptyState" class="empty-state">
        <div class="empty-state__eyebrow">Users</div>
        <h2 class="empty-state__title">{{ emptyStateTitle }}</h2>
        <p class="empty-state__description">{{ emptyStateDescription }}</p>

        <div class="empty-state__actions">
          <el-button plain @click="loadUsers">重新加载</el-button>
          <el-button v-if="hasActiveFilters" type="primary" @click="resetFilters">清空筛选</el-button>
        </div>
      </div>

      <el-table v-else v-loading="loading" :data="pageResult?.list ?? []" stripe>
        <el-table-column label="头像" width="88">
          <template #default="scope">
            <img v-if="scope.row.avatar" class="user-avatar" :src="scope.row.avatar" alt="avatar" />
            <div v-else class="user-avatar user-avatar--fallback">{{ scope.row.nickname.slice(0, 1) }}</div>
          </template>
        </el-table-column>
        <el-table-column prop="username" label="用户名" min-width="140" />
        <el-table-column label="昵称" min-width="140">
          <template #default="scope">
            <span
              class="ellipsis-text"
              @mouseenter="tooltip.show(scope.row.nickname, $event)"
              @mousemove="tooltip.move($event)"
              @mouseleave="tooltip.hide()"
            >{{ scope.row.nickname }}</span>
          </template>
        </el-table-column>
        <el-table-column label="身份" min-width="110">
          <template #default="scope">
            <el-tag :type="scope.row.identity === 'MANAGER' ? 'warning' : 'info'">{{ scope.row.identity }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="权限" min-width="160">
          <template #default="scope">{{ permissionLabel(scope.row) }}</template>
        </el-table-column>
        <el-table-column label="年龄" width="90">
          <template #default="scope">{{ scope.row.age ?? '—' }}</template>
        </el-table-column>
      </el-table>

      <div class="pagination-row">
        <el-pagination
          v-if="pageResult"
          background
          layout="prev, pager, next"
          :current-page="pagination.page_num"
          :page-size="pagination.page_size"
          :total="pageResult.total"
          @current-change="handlePageChange"
        />
      </div>
    </section>

    <UserQuickCreateDrawer
      v-model="createDrawerVisible"
      v-model:loading="createSubmitting"
      @submitted="handleQuickCreateSubmitted"
    />

    <teleport to="body">
      <div
        v-show="tooltip.visible"
        class="mouse-tooltip"
        :style="{ left: tooltip.x + 'px', top: tooltip.y + 'px' }"
      >
        {{ tooltip.text }}
      </div>
    </teleport>
  </RoutePageShell>
</template>

<style scoped>
.page-card {
  display: flex;
  flex-direction: column;
  gap: 1rem;
  padding: 0.85rem 1.25rem;
}

.page-card--table {
  min-height: 440px;
}

.create-feedback {
  display: grid;
  gap: var(--barc-space-4);
  margin-bottom: var(--barc-space-1);
  padding: var(--barc-space-4);
  border: 1px solid var(--barc-border);
  border-radius: var(--barc-radius-md);
  background:
    radial-gradient(circle at top right, rgba(255, 255, 255, 0.7), transparent 34%),
    linear-gradient(160deg, var(--barc-surface-strong), var(--barc-surface));
  box-shadow: var(--barc-shadow-sm);
}

.create-feedback--success {
  border-color: rgba(79, 139, 119, 0.3);
}

.create-feedback--info {
  border-color: rgba(95, 136, 173, 0.28);
}

.create-feedback--warning {
  border-color: rgba(200, 93, 99, 0.26);
}

.create-feedback__header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--barc-space-4);
}

.create-feedback__eyebrow,
.create-feedback__outcome {
  color: var(--barc-accent-strong);
  font-size: 0.78rem;
  letter-spacing: 0.16em;
  text-transform: uppercase;
}

.create-feedback__outcome {
  margin-top: var(--barc-space-2);
}

.create-feedback__title {
  margin: var(--barc-space-2) 0 0;
  font-family: var(--barc-font-display);
  font-size: 1.25rem;
}

.create-feedback__description {
  margin: var(--barc-space-2) 0 0;
  color: var(--barc-text-soft);
  line-height: 1.7;
}

.create-feedback__hints {
  display: grid;
  gap: var(--barc-space-3);
  margin: 0;
  padding: 0;
  list-style: none;
}

.create-feedback__hint {
  padding: var(--barc-space-3) var(--barc-space-4);
  border: 1px solid var(--barc-border);
  border-radius: var(--barc-radius-sm);
  background: rgba(255, 255, 255, 0.56);
  color: var(--barc-text-soft);
  line-height: 1.7;
}

.filters {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: 1rem;
  align-items: end;
}

.filters :deep(.el-form-item) {
  margin-bottom: 0;
}

.filters__actions {
  display: flex;
  align-items: end;
  justify-content: flex-end;
  gap: 0.75rem;
}

.pagination-row {
  display: flex;
  justify-content: center;
  padding-top: 1rem;
}

.empty-state {
  display: flex;
  min-height: 320px;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: var(--barc-space-3);
  padding: var(--barc-space-6) var(--barc-space-5);
  border: 1px dashed var(--barc-border-strong);
  border-radius: var(--barc-radius-md);
  background:
    radial-gradient(circle at top, var(--barc-bg-accent), transparent 40%),
    linear-gradient(180deg, var(--barc-surface-strong), var(--barc-surface));
  text-align: center;
}

.empty-state__eyebrow {
  color: var(--barc-accent-strong);
  font-size: 0.78rem;
  letter-spacing: 0.18em;
  text-transform: uppercase;
}

.empty-state__title {
  margin: 0;
  font-family: var(--barc-font-display);
  font-size: 1.4rem;
}

.empty-state__description {
  max-width: 32rem;
  margin: 0;
  color: var(--barc-text-soft);
  line-height: 1.7;
}

.empty-state__actions {
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
  gap: 0.75rem;
}

.ellipsis-text {
  display: block;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.mouse-tooltip {
  position: fixed;
  z-index: 9999;
  padding: 6px 10px;
  border-radius: 6px;
  background: rgba(23, 33, 45, 0.92);
  color: #fff;
  font-size: 0.8rem;
  line-height: 1.4;
  pointer-events: none;
  white-space: nowrap;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
}

.user-avatar {
  width: 42px;
  height: 42px;
  border-radius: 16px;
  object-fit: cover;
}

.user-avatar--fallback {
  display: grid;
  place-items: center;
  background: linear-gradient(145deg, rgba(95, 136, 173, 0.85), rgba(170, 193, 214, 0.92));
  color: white;
  font-weight: 700;
}

@media (max-width: 1080px) {
  .filters {
    grid-template-columns: 1fr;
  }

  .create-feedback__header {
    flex-direction: column;
  }
}
</style>
