<template>
  <div class="claims-list-view">
    <el-tabs v-model="activeTab">
      <el-tab-pane label="待审批" name="pending">
        <ClaimListComp ref="claimListRef" @approve="row => handleClaim(row, true)" @reject="row => handleClaim(row, false)" @preview="handlePreview" />
      </el-tab-pane>
      <el-tab-pane label="指派作者" name="assign">
        <div style="margin-top:16px;display:flex;gap:12px;align-items:flex-start;flex-wrap:wrap">
          <el-select v-model="assignForm.workId" placeholder="搜索作品ID或标题" filterable remote reserve-keyword
            :remote-method="searchWorkOptions" :loading="workSearchLoading" clearable style="width:280px">
            <el-option v-for="w in workOptions" :key="w.id" :label="w.title" :value="w.id" />
          </el-select>
          <el-button v-if="assignForm.workId" size="small" @click="previewVisible = true">预览作品</el-button>
          <el-select v-model="assignForm.authorUuid" placeholder="搜索用户UUID/用户名/昵称" filterable remote reserve-keyword
            :remote-method="searchUserOptions" :loading="userSearchLoading" clearable style="width:280px">
            <el-option v-for="u in userOptions" :key="u.uuid" :value="u.uuid">
              <div style="display:flex;align-items:center;gap:8px">
                <el-avatar :size="24" :src="u.avatar" />
                <span>{{ u.nickname }}</span>
                <span style="color:#999;font-size:12px">@{{ u.username }}</span>
              </div>
            </el-option>
          </el-select>
          <el-button type="primary" @click="handleAssign" :disabled="!assignForm.workId || !assignForm.authorUuid">指派</el-button>
        </div>
      </el-tab-pane>
      <el-tab-pane label="撤销认领" name="revoke">
        <el-form :inline="true" style="margin-top: 16px">
          <el-form-item label="作品ID"><el-input v-model="revokeWorkId" placeholder="输入作品ID" /></el-form-item>
          <el-form-item><el-button type="danger" @click="handleRevoke">撤销认领</el-button></el-form-item>
        </el-form>
      </el-tab-pane>
    </el-tabs>

    <!-- 作品预览弹窗 -->
    <el-dialog v-model="previewVisible" title="作品预览" width="960px" destroy-on-close>
      <WorkDetailComp v-if="previewVisible && assignForm.workId" :workId="assignForm.workId" />
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import ClaimListComp from '../components/ClaimListComp.vue'
import WorkDetailComp from '../components/WorkDetailComp.vue'
import { approveClaim, assignAuthor, revokeClaim, searchWorks, searchUsers } from '../api/workManage'

const activeTab = ref('pending')
const router = useRouter()
const claimListRef = ref()
const revokeWorkId = ref('')
const assignForm = ref({ workId: '', authorUuid: '' })
const workOptions = ref<any[]>([])
const userOptions = ref<any[]>([])
const workSearchLoading = ref(false)
const userSearchLoading = ref(false)
const previewVisible = ref(false)

const handleClaim = async (row: any, approved: boolean) => {
  try {
    await approveClaim(row.id, approved)
    ElMessage.success(approved ? '认领已通过' : '认领已拒绝')
    claimListRef.value?.refresh()
  } catch { ElMessage.error('操作失败') }
}

const handlePreview = (row: any) => {
  router.push({ name: 'works-detail', params: { workId: row.work_id } })
}

const handleRevoke = async () => {
  if (!revokeWorkId.value) { ElMessage.warning('请输入作品ID'); return }
  try { await revokeClaim(revokeWorkId.value); ElMessage.success('认领已撤销'); revokeWorkId.value = '' } catch { ElMessage.error('操作失败') }
}

const handleAssign = async () => {
  if (!assignForm.value.workId || !assignForm.value.authorUuid) { ElMessage.warning('请选择作品和用户'); return }
  try { await assignAuthor(assignForm.value.workId, assignForm.value.authorUuid); ElMessage.success('作者指派成功'); assignForm.value = { workId: '', authorUuid: '' } } catch { ElMessage.error('操作失败') }
}

const searchWorkOptions = async (kw: string) => {
  if (!kw) { workOptions.value = []; return }
  workSearchLoading.value = true
  try { workOptions.value = await searchWorks(kw) } catch { workOptions.value = [] } finally { workSearchLoading.value = false }
}

const searchUserOptions = async (kw: string) => {
  if (!kw) { userOptions.value = []; return }
  userSearchLoading.value = true
  try { userOptions.value = await searchUsers(kw) } catch { userOptions.value = [] } finally { userSearchLoading.value = false }
}
</script>
