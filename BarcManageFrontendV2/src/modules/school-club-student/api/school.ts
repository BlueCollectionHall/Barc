import type { EntityDraft } from './entityDraft'
import { http } from '@/shared/api/http'

export interface School {
  // 原始 key 只用于写入，签名 URL 只用于展示。
  logo_key?: string
  logo_url?: string
  beautify_logo_key?: string
  beautify_logo_url?: string
  bg_key?: string
  bg_url?: string
  id: string
  cn_name: string
  jp_name?: string
  kr_name?: string
  en_name: string
  introduce?: string
  logo?: string
  beautify_logo?: string
  bg?: string
}

export interface PageResult<T> {
  list: T[]
  total: number
}

export function fetchSchoolList(keyword: string, page: number, size: number): Promise<PageResult<School>> {
  return http.get<PageResult<School>>('/api/school/list', { params: { keyword, page, size } })
}

export function fetchSchoolById(id: string): Promise<School> {
  return http.get<School>(`/api/school/${id}`)
}

export function createSchool(payload: EntityDraft): Promise<School> {
  return http.post<School>('/api/school', payload)
}

export function updateSchool(id: string, payload: Partial<EntityDraft>): Promise<School> {
  return http.put<School>(`/api/school/${id}`, payload)
}

export function deleteSchool(id: string): Promise<unknown> {
  return http.delete(`/api/school/${id}`)
}

export function checkSchoolIdAvailable(id: string): Promise<boolean> {
  return http.get<boolean>('/api/school/check_id_available', { params: { id } })
}
