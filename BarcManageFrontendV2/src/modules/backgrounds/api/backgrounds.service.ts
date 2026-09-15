import { http } from '@/shared/api/http'
import { fetchFestivalOptions } from '@/modules/system/api/festivals.service'

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
  /**
   * 该选项对应的节日是否已关闭。
   * 关闭的节日仍会出现在下拉里（关闭只表示「不参与用户端判定」，绑定关系要保留），
   * 但会用「（已关闭）」标注出来，避免馆长误以为它仍在生效。
   */
  disabled?: boolean
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
 * 节日来自「系统管理 → 节日管理」的动态数据，这里列出全部节日（含已关闭）：
 * 关闭的节日必须继续可选 —— 否则这些背景图上的节日绑定会在页面上「消失」，
 * 且馆长连「时段」都无法再修改（提交时会连带已关闭的节日 ID 一起被后端拒绝）。
 *
 * 拉取失败时退化为只含「无」选项，保证页面仍可操作。
 */
export async function fetchFestivalSceneOptions(): Promise<BackgroundSceneOption[]> {
  const options: BackgroundSceneOption[] = [{ value: null, label: '无' }]
  try {
    const festivals = await fetchFestivalOptions()
    for (const festival of festivals) {
      options.push({
        value: festival.id,
        label: festival.enabled ? festival.name : `${festival.name}（已关闭）`,
        disabled: !festival.enabled,
      })
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
