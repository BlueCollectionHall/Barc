import { describe, expect, it } from "vitest";

import {
  FESTIVAL,
  TIME_PERIOD,
  type BackgroundItem,
  pickBackgroundByScene,
  resolveCurrentScene,
} from "@/utils/backgroundApi.ts";

function item(url: string, time_period: string | null, festival: string | null): BackgroundItem {
  return { url, time_period, festival };
}

describe("resolveCurrentScene", () => {
  it("maps hours to night / day / eventing / night", () => {
    expect(resolveCurrentScene(new Date(2026, 0, 1, 3)).time_period).toBe(TIME_PERIOD.NIGHT);
    expect(resolveCurrentScene(new Date(2026, 0, 1, 10)).time_period).toBe(TIME_PERIOD.DAY);
    expect(resolveCurrentScene(new Date(2026, 0, 1, 18)).time_period).toBe(TIME_PERIOD.EVENTING);
    expect(resolveCurrentScene(new Date(2026, 0, 1, 22)).time_period).toBe(TIME_PERIOD.NIGHT);
  });

  it("marks Nov-Feb as new year festival", () => {
    expect(resolveCurrentScene(new Date(2026, 10, 15)).festival).toBe(FESTIVAL.NEW_YEAR); // 11月
    expect(resolveCurrentScene(new Date(2026, 11, 15)).festival).toBe(FESTIVAL.NEW_YEAR); // 12月
    expect(resolveCurrentScene(new Date(2026, 0, 15)).festival).toBe(FESTIVAL.NEW_YEAR); // 1月
    expect(resolveCurrentScene(new Date(2026, 1, 15)).festival).toBe(FESTIVAL.NEW_YEAR); // 2月
  });

  it("leaves off-season months as non-festival", () => {
    expect(resolveCurrentScene(new Date(2026, 5, 15)).festival).toBeNull(); // 6月
    expect(resolveCurrentScene(new Date(2026, 8, 15)).festival).toBeNull(); // 9月
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
    const picked = pickBackgroundByScene(items, new Date(2026, 5, 15, 10), () => 0.0);
    expect(picked).toBe("day.jpg");
  });

  it("uses the whole base pool when no image matches the time period", () => {
    const items = [item("night.jpg", TIME_PERIOD.NIGHT, null), item("day.jpg", TIME_PERIOD.DAY, null)];
    // 傍晚 18 点，无傍晚图也无未标注时段的图 -> 退化为整个选池
    const picked = pickBackgroundByScene(items, new Date(2026, 5, 15, 18), () => 0.0);
    expect(picked).toBe("night.jpg");
  });

  it("during festival season prefers festival images", () => {
    const items = [
      item("ny1.jpg", null, FESTIVAL.NEW_YEAR),
      item("general.jpg", null, null),
    ];
    const picked = pickBackgroundByScene(items, new Date(2026, 11, 25, 10), () => 0.0);
    expect(picked).toBe("ny1.jpg");
  });

  it("off-season excludes festival-tagged images", () => {
    const items = [
      item("ny1.jpg", null, FESTIVAL.NEW_YEAR),
      item("general.jpg", null, null),
    ];
    const picked = pickBackgroundByScene(items, new Date(2026, 5, 15, 10), () => 0.0);
    expect(picked).toBe("general.jpg");
  });

  it("falls back to general pool when festival season has no festival images", () => {
    const items = [item("general.jpg", null, null)];
    const picked = pickBackgroundByScene(items, new Date(2026, 11, 25, 10), () => 0.0);
    expect(picked).toBe("general.jpg");
  });
});
