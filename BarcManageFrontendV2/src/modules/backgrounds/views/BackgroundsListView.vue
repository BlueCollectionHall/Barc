<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'

import RoutePageShell from '@/app/components/RoutePageShell.vue'
import {
  BACKGROUND_TIME_PERIOD_OPTIONS,
  deleteBackground,
  fetchBackgroundList,
  fetchBackgroundModules,
  fetchFestivalSceneOptions,
  reorderBackground,
  setBackgroundEnabled,
  updateBackgroundScene,
  uploadBackground,
  type BackgroundImageRecord,
  type BackgroundModuleOption,
  type BackgroundSceneOption,
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

/** 当前选中模块是否启用「时段 + 节日」场景化筛选（仅首页 true，其余纯随机） */
const sceneEnabled = computed(
  () => modules.value.find((item) => item.value === module.value)?.sceneEnabled ?? false,
)

// ============ 时段 / 节日筛选（仅启用场景化的模块显示） ============

/**
 * 筛选哨兵值：表示「不过滤」。
 * 必须与真实的标签 null（无时段 / 无节日）区分开，否则无法筛"未标注"的图。
 */
const FILTER_ALL = '__all__'

/**
 * 时段筛选选项：全部时段 / 白天 / 傍晚 / 夜晚 / 未标注时段(null)。
 * 复用表格下拉的选项常量，仅把 null 的中文名改为「未标注（任意）」，
 * 以免与新增的「全部时段」混淆。
 */
const TIME_PERIOD_FILTER_OPTIONS: BackgroundSceneOption[] = [
  { value: FILTER_ALL, label: '全部时段' },
  ...BACKGROUND_TIME_PERIOD_OPTIONS.map((opt) => ({
    value: opt.value,
    label: opt.value === null ? '未标注（任意）' : opt.label,
  })),
]

/**
 * 「节日」下拉选项（null 表示无节日通用图）。
 * 由页面挂载时拉取「启用中」的节日后填充，避免写死枚举。
 */
const festivalOptions = ref<BackgroundSceneOption[]>([{ value: null, label: '无' }])

/**
 * 节日筛选选项：全部节日 / 启用中的节日 / 未标注节日(null)。
 * 节日来自「系统管理 → 节日管理」，因此这里用 computed 承载异步结果。
 */
const FESTIVAL_FILTER_OPTIONS = computed<BackgroundSceneOption[]>(() => [
  { value: FILTER_ALL, label: '全部节日' },
  ...festivalOptions.value.map((opt) => ({
    value: opt.value,
    label: opt.value === null ? '未标注（无节日）' : opt.label,
  })),
])

/** 当前筛选的时段条件：FILTER_ALL = 不过滤；null = 只看未标注时段的图 */
const filterTimePeriod = ref<string | null>(FILTER_ALL)
/** 当前筛选的节日条件：FILTER_ALL = 不过滤；null = 只看未标注节日的图 */
const filterFestival = ref<string | null>(FILTER_ALL)

/** 是否存在生效的筛选条件（未启用场景化的模块永远为 false，不参与筛选） */
const filterActive = computed(
  () =>
    sceneEnabled.value &&
    (filterTimePeriod.value !== FILTER_ALL || filterFestival.value !== FILTER_ALL),
)

/**
 * 按「时段 + 节日」筛选后的列表（仅用于展示，不改变原始数据顺序）。
 * 两个条件取交集；条件为 FILTER_ALL 时该维度不过滤；
 * 条件为 null 时精确匹配「未标注」的图（兼容无时段 / 无节日的筛选诉求）。
 */
const filteredList = computed(() => {
  if (!filterActive.value) {
    return list.value
  }
  return list.value.filter((row) => {
    const timeMatched =
      filterTimePeriod.value === FILTER_ALL || row.time_period === filterTimePeriod.value
    const festivalMatched =
      filterFestival.value === FILTER_ALL || row.festival === filterFestival.value
    return timeMatched && festivalMatched
  })
})

/** 某行在完整列表中的真实序号（筛选后依然显示原始位置，便于对照排序） */
function orderNo(row: BackgroundImageRecord): number {
  return list.value.findIndex((item) => item.id === row.id) + 1
}

/** 清空筛选条件，恢复显示全部 */
function resetFilter(): void {
  filterTimePeriod.value = FILTER_ALL
  filterFestival.value = FILTER_ALL
}

/** 切换模块：先清空筛选再加载，避免带着上个模块的条件导致看不到图 */
async function handleModuleChange(): Promise<void> {
  resetFilter()
  await loadList()
}

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

/**
 * 加载「节日」下拉选项（启用中的节日）。
 * 单独拉取：节日来自「节日管理」模块，与背景模块列表互不依赖，任一失败都不影响另一个。
 */
async function loadFestivalOptions(): Promise<void> {
  festivalOptions.value = await fetchFestivalSceneOptions()
  normalizeStaleFestivals()
}

/**
 * 同步「已失效的节日绑定」。
 *
 * 场景：馆长在「节日管理」里删除或关闭了某个节日后，绑定它的背景图会被后端自动划归「无节日」；
 * 若本页面仍停留在打开状态，本地列表还持有旧的节日 ID，会让下拉框显示成空值造成误解。
 * 这里把「不在当前可选节日里的绑定」统一显示为「无」，与后端已置 NULL 的状态保持一致。
 */
function normalizeStaleFestivals(): void {
  const selectable = new Set(festivalOptions.value.map((opt) => opt.value))
  for (const row of list.value) {
    const bound = row.festival
    if (bound === null || bound === undefined || !selectable.has(bound)) {
      row.festival = null
    }
  }
}

async function loadList(): Promise<void> {
  if (!module.value) return
  loading.value = true
  try {
    list.value = await fetchBackgroundList(module.value)
    // 拉回列表后立即对齐一次节日绑定，避免外部删除节日导致的残留显示
    normalizeStaleFestivals()
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
  // 先取节日选项，再拉列表：这样列表回来时就能立刻对齐失效的节日绑定
  await loadFestivalOptions()
  await loadList()
})
</script>

<template>
  <RoutePageShell title="背景图设置" eyebrow="Background Images">
    <template #actions>
      <el-select v-model="module" class="module-select" @change="handleModuleChange">
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

    <!-- 时段 / 节日筛选：仅对启用场景化的模块（如首页）展示，未启用的模块完全不显示 -->
    <section
      v-if="sceneEnabled"
      class="glass-panel filter-panel"
      data-test="scene-filter"
    >
      <div class="filter-field">
        <span class="filter-field__label">时段</span>
        <el-select
          v-model="filterTimePeriod"
          size="small"
          class="filter-field__select"
          data-test="filter-time-period"
        >
          <el-option
            v-for="opt in TIME_PERIOD_FILTER_OPTIONS"
            :key="String(opt.value)"
            :value="opt.value"
            :label="opt.label"
          />
        </el-select>
      </div>
      <div class="filter-field">
        <span class="filter-field__label">节日</span>
        <el-select
          v-model="filterFestival"
          size="small"
          class="filter-field__select"
          data-test="filter-festival"
        >
          <el-option
            v-for="opt in FESTIVAL_FILTER_OPTIONS"
            :key="String(opt.value)"
            :value="opt.value"
            :label="opt.label"
          />
        </el-select>
      </div>
      <el-button size="small" :disabled="!filterActive" @click="resetFilter">重置筛选</el-button>
      <span class="filter-summary" data-test="filter-summary">
        命中 {{ filteredList.length }} / 共 {{ list.length }} 张
      </span>
    </section>

    <section class="glass-panel panel" v-loading="loading">
      <el-alert
        v-if="!sceneEnabled"
        class="scene-hint"
        type="info"
        :closable="false"
        show-icon
        title="该模块按启用顺序随机轮播，暂不支持时段/节日"
      />
      <el-table
        :data="filteredList"
        row-key="id"
        :empty-text="filterActive ? '没有符合当前筛选条件的背景图' : '当前模块还没有背景图'"
      >
        <el-table-column label="预览" width="180">
          <template #default="{ row }">
            <el-image
              class="preview"
              :src="row.url || row.object_key"
              fit="cover"
              :preview-src-list="row.url ? [row.url] : []"
              preview-teleported
            />
          </template>
        </el-table-column>

        <el-table-column label="文件名" min-width="200">
          <template #default="{ row }">
            <span class="filename">{{ row.filename || '未命名' }}</span>
          </template>
        </el-table-column>

        <el-table-column v-if="sceneEnabled" label="时段" width="120">
          <template #default="{ row }">
            <el-select
              v-model="row.time_period"
              size="small"
              class="scene-select"
              data-test="row-time-period"
              @change="persistScene(row)"
            >
              <el-option
                v-for="opt in BACKGROUND_TIME_PERIOD_OPTIONS"
                :key="String(opt.value)"
                :value="opt.value"
                :label="opt.label"
              />
            </el-select>
          </template>
        </el-table-column>

        <el-table-column v-if="sceneEnabled" label="节日" width="140">
          <template #default="{ row }">
            <el-select
              v-model="row.festival"
              size="small"
              class="scene-select"
              data-test="row-festival"
              @change="persistScene(row)"
            >
              <el-option
                v-for="opt in festivalOptions"
                :key="String(opt.value)"
                :value="opt.value"
                :label="opt.label"
              />
            </el-select>
          </template>
        </el-table-column>

        <el-table-column label="排序" width="150">
          <template #default="{ row, $index }">
            <!-- 筛选状态下显示行在完整列表中的真实序号，避免与实际顺序脱节 -->
            <span class="order-no">{{ orderNo(row) }}</span>
            <el-button-group>
              <!-- 筛选生效时行索引不等于真实顺序，禁用排序以免错位 -->
              <el-button
                size="small"
                :disabled="filterActive || $index === 0"
                @click="moveItem($index, -1)"
              >
                上移
              </el-button>
              <el-button
                size="small"
                :disabled="filterActive || $index === list.length - 1"
                @click="moveItem($index, 1)"
              >
                下移
              </el-button>
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

.scene-hint {
  margin-bottom: 0.75rem;
}

/* 时段 / 节日筛选栏 */
.filter-panel {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 0.75rem;
  padding: 0.75rem 1rem;
}

.filter-field {
  display: flex;
  align-items: center;
  gap: 0.5rem;
}

.filter-field__label {
  color: var(--barc-text-soft);
  font-size: 0.85rem;
  white-space: nowrap;
}

.filter-field__select {
  width: 160px;
}

.filter-summary {
  margin-left: auto;
  color: var(--barc-text-soft);
  font-size: 0.85rem;
  white-space: nowrap;
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
