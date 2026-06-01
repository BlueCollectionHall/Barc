import { http } from '@/shared/api/http'
import type { PageRequest, PageResult } from '@/shared/types/api'
import type { MessageBatchDeletePayload, MessageRecord, MessageUpdatePayload } from '@/shared/types/message'

export function fetchMessagesByPage(payload: PageRequest): Promise<PageResult<MessageRecord>> {
  return http.post<PageResult<MessageRecord>>('/comment/message_board/admin/list', payload)
}

export function updateMessage(payload: MessageUpdatePayload): Promise<string> {
  return http.put<string>('/comment/message_board/admin/update', payload)
}

export function batchDeleteMessages(payload: MessageBatchDeletePayload): Promise<string> {
  return http.delete<string>('/comment/message_board/admin/batch_delete', { data: payload })
}

export function deleteMessage(messageId: string): Promise<string> {
  return http.delete<string>('/comment/message_board/delete', {
    params: { message_id: messageId },
  })
}
