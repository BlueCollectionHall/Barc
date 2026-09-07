import type { ResponseImpl } from "@/interfaces/ResponseImpl.ts";
import { baseHttp } from "@/utils/https.ts";

/** 公开接口返回的单条背景图（URL + 时段 + 节日） */
export interface BackgroundItem {
  url: string;
  time_period: string | null;
  festival: string | null;
}

type BackgroundMap = Record<string, BackgroundItem[]>;

let cache: BackgroundMap | null = null;

/** 拉取所有「启用」的背景图（按模块分组，每项已解析为 COS 签名 URL 并携带场景标签），带缓存 */
export async function fetchBackgrounds(): Promise<BackgroundMap> {
  if (cache) {
    return cache;
  }
  try {
    const response = await baseHttp.get("/api/background/all");
    const data: ResponseImpl = response.data;
    if (data.code === 0 && data.data && typeof data.data === "object") {
      cache = data.data as BackgroundMap;
      return cache;
    }
  } catch {
    // 网络异常时兜底为空，组件回退到写死图片
  }
  return {};
}

/** 获取某个模块的启用背景图 URL 列表（兼容旧用法，只取 url） */
export async function fetchModuleBackgrounds(module: string): Promise<string[]> {
  const items = await fetchModuleBackgroundItems(module);
  return items.map((item) => item.url);
}

/** 获取某个模块的启用背景图元数据列表（URL + 时段 + 节日） */
export async function fetchModuleBackgroundItems(module: string): Promise<BackgroundItem[]> {
  const map = await fetchBackgrounds();
  return map[module] ?? [];
}

// ============ 场景选择（纯函数，便于单元测试） ============

/** 时段常量 */
export const TIME_PERIOD = {
  DAY: "day",
  EVENTING: "eventing",
  NIGHT: "night",
} as const;

/** 节日常量 */
export const FESTIVAL = {
  NEW_YEAR: "newyear",
} as const;

/**
 * 依据当前时间解析出「时段 + 节日」场景
 * 时段：0-6 点夜晚、6-17 点白天、17-19 点傍晚、其余夜晚（与旧 fallback 保持一致）
 * 节日：11月-次年2月（getMonth() 0 基：10为11月、0为1月、1为2月）视为新年前后
 */
export function resolveCurrentScene(date: Date): {
  time_period: string | null;
  festival: string | null;
} {
  const hour = date.getHours();
  let time_period: string | null;
  if (hour < 6) {
    time_period = TIME_PERIOD.NIGHT;
  } else if (hour < 17) {
    time_period = TIME_PERIOD.DAY;
  } else if (hour < 19) {
    time_period = TIME_PERIOD.EVENTING;
  } else {
    time_period = TIME_PERIOD.NIGHT;
  }

  const month = date.getMonth();
  const festival = month >= 10 || month <= 1 ? FESTIVAL.NEW_YEAR : null;

  return { time_period, festival };
}

/**
 * 依据当前场景从启用图池中随机选一张：
 * - 节日季：优先节日图，否则用非节日通用图；非节日季：只用非节日通用图
 * - 时段优先：选池内匹配当前时段的图（含未标注时段的通用图），无匹配则退回整个选池
 * - random 参数可注入以便测试（默认 Math.random）
 */
export function pickBackgroundByScene(
  items: BackgroundItem[],
  date: Date,
  random: () => number = Math.random,
): string | null {
  if (!items || items.length === 0) {
    return null;
  }
  const { time_period, festival } = resolveCurrentScene(date);

  // 节日季优先节日图；否则只用非节日通用图
  const festivalPool = items.filter((item) => item.festival === festival);
  const generalPool = items.filter((item) => item.festival == null);
  const basePool = festival ? (festivalPool.length > 0 ? festivalPool : generalPool) : generalPool;
  if (basePool.length === 0) {
    return null;
  }

  // 优先匹配当前时段的图（含未标注时段的通用图），无匹配退化为整个选池
  const timePool = basePool.filter((item) => !item.time_period || item.time_period === time_period);
  const pool = timePool.length > 0 ? timePool : basePool;

  const index = Math.floor(random() * pool.length);
  return pool[index].url;
}
