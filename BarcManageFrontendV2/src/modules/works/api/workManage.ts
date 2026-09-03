import { http } from '@/shared/api/http'
import type { PageRequest, PageResult } from '@/shared/types/api'

// 类型定义
export interface WorkRecord {
  id: string; title: string; description: string
  content: string; banner_image: string; cover_image: string
  view_count: number; like_count: number
  author: string; author_nickname: string | null; author_display?: string | null; uploader: string | null
  is_claim: boolean; status: string; student: string
  review_status?: 'PENDING' | 'APPROVED' | 'REJECTED' | null
  review_reason?: string | null
  reviewer_uuid?: string | null
  review_submitted_at?: string | null
  reviewed_at?: string | null
  created_at: string; content_updated_at?: string | null; updated_at: string
}

export interface ClaimRecord {
  id: string; work_id: string; work_title: string
  applicant_uuid: string; applicant_nickname: string; applicant_username: string
  created_at: string
}

export interface ComplaintRecord {
  id: string; target_id: string; author: string
  content: string; email: string; echo: string | null; type: 'WORK'; status: string
  created_at: string; updated_at: string
}

export interface OperationLogRecord {
  id: string; work_id: string; operator_uuid: string
  operation_type: string; detail: string; created_at: string
}

export interface WorkEditDetail {
  work: WorkRecord
  cover_image_url: string
  content_image_urls: string[]
  uploader_nickname: string
  author_display: string
  school_name: string
  club_name: string
  student_name: string
}

/** 管理端作品管理 API - 所有接口需要 SEC_MAINTAINER 权限 */

export function getWorkListManage(payload: PageRequest): Promise<PageResult<WorkRecord>> {
  return http.post<PageResult<WorkRecord>>('/api/work/manage/list', payload)
}

export function getWorkDetailManage(workId: string): Promise<WorkRecord> {
  return http.get<WorkRecord>('/api/work/manage/detail', { params: { work_id: workId } })
}

/** 获取作品编辑详情（含封面/内容图签名URL、作者/收录者信息、学园/部团/学生名） */
export function getWorkEditDetail(workId: string): Promise<WorkEditDetail> {
  return http.get<WorkEditDetail>('/api/work/manage/edit-detail', { params: { work_id: workId } })
}

export function updateWorkStatus(workId: string, status: string, remark?: string): Promise<string> {
  return http.put<string>('/api/work/manage/status', { work_id: workId, status, remark })
}

export function updateWorkContent(model: Record<string, unknown>): Promise<string> {
  return http.put<string>('/api/work/manage/update', model)
}

export function getClaimsList(): Promise<ClaimRecord[]> {
  return http.get<ClaimRecord[]>('/api/work/manage/claims')
}

export function approveClaim(claimId: string, approved: boolean): Promise<string> {
  return http.post<string>('/api/work/manage/claim/approve', { claim_id: claimId, approved })
}

export function revokeClaim(workId: string): Promise<string> {
  return http.post<string>('/api/work/manage/claim/revoke', { work_id: workId })
}

export function assignAuthor(workId: string, authorUuid: string): Promise<string> {
  return http.post<string>('/api/work/manage/claim/assign', { work_id: workId, author_uuid: authorUuid })
}

export function getClaimHistory(workId: string): Promise<ClaimRecord[]> {
  return http.get<ClaimRecord[]>('/api/work/manage/claim/history', { params: { work_id: workId } })
}

export function getComplaintsList(): Promise<ComplaintRecord[]> {
  return http.get<ComplaintRecord[]>('/feedback/feedbacks_by_type', {
    params: { type: 'WORK', is_manager: true },
  })
}

export type WorkReviewStatus = 'PENDING' | 'APPROVED' | 'REJECTED'

/** 获取作品上传审核队列；后端使用 SEC_MAINTAINER 严格权限位鉴权。 */
export function getWorkReviewList(
  payload: PageRequest,
  reviewStatus: WorkReviewStatus = 'PENDING',
): Promise<PageResult<WorkRecord>> {
  return http.post<PageResult<WorkRecord>>('/api/work/manage/reviews', payload, {
    params: { review_status: reviewStatus },
  })
}

/** 静默提交审核结论，拒绝时 reason 必填；该接口不会发送邮件。 */
export function reviewWork(workId: string, approved: boolean, reason?: string): Promise<string> {
  return http.post<string>('/api/work/manage/review', {
    work_id: workId,
    approved,
    reason,
  })
}

export function processComplaint(feedbackId: string, status: string, echo?: string): Promise<string> {
  return http.put<string>('/feedback/update', {
    id: feedbackId,
    type: 'WORK',
    status,
    echo,
  })
}

export function getOperationLogs(payload: PageRequest): Promise<PageResult<OperationLogRecord>> {
  return http.post<PageResult<OperationLogRecord>>('/api/work/manage/logs', payload)
}

// 搜索作品（远程搜索用）
export function searchWorks(keyword: string): Promise<WorkRecord[]> {
  return http.get<WorkRecord[]>('/api/work/manage/search-work', { params: { keyword } })
}

// 搜索用户（远程搜索用）
export function searchUsers(keyword: string): Promise<any[]> {
  return http.get<any[]>('/api/work/manage/search-user', { params: { keyword } })
}
