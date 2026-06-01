<template>
  <el-table :data="claims" border stripe v-loading="loading" empty-text="暂无认领申请">
    <el-table-column prop="work_title" label="作品名称" min-width="180" show-overflow-tooltip />
    <el-table-column prop="applicant_nickname" label="申请人" width="100" />
    <el-table-column prop="applicant_username" label="用户名" width="100" />
    <el-table-column prop="created_at" label="申请时间" width="160" />
    <el-table-column label="操作" width="220">
      <template #default="{ row }">
        <el-button size="small" @click="emit('preview', row)">预览</el-button>
        <el-button size="small" type="success" @click="emit('approve', row)">通过</el-button>
        <el-button size="small" type="danger" @click="emit('reject', row)">拒绝</el-button>
      </template>
    </el-table-column>
  </el-table>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { getClaimsList } from '../api/workManage'

const emit = defineEmits<{ approve: [row: any]; reject: [row: any]; preview: [row: any] }>()

const claims = ref<any[]>([])
const loading = ref(false)

const fetchData = async () => {
  loading.value = true
  try { claims.value = await getClaimsList() } finally { loading.value = false }
}
onMounted(() => fetchData())
defineExpose({ refresh: fetchData })
</script>
