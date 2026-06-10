<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'

import RoutePageShell from '@/app/components/RoutePageShell.vue'
import {
  getFeedbacksByType,
  updateFeedbackStatus,
  type FeedbackRecord,
  type FeedbackStatus,
  type FeedbackType,
} from '@/modules/feedback/api/feedbackManage'
import { formatDateTime } from '@/shared/utils/date'

const route = useRoute()

const feedbackTypes: FeedbackType[] = ['USER', 'BUG', 'SUGGESTION', 'OTHER']

const loading = ref(false)
const feedbackList = ref<FeedbackRecord[]>([])
const dialogVisible = ref(false)
const submitting = ref(false)
const pendingRow = ref<FeedbackRecord | null>(null)
const processStatus = ref<Exclude<FeedbackStatus, 'PENDING'>>('PROCESSING')
const processRemark = ref('')

const currentType = computed<FeedbackType | null>(() => {
  const feedbackType = route.meta.feedbackType
  return feedbackTypes.includes(feedbackType as FeedbackType) ? (feedbackType as FeedbackType) : null
})

const routeTypeError = computed(() => currentType.value === null)
const pageTitle = computed(() => String(route.meta.title || (currentType.value ? feedbackTypeLabel(currentType.value) : '反馈管理')))
const showTargetUser = computed(() => currentType.value === 'USER')
const emptyDescription = computed(() => (currentType.value ? `暂无${feedbackTypeLabel(currentType.value)}记录` : '请检查当前菜单路由配置'))
const pageSubtitle = computed(() => {
  if (!currentType.value) {
    return '反馈类型配置错误，当前页面已停止加载反馈列表。'
  }

  return `${feedbackTypeLabel(currentType.value)}会按当前路由类型加载，处理动作仅更新反馈状态与处理备注。`
})

async function loadFeedbacks(): Promise<void> {
  if (!currentType.value) {
    feedbackList.value = []
    return
  }

  loading.value = true
  try {
    feedbackList.value = await getFeedbacksByType(currentType.value)
  } catch {
    ElMessage.error('加载反馈列表失败')
  } finally {
    loading.value = false
  }
}

function openProcessDialog(row: FeedbackRecord): void {
  pendingRow.value = row
  processStatus.value = row.status === 'PROCESSING' ? 'COMPLETED' : 'PROCESSING'
  processRemark.value = row.echo ?? ''
  dialogVisible.value = true
}

async function confirmProcess(): Promise<void> {
  if (!pendingRow.value || !currentType.value) return

  submitting.value = true
  try {
    await updateFeedbackStatus(
      pendingRow.value.id,
      currentType.value,
      processStatus.value,
      processRemark.value.trim(),
    )
    ElMessage.success('反馈处理完成')
    dialogVisible.value = false
    pendingRow.value = null
    processRemark.value = ''
    processStatus.value = 'PROCESSING'
    await loadFeedbacks()
  } catch {
    ElMessage.error('处理反馈失败')
  } finally {
    submitting.value = false
  }
}

function statusTag(status: string): 'warning' | 'info' | 'success' | 'danger' {
  if (status === 'PENDING') return 'warning'
  if (status === 'PROCESSING') return 'info'
  if (status === 'COMPLETED') return 'success'
  if (status === 'REJECTED') return 'danger'
  return 'info'
}

function statusLabel(status: string): string {
  const labels: Record<string, string> = {
    PENDING: '待处理',
    PROCESSING: '处理中',
    COMPLETED: '已完成',
    REJECTED: '已驳回',
  }
  return labels[status] || status
}

function feedbackTypeLabel(type: FeedbackType): string {
  const labels: Record<FeedbackType, string> = {
    USER: '用户投诉',
    BUG: 'BUG反馈',
    SUGGESTION: '意见反馈',
    OTHER: '其他反馈',
  }
  return labels[type]
}

watch(
  () => currentType.value,
  () => {
    void loadFeedbacks()
  },
)

onMounted(() => {
  void loadFeedbacks()
})
</script>

<template>
  <RoutePageShell
    :title="pageTitle"
    eyebrow="Feedback Management"
    :subtitle="pageSubtitle"
  >
    <template #actions>
      <span class="header-meta">当前 {{ feedbackList.length }} 条</span>
      <el-button plain :loading="loading" :disabled="routeTypeError" @click="loadFeedbacks">刷新</el-button>
    </template>

    <section class="glass-panel table-section" v-loading="loading">
      <el-table :data="feedbackList" style="width: 100%">
        <el-table-column label="投诉/反馈单 ID" prop="id" min-width="180" show-overflow-tooltip />
        <el-table-column v-if="showTargetUser" label="被投诉用户" prop="target_id" min-width="160" show-overflow-tooltip />
        <el-table-column label="作者" prop="author" min-width="160" show-overflow-tooltip />
        <el-table-column label="内容" min-width="260" show-overflow-tooltip>
          <template #default="{ row }">
            <span class="content-cell">{{ row.content || '(无详细说明)' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="邮箱" min-width="180" show-overflow-tooltip>
          <template #default="{ row }">
            {{ row.email || '(未提供)' }}
          </template>
        </el-table-column>
        <el-table-column label="状态" width="110">
          <template #default="{ row }">
            <el-tag :type="statusTag(row.status)" size="small">
              {{ statusLabel(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="创建时间" width="180">
          <template #default="{ row }">
            {{ formatDateTime(row.created_at) }}
          </template>
        </el-table-column>
        <el-table-column label="更新时间" width="180">
          <template #default="{ row }">
            {{ formatDateTime(row.updated_at) }}
          </template>
        </el-table-column>
        <el-table-column label="处理备注" min-width="220" show-overflow-tooltip>
          <template #default="{ row }">
            {{ row.echo || '—' }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="110" fixed="right">
          <template #default="{ row }">
            <el-button
              type="primary"
              size="small"
              text
              :data-testid="`open-process-${row.id}`"
              @click="openProcessDialog(row)"
            >
              处理
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-alert
        v-if="routeTypeError"
        title="反馈类型配置错误"
        description="请检查当前菜单路由配置。"
        type="error"
        show-icon
        :closable="false"
        class="route-error"
      />
      <el-empty v-if="!loading && feedbackList.length === 0" :description="emptyDescription" />
    </section>

    <el-dialog v-model="dialogVisible" title="处理反馈" width="480px" :close-on-click-modal="false">
      <el-form label-width="90px">
        <el-form-item label="反馈单 ID">
          <span class="dialog-meta">{{ pendingRow?.id || '—' }}</span>
        </el-form-item>
        <el-form-item label="处理状态">
          <el-select v-model="processStatus" placeholder="选择处理状态" style="width: 100%">
            <el-option label="标记处理中" value="PROCESSING" />
            <el-option label="处理完成" value="COMPLETED" />
            <el-option label="驳回反馈" value="REJECTED" />
          </el-select>
        </el-form-item>
        <el-form-item label="处理备注">
          <el-input
            v-model="processRemark"
            type="textarea"
            :rows="3"
            maxlength="300"
            show-word-limit
            placeholder="输入处理备注，会作为 echo 回写反馈单"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="confirmProcess">确认处理</el-button>
      </template>
    </el-dialog>
  </RoutePageShell>
</template>

<style scoped>
.header-meta {
  color: var(--barc-text-soft);
  font-size: 0.86rem;
}

.table-section {
  padding: 1.25rem;
}

.content-cell {
  color: var(--barc-text-soft);
  line-height: 1.5;
}

.dialog-meta {
  color: var(--barc-text-soft);
  font-size: 0.9rem;
}
</style>
