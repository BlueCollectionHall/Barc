import { http } from '@/shared/api/http'
import { fetchEnabledFestivals } from '@/modules/system/api/festivals.service'

export interface BackgroundModuleOption {
  value: string
  label: string
  /** 该模块是否启用「时段 + 节日」场景化筛选（仅首页 true，其余纯随机） */
  sceneEnabled: boolean
}

/** 时段：day/eventing/night，null 表示任意时段 */
export type BackgroundTimePeriod = 'day' | 'eventing' | 'night'

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

/**
 * 管理端「节日」下拉选项（null 表示无节日通用图）。
 *
 * 节日已改为「系统管理 → 节日管理」中的动态数据，这里不再写死枚举：
 * 只列出当前「启用中」的节日，已被关闭的节日不允许再被绑定。
 * 拉取失败时退化为只含「无」选项，保证页面仍可操作。
 */
export async function fetchFestivalSceneOptions(): Promise<BackgroundSceneOption[]> {
  const options: BackgroundSceneOption[] = [{ value: null, label: '无' }]
  try {
    const festivals = await fetchEnabledFestivals()
    for (const festival of festivals) {
      options.push({ value: festival.id, label: festival.name })
    }
  } catch {
    // 节日接口异常不应阻断背景图页面的其他功能
  }
  return options
}

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
