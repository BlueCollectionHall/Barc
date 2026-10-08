<script setup lang="ts">
/**
 * 节日管理（系统管理 → 节日管理，仅馆长可访问）
 *
 * 功能：
 * 1、列表展示全部节日（含已关闭），并显示绑定的背景图数量；
 * 2、新增 / 修改节日：支持「月份式」（某月第一天至某月最后一天）与「日期式」（精确某月某日到某月某日）
 *    两种录入方式，二者提交给后端的本质都是「某月某日 → 某月某日」；
 * 3、关闭节日：保留数据与背景图绑定，仅不参与用户端节日判定；
 * 4、删除节日：后端会把绑定该节日的背景图自动划归「无节日」，避免用户端取图链路出错。
 */

import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'

import RoutePageShell from '@/app/components/RoutePageShell.vue'
import {
  MONTH_OPTIONS,
  MONTH_MAX_DAY,
  createFestival,
  dayOptionsFor,
  deleteFestival,
  detectDateMode,
  fetchFestivals,
  monthRangeOf,
  setFestivalEnabled,
  updateFestival,
  type FestivalDateMode,
  type FestivalRecord,
} from '@/modules/system/api/festivals.service'
import { getErrorMessage } from '@/shared/types/api'
import { showError, showSuccess } from '@/shared/utils/message'

/** 表格行：在原始记录上附带由后端区间推导出的录入方式 */
interface FestivalRow extends FestivalRecord {
  mode: FestivalDateMode
}

const list = ref<FestivalRow[]>([])
const loading = ref(false)
const saving = ref(false)

const dialogVisible = ref(false)
/** 当前编辑的节日 ID；为空表示新增 */
const editingId = ref<string | null>(null)

/** 表单模型：月份式只填起止月，日期式额外填起止日 */
const form = reactive({
  name: '',
  mode: 'month' as FestivalDateMode,
  startMonth: 1,
  endMonth: 1,
  startDay: 1,
  endDay: 1,
})

const dialogTitle = computed(() => (editingId.value ? '修改节日' : '新增节日'))

/** 日期式下，日期超出该月天数时自动收敛到该月最后一天，避免提交非法日期 */
function clampDays(): void {
  const startMax = MONTH_MAX_DAY[form.startMonth] ?? 31
  const endMax = MONTH_MAX_DAY[form.endMonth] ?? 31
  if (form.startDay > startMax) {
    form.startDay = startMax
  }
  if (form.endDay > endMax) {
    form.endDay = endMax
  }
}

/** 把当前表单转换为提交给后端的月日区间（月份式自动展开为该月首末日） */
function buildPayload(): {
  name: string
  start_month: number
  start_day: number
  end_month: number
  end_day: number
} {
  if (form.mode === 'month') {
    // 月份式：起始月第一天 → 结束月最后一天
    return { name: form.name.trim(), ...monthRangeOf(form.startMonth, form.endMonth) }
  }
  return {
    name: form.name.trim(),
    start_month: form.startMonth,
    start_day: form.startDay,
    end_month: form.endMonth,
    end_day: form.endDay,
  }
}

/** 根据录入方式生成预览文案，让馆长在提交前确认落库区间 */
const rangePreview = computed(() => {
  const payload = buildPayload()
  const pad = (value: number): string => String(value).padStart(2, '0')
  const start = `${pad(payload.start_month)}月${pad(payload.start_day)}日`
  const end = `${pad(payload.end_month)}月${pad(payload.end_day)}日`
  // 起始月大于结束月即为跨年区间
  return payload.start_month > payload.end_month ? `${start} ~ 次年${end}` : `${start} ~ ${end}`
})

/** 列表行的区间文案：月份式与日期式的展示粒度不同 */
function rangeText(row: FestivalRow): string {
  const pad = (value: number): string => String(value).padStart(2, '0')
  const crossYear = row.start_month > row.end_month
  const endPrefix = crossYear ? '次年' : ''
  if (row.mode === 'month') {
    return `${pad(row.start_month)}月 ~ ${endPrefix}${pad(row.end_month)}月`
  }
  return `${pad(row.start_month)}月${pad(row.start_day)}日 ~ ${endPrefix}${pad(row.end_month)}月${pad(row.end_day)}日`
}

async function loadList(): Promise<void> {
  loading.value = true
  try {
    const records = await fetchFestivals()
    list.value = records.map((record) => ({ ...record, mode: detectDateMode(record) }))
  } catch (error) {
    showError(getErrorMessage(error))
  } finally {
    loading.value = false
  }
}

/** 打开弹窗（新增或修改） */
function openDialog(row?: FestivalRow): void {
  if (row) {
    editingId.value = row.id
    form.name = row.name
    form.mode = row.mode
    form.startMonth = row.start_month
    form.endMonth = row.end_month
    form.startDay = row.start_day
    form.endDay = row.end_day
  } else {
    editingId.value = null
    form.name = ''
    form.mode = 'month'
    form.startMonth = 1
    form.endMonth = 1
    form.startDay = 1
    form.endDay = 1
  }
  dialogVisible.value = true
}

/** 校验表单，返回错误信息；通过则返回 null */
function validateForm(): string | null {
  if (!form.name.trim()) {
    return '请填写节日名称'
  }
  if (form.mode === 'date') {
    const startMax = MONTH_MAX_DAY[form.startMonth] ?? 31
    const endMax = MONTH_MAX_DAY[form.endMonth] ?? 31
    if (form.startDay < 1 || form.startDay > startMax) {
      return `起始日期不合法，${form.startMonth} 月最多 ${startMax} 日`
    }
    if (form.endDay < 1 || form.endDay > endMax) {
      return `结束日期不合法，${form.endMonth} 月最多 ${endMax} 日`
    }
  }
  return null
}

async function submitForm(): Promise<void> {
  const error = validateForm()
  if (error) {
    ElMessage.error(error)
    return
  }
  saving.value = true
  try {
    const payload = buildPayload()
    if (editingId.value) {
      await updateFestival(editingId.value, payload)
      showSuccess('节日已更新')
    } else {
      await createFestival(payload)
      showSuccess('节日已新增')
    }
    dialogVisible.value = false
    await loadList()
  } catch (err) {
    // 后端会返回「名称已存在」「与某节日重叠重合」等具体原因，直接透出
    showError(getErrorMessage(err))
  } finally {
    saving.value = false
  }
}

/** 关闭 / 启用：关闭只影响用户端判定，不清空背景图绑定 */
async function toggleEnabled(row: FestivalRow): Promise<void> {
  const next = row.enabled
  try {
    await setFestivalEnabled(row.id, next)
    showSuccess(next ? '节日已启用' : '节日已关闭（保留背景图绑定）')
  } catch (error) {
    row.enabled = !next
    showError(getErrorMessage(error))
  }
}

async function removeFestival(row: FestivalRow): Promise<void> {
  const bound = row.bound_backgrounds ?? 0
  const tip =
    bound > 0
      ? `删除后，绑定该节日的 ${bound} 张背景图将自动划归到「无节日」通用图，是否继续？`
      : '删除后该节日不再参与用户端节日判定，是否继续？'
  try {
    await ElMessageBox.confirm(tip, `删除节日「${row.name}」`, {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消',
    })
  } catch (error) {
    // 用户取消或关闭弹窗，不做任何处理
    if (error === 'cancel' || error === 'close') return
    showError(getErrorMessage(error))
    return
  }

  try {
    const result = await deleteFestival(row.id)
    const unbound = result?.unboundBackgrounds ?? 0
    showSuccess(unbound > 0 ? `节日已删除，${unbound} 张背景图已划归「无节日」` : '节日已删除')
    await loadList()
  } catch (error) {
    showError(getErrorMessage(error))
  }
}

onMounted(loadList)
</script>
<template>
  <RoutePageShell
    title="节日管理"
    eyebrow="Festivals"
    subtitle="维护节日及其日期区间；背景图可绑定节日，节日到期或删除后背景图会自动回到「无节日」通用图。"
  >
    <template #actions>
      <el-button type="primary" data-test="create-festival" @click="openDialog()">新增节日</el-button>
    </template>

    <section class="glass-panel panel" v-loading="loading">
      <el-alert
        class="tip"
        type="info"
        :closable="false"
        show-icon
        title="节日之间不可重叠重合；起始月大于结束月表示跨年区间（如新年 12 月 → 次年 2 月）。"
      />
      <el-table :data="list" row-key="id" empty-text="还没有任何节日，点击右上角新增">
        <el-table-column label="节日名称" min-width="160">
          <template #default="{ row }">
            <span class="festival-name" :class="{ 'is-off': !row.enabled }">{{ row.name }}</span>
          </template>
        </el-table-column>

        <el-table-column label="日期类型" width="110">
          <template #default="{ row }">
            <el-tag
              :type="row.mode === 'month' ? 'success' : 'warning'"
              size="small"
              data-test="row-mode"
            >
              {{ row.mode === 'month' ? '月份式' : '日期式' }}
            </el-tag>
          </template>
        </el-table-column>

        <el-table-column label="日期区间" min-width="200">
          <template #default="{ row }">
            <span class="range-text" data-test="row-range">{{ rangeText(row) }}</span>
          </template>
        </el-table-column>

        <el-table-column label="绑定背景图" width="120">
          <template #default="{ row }">
            <span class="bound-count" data-test="row-bound">{{ row.bound_backgrounds ?? 0 }} 张</span>
          </template>
        </el-table-column>

        <el-table-column label="启用" width="90">
          <template #default="{ row }">
            <el-switch v-model="row.enabled" data-test="row-enabled" @change="toggleEnabled(row)" />
          </template>
        </el-table-column>

        <el-table-column label="操作" width="170">
          <template #default="{ row }">
            <el-button size="small" data-test="row-edit" @click="openDialog(row)">修改</el-button>
            <el-button
              type="danger"
              plain
              size="small"
              data-test="row-delete"
              @click="removeFestival(row)"
            >
              删除
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </section>

    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="520px" data-test="festival-dialog">
      <el-form label-width="88px">
        <el-form-item label="节日名称">
          <el-input
            v-model="form.name"
            maxlength="64"
            show-word-limit
            placeholder="例如：新年"
            data-test="form-name"
          />
        </el-form-item>

        <el-form-item label="日期类型">
          <el-radio-group v-model="form.mode" data-test="form-mode">
            <el-radio-button value="month">月份式</el-radio-button>
            <el-radio-button value="date">日期式</el-radio-button>
          </el-radio-group>
        </el-form-item>

        <!-- 月份式：只选起止月份，落库自动展开为该月第一天至该月最后一天 -->
        <template v-if="form.mode === 'month'">
          <el-form-item label="起始月份">
            <el-select v-model="form.startMonth" data-test="form-start-month">
              <el-option v-for="opt in MONTH_OPTIONS" :key="opt.value" :value="opt.value" :label="opt.label" />
            </el-select>
          </el-form-item>
          <el-form-item label="结束月份">
            <el-select v-model="form.endMonth" data-test="form-end-month">
              <el-option v-for="opt in MONTH_OPTIONS" :key="opt.value" :value="opt.value" :label="opt.label" />
            </el-select>
          </el-form-item>
        </template>

        <!-- 日期式：精确到某月某日 -->
        <template v-else>
          <el-form-item label="起始日期">
            <div class="date-row">
              <el-select v-model="form.startMonth" data-test="form-start-month" @change="clampDays">
                <el-option v-for="opt in MONTH_OPTIONS" :key="opt.value" :value="opt.value" :label="opt.label" />
              </el-select>
              <el-select v-model="form.startDay" data-test="form-start-day">
                <el-option
                  v-for="opt in dayOptionsFor(form.startMonth)"
                  :key="opt.value"
                  :value="opt.value"
                  :label="opt.label"
                />
              </el-select>
            </div>
          </el-form-item>
          <el-form-item label="结束日期">
            <div class="date-row">
              <el-select v-model="form.endMonth" data-test="form-end-month" @change="clampDays">
                <el-option v-for="opt in MONTH_OPTIONS" :key="opt.value" :value="opt.value" :label="opt.label" />
              </el-select>
              <el-select v-model="form.endDay" data-test="form-end-day">
                <el-option
                  v-for="opt in dayOptionsFor(form.endMonth)"
                  :key="opt.value"
                  :value="opt.value"
                  :label="opt.label"
                />
              </el-select>
            </div>
          </el-form-item>
        </template>

        <el-form-item label="保存区间">
          <span class="range-preview" data-test="form-preview">{{ rangePreview }}</span>
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" data-test="form-submit" @click="submitForm">
          保存
        </el-button>
      </template>
    </el-dialog>
  </RoutePageShell>
</template>

<style scoped>
.panel {
  padding: 1rem;
}

.tip {
  margin-bottom: 0.75rem;
}

.festival-name {
  font-weight: 600;
}

/* 已关闭的节日淡化显示，便于一眼区分生效状态 */
.festival-name.is-off {
  color: var(--barc-text-soft);
  text-decoration: line-through;
}

.range-text,
.bound-count {
  color: var(--barc-text-soft);
}

/**
 * 日期式下的「月份 + 日」输入行。
 *
 * 修复说明：日期式曾出现「起始 / 结束日期的下拉框里看不到任何文字，只剩一个箭头」。
 * 原因：el-form-item 的内容区 `.el-form-item__content` 本身是 flex 容器，
 *      本行未显式撑满时会被收缩到内容固有宽度（约 44px/个）；
 *      而 el-select 的选中文本 `.el-select__placeholder` 是绝对定位元素，不参与固有宽度计算，
 *      于是 `.el-select__selection` 被压成 0 宽，文字被 overflow 整体裁掉。
 * 处理：本行显式 flex: 1 撑满内容区，两个下拉各自 flex: 1 平分宽度（flex-basis 覆盖 el-select 的 width: 100%），
 *      确保「1 月 / 1 日」等选中文本始终可见。
 */
.date-row {
  display: flex;
  gap: 0.5rem;
  flex: 1;
  min-width: 0;
}

.date-row :deep(.el-select) {
  flex: 1;
  min-width: 0;
}

.range-preview {
  color: var(--barc-accent-strong);
  font-weight: 600;
}
</style>