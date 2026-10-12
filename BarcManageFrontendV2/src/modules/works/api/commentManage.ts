import { http } from '@/shared/api/http'
import { fetchFeedbackPage } from '@/shared/api/feedback'
import type { PageResult } from '@/shared/types/api'

/** 管理端评论管理 API */

export interface ManageCommentRecord {
  id: string
  work_id: string
  author: string
  content: string
  created_at: string
  reply_count: number
  replies?: ManageReplyRecord[]
}

export interface ManageReplyRecord {
  id: string
  parent_id: string
  author: string
  reply_user: string | null
  content: string
  created_at: string
}

export interface ManageCommentComplaintRecord {
  id: string
  target_id: string
  author: string
  email: string
  content: string
  echo: string | null
  type: string
  status: string
  created_at: string
  updated_at: string
}

/** 管理分页允许检查隐藏作品；每页包含前五条回复预览。 */
export function getCommentsByWork(workId: string, page = 1): Promise<PageResult<ManageCommentRecord>> {
  return http.get('/comment/work/manage/page', { params: { work_id: workId, page, size: 10 } })
}

/** 完整回复按页读取，不再以预览条数冒充真实总数。 */
export function getManagementReplies(parentId: string, page: number): Promise<PageResult<ManageReplyRecord>> {
  return http.get('/comment/work/manage/replies/page', { params: { parent_id: parentId, page, size: 5 } })
}

/** 管理员删除评论 */
export function deleteWorkComment(commentId: string): Promise<string> {
  return http.delete<string>('/comment/work/delete_comment', { params: { comment_id: commentId } })
}

/** 管理员删除回复 */
export function deleteWorkReply(replyId: string): Promise<string> {
  return http.delete<string>('/comment/work/delete_reply', { params: { reply_id: replyId } })
}

/** 获取 COMMENT 类型的投诉列表 */
export function getCommentComplaints(page = 1): Promise<PageResult<ManageCommentComplaintRecord>> {
  return fetchFeedbackPage<ManageCommentComplaintRecord>('COMMENT', page)
}

/** 处理投诉（更新状态） */
export function processCommentComplaint(
  feedbackId: string,
  status: string,
  echo: string,
): Promise<string> {
  return http.put<string>('/feedback/update', {
    id: feedbackId,
    type: 'COMMENT',
    status,
    echo,
  })
}

/** 获取用户档案 */
export function getUserArchive(uuid: string): Promise<{ nickname: string; avatar: string | null } | null> {
  return http.get<{ nickname: string; avatar: string | null }>('/user/current_by_uuid', { params: { uuid } }).catch(() => null)
}

/** 管理取证内容使用受控入口，保持隐藏作品举报可处理。 */
export interface CommentContentResult {
  id: string
  content: string
  created_at: string
  type: 'comment' | 'reply'
  work_id?: string
  parent_id?: string
}

export function getCommentById(commentId: string): Promise<CommentContentResult | null> {
  return http.get<CommentContentResult>('/comment/work/manage/detail', { params: { comment_id: commentId } })
}
