<template>
  <div class="work-detail-view">
    <el-page-header @back="$router.back()" title="返回">
      <template #content><span>作品详情</span></template>
    </el-page-header>
    <div style="margin-top:20px">
      <WorkDetailComp :workId="workId" @ban="act('BAN')" @off="act('OFF')" @restore="act('PUBLIC')" @delete="handleDelete" />
    </div>

    <!-- 评论管理 -->
    <el-card style="margin-top:24px" shadow="never">
      <WorkCommentManage :workId="workId" />
    </el-card>

    <!-- 状态操作备注弹窗 -->
    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="400px">
      <el-input v-model="remark" type="textarea" :rows="3" placeholder="操作备注（选填）" />
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="confirm">确认</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import WorkDetailComp from '../components/WorkDetailComp.vue'
import WorkCommentManage from '../components/WorkCommentManage.vue'
import { updateWorkStatus } from '../api/workManage'

const route = useRoute()
const router = useRouter()
const workId = route.params.workId as string

const dialogVisible = ref(false)
const dialogTitle = ref('')
const remark = ref('')
let pendingStatus = ''

const act = (status: string) => {
  pendingStatus = status
  dialogTitle.value = status === 'PUBLIC' ? '恢复公开' : status === 'BAN' ? '封禁作品' : '下架作品'
  remark.value = ''
  dialogVisible.value = true
}

const confirm = async () => {
  try {
    await updateWorkStatus(workId, pendingStatus, remark.value)
    ElMessage.success(dialogTitle.value + '成功')
    dialogVisible.value = false
    router.back()
  } catch { ElMessage.error('操作失败') }
}

const handleDelete = () => {
  ElMessageBox.confirm('确定要删除此作品吗？此为软删除，可恢复。', '确认删除', { confirmButtonText: '确定', cancelButtonText: '取消', type: 'warning' })
    .then(async () => { await updateWorkStatus(workId, 'DELETED', '管理员删除'); ElMessage.success('已删除'); router.back() })
    .catch(() => {})
}
</script>
