import { http } from '@/shared/api/http'
import type { PageResult } from './school'

export interface Club {
  id: string
  school?: string
  cn_name: string
  jp_name?: string
  kr_name?: string
  en_name: string
  logo?: string
  bg?: string
}

export function fetchClubList(schoolId: string, keyword: string, page: number, size: number): Promise<PageResult<Club>> {
  const params: Record<string, any> = { keyword, page, size }
  if (schoolId) params.school_id = schoolId
  return http.get<PageResult<Club>>('/api/club/list', { params })
}

export function fetchClubById(id: string): Promise<Club> {
  return http.get<Club>(`/api/club/${id}`)
}

export function createClub(payload: Club, schoolId: string): Promise<Club> {
  return http.post<Club>('/api/club', payload, { params: { school_id: schoolId } })
}

export function updateClub(id: string, payload: Partial<Club>): Promise<Club> {
  return http.put<Club>(`/api/club/${id}`, payload)
}

export function deleteClub(id: string): Promise<unknown> {
  return http.delete(`/api/club/${id}`)
}

export function checkClubIdAvailable(id: string): Promise<boolean> {
  return http.get<boolean>('/api/club/check_id_available', { params: { id } })
}
