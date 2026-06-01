<template>
  <div class="image-upload-field">
    <div v-if="modelValue || localPreview" class="preview">
      <img :src="displayUrl" />
      <el-button size="small" @click="clear">替换</el-button>
      <el-button v-if="hasChanges && entityId" type="success" size="small" @click="save" :loading="saving">
        保存图片
      </el-button>
    </div>
    <el-upload
      v-else-if="isReady"
      :action="uploadUrl"
      :headers="uploadHeaders"
      :show-file-list="false"
      :before-upload="beforeUpload"
      :on-success="handleSuccess"
      :on-error="handleError"
      accept="image/*"
    >
      <el-button type="primary" size="small">上传</el-button>
    </el-upload>
    <div v-else class="upload-disabled">
      <el-button type="primary" size="small" disabled>上传</el-button>
      <span class="tip">请先填写 ID / 英文名</span>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { SESSION_TOKEN_KEY } from '@/shared/constants/auth'

const props = defineProps<{
  modelValue?: string
  uploadUrl: string
  entityId?: string
  fieldKey?: string
  saveFn?: (id: string, value: string) => Promise<any>
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', val: string): void
  (e: 'saved'): void
}>()

// 本地预览（base64），用于上传成功后立即显示
const localPreview = ref<string>('')
// 是否有未保存的更改
const hasChanges = ref(false)
// 保存中状态
const saving = ref(false)

const uploadHeaders = computed(() => {
  const token = window.localStorage.getItem(SESSION_TOKEN_KEY)
  return token ? { Authorization: token } : {}
})

const isReady = computed(() => {
  try {
    const url = new URL(props.uploadUrl)
    const path = url.searchParams.get('path') || ''
    return path.length > 0 && !path.includes('//')
  } catch {
    return false
  }
})

const displayUrl = computed(() => {
  // 优先显示本地预览（base64）
  if (localPreview.value) return localPreview.value
  const v = props.modelValue
  if (!v) return ''
  // 完整URL直接返回
  if (v.startsWith('http://') || v.startsWith('https://')) return v
  // COS key 形式：无法直接显示，返回空
  return ''
})

function beforeUpload(file: File) {
  const isImage = file.type.startsWith('image/')
  if (!isImage) {
    ElMessage.error('请上传图片文件')
    return false
  }
  // 生成本地预览（base64）
  const reader = new FileReader()
  reader.onload = (e) => {
    localPreview.value = e.target?.result as string
  }
  reader.readAsDataURL(file)
  return true
}

function handleSuccess(res: any) {
  if (res.code === 0) {
    // 上传成功，保存 COS key 到 modelValue
    emit('update:modelValue', res.data as string)
    hasChanges.value = true
    // 保持本地预览显示，直到保存后从后端获取签名URL
  } else {
    localPreview.value = ''
    hasChanges.value = false
    ElMessage.error(res.msg || '上传失败')
  }
}

function handleError(err: any) {
  console.error('上传失败:', err)
  localPreview.value = ''
  hasChanges.value = false
  const msg = err?.message || err?.msg || '上传失败，请重试'
  ElMessage.error(msg)
}

function clear() {
  localPreview.value = ''
  hasChanges.value = false
  emit('update:modelValue', '')
}

async function save() {
  if (!props.entityId || !props.saveFn || !props.modelValue) {
    ElMessage.error('保存失败：缺少必要参数')
    return
  }
  saving.value = true
  try {
    await props.saveFn(props.entityId, props.modelValue)
    ElMessage.success('图片保存成功')
    hasChanges.value = false
    localPreview.value = ''
    emit('saved')
  } catch (err: any) {
    console.error('保存图片失败:', err)
    ElMessage.error(err?.message || '保存图片失败')
  } finally {
    saving.value = false
  }
}
</script>

<style scoped>
.image-upload-field {
  display: flex;
  align-items: center;
}
.preview img {
  width: 80px;
  height: 80px;
  object-fit: cover;
  border-radius: 4px;
  margin-right: 8px;
}
.upload-disabled {
  display: flex;
  align-items: center;
  gap: 8px;
}
.tip {
  font-size: 12px;
  color: #f56c6c;
}
</style>
