import type { EntityDraft } from './entityDraft'
import { http } from '@/shared/api/http'
import type { PageResult } from './school'

export interface Student {
  // 原始 key 只用于写入，签名 URL 只用于展示。
  avatar_square_key?: string
  avatar_square_url?: string
  avatar_rectangle_key?: string
  avatar_rectangle_url?: string
  body_image_key?: string
  body_image_url?: string
  id: string
  cn_name: string
  jp_name?: string
  kr_name?: string
  en_name: string
  introduce?: string
  avatar_square?: string
  avatar_rectangle?: string
  body_image?: string
  school?: string
  club: string
}

export function fetchStudentList(clubId: string, keyword: string, page: number, size: number): Promise<PageResult<Student>> {
  const params: Record<string, string | number> = { keyword, page, size }
  if (clubId) params.club_id = clubId
  return http.get<PageResult<Student>>('/api/student/list', { params })
}

export function fetchStudentById(id: string): Promise<Student> {
  return http.get<Student>(`/api/student/${id}`)
}

export function createStudent(payload: EntityDraft, clubId: string): Promise<Student> {
  return http.post<Student>('/api/student', payload, { params: { club_id: clubId } })
}

export function updateStudent(id: string, payload: Partial<EntityDraft>, clubId?: string): Promise<Student> {
  return http.put<Student>(`/api/student/${id}`, payload, { params: clubId ? { club_id: clubId } : undefined })
}

export function deleteStudent(id: string): Promise<unknown> {
  return http.delete(`/api/student/${id}`)
}

export function updateStudentAvatarSquare(id: string, value: string): Promise<string> {
  return http.put<string>(`/api/student/${id}/avatar_square`, null, { params: { value } })
}

export function updateStudentAvatarRectangle(id: string, value: string): Promise<string> {
  return http.put<string>(`/api/student/${id}/avatar_rectangle`, null, { params: { value } })
}

export function updateStudentBodyImage(id: string, value: string): Promise<string> {
  return http.put<string>(`/api/student/${id}/body_image`, null, { params: { value } })
}

export function checkStudentIdAvailable(id: string): Promise<boolean> {
  return http.get<boolean>('/api/student/check_id_available', { params: { id } })
}
