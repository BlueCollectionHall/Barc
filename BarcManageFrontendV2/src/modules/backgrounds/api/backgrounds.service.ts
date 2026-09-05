import { http } from '@/shared/api/http'

export interface BackgroundModuleOption {
  value: string
  label: string
}

export interface BackgroundImageRecord {
  id: string
  module: string
  object_key: string
  filename: string | null
  sort_order: number
  enabled: boolean
  created_by: string | null
  created_at: string | null
  updated_at: string | null
  deleted_at: string | null
  url?: string | null
}

export interface BackgroundOrderItem {
  id: string
  sort_order: number
}

export function fetchBackgroundModules(): Promise<BackgroundModuleOption[]> {
  return http.get<BackgroundModuleOption[]>('/api/background/modules')
}

export function fetchBackgroundList(module: string): Promise<BackgroundImageRecord[]> {
  return http.get<BackgroundImageRecord[]>('/api/background/admin/list', {
    params: { module },
  })
}

export function uploadBackground(file: File, module: string): Promise<BackgroundImageRecord> {
  const form = new FormData()
  form.append('file', file)
  form.append('module', module)
  return http.post<BackgroundImageRecord>('/api/background/upload', form)
}

export function reorderBackground(items: BackgroundOrderItem[]): Promise<unknown> {
  return http.put<unknown>('/api/background/reorder', items)
}

export function setBackgroundEnabled(id: string, enabled: boolean): Promise<BackgroundImageRecord> {
  return http.put<BackgroundImageRecord>(`/api/background/${id}/enabled`, undefined, {
    params: { enabled },
  })
}

export function deleteBackground(id: string): Promise<unknown> {
  return http.delete<unknown>(`/api/background/${id}`)
}
