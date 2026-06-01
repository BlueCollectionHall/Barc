<template>
  <div class="works-list-view">
    <WorkListComp
      @edit="handleEdit"
      @ban="row => openStatusDialog(row, 'BAN', '封禁')"
      @off="row => openStatusDialog(row, 'OFF', '下架')"
      @restore="row => openStatusDialog(row, 'PUBLIC', '恢复')"
      @delete="handleDelete"
    />

    <el-dialog v-model="statusDialogVisible" :title="statusDialogTitle" width="400px">
      <el-input v-model="statusRemark" type="textarea" :rows="3" placeholder="操作备注（选填）" />
      <template #footer>
        <el-button @click="statusDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="confirmStatusChange">确认</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import WorkListComp from '../components/WorkListComp.vue'
import { updateWorkStatus } from '../api/workManage'

const router = useRouter()

const statusDialogVisible = ref(false)
const statusDialogTitle = ref('')
const statusRemark = ref('')
let pendingRow: any = null
let pendingStatus = ''

const handleEdit = (row: any) => {
  router.push({ name: 'works-detail', params: { workId: row.id } })
}

const openStatusDialog = (row: any, status: string, title: string) => {
  pendingRow = row
  pendingStatus = status
  statusDialogTitle.value = title
  statusRemark.value = ''
  statusDialogVisible.value = true
}

const confirmStatusChange = async () => {
  if (!pendingRow) return
  try {
    await updateWorkStatus(pendingRow.id, pendingStatus, statusRemark.value)
    ElMessage.success(statusDialogTitle.value + '成功')
    statusDialogVisible.value = false
    window.location.reload()
  } catch {
    ElMessage.error('操作失败')
  }
}

const handleDelete = (row: any) => {
  ElMessageBox.confirm(`确定要删除作品《${row.title}》吗？此为软删除，可恢复。`, '确认删除', {
    confirmButtonText: '确定', cancelButtonText: '取消', type: 'warning',
  }).then(async () => {
    await updateWorkStatus(row.id, 'DELETED', '管理员删除')
    ElMessage.success('删除成功')
    window.location.reload()
  }).catch(() => {})
}
</script>
