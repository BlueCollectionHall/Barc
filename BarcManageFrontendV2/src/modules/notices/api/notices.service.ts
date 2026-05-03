import { http } from '@/shared/api/http'
import type { PageRequest, PageResult } from '@/shared/types/api'
import type { NoticeDraft, NoticeRecord } from '@/shared/types/notice'

export function fetchNoticesByPage(payload: PageRequest): Promise<PageResult<NoticeRecord>> {
  return http.post<PageResult<NoticeRecord>>('/notice/notices_by_page', payload)
}

export function fetchNotice(noticeId: string): Promise<NoticeRecord> {
  return http.get<NoticeRecord>('/notice/only', {
    params: { notice_id: noticeId },
  })
}

export function createNotice(payload: NoticeDraft): Promise<string> {
  return http.post<string>('/notice/upload', payload)
}

export function updateNotice(payload: NoticeRecord): Promise<string> {
  return http.put<string>('/notice/update', payload)
}

export function deleteNotice(noticeId: string): Promise<string> {
  return http.delete<string>('/notice/delete', {
    params: { notice_id: noticeId },
  })
}
