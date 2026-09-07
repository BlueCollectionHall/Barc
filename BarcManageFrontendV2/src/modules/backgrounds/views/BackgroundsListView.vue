<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'

import RoutePageShell from '@/app/components/RoutePageShell.vue'
import {
  BACKGROUND_FESTIVAL_OPTIONS,
  BACKGROUND_TIME_PERIOD_OPTIONS,
  deleteBackground,
  fetchBackgroundList,
  fetchBackgroundModules,
  reorderBackground,
  setBackgroundEnabled,
  updateBackgroundScene,
  uploadBackground,
  type BackgroundImageRecord,
  type BackgroundModuleOption,
} from '@/modules/backgrounds/api/backgrounds.service'
import { getErrorMessage } from '@/shared/types/api'
import { showError, showSuccess } from '@/shared/utils/message'

const MAX_FILE_SIZE = 1024 * 1024

const modules = ref<BackgroundModuleOption[]>([])
const module = ref<string>('')
const list = ref<BackgroundImageRecord[]>([])
const loading = ref(false)
const uploading = ref(false)

const moduleLabel = computed(
  () => modules.value.find((item) => item.value === module.value)?.label ?? module.value,
)

async function loadModules(): Promise<void> {
  try {
    modules.value = await fetchBackgroundModules()
    const firstModule = modules.value[0]
    if (firstModule && !module.value) {
      module.value = firstModule.value
    }
  } catch (error) {
    showError(getErrorMessage(error))
  }
}

async function loadList(): Promise<void> {
  if (!module.value) return
  loading.value = true
  try {
    list.value = await fetchBackgroundList(module.value)
  } catch (error) {
    showError(getErrorMessage(error))
  } finally {
    loading.value = false
  }
}

function beforeUpload(file: File): boolean {
  const isImage = file.type.startsWith('image/')
  if (!isImage) {
    ElMessage.error('请上传图片文件')
    return false
  }
  if (file.size > MAX_FILE_SIZE) {
    ElMessage.error('图片大小不能超过 1MB')
    return false
  }
  return true
}

async function handleUpload(options: {
  file: File
  onSuccess?: (response: unknown) => void
  onError?: (error: unknown) => void
}): Promise<void> {
  if (!module.value) {
    ElMessage.error('请先选择背景模块')
    options.onError?.(new Error('请先选择背景模块'))
    return
  }
  uploading.value = true
  try {
    const record = await uploadBackground(options.file, module.value)
    options.onSuccess?.(record)
    showSuccess('背景图上传成功')
    await loadList()
  } catch (error) {
    options.onError?.(error)
    showError(getErrorMessage(error))
  } finally {
    uploading.value = false
  }
}

async function persistEnabled(row: BackgroundImageRecord): Promise<void> {
  const next = row.enabled
  try {
    await setBackgroundEnabled(row.id, next)
    showSuccess(next ? '背景图已启用' : '背景图已停用')
  } catch (error) {
    row.enabled = !next
    showError(getErrorMessage(error))
  }
}

/** 保存单张背景图的场景标签（时段 + 节日） */
async function persistScene(row: BackgroundImageRecord): Promise<void> {
  try {
    await updateBackgroundScene(row.id, row.time_period, row.festival)
    showSuccess('背景图场景已更新')
  } catch (error) {
    showError(getErrorMessage(error))
  }
}

async function moveItem(index: number, direction: -1 | 1): Promise<void> {
  const target = index + direction
  if (target < 0 || target >= list.value.length) return
  const arr = [...list.value]
  const item = arr.splice(index, 1)[0]
  if (!item) return
  arr.splice(target, 0, item)
  const ordered = arr.map((row, idx) => ({ id: row.id, sort_order: idx + 1 }))
  try {
    await reorderBackground(ordered)
    list.value = arr.map((row, idx) => ({ ...row, sort_order: idx + 1 }))
  } catch (error) {
    showError(getErrorMessage(error))
  }
}

async function removeItem(row: BackgroundImageRecord): Promise<void> {
  try {
    await ElMessageBox.confirm('删除后该背景图将不再展示，是否继续？', '确认删除', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消',
    })
    await deleteBackground(row.id)
    showSuccess('背景图已删除')
    await loadList()
  } catch (error) {
    if (error === 'cancel' || error === 'close') return
    showError(getErrorMessage(error))
  }
}

onMounted(async () => {
  await loadModules()
  await loadList()
})
</script>

<template>
  <RoutePageShell title="背景图设置" eyebrow="Background Images">
    <template #actions>
      <el-select v-model="module" class="module-select" @change="loadList">
        <el-option
          v-for="item in modules"
          :key="item.value"
          :value="item.value"
          :label="item.label"
        />
      </el-select>
      <el-upload
        :http-request="handleUpload"
        :before-upload="beforeUpload"
        :show-file-list="false"
        :disabled="uploading"
        accept="image/*"
      >
        <el-button :loading="uploading" type="primary">上传{{ moduleLabel }}背景图</el-button>
      </el-upload>
    </template>

    <section class="glass-panel panel" v-loading="loading">
      <el-table :data="list" row-key="id" empty-text="当前模块还没有背景图">
        <el-table-column label="预览" width="180">
          <template #default="{ row }">
            <el-image
              class="preview"
              :src="row.url || row.object_key"
              fit="cover"
              :preview-src-list="row.url ? [row.url] : []"
            />
          </template>
        </el-table-column>

        <el-table-column label="文件名" min-width="200">
          <template #default="{ row }">
            <span class="filename">{{ row.filename || '未命名' }}</span>
          </template>
        </el-table-column>

        <el-table-column label="时段" width="120">
          <template #default="{ row }">
            <el-select v-model="row.time_period" size="small" class="scene-select" @change="persistScene(row)">
              <el-option
                v-for="opt in BACKGROUND_TIME_PERIOD_OPTIONS"
                :key="String(opt.value)"
                :value="opt.value"
                :label="opt.label"
              />
            </el-select>
          </template>
        </el-table-column>

        <el-table-column label="节日" width="110">
          <template #default="{ row }">
            <el-select v-model="row.festival" size="small" class="scene-select" @change="persistScene(row)">
              <el-option
                v-for="opt in BACKGROUND_FESTIVAL_OPTIONS"
                :key="String(opt.value)"
                :value="opt.value"
                :label="opt.label"
              />
            </el-select>
          </template>
        </el-table-column>

        <el-table-column label="排序" width="150">
          <template #default="{ row, $index }">
            <span class="order-no">{{ $index + 1 }}</span>
            <el-button-group>
              <el-button size="small" :disabled="$index === 0" @click="moveItem($index, -1)">上移</el-button>
              <el-button size="small" :disabled="$index === list.length - 1" @click="moveItem($index, 1)">下移</el-button>
            </el-button-group>
          </template>
        </el-table-column>

        <el-table-column label="启用" width="90">
          <template #default="{ row }">
            <el-switch v-model="row.enabled" @change="persistEnabled(row)" />
          </template>
        </el-table-column>

        <el-table-column label="操作" width="100">
          <template #default="{ row }">
            <el-button type="danger" plain size="small" @click="removeItem(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </section>
  </RoutePageShell>
</template>

<style scoped>
.module-select {
  width: 160px;
}

.scene-select {
  width: 100%;
}

.panel {
  padding: 1rem;
}

.preview {
  width: 120px;
  height: 68px;
  border-radius: 6px;
  background-color: rgba(127, 127, 127, 0.2);
}

.filename {
  color: var(--barc-text-soft);
  font-size: 0.9rem;
}

.order-no {
  display: inline-block;
  min-width: 1.6em;
  margin-right: 0.5rem;
  font-weight: 600;
}
</style>
