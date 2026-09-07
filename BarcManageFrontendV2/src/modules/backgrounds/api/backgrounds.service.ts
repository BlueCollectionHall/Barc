import { http } from '@/shared/api/http'

export interface BackgroundModuleOption {
  value: string
  label: string
}

/** 时段：day/eventing/night，null 表示任意时段 */
export type BackgroundTimePeriod = 'day' | 'eventing' | 'night'
/** 节日：newyear */
export type BackgroundFestival = 'newyear'

export interface BackgroundSceneOption {
  value: string | null
  label: string
}

/** 管理端「时段」下拉选项（null 表示任意时段） */
export const BACKGROUND_TIME_PERIOD_OPTIONS: BackgroundSceneOption[] = [
  { value: null, label: '任意时段' },
  { value: 'day', label: '白天' },
  { value: 'eventing', label: '傍晚' },
  { value: 'night', label: '夜晚' },
]

/** 管理端「节日」下拉选项（null 表示非节日通用图） */
export const BACKGROUND_FESTIVAL_OPTIONS: BackgroundSceneOption[] = [
  { value: null, label: '无' },
  { value: 'newyear', label: '新年' },
]

export interface BackgroundImageRecord {
  id: string
  module: string
  object_key: string
  filename: string | null
  sort_order: number
  enabled: boolean
  time_period: string | null
  festival: string | null
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

/** 更新背景图的场景标签（时段 + 节日） */
export function updateBackgroundScene(
  id: string,
  timePeriod: string | null,
  festival: string | null,
): Promise<BackgroundImageRecord> {
  return http.put<BackgroundImageRecord>(`/api/background/${id}/scene`, {
    time_period: timePeriod,
    festival,
  })
}

export function deleteBackground(id: string): Promise<unknown> {
  return http.delete<unknown>(`/api/background/${id}`)
}
