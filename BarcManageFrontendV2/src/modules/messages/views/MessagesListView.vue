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
  keyword: '',
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
  if (filters.keyword.trim()) params.keyword = filters.keyword.trim()
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
  filters.keyword = ''
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
  return content.length > maxLen ? content.slice(0, maxLen) + '\u2026' : content
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
        <el-form-item label="发布者">
          <el-input
            v-model="filters.keyword"
            placeholder="UUID / 用户名 / 昵称"
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
        <el-table-column label="发布者" min-width="240">
          <template #default="{ row }">
            <span>{{ row.author_nickname || '未知昵称' }}@{{ row.author_name || row.author }}</span>
          </template>
        </el-table-column>
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
