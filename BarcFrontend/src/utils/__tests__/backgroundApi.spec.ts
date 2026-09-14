import { describe, expect, it } from "vitest";

import {
  TIME_PERIOD,
  type BackgroundItem,
  type FestivalItem,
  isDayInFestival,
  pickBackgroundByScene,
  resolveCurrentScene,
  resolveFestivalForDate,
} from "@/utils/backgroundApi.ts";

function item(url: string, time_period: string | null, festival: string | null): BackgroundItem {
  return { url, time_period, festival };
}

/**
 * 构造节日：统一索引与后端一致（月份 * 32 + 日），
 * start_index > end_index 表示跨年区间。
 */
function festival(
  id: string,
  name: string,
  sm: number,
  sd: number,
  em: number,
  ed: number,
): FestivalItem {
  return {
    id,
    name,
    start_month: sm,
    start_day: sd,
    end_month: em,
    end_day: ed,
    start_index: sm * 32 + sd,
    end_index: em * 32 + ed,
  };
}

/** 新年：月份式 12 月 → 次年 2 月，落库为 12-01 → 02-28（跨年区间） */
const NEW_YEAR = festival("fest-ny", "新年", 12, 1, 2, 28);
/** 暑期：日期式 07-01 → 08-31（不跨年） */
const SUMMER = festival("fest-summer", "暑期", 7, 1, 8, 31);

describe("resolveCurrentScene", () => {
  it("maps hours to night / day / eventing / night", () => {
    expect(resolveCurrentScene(new Date(2026, 0, 1, 3)).time_period).toBe(TIME_PERIOD.NIGHT);
    expect(resolveCurrentScene(new Date(2026, 0, 1, 10)).time_period).toBe(TIME_PERIOD.DAY);
    expect(resolveCurrentScene(new Date(2026, 0, 1, 18)).time_period).toBe(TIME_PERIOD.EVENTING);
    expect(resolveCurrentScene(new Date(2026, 0, 1, 22)).time_period).toBe(TIME_PERIOD.NIGHT);
  });
});

describe("resolveFestivalForDate", () => {
  it("resolves a cross-year festival on both sides of the new year", () => {
    // 新年 12-01 → 02-28：年末与年初都命中
    expect(resolveFestivalForDate([NEW_YEAR], new Date(2026, 11, 15))).toBe(NEW_YEAR.id); // 12月
    expect(resolveFestivalForDate([NEW_YEAR], new Date(2026, 0, 15))).toBe(NEW_YEAR.id); // 1月
    expect(resolveFestivalForDate([NEW_YEAR], new Date(2026, 1, 28))).toBe(NEW_YEAR.id); // 2月28日边界
  });

  it("leaves off-range months as non-festival for a cross-year festival", () => {
    // 11 月与 3 月都不在新年区间内
    expect(resolveFestivalForDate([NEW_YEAR], new Date(2026, 10, 15))).toBeNull(); // 11月
    expect(resolveFestivalForDate([NEW_YEAR], new Date(2026, 2, 1))).toBeNull(); // 3月1日
    expect(resolveFestivalForDate([NEW_YEAR], new Date(2026, 5, 15))).toBeNull(); // 6月
  });

  it("resolves a non-cross-year festival inside its range only", () => {
    expect(resolveFestivalForDate([SUMMER], new Date(2026, 6, 1))).toBe(SUMMER.id); // 7月1日
    expect(resolveFestivalForDate([SUMMER], new Date(2026, 7, 31))).toBe(SUMMER.id); // 8月31日边界
    expect(resolveFestivalForDate([SUMMER], new Date(2026, 8, 1))).toBeNull(); // 9月1日
    expect(resolveFestivalForDate([SUMMER], new Date(2026, 5, 30))).toBeNull(); // 6月30日
  });

  it("returns null when there is no festival configured", () => {
    // 管理端把节日全部关闭 / 删除后，用户端应判定为「无节日」
    expect(resolveFestivalForDate([], new Date(2026, 11, 25))).toBeNull();
  });

  it("picks the matching festival among several configured ones", () => {
    const festivals = [NEW_YEAR, SUMMER];
    expect(resolveFestivalForDate(festivals, new Date(2026, 11, 25))).toBe(NEW_YEAR.id);
    expect(resolveFestivalForDate(festivals, new Date(2026, 6, 15))).toBe(SUMMER.id);
    expect(resolveFestivalForDate(festivals, new Date(2026, 4, 15))).toBeNull();
  });
});

describe("isDayInFestival", () => {
  it("handles day boundaries of a normal range", () => {
    expect(isDayInFestival(new Date(2026, 6, 1), SUMMER)).toBe(true);
    expect(isDayInFestival(new Date(2026, 5, 30), SUMMER)).toBe(false);
  });

  it("handles day boundaries of a cross-year range", () => {
    expect(isDayInFestival(new Date(2026, 11, 1), NEW_YEAR)).toBe(true);
    expect(isDayInFestival(new Date(2026, 10, 30), NEW_YEAR)).toBe(false);
    expect(isDayInFestival(new Date(2026, 2, 1), NEW_YEAR)).toBe(false);
  });
});

describe("pickBackgroundByScene", () => {
  it("returns null for an empty pool", () => {
    expect(pickBackgroundByScene([], new Date(2026, 5, 15, 10))).toBeNull();
  });

  it("prefers the current time period inside the general pool", () => {
    const items = [
      item("night.jpg", TIME_PERIOD.NIGHT, null),
      item("day.jpg", TIME_PERIOD.DAY, null),
      item("any.jpg", null, null),
    ];
    // 白天 10 点
    const picked = pickBackgroundByScene(items, new Date(2026, 5, 15, 10), [], () => 0.0);
    expect(picked).toBe("day.jpg");
  });

  it("uses the whole base pool when no image matches the time period", () => {
    const items = [item("night.jpg", TIME_PERIOD.NIGHT, null), item("day.jpg", TIME_PERIOD.DAY, null)];
    // 傍晚 18 点，无傍晚图也无未标注时段的图 -> 退化为整个选池
    const picked = pickBackgroundByScene(items, new Date(2026, 5, 15, 18), [], () => 0.0);
    expect(picked).toBe("night.jpg");
  });

  it("during a festival prefers images bound to that festival", () => {
    const items = [item("ny1.jpg", null, NEW_YEAR.id), item("general.jpg", null, null)];
    const picked = pickBackgroundByScene(items, new Date(2026, 11, 25, 10), [NEW_YEAR], () => 0.0);
    expect(picked).toBe("ny1.jpg");
  });

  it("excludes festival-bound images outside the festival range", () => {
    const items = [item("ny1.jpg", null, NEW_YEAR.id), item("general.jpg", null, null)];
    // 6 月不属于任何节日 -> 只用未绑定节日的通用图
    const picked = pickBackgroundByScene(items, new Date(2026, 5, 15, 10), [NEW_YEAR], () => 0.0);
    expect(picked).toBe("general.jpg");
  });

  it("falls back to the general pool when the festival has no bound image", () => {
    const items = [item("general.jpg", null, null)];
    const picked = pickBackgroundByScene(items, new Date(2026, 11, 25, 10), [NEW_YEAR], () => 0.0);
    expect(picked).toBe("general.jpg");
  });

  it("treats a festival deleted in admin as non-festival (binding auto-cleared)", () => {
    // 节日被删除后，背景图上的 festival 已被后端置空，用户端不再当作节日图
    const items = [item("former-newyear.jpg", null, null), item("general.jpg", null, null)];
    const picked = pickBackgroundByScene(items, new Date(2026, 11, 25, 10), [], () => 0.0);
    // 两张都成了通用图，任取其一（random 注入 0 取第一张）
    expect(picked).toBe("former-newyear.jpg");
  });
});
