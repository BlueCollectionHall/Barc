import { http } from '@/shared/api/http'

/** 管理端评论管理 API */

export interface ManageCommentRecord {
  id: string
  work_id: string
  author: string
  content: string
  created_at: string
  replyCount: number
}

export interface ManageReplyRecord {
  id: string
  parent_id: string
  author: string
  reply_user: string
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

/** 获取作品的全部评论（返回数组，每条包含嵌套回复） */
export function getCommentsByWork(workId: string): Promise<any[]> {
  return http.get<any[]>('/comment/work/comments_by_work', { params: { work_id: workId } })
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
export function getCommentComplaints(): Promise<ManageCommentComplaintRecord[]> {
  return http.get<ManageCommentComplaintRecord[]>('/feedback/feedbacks_by_type', {
    params: { type: 'COMMENT', is_manager: true },
  })
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
  return http.get<any>('/user/current_by_uuid', { params: { uuid } }).catch(() => null)
}

/** 根据评论/回复ID获取内容（兼容主评论和子回复） */
export interface CommentContentResult {
  id: string
  content: string
  created_at: string
  type: 'comment' | 'reply'
  work_id?: string
  parent_id?: string
}

export function getCommentById(commentId: string): Promise<CommentContentResult | null> {
  return http.get<CommentContentResult>('/comment/work/comment_by_id', { params: { comment_id: commentId } }).catch(() => null)
}
