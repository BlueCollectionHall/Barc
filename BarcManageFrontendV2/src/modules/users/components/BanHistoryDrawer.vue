<script setup lang="ts">
import { ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { getBanHistory } from '@/modules/users/api/users.service'
import type { BanRecord } from '@/shared/types/user'
import { BAN_TYPE_LABELS } from '@/shared/types/user'

const props = defineProps<{
  visible: boolean
  userId: string
  username: string
}>()

const emit = defineEmits<{
  (e: 'update:visible', value: boolean): void
}>()

const loading = ref(false)
const records = ref<BanRecord[]>([])
const pagination = ref({
  page: 1,
  size: 10,
  total: 0,
})

watch(
  () => props.visible,
  (visible) => {
    if (visible) {
      loadHistory()
    }
  },
)

async function loadHistory(): Promise<void> {
  loading.value = true
  try {
    const result = await getBanHistory(
      props.userId,
      pagination.value.page,
      pagination.value.size,
    )
    records.value = result.list
    pagination.value.total = result.total
  } catch (error: any) {
    ElMessage.error(error.message || '加载封号历史失败')
  } finally {
    loading.value = false
  }
}

function handlePageChange(page: number): void {
  pagination.value.page = page
  loadHistory()
}

function handleClose(): void {
  emit('update:visible', false)
}

function formatTime(time: string | null): string {
  if (!time) return '—'
  return new Date(time).toLocaleString('zh-CN')
}

function getBanTypeLabel(type: number): string {
  return BAN_TYPE_LABELS[type as keyof typeof BAN_TYPE_LABELS] || '未知'
}

function getBanTypeTagType(type: number): string {
  switch (type) {
    case 0: return 'warning'
    case 1: return 'danger'
    case 2: return 'danger'
    case 3: return 'info'
    default: return 'info'
  }
}
</script>

<template>
  <el-drawer
    :model-value="visible"
    title="封号历史"
    size="600px"
    @close="handleClose"
  >
    <div class="ban-history">
      <div class="ban-history__header">
        <span class="ban-history__username">{{ username }}</span>
        <span class="ban-history__count">共 {{ pagination.total }} 条记录</span>
      </div>

      <el-table
        v-loading="loading"
        :data="records"
        stripe
        style="width: 100%"
      >
        <el-table-column label="封号类型" width="100">
          <template #default="{ row }">
            <el-tag :type="getBanTypeTagType(row.banType)" size="small">
              {{ getBanTypeLabel(row.banType) }}
            </el-tag>
          </template>
        </el-table-column>

        <el-table-column label="封号原因" min-width="150">
          <template #default="{ row }">
            <span class="ban-reason">{{ row.banReason }}</span>
          </template>
        </el-table-column>

        <el-table-column label="封号天数" width="80">
          <template #default="{ row }">
            {{ row.banDurationDays ?? '永久' }}
          </template>
        </el-table-column>

        <el-table-column label="封号时间" width="160">
          <template #default="{ row }">
            {{ formatTime(row.bannedAt) }}
          </template>
        </el-table-column>

        <el-table-column label="解封时间" width="160">
          <template #default="{ row }">
            {{ formatTime(row.unbannedAt) }}
          </template>
        </el-table-column>

        <el-table-column label="解封原因" min-width="120">
          <template #default="{ row }">
            {{ row.unbanReason || '—' }}
          </template>
        </el-table-column>
      </el-table>

      <div v-if="pagination.total > pagination.size" class="ban-history__pagination">
        <el-pagination
          background
          layout="prev, pager, next"
          :current-page="pagination.page"
          :page-size="pagination.size"
          :total="pagination.total"
          @current-change="handlePageChange"
        />
      </div>
    </div>
  </el-drawer>
</template>

<style scoped>
.ban-history {
  padding: 16px;
}

.ban-history__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
}

.ban-history__username {
  font-size: 16px;
  font-weight: 600;
}

.ban-history__count {
  color: var(--el-text-color-secondary);
  font-size: 14px;
}

.ban-reason {
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  text-overflow: ellipsis;
}

.ban-history__pagination {
  display: flex;
  justify-content: center;
  margin-top: 16px;
}
</style>
