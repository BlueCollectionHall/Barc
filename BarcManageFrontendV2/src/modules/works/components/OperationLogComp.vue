<template>
  <div class="operation-log">
    <el-form :inline="true" class="filter-bar">
      <el-form-item label="操作类型">
        <el-select v-model="filterType" placeholder="全部" clearable @change="fetchData">
          <el-option label="全部" value="" />
          <el-option label="封禁" value="BAN" /><el-option label="下架" value="OFF" /><el-option label="删除" value="DELETE" /><el-option label="恢复" value="RESTORE" />
          <el-option label="编辑" value="EDIT" /><el-option label="认领审批" value="CLAIM_APPROVE" /><el-option label="认领撤销" value="CLAIM_REVOKE" />
          <el-option label="指派作者" value="CLAIM_ASSIGN" /><el-option label="投诉处理" value="COMPLAINT_PROCESS" />
        </el-select>
      </el-form-item>
    </el-form>
    <el-table :data="logs" border stripe v-loading="loading" empty-text="暂无操作日志">
      <el-table-column prop="created_at" label="时间" width="180" />
      <el-table-column prop="operator_uuid" label="操作人" width="150" show-overflow-tooltip />
      <el-table-column label="操作类型" width="110"><template #default="{ row }"><el-tag size="small">{{ typeLabel(row.operation_type) }}</el-tag></template></el-table-column>
      <el-table-column prop="work_id" label="作品ID" width="130" show-overflow-tooltip />
      <el-table-column label="详情" min-width="280">
        <template #default="{ row }">{{ formatDetail(row.operation_type, row.detail) }}</template>
      </el-table-column>
    </el-table>
    <div style="margin-top: 16px; display: flex; justify-content: flex-end">
      <el-pagination v-model:current-page="currentPage" :page-size="20" :total="total" layout="total, prev, pager, next" @current-change="fetchData" />
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { getOperationLogs } from '../api/workManage'

const typeMap: Record<string, string> = { BAN: '封禁', OFF: '下架', DELETE: '删除', RESTORE: '恢复', EDIT: '编辑', CLAIM_APPROVE: '认领审批', CLAIM_REVOKE: '认领撤销', CLAIM_ASSIGN: '指派作者', COMPLAINT_PROCESS: '投诉处理' }
const logs = ref<any[]>([])
const total = ref(0)
const currentPage = ref(1)
const loading = ref(false)
const filterType = ref('')
const typeLabel = (type: string) => typeMap[type] || type

const statusLabel: Record<string, string> = { PUBLIC: '公开', PRIVATE: '私有', OFF: '下架', BAN: '封禁', DELETED: '已删除' }

const formatDetail = (type: string, detail: string): string => {
  try {
    const d = JSON.parse(detail)
    switch (type) {
      case 'BAN': case 'OFF': case 'DELETE': case 'RESTORE': {
        const old = statusLabel[d.old_status] || d.old_status || '?'
        const nw = statusLabel[d.new_status] || d.new_status || '?'
        return `从「${old}」改为「${nw}」${d.remark ? '，备注：' + d.remark : ''}`
      }
      case 'EDIT': return d.remark || '修改了作品内容'
      case 'CLAIM_APPROVE': return d.result === 'approved' ? '审批通过' : '审批拒绝'
      case 'CLAIM_REVOKE': return d.remark || '撤销认领'
      case 'CLAIM_ASSIGN': return '指派了新作者'
      case 'COMPLAINT_PROCESS': {
        const act: Record<string, string> = { BAN: '封禁作品', OFF: '下架作品', DELETE: '删除作品', IGNORE: '忽略投诉' }
        return `处理投诉，${act[d.action] || d.action || '未知操作'}${d.remark ? '，备注：' + d.remark : ''}`
      }
      default: return detail
    }
  } catch { return detail }
}

const fetchData = async () => {
  loading.value = true
  try {
    const result = await getOperationLogs({ page_num: currentPage.value, page_size: 20, params: { operation_type: filterType.value || undefined } })
    logs.value = result.list ?? []
    total.value = result.total ?? 0
  } finally { loading.value = false }
}
onMounted(() => fetchData())
</script>
