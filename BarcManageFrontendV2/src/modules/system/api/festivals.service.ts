import { http } from '@/shared/api/http'

/**
 * 节日（系统管理 → 节日管理）
 *
 * 设计约定：
 * 1、不论「月份式」（某月第一天至某月最后一天）还是「日期式」（精确某月某日到某月某日），
 *    后端持久化的本质都是「某月某日 → 某月某日」，因此前端只提交 start_month/start_day/end_month/end_day；
 * 2、「月份式 / 日期式」只是表单录入方式，由前端根据区间是否为「整月」自动推导回显；
 * 3、允许起始月日 > 结束月日，表示该区间跨年环绕（如新年 12-01 → 02-28）。
 */

/** 表单录入方式：月份式 / 日期式 */
export type FestivalDateMode = 'month' | 'date'

export interface FestivalRecord {
  id: string
  name: string
  start_month: number
  start_day: number
  end_month: number
  end_day: number
  /** 统一索引：月份 * 32 + 日（后端派生列，用于比较排序） */
  start_index: number
  end_index: number
  /** false 表示已关闭：保留数据与背景图绑定，仅不参与用户端节日判定 */
  enabled: boolean
  created_by: string | null
  created_at: string | null
  updated_at: string | null
  /** 绑定该节日的背景图数量（删除前提示影响面） */
  bound_backgrounds: number | null
}

export interface FestivalPayload {
  name: string
  start_month: number
  start_day: number
  end_month: number
  end_day: number
}

export interface FestivalDeleteResult {
  id: string
  unboundBackgrounds: number
}

/** 各月最大天数；2 月按闰年上限 29 天处理，与后端放宽策略保持一致 */
export const MONTH_MAX_DAY: Record<number, number> = {
  1: 31,
  2: 29,
  3: 31,
  4: 30,
  5: 31,
  6: 30,
  7: 31,
  8: 31,
  9: 30,
  10: 31,
  11: 30,
  12: 31,
}

/** 月份下拉选项 */
export const MONTH_OPTIONS: Array<{ value: number; label: string }> = Array.from(
  { length: 12 },
  (_, index) => ({ value: index + 1, label: `${index + 1} 月` }),
)

/** 某月的日期下拉选项（按该月最大天数生成） */
export function dayOptionsFor(month: number): Array<{ value: number; label: string }> {
  const max = MONTH_MAX_DAY[month] ?? 31
  return Array.from({ length: max }, (_, index) => ({
    value: index + 1,
    label: `${index + 1} 日`,
  }))
}

/**
 * 推导某区间的录入方式：
 * 起始为该月第一天、结束为该月最后一天时视为「月份式」，否则为「日期式」。
 */
export function detectDateMode(record: Pick<FestivalRecord, 'start_day' | 'end_day' | 'end_month'>): FestivalDateMode {
  const isFirstDay = record.start_day === 1
  const isLastDay = record.end_day === (MONTH_MAX_DAY[record.end_month] ?? 31)
  return isFirstDay && isLastDay ? 'month' : 'date'
}

/** 月份式：把「起始月 / 结束月」展开为完整的月日区间（起始取 1 号，结束取该月最后一天） */
export function monthRangeOf(startMonth: number, endMonth: number): {
  start_month: number
  start_day: number
  end_month: number
  end_day: number
} {
  return {
    start_month: startMonth,
    start_day: 1,
    end_month: endMonth,
    end_day: MONTH_MAX_DAY[endMonth] ?? 31,
  }
}

/** 格式化展示区间，跨年时在结束月日前标注「次年」 */
export function formatFestivalRange(
  record: Pick<FestivalRecord, 'start_month' | 'start_day' | 'end_month' | 'end_day'>,
): string {
  const pad = (value: number): string => String(value).padStart(2, '0')
  const start = `${pad(record.start_month)}月${pad(record.start_day)}日`
  const end = `${pad(record.end_month)}月${pad(record.end_day)}日`
  const crossYear = record.start_month > record.end_month
  return crossYear ? `${start} ~ 次年${end}` : `${start} ~ ${end}`
}

/** 管理端：节日列表（含已关闭） */
export function fetchFestivals(): Promise<FestivalRecord[]> {
  return http.get<FestivalRecord[]>('/api/festival/admin/list')
}

/**
 * 管理端：全部节日（含已关闭），供背景图的「节日」下拉与筛选使用
 *
 * 与 fetchFestivals 的区别：不附带背景图绑定数量统计，避免背景图页面为了下拉选项触发 N 次统计查询。
 */
export function fetchFestivalOptions(): Promise<FestivalRecord[]> {
  return http.get<FestivalRecord[]>('/api/festival/options')
}

/** 管理端：新增节日 */
export function createFestival(payload: FestivalPayload): Promise<FestivalRecord> {
  return http.post<FestivalRecord>('/api/festival/admin/create', payload)
}

/** 管理端：修改节日 */
export function updateFestival(id: string, payload: FestivalPayload): Promise<FestivalRecord> {
  return http.put<FestivalRecord>(`/api/festival/admin/${id}`, payload)
}

/** 管理端：关闭 / 启用节日（关闭保留数据与背景图绑定） */
export function setFestivalEnabled(id: string, enabled: boolean): Promise<FestivalRecord> {
  return http.put<FestivalRecord>(`/api/festival/admin/${id}/enabled`, undefined, {
    params: { enabled },
  })
}

/** 管理端：删除节日（后端自动把绑定背景图划归「无节日」） */
export function deleteFestival(id: string): Promise<FestivalDeleteResult> {
  return http.delete<FestivalDeleteResult>(`/api/festival/admin/${id}`)
}
