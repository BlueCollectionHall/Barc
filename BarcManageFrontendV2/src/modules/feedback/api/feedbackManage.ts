import { http } from '@/shared/api/http'

export type FeedbackType = 'USER' | 'BUG' | 'SUGGESTION' | 'OTHER'
export type FeedbackStatus = 'PENDING' | 'PROCESSING' | 'COMPLETED' | 'REJECTED'

export interface FeedbackRecord {
  id: string
  target_id: string
  author: string
  content: string
  email: string
  echo: string | null
  type: FeedbackType
  status: FeedbackStatus | string
  created_at: string
  updated_at: string
}

export function getFeedbacksByType(type: FeedbackType): Promise<FeedbackRecord[]> {
  return http.get<FeedbackRecord[]>('/feedback/feedbacks_by_type', {
    params: { type, is_manager: true },
  })
}

export function updateFeedbackStatus(
  feedbackId: string,
  type: FeedbackType,
  status: Exclude<FeedbackStatus, 'PENDING'>,
  echo: string,
): Promise<string> {
  return http.put<string>('/feedback/update', {
    id: feedbackId,
    type,
    status,
    echo,
  })
}
