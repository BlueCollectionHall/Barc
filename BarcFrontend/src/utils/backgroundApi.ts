import type { ResponseImpl } from "@/interfaces/ResponseImpl.ts";
import { baseHttp } from "@/utils/https.ts";

/** 公开接口返回的单条背景图（URL + 时段 + 节日） */
export interface BackgroundItem {
  url: string;
  time_period: string | null;
  /** 绑定的节日 ID，null 表示无节日通用图 */
  festival: string | null;
}

/**
 * 节日（来自管理端「系统管理 → 节日管理」）
 *
 * 日期区间统一为「某月某日 → 某月某日」：不论管理端用「月份式」还是「日期式」录入，
 * 后端持久化的都是月日对；start_month > end_month 表示该区间跨年环绕（如新年 12-01 → 02-28）。
 */
export interface FestivalItem {
  id: string;
  name: string;
  start_month: number;
  start_day: number;
  end_month: number;
  end_day: number;
  /** 统一索引：月份 * 32 + 日，与后端保持一致，便于比较排序 */
  start_index: number;
  end_index: number;
}

type BackgroundMap = Record<string, BackgroundItem[]>;

let cache: BackgroundMap | null = null;
let festivalCache: FestivalItem[] | null = null;

/**
 * 月日统一索引：月份 * 32 + 日
 *
 * 与后端 MonthDayRangeUtils.toIndex 保持一致：每月按 32 进位，
 * 保证不同月份的索引绝不会相撞，便于用整数直接比较。
 */
function monthDayIndex(month: number, day: number): number {
  return month * 32 + day;
}

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

/**
 * 拉取「启用中」的节日列表（公开接口，无需登录），带缓存。
 *
 * 节日由馆长在管理端维护，用户端不再写死「11 月 - 次年 2 月视为新年」，
 * 而是按这份数据动态判定当前时间属于哪个节日。
 * 网络异常时返回空数组，此时判定结果恒为无节日，背景图退化为通用图逻辑。
 */
export async function fetchFestivals(): Promise<FestivalItem[]> {
  if (festivalCache) {
    return festivalCache;
  }
  try {
    const response = await baseHttp.get("/api/festival/all");
    const data: ResponseImpl = response.data;
    if (data.code === 0 && Array.isArray(data.data)) {
      festivalCache = data.data as FestivalItem[];
      return festivalCache;
    }
  } catch {
    // 网络异常时兜底为空，等价于「当前无节日」
  }
  return [];
}

/**
 * 获取某个模块的启用背景图 URL 列表（兼容旧用法，只取 url）。
 *
 * ⚠️ 注意：此方法「只取 url、丢弃时段/节日标签」，不做任何场景筛选。
 * 当前仅「首页 home」启用场景化（走 fetchModuleBackgroundItems + pickBackgroundByScene），
 * 其余模块（sign / join / feedback / messageboard）均通过本方法纯随机抽取，
 * 即「按启用顺序随机轮播，不区分时段/节日」。
 *
 * 扩展点：若未来某模块要启用场景化，只需把对应组件改为
 * 调用 fetchModuleBackgroundItems(module) + pickBackgroundByScene(items, new Date()) 即可，
 * 同时将后端 BackgroundModuleEnum 中该模块的 sceneEnabled 置为 true。
 */
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

/**
 * 依据当前时间解析出「时段」场景
 * 时段：0-6 点夜晚、6-17 点白天、17-19 点傍晚、其余夜晚（与旧 fallback 保持一致）
 *
 * 注意：节日已改为动态数据，不再由此函数返回，请配合 resolveFestivalForDate 使用。
 */
export function resolveCurrentScene(date: Date): {
  time_period: string | null;
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

  return { time_period };
}

/**
 * 判断某一天是否落在节日区间内
 *
 * 算法与后端 MonthDayRangeUtils.containsDay 完全一致：
 * 普通区间取 [起始索引, 结束索引]；跨年区间（起始索引 > 结束索引）取
 * 「大于等于起始」或「小于等于结束」两段，即绕年一圈。
 *
 * 实现上用两次纯数值比较代替逐日遍历，因此可以安全地用于日期式与月份式两种区间。
 */
export function isDayInFestival(
  date: Date,
  festival: Pick<FestivalItem, "start_index" | "end_index">,
): boolean {
  const dayIndex = monthDayIndex(date.getMonth() + 1, date.getDate());
  const { start_index, end_index } = festival;
  if (start_index > end_index) {
    // 跨年区间：年末段 或 年初段
    return dayIndex >= start_index || dayIndex <= end_index;
  }
  return dayIndex >= start_index && dayIndex <= end_index;
}

/**
 * 依据当前日期从节日列表中解析出所属节日 ID，无节日时返回 null。
 *
 * 单个日期最多只会命中一个节日 —— 这一点由管理端的「节日之间不可重叠重合」校验保证。
 */
export function resolveFestivalForDate(festivals: FestivalItem[], date: Date): string | null {
  if (!festivals || festivals.length === 0) {
    return null;
  }
  for (const festival of festivals) {
    if (isDayInFestival(date, festival)) {
      return festival.id;
    }
  }
  return null;
}

/**
 * 依据当前场景从启用图池中随机选一张（「首页 home」专用的场景化入口）。
 * - 节日：当日期落在某个启用节日区间内时，优先取绑定该节日的图；节日季无节日图则回退通用图；
 *   非节日季只使用未绑定节日的通用图
 * - 时段优先：选池内匹配当前时段的图（含未标注时段的通用图），无匹配则退回整个选池
 * - 无时段图（time_period 为空）会被无条件纳入每个时段候选池 → 参与各时段轮播
 * - random 参数可注入以便测试（默认 Math.random）
 *
 * 扩展点：仅当某模块启用场景化（后端 BackgroundModuleEnum.sceneEnabled = true）时，
 * 该模块的组件才走本方法；其余模块保持 fetchModuleBackgrounds（纯随机）。
 */
export function pickBackgroundByScene(
  items: BackgroundItem[],
  date: Date,
  festivals: FestivalItem[] = [],
  random: () => number = Math.random,
): string | null {
  if (!items || items.length === 0) {
    return null;
  }
  const { time_period } = resolveCurrentScene(date);
  const festival = resolveFestivalForDate(festivals, date);

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
