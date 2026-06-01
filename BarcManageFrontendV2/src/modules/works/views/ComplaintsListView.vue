<template>
  <div class="complaints-list-view">
    <ComplaintListComp ref="complaintListRef" @process="openProcessDialog" />

    <el-dialog v-model="dialogVisible" title="处理投诉" width="450px">
      <el-form label-width="80px">
        <el-form-item label="操作">
          <el-select v-model="processAction" placeholder="选择操作" style="width:100%">
            <el-option label="封禁作品" value="BAN" />
            <el-option label="下架作品" value="OFF" />
            <el-option label="删除作品" value="DELETE" />
            <el-option label="忽略投诉" value="IGNORE" />
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
import { processComplaint } from '../api/workManage'

const complaintListRef = ref()
const dialogVisible = ref(false)
const processAction = ref('BAN')
const processRemark = ref('')
let pendingRow: any = null

const openProcessDialog = (row: any) => { pendingRow = row; dialogVisible.value = true }

const confirmProcess = async () => {
  if (!pendingRow) return
  try {
    await processComplaint(pendingRow.id, processAction.value, processRemark.value)
    ElMessage.success('投诉处理完成')
    dialogVisible.value = false
    processRemark.value = ''
    processAction.value = 'BAN'
    complaintListRef.value?.refresh()
  } catch { ElMessage.error('处理失败') }
}
</script>
