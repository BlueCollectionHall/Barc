<script setup lang="ts">
import { ElMessageBox } from 'element-plus'
import { computed, onMounted, reactive, ref, watch } from 'vue'

import RoutePageShell from '@/app/components/RoutePageShell.vue'
import { usePermissionStore } from '@/app/stores/permissions'
import { changePermission, queryUsersByPage } from '@/modules/users/api/users.service'
import { getErrorMessage, type PageResult } from '@/shared/types/api'
import type { PermissionOption, UserIdentity, UserListFilters, UserListItem } from '@/shared/types/user'
import { showError, showSuccess } from '@/shared/utils/message'

interface EditingState {
  uuid: string
  nickname: string
  username: string
  identity: UserIdentity
  permission: number
}

interface PermissionChangePreview {
  classification: string
  identity_after_label: string
  identity_before_label: string
  permission_after_label: string
  permission_before_label: string
}

interface AppliedPermissionFeedback extends PermissionChangePreview {
  title: string
}

const permissionStore = usePermissionStore()

const filters = reactive<UserListFilters>({
  identity: 'MANAGER',
})

const pagination = reactive({
  page_num: 1,
  page_size: 10,
})

const loading = ref(false)
const saving = ref(false)
const dialogVisible = ref(false)
const pageResult = ref<PageResult<UserListItem> | null>(null)
const originalEditing = ref<EditingState | null>(null)
const appliedFeedback = ref<AppliedPermissionFeedback | null>(null)
const editing = reactive<EditingState>({
  uuid: '',
  nickname: '',
  username: '',
  identity: 'MANAGER',
  permission: 1,
})

const filterPermissionOptions = computed(() => permissionStore.optionsForIdentity((filters.identity ?? 'MANAGER') as UserIdentity))
const nearMaxLabel = computed(() => permissionStore.myPermissionNearMax?.label ?? '加载中…')
const operatorPermissionCeiling = computed(() => permissionStore.myPermissionNearMax?.value ?? null)
const dialogPermissionOptions = computed<PermissionOption[]>(() => {
  const options = permissionStore.optionsForIdentity(editing.identity)
  const ceiling = operatorPermissionCeiling.value

  if (ceiling === null) {
    return options
  }

  return options.filter((option) => option.value <= ceiling)
})
const hasActiveFilters = computed(
  () =>
    Boolean(filters.username?.trim()) ||
    Boolean(filters.nickname?.trim()) ||
    filters.permission !== undefined ||
    filters.identity !== 'MANAGER',
)
const showEmptyState = computed(() => !loading.value && pageResult.value !== null && pageResult.value.list.length === 0)
const emptyStateTitle = computed(() => {
  if (hasActiveFilters.value) {
    return '当前筛选下没有可管理账号'
  }

  if (pagination.page_num > 1) {
    return '这一页暂时没有可调整权限的账号'
  }

  return '暂时没有可显示的管理账号'
})
const emptyStateDescription = computed(() => {
  if (hasActiveFilters.value) {
    return '用户名、昵称、身份或权限筛选仍在生效，可以调整条件后再试。'
  }

  if (pagination.page_num > 1) {
    return '当前页没有结果，可以返回第一页或重新刷新权限列表。'
  }

  return '当具备权限调整资格的账号出现后，这里会继续按当前视图展示。'
})
const dialogHasChanges = computed(() => {
  if (!dialogVisible.value || !originalEditing.value) {
    return false
  }

  return (
    originalEditing.value.identity !== editing.identity ||
    originalEditing.value.permission !== editing.permission
  )
})
const isSubmitDisabled = computed(
  () => saving.value || !dialogHasChanges.value || dialogPermissionOptions.value.length === 0,
)
const dialogChangePreview = computed<PermissionChangePreview | null>(() => {
  if (!originalEditing.value) {
    return null
  }

  return buildPermissionChangePreview(originalEditing.value, editing)
})

watch(
  () => filters.identity,
  (identity) => {
    const options = permissionStore.optionsForIdentity((identity ?? 'MANAGER') as UserIdentity)
    if (!options.some((option) => option.value === filters.permission)) {
      filters.permission = undefined
    }
  },
)

watch(
  () => editing.identity,
  () => {
    const firstOption = dialogPermissionOptions.value[0]
    if (!dialogPermissionOptions.value.some((option) => option.value === editing.permission) && firstOption) {
      editing.permission = firstOption.value
    }
  },
)

watch(dialogVisible, (visible) => {
  if (!visible) {
    originalEditing.value = null
  }
})

async function loadUsers(): Promise<void> {
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
  } catch (error) {
    showError(getErrorMessage(error))
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
  filters.identity = 'MANAGER'
  filters.permission = undefined
  pagination.page_num = 1
  void loadUsers()
}

function handlePageChange(page: number): void {
  pagination.page_num = page
  void loadUsers()
}

function identityLabel(identity: UserIdentity): string {
  return permissionStore.identities.find((option) => option.value === identity)?.label ?? identity
}

function classifyPermissionChange(original: EditingState, next: EditingState): string {
  const identityChanged = original.identity !== next.identity
  const permissionChanged = original.permission !== next.permission

  if (identityChanged && permissionChanged) {
    return '身份与权限联动调整'
  }

  if (identityChanged) {
    return '仅身份调整'
  }

  if (permissionChanged) {
    if (next.permission > original.permission) {
      return '权限上调'
    }

    if (next.permission < original.permission) {
      return '权限下调'
    }

    return '仅权限调整'
  }

  return '无变更'
}

function buildPermissionChangePreview(original: EditingState, next: EditingState): PermissionChangePreview {
  return {
    classification: classifyPermissionChange(original, next),
    identity_before_label: identityLabel(original.identity),
    identity_after_label: identityLabel(next.identity),
    permission_before_label: permissionStore.labelFor(original.identity, original.permission),
    permission_after_label: permissionStore.labelFor(next.identity, next.permission),
  }
}

function openDialog(row: UserListItem): void {
  editing.uuid = row.uuid
  editing.nickname = row.nickname
  editing.username = row.username
  editing.identity = row.identity
  editing.permission = row.permission
  originalEditing.value = {
    uuid: row.uuid,
    nickname: row.nickname,
    username: row.username,
    identity: row.identity,
    permission: row.permission,
  }
  dialogVisible.value = true
}

async function confirmDiscardChanges(): Promise<boolean> {
  if (!dialogHasChanges.value) {
    return true
  }

  try {
    await ElMessageBox.confirm('当前权限调整尚未保存，确定要放弃吗？', '放弃权限修改', {
      type: 'warning',
      distinguishCancelAndClose: true,
      confirmButtonText: '放弃修改',
      cancelButtonText: '继续编辑',
    })
    return true
  } catch (error) {
    if (error === 'cancel' || error === 'close') {
      return false
    }

    showError(getErrorMessage(error))
    return false
  }
}

async function requestCloseDialog(): Promise<void> {
  if (saving.value) {
    return
  }

  if (await confirmDiscardChanges()) {
    dialogVisible.value = false
  }
}

async function handleDialogBeforeClose(done: () => void): Promise<void> {
  if (saving.value) {
    return
  }

  if (await confirmDiscardChanges()) {
    done()
  }
}

async function submitChange(): Promise<void> {
  if (isSubmitDisabled.value) {
    return
  }

  saving.value = true
  const originalState = originalEditing.value ? { ...originalEditing.value } : null
  const nextState = { ...editing }

  try {
    const message = await changePermission({
      uuid: editing.uuid,
      identity: editing.identity,
      permission: editing.permission,
    })

    if (originalState) {
      appliedFeedback.value = {
        title: `已同步 ${nextState.nickname} #${nextState.username} 的权限调整`,
        ...buildPermissionChangePreview(originalState, nextState),
      }
    }

    showSuccess(message || '权限已更新。')
    dialogVisible.value = false
    await permissionStore.bootstrapManagerContext()
    await loadUsers()
  } catch (error) {
    showError(getErrorMessage(error))
  } finally {
    saving.value = false
  }
}

function permissionLabel(row: UserListItem): string {
  return permissionStore.labelFor(row.identity, row.permission)
}

onMounted(async () => {
  await permissionStore.ensureCatalog()
  await permissionStore.bootstrapManagerContext()
  await loadUsers()
})
</script>

<template>
  <RoutePageShell
    eyebrow="Users"
    title="权限调整"
    subtitle="真实后端只接受单一权限值，因此 V2 使用单选权限模型，而不是继续累加勾选。"
  >
    <section class="glass-panel page-card">
      <el-alert :closable="false" type="info" :title="`你的当前可管理上限：${nearMaxLabel}`" />

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
            <el-option v-for="option in filterPermissionOptions" :key="option.value" :label="option.label" :value="option.value" />
          </el-select>
        </el-form-item>
        <div class="filters__actions">
          <el-button @click="resetFilters">重置</el-button>
          <el-button type="primary" native-type="submit" :loading="loading">刷新结果</el-button>
        </div>
      </el-form>
    </section>

    <section class="glass-panel page-card page-card--table">
      <transition name="fade-slide">
        <article v-if="appliedFeedback" class="applied-feedback">
          <div class="applied-feedback__header">
            <div>
              <div class="applied-feedback__eyebrow">Applied change</div>
              <div class="applied-feedback__classification">{{ appliedFeedback.classification }}</div>
              <h2 class="applied-feedback__title">{{ appliedFeedback.title }}</h2>
            </div>

            <el-button text @click="appliedFeedback = null">收起</el-button>
          </div>

          <div class="applied-feedback__rows">
            <div class="applied-feedback__row">
              <span class="applied-feedback__label">身份</span>
              <span class="applied-feedback__value">{{ appliedFeedback.identity_before_label }}</span>
              <span class="applied-feedback__arrow">→</span>
              <span class="applied-feedback__value applied-feedback__value--after">{{ appliedFeedback.identity_after_label }}</span>
            </div>

            <div class="applied-feedback__row">
              <span class="applied-feedback__label">权限</span>
              <span class="applied-feedback__value">{{ appliedFeedback.permission_before_label }}</span>
              <span class="applied-feedback__arrow">→</span>
              <span class="applied-feedback__value applied-feedback__value--after">{{ appliedFeedback.permission_after_label }}</span>
            </div>
          </div>
        </article>
      </transition>

      <div v-if="showEmptyState" class="empty-state">
        <div class="empty-state__eyebrow">Permissions</div>
        <h2 class="empty-state__title">{{ emptyStateTitle }}</h2>
        <p class="empty-state__description">{{ emptyStateDescription }}</p>

        <div class="empty-state__actions">
          <el-button plain @click="loadUsers">重新加载</el-button>
          <el-button v-if="hasActiveFilters" type="primary" @click="resetFilters">清空筛选</el-button>
        </div>
      </div>

      <el-table v-else v-loading="loading" :data="pageResult?.list ?? []">
        <el-table-column prop="nickname" label="昵称" min-width="140" />
        <el-table-column prop="username" label="用户名" min-width="140" />
        <el-table-column label="身份" min-width="110">
          <template #default="scope">
            <el-tag :type="scope.row.identity === 'MANAGER' ? 'warning' : 'info'">{{ scope.row.identity }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="权限" min-width="180">
          <template #default="scope">{{ permissionLabel(scope.row) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="140" align="right">
          <template #default="scope">
            <el-button size="small" type="primary" @click="openDialog(scope.row)">修改</el-button>
          </template>
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

    <el-dialog
      v-model="dialogVisible"
      width="520px"
      :title="`修改 ${editing.nickname} #${editing.username}`"
      :before-close="handleDialogBeforeClose"
    >
      <el-form label-position="top" @submit.prevent="submitChange">
        <el-form-item label="身份">
          <el-select v-model="editing.identity">
            <el-option v-for="option in permissionStore.identities" :key="option.value" :label="option.label" :value="option.value" />
          </el-select>
        </el-form-item>

        <el-form-item label="权限等级">
          <el-radio-group v-model="editing.permission" class="permission-radios">
            <el-radio v-for="option in dialogPermissionOptions" :key="option.value" :value="option.value">
              {{ option.label }}
            </el-radio>
          </el-radio-group>
          <p class="dialog-tip">当前可操作上限：{{ nearMaxLabel }}。高于该上限的选项不会显示。</p>
        </el-form-item>

        <section v-if="dialogChangePreview" class="dialog-diff">
          <div class="dialog-diff__header">
            <div>
              <div class="dialog-diff__eyebrow">Change preview</div>
              <h3 class="dialog-diff__title">提交前确认这次调整</h3>
            </div>

            <div class="dialog-diff__classification">{{ dialogChangePreview.classification }}</div>
          </div>

          <div class="dialog-diff__rows">
            <div class="dialog-diff__row">
              <span class="dialog-diff__label">身份</span>
              <span class="dialog-diff__value">{{ dialogChangePreview.identity_before_label }}</span>
              <span class="dialog-diff__arrow">→</span>
              <span class="dialog-diff__value dialog-diff__value--after">{{ dialogChangePreview.identity_after_label }}</span>
            </div>

            <div class="dialog-diff__row">
              <span class="dialog-diff__label">权限</span>
              <span class="dialog-diff__value">{{ dialogChangePreview.permission_before_label }}</span>
              <span class="dialog-diff__arrow">→</span>
              <span class="dialog-diff__value dialog-diff__value--after">{{ dialogChangePreview.permission_after_label }}</span>
            </div>
          </div>
        </section>

        <div class="dialog-actions">
          <el-button :disabled="saving" @click="requestCloseDialog">取消</el-button>
          <el-button type="primary" native-type="submit" :loading="saving" :disabled="isSubmitDisabled">确认修改</el-button>
        </div>
      </el-form>
    </el-dialog>
  </RoutePageShell>
</template>

<style scoped>
.page-card {
  display: flex;
  flex-direction: column;
  gap: 1rem;
  padding: 1.25rem;
}

.page-card--table {
  min-height: 420px;
}

.applied-feedback,
.dialog-diff {
  display: grid;
  gap: var(--barc-space-4);
  padding: var(--barc-space-4);
  border: 1px solid var(--barc-border);
  border-radius: var(--barc-radius-md);
  background:
    radial-gradient(circle at top right, rgba(255, 255, 255, 0.7), transparent 35%),
    linear-gradient(160deg, var(--barc-surface-strong), var(--barc-surface));
  box-shadow: var(--barc-shadow-sm);
}

.applied-feedback {
  margin-bottom: var(--barc-space-1);
}

.applied-feedback__header,
.dialog-diff__header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--barc-space-4);
}

.applied-feedback__eyebrow,
.applied-feedback__classification,
.dialog-diff__eyebrow,
.dialog-diff__classification {
  color: var(--barc-accent-strong);
  font-size: 0.78rem;
  letter-spacing: 0.16em;
  text-transform: uppercase;
}

.applied-feedback__classification,
.dialog-diff__classification {
  margin-top: var(--barc-space-2);
}

.applied-feedback__title,
.dialog-diff__title {
  margin: var(--barc-space-2) 0 0;
  font-family: var(--barc-font-display);
}

.applied-feedback__rows,
.dialog-diff__rows {
  display: grid;
  gap: var(--barc-space-3);
}

.applied-feedback__row,
.dialog-diff__row {
  display: grid;
  grid-template-columns: minmax(4rem, auto) minmax(0, 1fr) auto minmax(0, 1fr);
  gap: var(--barc-space-3);
  align-items: center;
  padding: var(--barc-space-3) var(--barc-space-4);
  border: 1px solid var(--barc-border);
  border-radius: var(--barc-radius-sm);
  background: rgba(255, 255, 255, 0.56);
}

.applied-feedback__label,
.dialog-diff__label {
  color: var(--barc-text-soft);
}

.applied-feedback__value,
.dialog-diff__value {
  min-width: 0;
  line-height: 1.7;
}

.applied-feedback__value--after,
.dialog-diff__value--after {
  color: var(--barc-text);
  font-weight: 600;
}

.applied-feedback__arrow,
.dialog-diff__arrow {
  color: var(--barc-accent-strong);
  font-weight: 700;
}

.filters {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 1rem;
}

.filters__actions,
.dialog-actions {
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
  min-height: 300px;
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

.empty-state__description,
.dialog-tip {
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

.permission-radios {
  display: grid;
  gap: 0.6rem;
}

@media (max-width: 1080px) {
  .filters {
    grid-template-columns: 1fr;
  }

  .applied-feedback__header,
  .dialog-diff__header {
    flex-direction: column;
  }

  .applied-feedback__row,
  .dialog-diff__row {
    grid-template-columns: 1fr;
  }
}
</style>
