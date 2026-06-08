<template>
  <div class="complaints-list-view">
    <ComplaintListComp ref="complaintListRef" @process="openProcessDialog" />

    <el-dialog v-model="dialogVisible" title="处理投诉" width="450px">
      <el-form label-width="80px">
        <el-form-item label="操作">
          <el-select v-model="processAction" placeholder="选择操作" style="width:100%">
            <el-option label="封禁作品并完成" value="BAN" />
            <el-option label="下架作品并完成" value="OFF" />
            <el-option label="恢复作品并完成" value="RESTORE" />
            <el-option label="处理完成" value="COMPLETED" />
            <el-option label="驳回投诉" value="REJECTED" />
          </el-select>
        </el-form-item>
        <el-form-item label="处理备注">
          <el-input v-model="processRemark" type="textarea" :rows="3" placeholder="输入处理备注" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="confirmProcess">确认处理</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import ComplaintListComp from '../components/ComplaintListComp.vue'
import { processComplaint, updateWorkStatus } from '../api/workManage'

const complaintListRef = ref()
const dialogVisible = ref(false)
const processAction = ref('COMPLETED')
const processRemark = ref('')
let pendingRow: any = null

const workStatusActions: Record<string, { status: string; defaultRemark: string }> = {
  BAN: { status: 'BAN', defaultRemark: '管理员已封禁作品并完成投诉' },
  OFF: { status: 'OFF', defaultRemark: '管理员已下架作品并完成投诉' },
  RESTORE: { status: 'PUBLIC', defaultRemark: '管理员已恢复作品公开状态' },
}

const openProcessDialog = (row: any) => { pendingRow = row; dialogVisible.value = true }

const confirmProcess = async () => {
  if (!pendingRow) return
  try {
    // WORK 类型统一走 feedback；涉及作品可见性时，先通过管理员作品状态接口落库，再完成投诉。
    const workStatusAction = workStatusActions[processAction.value]
    if (workStatusAction) {
      await updateWorkStatus(pendingRow.target_id, workStatusAction.status, processRemark.value)
      await processComplaint(pendingRow.id, 'COMPLETED', processRemark.value || workStatusAction.defaultRemark)
    } else {
      await processComplaint(pendingRow.id, processAction.value, processRemark.value)
    }
    ElMessage.success('投诉处理完成')
    dialogVisible.value = false
    processRemark.value = ''
    processAction.value = 'COMPLETED'
    complaintListRef.value?.refresh()
  } catch { ElMessage.error('处理失败') }
}
</script>
