import { http } from '@/shared/api/http'
import type { PageResult } from '@/shared/types/api'

/** 作品、评论及通用反馈页共用分页请求，服务端负责分类治理权限。 */
export function fetchFeedbackPage<T>(type: string, page = 1, status?: string): Promise<PageResult<T>> {
  return http.get<PageResult<T>>('/feedback/page', { params: { type, is_manager: true, page, size: 20, status } })
}
