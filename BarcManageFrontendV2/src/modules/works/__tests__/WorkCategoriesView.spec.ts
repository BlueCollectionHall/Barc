import { flushPromises, mount } from '@vue/test-utils'
import type { VueWrapper } from '@vue/test-utils'
import { ElMessage, ElMessageBox } from 'element-plus'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { defineComponent, h } from 'vue'

import type { CategoryNode } from '@/modules/works/api/categories.service'

const {
  fetchCategoryTree,
  createCategory,
  updateCategory,
  moveCategorySort,
  deleteCategory,
  showError,
  showSuccess,
} = vi.hoisted(() => ({
  fetchCategoryTree: vi.fn(),
  createCategory: vi.fn(),
  updateCategory: vi.fn(),
  moveCategorySort: vi.fn(),
  deleteCategory: vi.fn(),
  showError: vi.fn(),
  showSuccess: vi.fn(),
}))

// 只替换网络层，validateCategoryForm / deleteBlockedReason 等纯函数保留真实实现
vi.mock('@/modules/works/api/categories.service', async () => {
  const actual = await vi.importActual<typeof import('@/modules/works/api/categories.service')>(
    '@/modules/works/api/categories.service',
  )
  return {
    ...actual,
    fetchCategoryTree,
    createCategory,
    updateCategory,
    moveCategorySort,
    deleteCategory,
  }
})

vi.mock('@/shared/utils/message', () => ({
  showError,
  showSuccess,
  showInfo: vi.fn(),
  showWarning: vi.fn(),
}))

import WorkCategoriesView from '@/modules/works/views/WorkCategoriesView.vue'

/**
 * 全局 setup 的 ElDialog stub 不渲染 footer 插槽，这里自定义一个会渲染 footer 的 stub，
 * 否则对话框里的「保存 / 保存并继续新增」按钮不存在，无法测试提交链路。
 */
const DialogStub = defineComponent({
  name: 'ElDialog',
  props: {
    modelValue: { type: Boolean, default: false },
    title: { type: String, default: '' },
  },
  setup(props, { slots }) {
    return () =>
      props.modelValue
        ? h('section', { 'data-testid': 'dialog-stub' }, [
            h('header', props.title),
            slots.default?.(),
            h('footer', slots.footer?.()),
          ])
        : null
  },
})

const mountOptions = {
  global: {
    stubs: {
      'el-dialog': DialogStub,
    },
  },
}

/** 构造分类节点 */
function node(
  id: string,
  parentId: string | null,
  name: string,
  level: number,
  sort: number,
  boundWorks = 0,
  childrenCount = 0,
  children: CategoryNode[] | null = null,
): CategoryNode {
  return {
    id,
    parent_id: parentId,
    name,
    level,
    sort,
    bound_works: boundWorks,
    children_count: childrenCount,
    children,
  }
}

/** 测试数据：游戏（有子分类） + 鼠标指针（可删除） */
const fixture: CategoryNode[] = [
  node('game', null, '游戏', 1, 1, 2, 1, [node('mod', 'game', '模组', 2, 1, 0, 0)]),
  node('mouse_theme', null, '鼠标指针', 1, 2, 0, 0),
]

type Queryable = {
  exists(): boolean
  findAll(selector: string): Array<{ text(): string; trigger(event: string): Promise<void>; attributes(): Record<string, string> }>
  find(selector: string): {
    exists(): boolean
    text(): string
    attributes(): Record<string, string>
    setValue(value: string): Promise<void>
    trigger(event: string): Promise<void>
  }
  findComponent(selector: string): { exists(): boolean; vm: { $emit(event: string, ...args: unknown[]): void } }
}

/** 在给定范围内按文案取按钮 */
function findButton(root: Queryable, label: string, index = 0): { trigger(event: string): Promise<void> } {
  const matches = root.findAll('button').filter((candidate) => candidate.text() === label)
  const button = matches[index]
  if (!button) {
    throw new Error(`Unable to find button: ${label}#${index}`)
  }
  return button
}

/** 取当前弹窗容器 */
function dialog(wrapper: ReturnType<typeof mount>): Queryable {
  return wrapper.find('[data-testid="dialog-stub"]') as unknown as Queryable
}

/** 写入输入框值（优先组件实例，取不到时退化为 DOM setValue） */
async function setInputValue(
  wrapper: ReturnType<typeof mount>,
  selector: string,
  value: string,
): Promise<void> {
  const component = wrapper.findComponent(selector)
  if (component.exists()) {
    await (component as unknown as { setValue(value: string): Promise<void> }).setValue(value)
    return
  }
  await wrapper.find(selector).setValue(value)
}

/* eslint-disable @typescript-eslint/no-explicit-any */
let confirmSpy: any
let errorSpy: any
/* eslint-enable @typescript-eslint/no-explicit-any */

describe('WorkCategoriesView', () => {
  beforeEach(() => {
    confirmSpy = vi.spyOn(ElMessageBox, 'confirm').mockResolvedValue('confirm' as never)
    errorSpy = vi.spyOn(ElMessage, 'error').mockImplementation(() => ({}) as never)
    fetchCategoryTree.mockResolvedValue(fixture)
    createCategory.mockResolvedValue(node('new_child', 'game', '新分类', 2, 1, 0, 0))
    updateCategory.mockResolvedValue(node('game', null, '游戏（改）', 1, 1, 2, 1))
    moveCategorySort.mockResolvedValue([])
    deleteCategory.mockResolvedValue('mouse_theme')
  })

  afterEach(() => {
    confirmSpy.mockRestore()
    errorSpy.mockRestore()
  })

  it('加载并渲染层级树、层级标签与统计', async () => {
    const wrapper = mount(WorkCategoriesView, mountOptions)

    await flushPromises()

    expect(fetchCategoryTree).toHaveBeenCalledTimes(1)
    const text = wrapper.text()
    expect(text).toContain('游戏')
    expect(text).toContain('模组')
    expect(text).toContain('鼠标指针')
    expect(text).toContain('当前共 3 个分类')
    expect(text).toContain('L2')
    expect(showError).not.toHaveBeenCalled()
  })

  it('列表加载失败时给出错误提示', async () => {
    fetchCategoryTree.mockRejectedValue(new Error('boom'))
    mount(WorkCategoriesView, mountOptions)

    await flushPromises()

    expect(showError).toHaveBeenCalled()
  })

  it('新增顶级分类：parent_id 提交 null', async () => {
    const wrapper = mount(WorkCategoriesView, mountOptions)
    await flushPromises()

    await wrapper.find('[data-test="create-root-category"]').trigger('click')
    await flushPromises()

    const box = dialog(wrapper)
    expect(box.exists()).toBe(true)
    await setInputValue(wrapper, '[data-test="form-id"]', 'new_top')
    await setInputValue(wrapper, '[data-test="form-name"]', '新顶级分类')

    await findButton(box, '保存').trigger('click')
    await flushPromises()

    expect(createCategory).toHaveBeenCalledWith({
      id: 'new_top',
      name: '新顶级分类',
      parent_id: null,
      sort: null,
    })
    expect(showSuccess).toHaveBeenCalledWith('分类已新增')
    expect(fetchCategoryTree).toHaveBeenCalledTimes(2)
  })

  it('新增子级 + 保存并继续新增：对话框保持打开且父分类切到刚创建的分类', async () => {
    const wrapper = mount(WorkCategoriesView, mountOptions)
    await flushPromises()

    const addChildButtons = wrapper.findAll('[data-test="row-add-child"]')
    await addChildButtons[0]!.trigger('click')
    await flushPromises()

    const box = dialog(wrapper)
    await setInputValue(wrapper, '[data-test="form-id"]', 'new_child')
    await setInputValue(wrapper, '[data-test="form-name"]', '新分类')
    // el-radio-group 的 data-test 落在组件根元素上，这里显式取组件实例触发 v-model 变化
    const continueModeGroup = wrapper.findComponent(
      '[data-test="form-continue-mode"]',
    ) as unknown as VueWrapper
    continueModeGroup.vm.$emit('update:modelValue', 'child')
    await flushPromises()

    await findButton(box, '保存并继续新增').trigger('click')
    await flushPromises()

    expect(createCategory).toHaveBeenCalledWith({
      id: 'new_child',
      name: '新分类',
      parent_id: 'game',
      sort: null,
    })
    // 连续录入：弹窗仍在，ID 与名称已清空，父分类切到刚创建的 new_child
    expect(dialog(wrapper).exists()).toBe(true)
    expect((wrapper.find('[data-test="form-id"]').element as HTMLInputElement).value).toBe('')
    expect((wrapper.find('[data-test="form-name"]').element as HTMLInputElement).value).toBe('')
    // el-input 会把 $attrs 透传到内部 input，因此 data-test 取到的就是 input 元素
    expect(wrapper.findAllComponents({ name: 'ElSelect' })[0]!.props('modelValue')).toBe('new_child')
    // 再次保存会因 ID 为空被前端拦截，证明表单确已清空（而不是又创建了一条）
    await findButton(box, '保存').trigger('click')
    await flushPromises()
    expect(createCategory).toHaveBeenCalledTimes(1)
    expect(showError).toHaveBeenCalled()
  })

  it('编辑：ID 只读，提交名称与父级', async () => {
    const wrapper = mount(WorkCategoriesView, mountOptions)
    await flushPromises()

    await wrapper.findAll('[data-test="row-edit"]')[0]!.trigger('click')
    await flushPromises()

    const box = dialog(wrapper)
    // 主键不可修改：ID 输入框被禁用（data-test 落在内部 input 上）
    expect(wrapper.find('[data-test="form-id"]').attributes('disabled')).toBeDefined()
    // 名称回填后修改
    expect((wrapper.find('[data-test="form-name"]').element as HTMLInputElement).value).toBe('游戏')
    await setInputValue(wrapper, '[data-test="form-name"]', '游戏（改）')

    await findButton(box, '保存').trigger('click')
    await flushPromises()

    expect(updateCategory).toHaveBeenCalledWith('game', {
      name: '游戏（改）',
      parent_id: null,
      sort: 1,
    })
    expect(showSuccess).toHaveBeenCalledWith('分类已更新')
  })

  it('表单校验：ID 非法时直接拦截，不发起请求', async () => {
    const wrapper = mount(WorkCategoriesView, mountOptions)
    await flushPromises()

    await wrapper.find('[data-test="create-root-category"]').trigger('click')
    await flushPromises()

    await setInputValue(wrapper, '[data-test="form-id"]', 'Bad-ID')
    await setInputValue(wrapper, '[data-test="form-name"]', '非法分类')
    await findButton(dialog(wrapper), '保存').trigger('click')
    await flushPromises()

    expect(createCategory).not.toHaveBeenCalled()
    expect(showError).toHaveBeenCalled()
  })

  it('删除：有关联的分类按钮禁用，无关联的分类才可删除', async () => {
    const wrapper = mount(WorkCategoriesView, mountOptions)
    await flushPromises()

    /** 按行文案定位删除按钮，避免依赖表格行的渲染顺序 */
    function deleteButtonOf(keyword: string) {
      const row = wrapper.findAll('tr').find((candidate) => candidate.text().includes(keyword))
      if (!row) {
        throw new Error(`Unable to find row: ${keyword}`)
      }
      const button = row.findAll('button').find((candidate) => candidate.text() === '删除')
      if (!button) {
        throw new Error(`Unable to find delete button in row: ${keyword}`)
      }
      return button
    }

    // 游戏下有子分类 → 禁用；鼠标指针无任何关联 → 可删除
    expect(deleteButtonOf('游戏').attributes('disabled')).toBeDefined()
    expect(deleteButtonOf('鼠标指针').attributes('disabled')).toBeUndefined()

    await deleteButtonOf('鼠标指针').trigger('click')
    await flushPromises()

    expect(confirmSpy).toHaveBeenCalled()
    expect(deleteCategory).toHaveBeenCalledWith('mouse_theme')
    expect(showSuccess).toHaveBeenCalledWith('分类已删除')
  })

  it('删除：确认框取消时不发起删除', async () => {
    confirmSpy.mockRejectedValue('cancel')
    const wrapper = mount(WorkCategoriesView, mountOptions)
    await flushPromises()

    const row = wrapper.findAll('tr').find((candidate) => candidate.text().includes('鼠标指针'))
    const button = row?.findAll('button').find((candidate) => candidate.text() === '删除')
    await button?.trigger('click')
    await flushPromises()

    expect(deleteCategory).not.toHaveBeenCalled()
  })

  it('上移 / 下移调用排序接口并在失败时透出提示', async () => {
    const wrapper = mount(WorkCategoriesView, mountOptions)
    await flushPromises()

    await wrapper.findAll('[data-test="row-move-up"]')[0]!.trigger('click')
    await flushPromises()
    expect(moveCategorySort).toHaveBeenCalledWith('game', 'up')

    // 行顺序为 游戏 / 模组 / 鼠标指针，第 2 行是「模组」
    await wrapper.findAll('[data-test="row-move-down"]')[1]!.trigger('click')
    await flushPromises()
    expect(moveCategorySort).toHaveBeenLastCalledWith('mod', 'down')

    moveCategorySort.mockRejectedValue(new Error('已经是同级的第一个，无法上移'))
    await wrapper.findAll('[data-test="row-move-up"]')[0]!.trigger('click')
    await flushPromises()
    expect(showError).toHaveBeenCalled()
  })
})
