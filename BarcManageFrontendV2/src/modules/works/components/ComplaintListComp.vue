<template>
  <el-table :data="complaints" border stripe v-loading="loading" empty-text="暂无投诉">
    <el-table-column prop="work_id" label="作品ID" width="130" show-overflow-tooltip />
    <el-table-column prop="reason_option" label="投诉原因" width="120" />
    <el-table-column prop="content" label="详细描述" min-width="180" show-overflow-tooltip />
    <el-table-column prop="email" label="投诉人邮箱" width="180" />
    <el-table-column label="状态" width="100"><template #default="{ row }"><el-tag :type="row.status ? 'success' : 'warning'" size="small">{{ row.status ? '已处理' : '待处理' }}</el-tag></template></el-table-column>
    <el-table-column prop="created_at" label="投诉时间" width="180" />
    <el-table-column label="操作" width="100" fixed="right">
      <template #default="{ row }"><el-button v-if="!row.status" size="small" type="primary" @click="emit('process', row)">处理</el-button><span v-else style="color:#999">已处理</span></template>
    </el-table-column>
  </el-table>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { getComplaintsList } from '../api/workManage'

const emit = defineEmits<{ process: [row: any] }>()
const complaints = ref<any[]>([])
const loading = ref(false)

const fetchData = async () => {
  loading.value = true
  try { complaints.value = await getComplaintsList() } finally { loading.value = false }
}
onMounted(() => fetchData())
defineExpose({ refresh: fetchData })
</script>
