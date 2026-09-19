import { http } from '@/shared/api/http'

/**
 * 作品分类管理（作品管理 → 作品分类管理）
 *
 * 设计约定（与后端 CategoryManageService 一一对应）：
 * 1、分类支持多层级（parent_id 自关联），level 由后端按父级派生，前端只提交 parent_id；
 * 2、id 由管理员手填（小写英文 / 数字 / 下划线），创建后不可修改 ——
 *    因为 work_category.category_id 的外键没有 ON UPDATE CASCADE，改主键会留下坏关联；
 * 3、排序用「上移 / 下移」按钮：后端会先把同级 sort 规范化为 1..n，再交换相邻两项；
 * 4、删除受约束：有子分类或有作品关联的分类会被后端拒绝（外键是 ON DELETE CASCADE，
 *    放开会连坐删除整棵子树与作品分类关联），前端据此禁用按钮并给出原因。
 */

/** 分类树节点（管理端返回结构） */
export interface CategoryNode {
  id: string
  parent_id: string | null
  name: string
  level: number
  sort: number
  /** 直接挂在本分类下的作品数（不含子分类） */
  bound_works: number | null
  /** 直接子分类数量 */
  children_count: number | null
  children: CategoryNode[] | null
}

/** 新增 / 编辑提交体 */
export interface CategoryPayload {
  /** 仅新增时提交；编辑时后端忽略该字段（主键不可修改） */
  id?: string
  name: string
  /** null 表示顶级分类 */
  parent_id: string | null
  /** 留空则由后端排到同级末尾 */
  sort?: number | null
}

/** 排序方向 */
export type CategorySortDirection = 'up' | 'down'

/** 父分类下拉项：value 为 null 表示顶级分类 */
export interface CategoryParentOption {
  value: string | null
  label: string
}

/** 分类 ID 规范：与后端 CategoryManageService 的校验保持一致 */
export const CATEGORY_ID_PATTERN = /^[a-z0-9_]{1,50}$/

/** 分类名称长度上限：与列宽 category.name varchar(20) 对齐 */
export const CATEGORY_NAME_MAX_LENGTH = 20

/** 管理端：分类树（含直接作品数与子分类数） */
export function fetchCategoryTree(): Promise<CategoryNode[]> {
  return http.get<CategoryNode[]>('/api/category/manage/list')
}

/** 管理端：新增分类 */
export function createCategory(payload: CategoryPayload): Promise<CategoryNode> {
  return http.post<CategoryNode>('/api/category/manage/create', payload)
}

/** 管理端：修改分类（含移动父分类） */
export function updateCategory(id: string, payload: CategoryPayload): Promise<CategoryNode> {
  return http.put<CategoryNode>(`/api/category/manage/${id}`, payload)
}

/** 管理端：同级上移 / 下移，返回该分类所属层级的排序结果 */
export function moveCategorySort(id: string, direction: CategorySortDirection): Promise<CategoryNode[]> {
  return http.put<CategoryNode[]>(`/api/category/manage/${id}/sort`, undefined, { params: { direction } })
}

/** 管理端：删除分类（仅无子分类且无作品关联时允许） */
export function deleteCategory(id: string): Promise<string> {
  return http.delete<string>(`/api/category/manage/${id}`)
}

/**
 * 在分类树中按 id 查找节点（深度优先）
 */
export function findCategoryNode(tree: CategoryNode[], id: string): CategoryNode | null {
  for (const node of tree) {
    if (node.id === id) {
      return node
    }
    const children = node.children ?? []
    if (children.length > 0) {
      const found = findCategoryNode(children, id)
      if (found) {
        return found
      }
    }
  }
  return null
}

/**
 * 把分类树按展示顺序拍平（深度优先）
 */
export function flattenCategoryTree(tree: CategoryNode[]): CategoryNode[] {
  const flat: CategoryNode[] = []
  const walk = (nodes: CategoryNode[]): void => {
    for (const node of nodes) {
      flat.push(node)
      const children = node.children ?? []
      if (children.length > 0) {
        walk(children)
      }
    }
  }
  walk(tree)
  return flat
}

/**
 * 收集某节点及其全部后代的 id
 */
function collectSelfAndDescendantIds(node: CategoryNode, bucket: Set<string>): void {
  bucket.add(node.id)
  const children = node.children ?? []
  for (const child of children) {
    collectSelfAndDescendantIds(child, bucket)
  }
}

/**
 * 构造「父分类」下拉选项。
 *
 * - 首项固定为「顶级分类」（value 为 null）；
 * - 编辑某个分类时传入 excludeId，其自身与全部后代会被排除 ——
 *   否则用户可以把分类挂到自己的后代下形成环，用户端建树将无限递归；
 * - 标签按层级缩进，便于在当前层级上直接看清归属。
 */
export function buildParentOptions(tree: CategoryNode[], excludeId?: string | null): CategoryParentOption[] {
  const blocked = new Set<string>()
  if (excludeId) {
    const target = findCategoryNode(tree, excludeId)
    if (target) {
      collectSelfAndDescendantIds(target, blocked)
    }
  }

  const options: CategoryParentOption[] = [{ value: null, label: '顶级分类' }]
  for (const node of flattenCategoryTree(tree)) {
    if (blocked.has(node.id)) {
      continue
    }
    const indent = '　'.repeat(Math.max(0, (node.level ?? 1) - 1))
    options.push({ value: node.id, label: `${indent}${node.name}` })
  }
  return options
}

/**
 * 表单校验：返回中文错误信息，通过返回 null。
 *
 * 与后端校验保持一致，先在前端拦一道，避免无意义的网络请求。
 */
export function validateCategoryForm(input: {
  isCreate: boolean
  id: string
  name: string
}): string | null {
  const id = input.id.trim()
  const name = input.name.trim()
  if (input.isCreate) {
    if (!id) {
      return '请填写分类 ID'
    }
    if (!CATEGORY_ID_PATTERN.test(id)) {
      return '分类 ID 只能使用小写英文、数字与下划线，长度不超过 50 个字符'
    }
  }
  if (!name) {
    return '请填写分类名称'
  }
  if (name.length > CATEGORY_NAME_MAX_LENGTH) {
    return `分类名称不能超过 ${CATEGORY_NAME_MAX_LENGTH} 个字符`
  }
  return null
}

/**
 * 删除按钮的阻断原因：返回 null 表示允许删除。
 *
 * 与后端 deleteService 的判定顺序保持一致（先子分类、后作品关联）。
 */
export function deleteBlockedReason(node: CategoryNode): string | null {
  const children = node.children_count ?? 0
  const works = node.bound_works ?? 0
  if (children > 0) {
    return `该分类下仍有 ${children} 个子分类，请先删除或移动这些子分类`
  }
  if (works > 0) {
    return `该分类下仍有 ${works} 个作品，请先调整这些作品的分类`
  }
  return null
}
