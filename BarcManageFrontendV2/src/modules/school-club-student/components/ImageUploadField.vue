<template>
  <div class="image-upload-field">
    <div v-if="modelValue || localPreview" class="preview">
      <img :src="displayUrl" />
      <el-button size="small" @click="clear">替换</el-button>
      <el-button v-if="hasChanges && entityId && saveFn" type="success" size="small" @click="save" :loading="saving">
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
import { computed, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { isApiEnvelope, getErrorMessage } from '@/shared/types/api'
import { SESSION_TOKEN_KEY } from '@/shared/constants/auth'

const props = defineProps<{
  modelValue?: string
  previewUrl?: string
  uploadUrl: string
  entityId?: string
  fieldKey?: string
  saveFn?: (id: string, value: string) => Promise<unknown>
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
  return token ? { Authorization: /^Token /i.test(token) ? token : `Token ${token}` } : {}
})

const isReady = computed(() => {
  try {
    const url = new URL(props.uploadUrl, window.location.origin)
    const path = url.searchParams.get('path') || ''
    return path.length > 0 && !path.includes('//')
  } catch {
    return false
  }
})

const displayUrl = computed(() => {
  // 优先显示本地预览（base64）
  if (localPreview.value) return localPreview.value
  if (props.previewUrl) return props.previewUrl
  const v = props.modelValue
  if (!v) return ''
  // 完整URL直接返回
  if (v.startsWith('http://') || v.startsWith('https://')) return v
  // COS key 形式：无法直接显示，返回空
  return ''
})

function beforeUpload(file: File) {
  const isImage = ['image/png', 'image/jpeg', 'image/gif', 'image/webp'].includes(file.type) && file.size <= 10 * 1024 * 1024
  if (!isImage) {
    ElMessage.error('请上传 10MB 以内的 PNG/JPEG/GIF/WebP 图片')
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

function handleSuccess(res: unknown) {
  if (isApiEnvelope(res) && res.code === 0 && typeof res.data === 'string') {
    // 上传成功，保存 COS key 到 modelValue
    emit('update:modelValue', res.data as string)
    hasChanges.value = true
    // 保持本地预览显示，直到保存后从后端获取签名URL
  } else {
    localPreview.value = ''
    hasChanges.value = false
    ElMessage.error(isApiEnvelope(res) ? res.msg || '上传失败' : '上传响应无效')
  }
}

function handleError(err: unknown) {
  console.error('上传失败:', err)
  localPreview.value = ''
  hasChanges.value = false
  const msg = getErrorMessage(err, '上传失败，请重试')
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
    // 保留本地预览直到父组件拿到新的签名 URL，避免保存后图片突然消失。
    emit('saved')
  } catch (err) {
    console.error('保存图片失败:', err)
    ElMessage.error(getErrorMessage(err, '保存图片失败'))
  } finally {
    saving.value = false
  }
}

// 服务端新签名展示已到达后，才释放上传阶段的本地预览。
watch(() => props.previewUrl, () => { localPreview.value = '' })
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
