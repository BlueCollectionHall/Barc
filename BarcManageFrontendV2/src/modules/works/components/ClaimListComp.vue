<script setup lang="ts">
/** 待审与历史复用同一分页契约，结案记录仍可查看说明和处理结果。 */
import {ref, onMounted} from 'vue'
import {ElMessage} from 'element-plus'
import {getClaimsPage, type ClaimRecord} from '../api/workManage'

const emit = defineEmits<{approve: [row: ClaimRecord]; reject: [row: ClaimRecord]; preview: [row: ClaimRecord]}>()
const claims = ref<ClaimRecord[]>([])
const loading = ref(false)
const page = ref(1)
const total = ref(0)
const status = ref('PENDING')
const labels: Record<string, string> = {PENDING: '待审核', APPROVED: '已通过', REJECTED: '未通过', SUPERSEDED: '已结案'}
async function fetchData(): Promise<void> {
  loading.value = true
  try { const result = await getClaimsPage(page.value, status.value || undefined); claims.value = result.list; total.value = result.total }
  catch { ElMessage.error('加载认领记录失败') }
  finally { loading.value = false }
}
function changeStatus(): void { page.value = 1; void fetchData() }
onMounted(fetchData)
defineExpose({refresh: fetchData})
</script>

<template>
  <el-select v-model="status" @change="changeStatus" style="width:180px;margin-bottom:16px">
    <el-option label="全部记录" value="" />
    <el-option v-for="(label, value) in labels" :key="value" :label="label" :value="value" />
  </el-select>
  <el-table :data="claims" border stripe v-loading="loading" empty-text="暂无认领申请">
    <el-table-column prop="work_title" label="作品名称" min-width="160" show-overflow-tooltip />
    <el-table-column prop="applicant_nickname" label="申请人" width="100" />
    <el-table-column prop="statement" label="认领说明" min-width="240" />
    <el-table-column label="状态" width="100"><template #default="{row}">{{labels[row.status]}}</template></el-table-column>
    <el-table-column prop="result_reason" label="处理结果" min-width="160" />
    <el-table-column prop="created_at" label="申请时间" width="170" />
    <el-table-column label="操作" width="220">
      <template #default="{row}">
        <el-button size="small" @click="emit('preview', row)">预览</el-button>
        <template v-if="row.status === 'PENDING'">
          <el-button size="small" type="success" @click="emit('approve', row)">通过</el-button>
          <el-button size="small" type="danger" @click="emit('reject', row)">拒绝</el-button>
        </template>
      </template>
    </el-table-column>
  </el-table>
  <el-pagination v-model:current-page="page" :total="total" :page-size="20" layout="prev, pager, next, total" @current-change="fetchData" />
</template>
