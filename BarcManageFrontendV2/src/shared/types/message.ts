export interface MessageRecord {
  id: string
  author: string
  author_name: string
  author_nickname: string
  content: string
  created_at: string | null
}

export interface MessageUpdatePayload {
  message_id: string
  content: string
  reason: string
}

export interface MessageBatchDeletePayload {
  message_ids: string[]
  reason: string
}
