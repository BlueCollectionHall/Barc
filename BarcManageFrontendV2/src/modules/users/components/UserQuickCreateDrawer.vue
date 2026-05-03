<script setup lang="ts">
import { ElMessageBox } from 'element-plus'
import { computed, reactive, watch } from 'vue'

import { uploadNewUser } from '@/modules/users/api/users.service'
import { getErrorMessage } from '@/shared/types/api'
import { showError, showSuccess } from '@/shared/utils/message'

interface UserQuickCreateForm {
  email: string
  email_verified: boolean
  password: string
  safe_level: number
  telephone: string
  username: string
}

interface UserQuickCreateSubmittedPayload {
  email_verified: boolean
  safe_level: number
  telephone_present: boolean
  username: string
}

interface UserQuickCreateFieldErrors {
  email: string
  password: string
  username: string
}

const props = defineProps<{
  modelValue: boolean
}>()

const emit = defineEmits<{
  (event: 'update:modelValue', value: boolean): void
  (event: 'submitted', payload: UserQuickCreateSubmittedPayload): void
}>()

const loading = defineModel<boolean>('loading', { default: false })
const visible = computed({
  get: () => props.modelValue,
  set: (value: boolean) => emit('update:modelValue', value),
})
const emailPattern = /^[^\s@]+@[^\s@]+\.[^\s@]+$/

function createInitialForm(): UserQuickCreateForm {
  return {
    username: '',
    password: '',
    email: '',
    telephone: '',
    safe_level: 0,
    email_verified: true,
  }
}

const form = reactive<UserQuickCreateForm>(createInitialForm())
const fieldErrors = reactive<UserQuickCreateFieldErrors>({
  username: '',
  password: '',
  email: '',
})
const isDirty = computed(
  () =>
    form.username.trim().length > 0 ||
    form.password.trim().length > 0 ||
    form.email.trim().length > 0 ||
    form.telephone.trim().length > 0 ||
    form.safe_level !== 0 ||
    form.email_verified !== true,
)

function resetForm(): void {
  Object.assign(form, createInitialForm())
  fieldErrors.username = ''
  fieldErrors.password = ''
  fieldErrors.email = ''
}

watch(
  () => props.modelValue,
  (opened) => {
    if (!opened) {
      resetForm()
    }
  },
)

watch(
  () => form.username,
  () => {
    fieldErrors.username = ''
  },
)

watch(
  () => form.password,
  () => {
    fieldErrors.password = ''
  },
)

watch(
  () => form.email,
  () => {
    fieldErrors.email = ''
  },
)

function validateForm(): boolean {
  fieldErrors.username = form.username.trim().length > 0 ? '' : '请输入用户名。'
  fieldErrors.password = form.password.trim().length > 0 ? '' : '请输入初始密码。'

  if (form.email.trim().length === 0) {
    fieldErrors.email = '请输入邮箱地址。'
  } else if (!emailPattern.test(form.email.trim())) {
    fieldErrors.email = '请输入有效的邮箱地址。'
  } else {
    fieldErrors.email = ''
  }

  const firstError = fieldErrors.username || fieldErrors.password || fieldErrors.email

  if (firstError) {
    showError(firstError)
    return false
  }

  return true
}

async function confirmDiscardIfNeeded(): Promise<boolean> {
  if (loading.value) {
    return false
  }

  if (!isDirty.value) {
    return true
  }

  try {
    await ElMessageBox.confirm('当前表单还有未保存的内容，确定要放弃吗？', '放弃新增用户', {
      type: 'warning',
      distinguishCancelAndClose: true,
      confirmButtonText: '放弃',
      cancelButtonText: '继续填写',
    })

    return true
  } catch (error) {
    if (error === 'cancel' || error === 'close') {
      return false
    }

    showError(getErrorMessage(error))
    return false
  }
}

async function requestClose(): Promise<void> {
  if (await confirmDiscardIfNeeded()) {
    visible.value = false
  }
}

async function handleBeforeClose(done: () => void): Promise<void> {
  if (await confirmDiscardIfNeeded()) {
    done()
  }
}

async function submit(): Promise<void> {
  if (loading.value) {
    return
  }

  loading.value = true

  try {
    if (!validateForm()) {
      return
    }

    const username = form.username.trim()
    const telephone = form.telephone.trim()

    const message = await uploadNewUser({
      username,
      password: form.password,
      email: form.email.trim(),
      telephone,
      safe_level: form.safe_level,
      email_verified: form.email_verified,
    })

    showSuccess(message || '新用户已创建。')
    emit('submitted', {
      username,
      email_verified: form.email_verified,
      safe_level: form.safe_level,
      telephone_present: telephone.length > 0,
    })
    visible.value = false
  } catch (error) {
    showError(getErrorMessage(error))
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <el-drawer
    v-model="visible"
    size="460px"
    title="新增用户"
    :before-close="handleBeforeClose"
  >
    <div class="drawer-shell">
      <div class="drawer-copy">
        <div class="drawer-copy__eyebrow">Quick Create</div>
        <p class="drawer-copy__body">
          这个入口只使用后端已确认的 `UserBasicModel` 字段：用户名、密码、邮箱，以及电话 / 安全等级等基础信息。
        </p>
      </div>

      <el-form :model="form" label-position="top" @submit.prevent="submit">
        <el-form-item label="用户名" :error="fieldErrors.username">
          <el-input v-model="form.username" autocomplete="off" />
        </el-form-item>
        <el-form-item label="初始密码" :error="fieldErrors.password">
          <el-input v-model="form.password" type="password" show-password autocomplete="new-password" />
        </el-form-item>
        <el-form-item label="邮箱" :error="fieldErrors.email">
          <el-input v-model="form.email" type="email" autocomplete="off" />
        </el-form-item>

        <div class="drawer-grid">
          <el-form-item label="电话">
            <el-input v-model="form.telephone" autocomplete="off" />
          </el-form-item>
          <el-form-item label="安全等级">
            <el-input-number v-model="form.safe_level" :min="0" :max="10" controls-position="right" />
          </el-form-item>
        </div>

        <el-form-item>
          <el-switch v-model="form.email_verified" inline-prompt active-text="已验证" inactive-text="待验证" />
        </el-form-item>

        <div class="drawer-actions">
          <el-button :disabled="loading" @click="requestClose">取消</el-button>
          <el-button type="primary" native-type="submit" :loading="loading">创建用户</el-button>
        </div>
      </el-form>
    </div>
  </el-drawer>
</template>

<style scoped>
.drawer-shell {
  display: flex;
  flex-direction: column;
  gap: var(--barc-space-5);
}

.drawer-copy {
  padding: var(--barc-space-4);
  border: 1px solid var(--barc-border);
  border-radius: var(--barc-radius-md);
  background:
    radial-gradient(circle at top right, var(--barc-bg-accent), transparent 45%),
    linear-gradient(160deg, var(--barc-surface-strong), var(--barc-surface));
  box-shadow: var(--barc-shadow-sm);
}

.drawer-copy__eyebrow {
  margin-bottom: var(--barc-space-2);
  color: var(--barc-accent-strong);
  font-size: 0.78rem;
  letter-spacing: 0.16em;
  text-transform: uppercase;
}

.drawer-copy__body {
  margin: 0;
  color: var(--barc-text-soft);
  line-height: 1.7;
}

.drawer-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 1rem;
}

.drawer-actions {
  display: flex;
  justify-content: flex-end;
  gap: 0.75rem;
  padding-top: var(--barc-space-2);
  border-top: 1px solid var(--barc-border);
}
</style>
