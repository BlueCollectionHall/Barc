import { http } from '@/shared/api/http'

/**
 * 友情链接（管理端「系统 → 友情链接」）
 *
 * 设计约定：
 * 1、logo 走「先传后落」流程：先调 uploadFriendLinkLogo 拿到 objectKey，
 *    再随表单一并提交（logo_key 字段）；数据库只存 key，列表接口返回签名好的 logo_url；
 * 2、排序用上移 / 下移按钮，一次移动只影响相邻两条，因此**没有批量排序接口**；
 *    移动成功后后端回传交换后的完整列表，前端直接替换本地状态，不必再发一次列表请求；
 * 3、只有标题（title）与链接（url）必填，logo_key 与 intro 可为空。
 */

/** 移动方向：与后端 moveService 的 direction 取值一致 */
export type FriendLinkMoveDirection = 'up' | 'down'

export interface FriendLinkRecord {
  id: string
  title: string
  url: string
  /** COS 对象 key（非 URL），未设置 logo 时为 null */
  logo_key: string | null
  intro: string | null
  /** 全表连续序号，从 1 开始；删除后会留下空洞，但相对次序正确 */
  sort_order: number
  /** 预留列：本期无启用/停用界面，公开接口也不按它过滤 */
  enabled: boolean
  created_by: string | null
  created_at: string | null
  updated_at: string | null
  /** 后端签名的完整 logo URL（非数据库列，logo_key 为空时为 null） */
  logo_url?: string | null
}

/** 新增 / 修改的提交体 */
export interface FriendLinkPayload {
  title: string
  url: string
  logo_key: string | null
  intro: string | null
}

/** 管理端：友链列表（按 sort_order 升序，含签名 logo_url） */
export function fetchFriendLinks(): Promise<FriendLinkRecord[]> {
  return http.get<FriendLinkRecord[]>('/api/friend-link/admin/list')
}

/**
 * 管理端：上传 logo（只上传不落库）
 *
 * @returns COS objectKey，直接写入表单的 logo_key 字段
 */
export function uploadFriendLinkLogo(file: File): Promise<string> {
  const form = new FormData()
  form.append('file', file)
  return http.post<string>('/api/friend-link/upload', form)
}

/** 管理端：新增友链（sort_order 由后端取当前最大值 + 1） */
export function createFriendLink(payload: FriendLinkPayload): Promise<FriendLinkRecord> {
  return http.post<FriendLinkRecord>('/api/friend-link/admin/create', payload)
}

/** 管理端：修改友链（logo 变更时后端会把旧对象交 GC 清理） */
export function updateFriendLink(id: string, payload: FriendLinkPayload): Promise<FriendLinkRecord> {
  return http.put<FriendLinkRecord>(`/api/friend-link/admin/${id}`, payload)
}

/**
 * 管理端：上移 / 下移
 *
 * 后端在同一事务内与相邻友链交换 sort_order；已在最前 / 最后时返回错误提示。
 * 成功时返回交换后的完整列表。
 */
export function moveFriendLink(
  id: string,
  direction: FriendLinkMoveDirection,
): Promise<FriendLinkRecord[]> {
  return http.put<FriendLinkRecord[]>(`/api/friend-link/admin/${id}/move`, undefined, {
    params: { direction },
  })
}

/** 管理端：删除友链（软删记录 + logo 对象交 GC 清理） */
export function deleteFriendLink(id: string): Promise<unknown> {
  return http.delete<unknown>(`/api/friend-link/admin/${id}`)
}
