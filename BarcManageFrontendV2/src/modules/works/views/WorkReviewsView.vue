<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'

import RoutePageShell from '@/app/components/RoutePageShell.vue'
import {
  getWorkReviewList,
  reviewWork,
  type WorkRecord,
  type WorkReviewStatus,
} from '@/modules/works/api/workManage'
import { formatDateTime } from '@/shared/utils/date'
import {
  getWorkCreatorDisplay,
  getWorkCreatorSourceLabel,
} from '@/modules/works/utils/workAttribution'

const router = useRouter()

const loading = ref(false)
const submitting = ref(false)
const rows = ref<WorkRecord[]>([])
const total = ref(0)
const currentPage = ref(1)
const pageSize = 10
const reviewFilter = ref<WorkReviewStatus>('PENDING')
const keyword = ref('')
const rejectDialogVisible = ref(false)
const rejectingWork = ref<WorkRecord | null>(null)
const rejectReason = ref('')

/** 审核页按当前结论分页加载，默认先处理最早提交的待审作品。 */
async function loadReviews(): Promise<void> {
  loading.value = true
  try {
    const result = await getWorkReviewList({
      page_num: currentPage.value,
      page_size: pageSize,
      params: { keyword: keyword.value.trim() || undefined },
    }, reviewFilter.value)
    rows.value = result.list ?? []
    total.value = result.total ?? 0
  } catch {
    ElMessage.error('加载审核队列失败')
  } finally {
    loading.value = false
  }
}

function search(): void {
  currentPage.value = 1
  void loadReviews()
}

function changeStatus(): void {
  currentPage.value = 1
  void loadReviews()
}

function changePage(page: number): void {
  currentPage.value = page
  void loadReviews()
}

function viewDetail(row: WorkRecord): void {
  void router.push({ name: 'works-detail', params: { workId: row.id } })
}

/** 通过操作会先二次确认，审核结论不会触发用户邮件。 */
async function approve(row: WorkRecord): Promise<void> {
  try {
    await ElMessageBox.confirm(
      '确认通过作品《' + row.title + '》的上传审核吗？作品将按作者设置的“'
        + visibilityLabel(row.status) + '”状态生效。',
      '通过审核',
      { confirmButtonText: '确认通过', cancelButtonText: '取消', type: 'success' },
    )
    await reviewWork(row.id, true)
    ElMessage.success('审核已通过')
    await loadReviews()
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') ElMessage.error('审核操作失败')
  }
}

function openReject(row: WorkRecord): void {
  rejectingWork.value = row
  rejectReason.value = ''
  rejectDialogVisible.value = true
}

async function confirmReject(): Promise<void> {
  if (!rejectingWork.value) return
  const reason = rejectReason.value.trim()
  if (!reason) {
    ElMessage.warning('请填写拒绝原因，用户会在内容管理中看到它')
    return
  }

  submitting.value = true
  try {
    await reviewWork(rejectingWork.value.id, false, reason)
    ElMessage.success('已记录审核未通过')
    rejectDialogVisible.value = false
    rejectingWork.value = null
    rejectReason.value = ''
    await loadReviews()
  } catch {
    ElMessage.error('审核操作失败')
  } finally {
    submitting.value = false
  }
}

function reviewTag(status?: string | null): 'warning' | 'success' | 'danger' | 'info' {
  if (status === 'PENDING') return 'warning'
  if (status === 'APPROVED') return 'success'
  if (status === 'REJECTED') return 'danger'
  return 'info'
}

function reviewLabel(status?: string | null): string {
  const labels: Record<string, string> = {
    PENDING: '审核中',
    APPROVED: '已通过',
    REJECTED: '未通过',
  }
  return labels[status || ''] || '未知'
}

function visibilityLabel(status: string): string {
  return status === 'PRIVATE' ? '私有' : status === 'PUBLIC' ? '公开' : status
}

onMounted(() => {
  void loadReviews()
})
</script>

<template>
  <RoutePageShell
    title="上传审核"
    eyebrow="Work Review"
    subtitle="审核结论静默记录；通过后按作者设置的公开/私有状态生效，拒绝原因只在用户回来查看时展示。"
  >
    <template #actions>
      <span class="header-meta">当前筛选 {{ total }} 条</span>
      <el-button plain :loading="loading" @click="loadReviews">刷新</el-button>
    </template>

    <section class="glass-panel review-panel">
      <div class="filter-row">
        <el-segmented
          v-model="reviewFilter"
          :options="[
            { label: '审核中', value: 'PENDING' },
            { label: '已通过', value: 'APPROVED' },
            { label: '未通过', value: 'REJECTED' },
          ]"
          @change="changeStatus"
        />
        <el-input
          v-model="keyword"
          clearable
          placeholder="搜索作品 ID、标题或作者"
          class="keyword-input"
          @keyup.enter="search"
        />
        <el-button type="primary" @click="search">搜索</el-button>
      </div>

      <el-table :data="rows" v-loading="loading" style="width: 100%">
        <el-table-column label="封面" width="116">
          <template #default="{ row }">
            <div class="cover">
              <img v-if="row.cover_image" :src="row.cover_image" alt="" />
              <span v-else>无封面</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="title" label="作品" min-width="190" show-overflow-tooltip>
          <template #default="{ row }">
            <div class="work-title">{{ row.title }}</div>
            <div class="work-id">{{ row.id }}</div>
          </template>
        </el-table-column>
        <el-table-column label="作品作者" min-width="145" show-overflow-tooltip>
          <template #default="{ row }">
            <div>{{ getWorkCreatorDisplay(row) }}</div>
            <div class="author-source">{{ getWorkCreatorSourceLabel(row) }}</div>
          </template>
        </el-table-column>
        <el-table-column label="展示意图" width="100">
          <template #default="{ row }">
            <el-tag effect="plain" type="info">{{ visibilityLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="审核状态" width="110">
          <template #default="{ row }">
            <el-tag :type="reviewTag(row.review_status)">{{ reviewLabel(row.review_status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="提交时间" width="180">
          <template #default="{ row }">
            {{ formatDateTime(row.review_submitted_at || row.created_at) }}
          </template>
        </el-table-column>
        <el-table-column v-if="reviewFilter === 'REJECTED'" label="拒绝原因" min-width="220" show-overflow-tooltip>
          <template #default="{ row }">{{ row.review_reason || '—' }}</template>
        </el-table-column>
        <el-table-column label="操作" width="240" fixed="right">
          <template #default="{ row }">
            <el-button size="small" text @click="viewDetail(row)">查看详情</el-button>
            <template v-if="row.review_status === 'PENDING'">
              <el-button size="small" type="success" @click="approve(row)">通过</el-button>
              <el-button size="small" type="danger" @click="openReject(row)">拒绝</el-button>
            </template>
          </template>
        </el-table-column>
      </el-table>

      <el-empty v-if="!loading && rows.length === 0" description="当前筛选下没有作品" />
      <div class="pagination-row">
        <el-pagination
          v-model:current-page="currentPage"
          :page-size="pageSize"
          :total="total"
          layout="total, prev, pager, next"
          @current-change="changePage"
        />
      </div>
    </section>

    <el-dialog
      v-model="rejectDialogVisible"
      title="拒绝作品审核"
      width="480px"
      :close-on-click-modal="false"
    >
      <p class="dialog-work">作品：{{ rejectingWork?.title || '—' }}</p>
      <el-input
        v-model="rejectReason"
        type="textarea"
        :rows="5"
        maxlength="500"
        show-word-limit
        placeholder="请具体说明未通过原因，用户会在个人中心看到这段内容"
      />
      <template #footer>
        <el-button @click="rejectDialogVisible = false">取消</el-button>
        <el-button type="danger" :loading="submitting" @click="confirmReject">确认拒绝</el-button>
      </template>
    </el-dialog>
  </RoutePageShell>
</template>

<style scoped>
.header-meta,
.work-id,
.dialog-work,
.author-source {
  color: var(--barc-text-soft);
  font-size: 0.86rem;
}

.review-panel {
  padding: 1.25rem;
}

.filter-row {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  margin-bottom: 1rem;
}

.keyword-input {
  width: min(360px, 40vw);
  margin-left: auto;
}

.cover {
  display: flex;
  width: 88px;
  height: 50px;
  align-items: center;
  justify-content: center;
  overflow: hidden;
  border-radius: 10px;
  background: rgba(95, 136, 173, 0.08);
  color: var(--barc-text-soft);
  font-size: 0.72rem;
}

.cover img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.work-title {
  color: var(--barc-text);
  font-weight: 600;
}

.pagination-row {
  display: flex;
  justify-content: flex-end;
  margin-top: 1rem;
}

.dialog-work {
  margin: 0 0 0.75rem;
}

@media (max-width: 760px) {
  .filter-row {
    align-items: stretch;
    flex-direction: column;
  }

  .keyword-input {
    width: 100%;
    margin-left: 0;
  }
}
</style>
