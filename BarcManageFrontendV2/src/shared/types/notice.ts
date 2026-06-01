export interface NoticeRecord {
  id: string
  title: string
  content: string
  author: string
  created_at: string | null
  updated_at: string | null
}

export interface NoticeDraft {
  id?: string
  title: string
  content: string
  author?: string
}
