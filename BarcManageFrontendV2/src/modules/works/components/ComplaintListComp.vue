<template>
  <el-table :data="complaints" border stripe v-loading="loading" empty-text="暂无投诉">
    <el-table-column prop="target_id" label="作品ID" width="130" show-overflow-tooltip />
    <el-table-column prop="author" label="投诉人" width="130" show-overflow-tooltip />
    <el-table-column prop="content" label="详细描述" min-width="180" show-overflow-tooltip />
    <el-table-column prop="email" label="投诉人邮箱" width="180" />
    <el-table-column prop="echo" label="处理回执" min-width="160" show-overflow-tooltip />
    <el-table-column label="状态" width="100"><template #default="{ row }"><el-tag :type="row.status === 'COMPLETED' ? 'success' : row.status === 'REJECTED' ? 'danger' : 'warning'" size="small">{{ statusLabel(row.status) }}</el-tag></template></el-table-column>
    <el-table-column prop="created_at" label="投诉时间" width="180" />
    <el-table-column label="操作" width="100" fixed="right">
      <template #default="{ row }"><el-button v-if="row.status === 'PENDING' || row.status === 'PROCESSING'" size="small" type="primary" @click="emit('process', row)">处理</el-button><span v-else style="color:#999">已处理</span></template>
    </el-table-column>
  </el-table>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { getComplaintsList } from '../api/workManage'

const emit = defineEmits<{ process: [row: any] }>()
const complaints = ref<any[]>([])
const loading = ref(false)

const statusLabel = (status: string) => ({
  PENDING: '待处理',
  PROCESSING: '处理中',
  COMPLETED: '已完成',
  REJECTED: '已驳回',
}[status] ?? status)

const fetchData = async () => {
  loading.value = true
  try { complaints.value = await getComplaintsList() } finally { loading.value = false }
}
onMounted(() => fetchData())
defineExpose({ refresh: fetchData })
</script>
