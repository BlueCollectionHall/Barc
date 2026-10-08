<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'

import RoutePageShell from '@/app/components/RoutePageShell.vue'
import {
  createFriendLink,
  deleteFriendLink,
  fetchFriendLinks,
  moveFriendLink,
  updateFriendLink,
  uploadFriendLinkLogo,
  type FriendLinkMoveDirection,
  type FriendLinkPayload,
  type FriendLinkRecord,
} from '@/modules/friend-link/api/friendLink.service'
import { getErrorMessage } from '@/shared/types/api'
import { showError, showSuccess } from '@/shared/utils/message'

/**
 * 友情链接管理页（系统 → 友情链接）
 *
 * 功能：
 * 1、列表展示：logo 缩略图、标题、链接、简介、序号；
 * 2、新增 / 编辑：抽屉表单，只有标题与链接必填，logo 与简介可选；
 * 3、logo 上传：走「先传后落」——先调上传接口拿 objectKey，再随表单一并提交（与背景图同构）；
 * 4、排序：上移 / 下移按钮（一次移动只影响相邻两条，后端在同一事务内交换 sort_order）；
 * 5、删除：二次确认后软删，logo 由后端交 GC 队列清理。
 *
 * 先传后落的已知副作用：上传 logo 后放弃保存会在 COS 留下孤儿对象，本期不做补偿，
 * 待「COS 孤儿对象巡检」独立需求统一治理（见设计文档 §3.8）。
 */

/** logo 大小上限，与后端 FriendLinkService.MAX_FILE_SIZE 保持一致 */
const MAX_FILE_SIZE = 1024 * 1024

const list = ref<FriendLinkRecord[]>([])
const loading = ref(false)

// ============ 新增 / 编辑抽屉 ============

/** 抽屉是否可见 */
const drawerVisible = ref(false)
/** 当前编辑的记录；为 null 表示新增 */
const editing = ref<FriendLinkRecord | null>(null)
const saving = ref(false)
const uploading = ref(false)
/** 本地预览（base64）：上传成功到保存之间先显示本地图，避免依赖签名 URL */
const logoPreview = ref('')
const formRef = ref<FormInstance>()

const form = reactive<FriendLinkPayload>({
  title: '',
  url: '',
  logo_key: null,
  intro: '',
})

const drawerTitle = computed(() => (editing.value ? '编辑友链' : '新增友链'))

const rules: FormRules = {
  title: [
    { required: true, message: '请填写友链标题', trigger: 'blur' },
    { max: 64, message: '标题长度不能超过 64 个字符', trigger: 'blur' },
  ],
  url: [
    { required: true, message: '请填写友链链接', trigger: 'blur' },
    // 与后端校验一致：仅要求 http/https 前缀，不探测域名真实性
    { pattern: /^https?:\/\//i, message: '链接必须以 http:// 或 https:// 开头', trigger: 'blur' },
    { max: 512, message: '链接长度不能超过 512 个字符', trigger: 'blur' },
  ],
  intro: [{ max: 255, message: '简介长度不能超过 255 个字符', trigger: 'blur' }],
}

/** 表单里当前展示的 logo 图片地址：本地预览优先，其次后端返回的签名 URL */
const logoDisplayUrl = computed(() => logoPreview.value || editing.value?.logo_url || '')

async function loadList(): Promise<void> {
  loading.value = true
  try {
    list.value = await fetchFriendLinks()
  } catch (error) {
    showError(getErrorMessage(error))
  } finally {
    loading.value = false
  }
}

/** 重置表单为「新增」初始态 */
function resetForm(): void {
  form.title = ''
  form.url = ''
  form.logo_key = null
  form.intro = ''
  logoPreview.value = ''
  formRef.value?.clearValidate()
}

function openCreate(): void {
  editing.value = null
  resetForm()
  drawerVisible.value = true
}

function openEdit(row: FriendLinkRecord): void {
  editing.value = row
  form.title = row.title
  form.url = row.url
  form.logo_key = row.logo_key
  form.intro = row.intro ?? ''
  logoPreview.value = ''
  formRef.value?.clearValidate()
  drawerVisible.value = true
}

/** 上传前本地校验：非图片 / 超 1MB 直接拦下，避免白跑一次请求 */
function beforeLogoUpload(file: File): boolean {
  if (!file.type.startsWith('image/')) {
    ElMessage.error('请上传图片文件')
    return false
  }
  if (file.size > MAX_FILE_SIZE) {
    ElMessage.error('图片大小不能超过 1MB')
    return false
  }
  // 生成本地预览（base64），上传成功后立即显示，直到保存后由后端签名 URL 接管
  const reader = new FileReader()
  reader.onload = (event) => {
    logoPreview.value = (event.target?.result as string) ?? ''
  }
  reader.readAsDataURL(file)
  return true
}

/**
 * 上传 logo：只上传不落库，拿到 objectKey 后写入表单，等提交时一起保存
 */
async function handleLogoUpload(options: {
  file: File
  onSuccess?: (response: unknown) => void
  onError?: (error: unknown) => void
}): Promise<void> {
  uploading.value = true
  try {
    const objectKey = await uploadFriendLinkLogo(options.file)
    form.logo_key = objectKey
    options.onSuccess?.(objectKey)
    showSuccess('logo 上传成功，保存后生效')
  } catch (error) {
    options.onError?.(error)
    logoPreview.value = ''
    showError(getErrorMessage(error))
  } finally {
    uploading.value = false
  }
}

/** 清除已选 logo（保存时提交 null，后端会把旧对象交 GC 清理） */
function clearLogo(): void {
  form.logo_key = null
  logoPreview.value = ''
}

/**
 * 提交前的显式校验。
 *
 * 为什么不只依赖 el-form 的 rules：rules 负责「行内红字提示」的观感，
 * 但它由 el-form 内部驱动，在部分环境（例如测试中 el-form-item 被 stub）不会真正阻断提交。
 * 因此这里再做一层显式校验，保证「校验不通过就绝不发请求」这条业务约束在任何环境下都成立。
 * 规则与 rules、以及后端 FriendLinkService#validate 三处保持一致。
 */
function validateForm(): string | null {
  const title = form.title.trim()
  const url = form.url.trim()
  if (!title) {
    return '请填写友链标题'
  }
  if (title.length > 64) {
    return '标题长度不能超过 64 个字符'
  }
  if (!url) {
    return '请填写友链链接'
  }
  if (!/^https?:\/\//i.test(url)) {
    return '链接必须以 http:// 或 https:// 开头'
  }
  if (url.length > 512) {
    return '链接长度不能超过 512 个字符'
  }
  if (form.intro && form.intro.trim().length > 255) {
    return '简介长度不能超过 255 个字符'
  }
  return null
}

/** 提交表单：新增或修改 */
async function submitForm(): Promise<void> {
  // 先让 el-form 把行内错误标出来（失败也继续走下面的显式校验）
  await formRef.value?.validate().catch(() => false)

  const invalidReason = validateForm()
  if (invalidReason) {
    showError(invalidReason)
    return
  }

  saving.value = true
  try {
    const payload: FriendLinkPayload = {
      title: form.title.trim(),
      url: form.url.trim(),
      logo_key: form.logo_key,
      intro: form.intro && form.intro.trim() ? form.intro.trim() : null,
    }
    if (editing.value) {
      await updateFriendLink(editing.value.id, payload)
      showSuccess('友链已更新')
    } else {
      await createFriendLink(payload)
      showSuccess('友链已新增')
    }
    drawerVisible.value = false
    await loadList()
  } catch (error) {
    showError(getErrorMessage(error))
  } finally {
    saving.value = false
  }
}

/**
 * 上移 / 下移：后端返回交换后的完整列表，直接替换本地状态（不必再发一次列表请求）
 */
async function moveItem(row: FriendLinkRecord, direction: FriendLinkMoveDirection): Promise<void> {
  try {
    const updated = await moveFriendLink(row.id, direction)
    if (Array.isArray(updated)) {
      list.value = updated
    } else {
      await loadList()
    }
    showSuccess(direction === 'up' ? '已上移' : '已下移')
  } catch (error) {
    showError(getErrorMessage(error))
  }
}

async function removeItem(row: FriendLinkRecord): Promise<void> {
  try {
    await ElMessageBox.confirm('删除后该友链将不再展示，是否继续？', '确认删除', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消',
    })
  } catch {
    // 用户取消：ElMessageBox 以 reject('cancel' | 'close') 结束，这里直接返回
    return
  }
  try {
    await deleteFriendLink(row.id)
    showSuccess('友链已删除')
    await loadList()
  } catch (error) {
    showError(getErrorMessage(error))
  }
}

onMounted(loadList)
</script>

<template>
  <RoutePageShell title="友情链接" eyebrow="Friend Links">
    <template #subtitle>
      维护用户端首页「友情链接&amp;特别感谢」区块展示的内容，支持 logo 上传与手动排序。
    </template>

    <template #actions>
      <el-button type="primary" data-test="create-friend-link" @click="openCreate">新增友链</el-button>
    </template>

    <section class="glass-panel panel" v-loading="loading">
      <el-table :data="list" row-key="id" empty-text="还没有友链，点击右上角「新增友链」添加">
        <el-table-column label="序号" width="80">
          <template #default="{ row }">
            <span class="order-no">{{ row.sort_order }}</span>
          </template>
        </el-table-column>

        <el-table-column label="logo" width="120">
          <template #default="{ row }">
            <el-image
              v-if="row.logo_url"
              class="logo"
              :src="row.logo_url"
              fit="cover"
              :preview-src-list="[row.logo_url]"
              preview-teleported
            />
            <span v-else class="logo-placeholder">无</span>
          </template>
        </el-table-column>

        <el-table-column label="标题" min-width="160">
          <template #default="{ row }">
            <span class="title">{{ row.title }}</span>
          </template>
        </el-table-column>

        <el-table-column label="链接" min-width="240">
          <template #default="{ row }">
            <a class="link" :href="row.url" target="_blank" rel="noopener noreferrer" :title="row.url">
              {{ row.url }}
            </a>
          </template>
        </el-table-column>

        <el-table-column label="简介" min-width="200">
          <template #default="{ row }">
            <span class="intro">{{ row.intro || '—' }}</span>
          </template>
        </el-table-column>

        <el-table-column label="排序" width="150">
          <template #default="{ row, $index }">
            <el-button-group>
              <el-button
                size="small"
                data-test="move-up"
                :disabled="$index === 0"
                @click="moveItem(row, 'up')"
              >
                上移
              </el-button>
              <el-button
                size="small"
                data-test="move-down"
                :disabled="$index === list.length - 1"
                @click="moveItem(row, 'down')"
              >
                下移
              </el-button>
            </el-button-group>
          </template>
        </el-table-column>

        <el-table-column label="操作" width="150">
          <template #default="{ row }">
            <el-button size="small" data-test="edit-friend-link" @click="openEdit(row)">编辑</el-button>
            <el-button type="danger" plain size="small" data-test="delete-friend-link" @click="removeItem(row)">
              删除
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </section>

    <el-drawer v-model="drawerVisible" :title="drawerTitle" size="460px" data-test="friend-link-drawer">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="72px">
        <el-form-item label="标题" prop="title">
          <el-input v-model="form.title" maxlength="64" placeholder="请输入友链标题" data-test="form-title" />
        </el-form-item>

        <el-form-item label="链接" prop="url">
          <el-input
            v-model="form.url"
            maxlength="512"
            placeholder="https://example.com"
            data-test="form-url"
          />
        </el-form-item>

        <el-form-item label="logo" prop="logo_key">
          <div class="logo-field">
            <img v-if="logoDisplayUrl" class="logo-preview" :src="logoDisplayUrl" alt="logo 预览" />
            <div class="logo-actions">
              <el-upload
                :http-request="handleLogoUpload"
                :before-upload="beforeLogoUpload"
                :show-file-list="false"
                :disabled="uploading"
                accept="image/*"
                data-test="upload-logo-field"
              >
                <el-button :loading="uploading" size="small" data-test="upload-logo">
                  {{ logoDisplayUrl ? '替换 logo' : '上传 logo' }}
                </el-button>
              </el-upload>
              <el-button v-if="form.logo_key || logoPreview" size="small" text @click="clearLogo">清除</el-button>
            </div>
          </div>
        </el-form-item>

        <el-form-item label="简介" prop="intro">
          <el-input
            v-model="form.intro"
            type="textarea"
            :rows="3"
            maxlength="255"
            show-word-limit
            placeholder="选填，展示在标题下方"
            data-test="form-intro"
          />
        </el-form-item>
      </el-form>

      <!-- 操作区放在表单内而不是 drawer 的 footer 插槽：保证保存按钮始终随表单一起渲染 -->
      <div class="drawer-actions">
        <el-button @click="drawerVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" data-test="submit-friend-link" @click="submitForm">
          保存
        </el-button>
      </div>
    </el-drawer>
  </RoutePageShell>
</template>

<style scoped>
.panel {
  padding: 1rem;
}

.order-no {
  display: inline-block;
  min-width: 1.6em;
  font-weight: 600;
}

.logo {
  width: 72px;
  height: 40px;
  border-radius: 6px;
  background-color: rgba(127, 127, 127, 0.2);
}

.logo-placeholder {
  color: var(--barc-text-soft);
  font-size: 0.85rem;
}

.title {
  font-weight: 600;
}

.link {
  color: var(--barc-accent-strong);
  text-decoration: none;
  word-break: break-all;
}

.link:hover {
  text-decoration: underline;
}

.intro {
  color: var(--barc-text-soft);
  font-size: 0.9rem;
}

.logo-field {
  display: flex;
  align-items: center;
  gap: 0.75rem;
}

.logo-preview {
  width: 96px;
  height: 54px;
  border-radius: 6px;
  object-fit: cover;
  background-color: rgba(127, 127, 127, 0.2);
}

.logo-actions {
  display: flex;
  align-items: center;
  gap: 0.25rem;
}

.drawer-actions {
  display: flex;
  justify-content: flex-end;
  gap: 0.5rem;
  margin-top: 1rem;
}
</style>
