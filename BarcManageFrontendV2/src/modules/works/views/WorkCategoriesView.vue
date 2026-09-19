<script setup lang="ts">
/**
 * 作品分类管理（作品管理 → 作品分类管理）
 *
 * 功能：
 * 1、树形展示全部分类（支持多层级），并显示每个分类的「直接作品数」与「子分类数」；
 * 2、新增：可指定父分类（不选即为顶级分类）；ID 由管理员手填，创建后不可修改；
 * 3、编辑：可改名称、排序与父分类（移动父分类由后端级联修正子树层级，并做防环校验）；
 * 4、排序：同级上移 / 下移（后端会先把同级 sort 规范化为 1..n 再交换相邻项）；
 * 5、删除：有子分类或有作品关联时按钮禁用，后端也会二次兜底拒绝；
 * 6、生产环境的分类体系目前为空，本页是恢复它的唯一入口，因此新增对话框提供
 *    「保存并继续新增」，并可在「同级 / 该分类的子级」之间切换，便于快速录入整棵树。
 */

import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'

import RoutePageShell from '@/app/components/RoutePageShell.vue'
import {
  CATEGORY_NAME_MAX_LENGTH,
  buildParentOptions,
  createCategory,
  deleteBlockedReason,
  deleteCategory,
  fetchCategoryTree,
  moveCategorySort,
  updateCategory,
  validateCategoryForm,
  type CategoryNode,
  type CategorySortDirection,
} from '@/modules/works/api/categories.service'
import { getErrorMessage } from '@/shared/types/api'
import { showError, showSuccess } from '@/shared/utils/message'

/** 分类树：直接使用后端返回的 children 嵌套结构 */
const tree = ref<CategoryNode[]>([])
const loading = ref(false)
const saving = ref(false)

const dialogVisible = ref(false)
/** 当前编辑的分类 id；为空表示新增 */
const editingId = ref<string | null>(null)
/** 连续新增时的落点：同级（沿用当前父分类）/ 子级（挂到刚保存的分类下） */
const continueMode = ref<'sibling' | 'child'>('sibling')

/** 表单模型：sort 留空表示由后端排到同级末尾 */
const form = reactive({
  id: '',
  name: '',
  parent_id: null as string | null,
  sort: null as number | null,
})

const dialogTitle = computed(() => (editingId.value ? '修改分类' : '新增分类'))

/** 父分类下拉项：编辑时排除自身与全部后代，避免形成环 */
const parentOptions = computed(() => buildParentOptions(tree.value, editingId.value))

/** 全部分类总数（含各级子分类），用于页头提示 */
const totalCount = computed(() => parentOptions.value.length - 1)

/**
 * 加载分类树
 */
async function loadList(): Promise<void> {
  loading.value = true
  try {
    tree.value = await fetchCategoryTree()
  } catch (error) {
    showError(getErrorMessage(error))
  } finally {
    loading.value = false
  }
}

/**
 * 打开新增对话框
 *
 * @param parentId 新增位置的父分类；null 表示顶级分类
 */
function openCreateDialog(parentId: string | null): void {
  editingId.value = null
  form.id = ''
  form.name = ''
  form.parent_id = parentId
  form.sort = null
  continueMode.value = 'sibling'
  dialogVisible.value = true
}

/**
 * 打开编辑对话框
 */
function openEditDialog(row: CategoryNode): void {
  editingId.value = row.id
  form.id = row.id
  form.name = row.name
  form.parent_id = row.parent_id
  form.sort = row.sort ?? null
  dialogVisible.value = true
}

/**
 * 提交表单
 *
 * @param keepOpen 保存后是否保持对话框打开以便连续录入（仅新增时有意义）
 */
async function submitForm(keepOpen: boolean): Promise<void> {
  const isCreate = !editingId.value
  const error = validateCategoryForm({ isCreate, id: form.id, name: form.name })
  if (error) {
    showError(error)
    return
  }

  saving.value = true
  try {
    const payload = {
      name: form.name.trim(),
      parent_id: form.parent_id,
      sort: form.sort,
    }
    if (!isCreate && editingId.value) {
      await updateCategory(editingId.value, payload)
      showSuccess('分类已更新')
      dialogVisible.value = false
    } else {
      const created = await createCategory({ ...payload, id: form.id.trim() })
      showSuccess('分类已新增')
      if (keepOpen) {
        // 连续录入：同级沿用当前父分类；子级则把父分类切到刚创建的分类
        form.parent_id = continueMode.value === 'child' ? created.id : form.parent_id
        form.id = ''
        form.name = ''
        form.sort = null
      } else {
        dialogVisible.value = false
      }
    }
    await loadList()
  } catch (err) {
    // 后端会返回「分类 ID 已存在」「同一层级下已存在同名分类」等具体原因，直接透出
    showError(getErrorMessage(err))
  } finally {
    saving.value = false
  }
}

/**
 * 同级上移 / 下移
 */
async function changeSort(row: CategoryNode, direction: CategorySortDirection): Promise<void> {
  try {
    await moveCategorySort(row.id, direction)
    await loadList()
  } catch (error) {
    // 「已经是同级的第一个 / 最后一个」也走这里，按提示展示即可
    showError(getErrorMessage(error))
  }
}

/**
 * 删除分类
 *
 * 有子分类或有作品关联时不允许删除（外键是 ON DELETE CASCADE，放开会连坐删除子树与作品关联）。
 */
async function removeCategory(row: CategoryNode): Promise<void> {
  const blocked = deleteBlockedReason(row)
  if (blocked) {
    showError(blocked)
    return
  }
  try {
    await ElMessageBox.confirm(
      `删除分类「${row.name}」后不可恢复，是否继续？`,
      '删除分类',
      { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' },
    )
  } catch (error) {
    // 用户取消或关闭弹窗，不做任何处理
    if (error === 'cancel' || error === 'close') {
      return
    }
    ElMessage.warning(getErrorMessage(error))
    return
  }

  try {
    await deleteCategory(row.id)
    showSuccess('分类已删除')
    await loadList()
  } catch (error) {
    showError(getErrorMessage(error))
  }
}

onMounted(loadList)
</script>

<template>
  <RoutePageShell
    title="作品分类管理"
    eyebrow="Work Categories"
    subtitle="维护作品分类的多层级结构；用户端目前只展示与选择顶级分类，子分类用于后续归档与扩展。"
  >
    <template #actions>
      <el-button type="primary" data-test="create-root-category" @click="openCreateDialog(null)">
        新增顶级分类
      </el-button>
    </template>

    <section class="glass-panel panel" v-loading="loading">
      <el-alert
        class="tip"
        type="info"
        :closable="false"
        show-icon
        title="分类 ID 由管理员手填（小写英文 / 数字 / 下划线）且创建后不可修改；删除前必须先清空其子分类与作品关联。"
      />
      <p class="count-hint" data-test="category-total">当前共 {{ totalCount }} 个分类</p>

      <el-table
        :data="tree"
        row-key="id"
        default-expand-all
        :tree-props="{ children: 'children' }"
        empty-text="还没有任何分类，点击右上角「新增顶级分类」开始录入"
        data-test="category-table"
      >
        <el-table-column label="分类名称" min-width="220">
          <template #default="{ row }">
            <span class="category-name" data-test="row-name">{{ row.name }}</span>
            <el-tag class="level-tag" size="small" type="info" data-test="row-level">
              L{{ row.level }}
            </el-tag>
          </template>
        </el-table-column>

        <el-table-column label="分类 ID" min-width="180">
          <template #default="{ row }">
            <code class="category-id" data-test="row-id">{{ row.id }}</code>
          </template>
        </el-table-column>

        <el-table-column label="排序" width="80">
          <template #default="{ row }">
            <span class="sort-text" data-test="row-sort">{{ row.sort }}</span>
          </template>
        </el-table-column>

        <el-table-column label="直接作品" width="100">
          <template #default="{ row }">
            <span data-test="row-works">{{ row.bound_works ?? 0 }}</span>
          </template>
        </el-table-column>

        <el-table-column label="子分类" width="90">
          <template #default="{ row }">
            <span data-test="row-children">{{ row.children_count ?? 0 }}</span>
          </template>
        </el-table-column>

        <el-table-column label="操作" width="330">
          <template #default="{ row }">
            <el-button size="small" data-test="row-add-child" @click="openCreateDialog(row.id)">
              新增子级
            </el-button>
            <el-button size="small" data-test="row-edit" @click="openEditDialog(row)">修改</el-button>
            <el-button
              size="small"
              data-test="row-move-up"
              @click="changeSort(row, 'up')"
            >
              ↑
            </el-button>
            <el-button
              size="small"
              data-test="row-move-down"
              @click="changeSort(row, 'down')"
            >
              ↓
            </el-button>
            <el-button
              type="danger"
              plain
              size="small"
              data-test="row-delete"
              :disabled="deleteBlockedReason(row) !== null"
              :title="deleteBlockedReason(row) ?? '删除该分类'"
              @click="removeCategory(row)"
            >
              删除
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </section>

    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="560px" data-test="category-dialog">
      <el-form label-width="96px">
        <el-form-item label="分类 ID">
          <el-input
            v-model="form.id"
            :disabled="editingId !== null"
            maxlength="50"
            placeholder="例如：mobile_theme_huawei"
            data-test="form-id"
          />
          <span v-if="editingId" class="field-hint">分类 ID 创建后不可修改</span>
        </el-form-item>

        <el-form-item label="分类名称">
          <el-input
            v-model="form.name"
            :maxlength="CATEGORY_NAME_MAX_LENGTH"
            show-word-limit
            placeholder="例如：手机主题"
            data-test="form-name"
          />
        </el-form-item>

        <el-form-item label="父分类">
          <el-select v-model="form.parent_id" placeholder="不选即为顶级分类" data-test="form-parent">
            <el-option
              v-for="option in parentOptions"
              :key="option.value ?? '__root__'"
              :value="option.value"
              :label="option.label"
            />
          </el-select>
        </el-form-item>

        <el-form-item label="排序">
          <el-input-number v-model="form.sort" :min="0" :step="1" data-test="form-sort" />
          <span class="field-hint">留空时排在所属层级的末尾</span>
        </el-form-item>

        <el-form-item v-if="!editingId" label="连续录入">
          <el-radio-group v-model="continueMode" data-test="form-continue-mode">
            <el-radio-button value="sibling">继续新增同级</el-radio-button>
            <el-radio-button value="child">继续新增子级</el-radio-button>
          </el-radio-group>
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button
          v-if="!editingId"
          :loading="saving"
          data-test="form-submit-continue"
          @click="submitForm(true)"
        >
          保存并继续新增
        </el-button>
        <el-button type="primary" :loading="saving" data-test="form-submit" @click="submitForm(false)">
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

.count-hint {
  margin: 0 0 0.75rem;
  color: var(--barc-text-soft);
  font-size: 0.9rem;
}

.category-name {
  font-weight: 600;
}

.level-tag {
  margin-left: 0.4rem;
}

.category-id {
  color: var(--barc-accent-strong);
  font-family: 'Consolas', 'Menlo', monospace;
}

.sort-text {
  color: var(--barc-text-soft);
}

.field-hint {
  margin-left: 0.5rem;
  color: var(--barc-text-soft);
  font-size: 0.82rem;
}
</style>
