<script setup lang="ts">
import { ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { banUser } from '@/modules/users/api/users.service'
import type { BanRequest, BanType } from '@/shared/types/user'
import { BAN_TYPE_LABELS } from '@/shared/types/user'

const props = defineProps<{
  visible: boolean
  userId: string
  username: string
}>()

const emit = defineEmits<{
  (e: 'update:visible', value: boolean): void
  (e: 'success'): void
}>()

const loading = ref(false)
const form = ref<BanRequest>({
  userId: '',
  banType: 1,
  reason: '',
  durationDays: 7,
})

watch(
  () => props.visible,
  (visible) => {
    if (visible) {
      form.value = {
        userId: props.userId,
        banType: 1,
        reason: '',
        durationDays: 7,
      }
    }
  },
  { immediate: true },
)

function handleClose(): void {
  emit('update:visible', false)
}

async function handleSubmit(): Promise<void> {
  if (!form.value.reason.trim()) {
    ElMessage.warning('请输入封号原因')
    return
  }

  if (form.value.banType === 1 && (!form.value.durationDays || form.value.durationDays <= 0)) {
    ElMessage.warning('请输入有效的封号天数')
    return
  }

  loading.value = true
  try {
    await banUser(form.value)
    ElMessage.success('封号成功')
    emit('success')
    handleClose()
  } catch (error: any) {
    ElMessage.error(error.message || '封号失败')
  } finally {
    loading.value = false
  }
}

const banTypeOptions = [
  { label: '风险冻结', value: 0 },
  { label: '临时封号', value: 1 },
  { label: '违规封号', value: 2 },
]

const durationOptions = [
  { label: '1天', value: 1 },
  { label: '3天', value: 3 },
  { label: '7天', value: 7 },
  { label: '30天', value: 30 },
]
</script>

<template>
  <el-dialog
    :model-value="visible"
    title="封号用户"
    width="500px"
    @close="handleClose"
  >
    <el-form :model="form" label-width="100px">
      <el-form-item label="用户">
        <el-input :value="username" disabled />
      </el-form-item>

      <el-form-item label="封号类型">
        <el-select v-model="form.banType" style="width: 100%">
          <el-option
            v-for="option in banTypeOptions"
            :key="option.value"
            :label="option.label"
            :value="option.value"
          />
        </el-select>
      </el-form-item>

      <el-form-item v-if="form.banType === 1" label="封号天数">
        <el-select v-model="form.durationDays" style="width: 100%">
          <el-option
            v-for="option in durationOptions"
            :key="option.value"
            :label="option.label"
            :value="option.value"
          />
        </el-select>
        <div class="form-tip">或自定义天数：</div>
        <el-input-number
          v-model="form.durationDays"
          :min="1"
          :max="365"
          style="width: 100%"
        />
      </el-form-item>

      <el-form-item label="封号原因">
        <el-input
          v-model="form.reason"
          type="textarea"
          :rows="3"
          placeholder="请输入封号原因"
        />
      </el-form-item>
    </el-form>

    <template #footer>
      <el-button @click="handleClose">取消</el-button>
      <el-button type="danger" :loading="loading" @click="handleSubmit">
        确认封号
      </el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.form-tip {
  margin-top: 8px;
  color: var(--el-text-color-secondary);
  font-size: 12px;
}
</style>
