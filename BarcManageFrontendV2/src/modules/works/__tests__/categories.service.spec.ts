import { beforeEach, describe, expect, it, vi } from 'vitest'

// 只替换网络层，纯函数（建树、校验、删除阻断）保留真实实现
const { get, post, put, remove } = vi.hoisted(() => ({
  get: vi.fn(),
  post: vi.fn(),
  put: vi.fn(),
  remove: vi.fn(),
}))

vi.mock('@/shared/api/http', () => ({
  http: {
    get,
    post,
    put,
    delete: remove,
  },
}))

import {
  CATEGORY_ID_PATTERN,
  CATEGORY_NAME_MAX_LENGTH,
  buildParentOptions,
  createCategory,
  deleteBlockedReason,
  deleteCategory,
  fetchCategoryTree,
  findCategoryNode,
  flattenCategoryTree,
  moveCategorySort,
  updateCategory,
  validateCategoryForm,
  type CategoryNode,
} from '@/modules/works/api/categories.service'

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

const mouseTheme = node('mobile_theme', 'beautify', '手机主题', 2, 1, 0, 3, [
  node('mobile_theme_huawei', 'mobile_theme', '华为手机主题', 3, 2),
  node('mobile_theme_vivo', 'mobile_theme', 'VIVO手机主题', 3, 2),
  node('mobile_theme_xiaomi', 'mobile_theme', '小米手机主题', 3, 2),
])
const beautify = node('beautify', null, '美化', 1, 2, 3, 1, [mouseTheme])
const model = node('3d_model', null, '模型', 1, 1, 29, 0)
const tree: CategoryNode[] = [model, beautify]

describe('categories.service 请求封装', () => {
  beforeEach(() => {
    get.mockReset()
    post.mockReset()
    put.mockReset()
    remove.mockReset()
  })

  it('拉取分类树', () => {
    fetchCategoryTree()
    expect(get).toHaveBeenCalledWith('/api/category/manage/list')
  })

  it('新增分类', () => {
    const payload = { id: 'new_top', name: '新分类', parent_id: null, sort: null }
    createCategory(payload)
    expect(post).toHaveBeenCalledWith('/api/category/manage/create', payload)
  })

  it('修改分类按 id 拼接路径', () => {
    const payload = { name: '手机主题', parent_id: 'beautify', sort: 1 }
    updateCategory('mobile_theme', payload)
    expect(put).toHaveBeenCalledWith('/api/category/manage/mobile_theme', payload)
  })

  it('上移 / 下移通过 query 传方向', () => {
    moveCategorySort('beautify', 'up')
    expect(put).toHaveBeenCalledWith('/api/category/manage/beautify/sort', undefined, {
      params: { direction: 'up' },
    })
  })

  it('删除分类', () => {
    deleteCategory('mouse_theme')
    expect(remove).toHaveBeenCalledWith('/api/category/manage/mouse_theme')
  })
})

describe('分类树工具函数', () => {
  it('深度优先拍平，保持展示顺序', () => {
    expect(flattenCategoryTree(tree).map((item) => item.id)).toEqual([
      '3d_model',
      'beautify',
      'mobile_theme',
      'mobile_theme_huawei',
      'mobile_theme_vivo',
      'mobile_theme_xiaomi',
    ])
  })

  it('按 id 查找节点，找不到返回 null', () => {
    expect(findCategoryNode(tree, 'mobile_theme_vivo')?.name).toBe('VIVO手机主题')
    expect(findCategoryNode(tree, 'not_exists')).toBeNull()
  })

  it('父分类选项：首项为顶级分类，其余按层级缩进', () => {
    const options = buildParentOptions(tree)

    expect(options[0]).toEqual({ value: null, label: '顶级分类' })
    // 3 个层级依次缩进：顶级无缩进、二级 1 个全角空格、三级 2 个
    expect(options[1]).toEqual({ value: '3d_model', label: '模型' })
    expect(options[2]).toEqual({ value: 'beautify', label: '美化' })
    expect(options[3]).toEqual({ value: 'mobile_theme', label: '　手机主题' })
    expect(options[4]).toEqual({ value: 'mobile_theme_huawei', label: '　　华为手机主题' })
  })

  it('父分类选项：编辑时排除自身与全部后代，避免形成环', () => {
    const options = buildParentOptions(tree, 'beautify').map((option) => option.value)

    expect(options).toContain('3d_model')
    expect(options).not.toContain('beautify')
    expect(options).not.toContain('mobile_theme')
    expect(options).not.toContain('mobile_theme_huawei')
  })

  it('父分类选项：excludeId 不存在时不影响全部选项', () => {
    const options = buildParentOptions(tree, 'ghost').map((option) => option.value)

    expect(options).toHaveLength(flattenCategoryTree(tree).length + 1)
  })
})

describe('分类表单校验', () => {
  it('新增时 id 必填且必须符合规范', () => {
    expect(validateCategoryForm({ isCreate: true, id: '', name: '名称' })).toBe('请填写分类 ID')
    expect(validateCategoryForm({ isCreate: true, id: 'Bad-ID', name: '名称' })).toContain('小写英文')
    expect(validateCategoryForm({ isCreate: true, id: '中文', name: '名称' })).toContain('小写英文')
    expect(CATEGORY_ID_PATTERN.test('mobile_theme_huawei')).toBe(true)
    expect(CATEGORY_ID_PATTERN.test('Mobile')).toBe(false)
  })

  it('编辑时不校验 id（主键不可修改）', () => {
    expect(validateCategoryForm({ isCreate: false, id: '', name: '名称' })).toBeNull()
  })

  it('名称必填且不超过 20 字', () => {
    expect(validateCategoryForm({ isCreate: false, id: 'x', name: '   ' })).toBe('请填写分类名称')
    expect(
      validateCategoryForm({ isCreate: false, id: 'x', name: '一'.repeat(CATEGORY_NAME_MAX_LENGTH + 1) }),
    ).toContain('不能超过 20')
    expect(validateCategoryForm({ isCreate: false, id: 'x', name: '一'.repeat(CATEGORY_NAME_MAX_LENGTH) })).toBeNull()
  })
})

describe('删除阻断原因', () => {
  it('有子分类时阻断并给出数量', () => {
    expect(deleteBlockedReason(beautify)).toContain('1 个子分类')
  })

  it('有作品关联时阻断并给出数量', () => {
    expect(deleteBlockedReason(model)).toContain('29 个作品')
  })

  it('无关联时允许删除', () => {
    expect(deleteBlockedReason(node('mouse_theme', 'beautify', '鼠标指针', 2, 1, 0, 0))).toBeNull()
  })

  it('统计字段为 null 时按 0 处理', () => {
    expect(
      deleteBlockedReason({
        id: 'x',
        parent_id: null,
        name: '空统计',
        level: 1,
        sort: 1,
        bound_works: null,
        children_count: null,
        children: null,
      }),
    ).toBeNull()
  })
})
