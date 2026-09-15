package com.miaoyu.barc.festival.utils;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 「月日区间」工具类单元测试
 *
 * 覆盖点：统一索引换算、月日合法性（含闰年 2 月）、跨年判定、跨年/普通区间枚举、
 * 以及最关键的「区间重叠」在各种跨年与不跨年组合下的准确判定。
 */
class MonthDayRangeUtilsTest {

    @Test
    @DisplayName("统一索引 - 月 * 32 + 日，1-1 到 12-31 单调递增")
    void toIndex_monotonic() {
        assertEquals(33, MonthDayRangeUtils.toIndex(1, 1));
        assertEquals(415, MonthDayRangeUtils.toIndex(12, 31));
        // 相邻月之间留有空隙，保证不同月的索引不会相撞
        assertTrue(MonthDayRangeUtils.toIndex(1, 31) < MonthDayRangeUtils.toIndex(2, 1));
        assertTrue(MonthDayRangeUtils.toIndex(2, 29) < MonthDayRangeUtils.toIndex(3, 1));
    }

    @Test
    @DisplayName("月合法性 - 仅 1-12 合法")
    void isValidMonth() {
        assertTrue(MonthDayRangeUtils.isValidMonth(1));
        assertTrue(MonthDayRangeUtils.isValidMonth(12));
        assertFalse(MonthDayRangeUtils.isValidMonth(0));
        assertFalse(MonthDayRangeUtils.isValidMonth(13));
        assertFalse(MonthDayRangeUtils.isValidMonth(null));
    }

    @Test
    @DisplayName("月日合法性 - 识别大小月与闰年 2 月")
    void isValidMonthDay() {
        assertTrue(MonthDayRangeUtils.isValidMonthDay(1, 31, 2026));
        assertFalse(MonthDayRangeUtils.isValidMonthDay(1, 32, 2026));
        assertFalse(MonthDayRangeUtils.isValidMonthDay(4, 31, 2026));
        assertTrue(MonthDayRangeUtils.isValidMonthDay(4, 30, 2026));
        // 2024 为闰年、2026 为平年
        assertTrue(MonthDayRangeUtils.isValidMonthDay(2, 29, 2024));
        assertFalse(MonthDayRangeUtils.isValidMonthDay(2, 29, 2026));
        // 未指定年份时按闰年放宽，避免误拒合法的 2-29
        assertTrue(MonthDayRangeUtils.isValidMonthDay(2, 29, null));
        assertFalse(MonthDayRangeUtils.isValidMonthDay(2, 30, null));
        assertFalse(MonthDayRangeUtils.isValidMonthDay(null, 1, 2026));
        assertFalse(MonthDayRangeUtils.isValidMonthDay(2, null, 2026));
        assertFalse(MonthDayRangeUtils.isValidMonthDay(2, 0, 2026));
    }

    @Test
    @DisplayName("跨年判定 - 起始索引大于结束索引即为跨年")
    void isCrossYear() {
        // 新年 12-01 → 02-28：起始索引 385 大于结束索引 92，属于跨年区间
        assertTrue(MonthDayRangeUtils.isCrossYear(
                MonthDayRangeUtils.toIndex(12, 1), MonthDayRangeUtils.toIndex(2, 28)));
        assertFalse(MonthDayRangeUtils.isCrossYear(
                MonthDayRangeUtils.toIndex(1, 1), MonthDayRangeUtils.toIndex(12, 31)));
        assertFalse(MonthDayRangeUtils.isCrossYear(33, 33));
    }

    @Test
    @DisplayName("回归 - 新年种子索引必须是 12-01 → 02-29（曾因按「月*31+日」手算写成 386/478 而失效）")
    void newYearSeedIndexRegression() {
        // 该用例锁定 V11 订正后的新年索引，防止再次出现「索引算错 → 新年季判定不到新年」的缺陷。
        // 错误索引 386/478 的下场：478 越出合法上界 415，区间被当成非跨年，1-2 月与 12-01 都不命中。
        int startIndex = MonthDayRangeUtils.toIndex(12, 1);
        int endIndex = MonthDayRangeUtils.toIndex(2, 29);
        assertEquals(385, startIndex);
        assertEquals(93, endIndex);
        assertTrue(MonthDayRangeUtils.isCrossYear(startIndex, endIndex));

        // 新年季的每一天都必须命中
        assertTrue(MonthDayRangeUtils.containsDay(MonthDayRangeUtils.toIndex(12, 1), startIndex, endIndex));
        assertTrue(MonthDayRangeUtils.containsDay(MonthDayRangeUtils.toIndex(12, 31), startIndex, endIndex));
        assertTrue(MonthDayRangeUtils.containsDay(MonthDayRangeUtils.toIndex(1, 15), startIndex, endIndex));
        assertTrue(MonthDayRangeUtils.containsDay(MonthDayRangeUtils.toIndex(2, 29), startIndex, endIndex));
        // 非新年季不能误命中
        assertFalse(MonthDayRangeUtils.containsDay(MonthDayRangeUtils.toIndex(11, 30), startIndex, endIndex));
        assertFalse(MonthDayRangeUtils.containsDay(MonthDayRangeUtils.toIndex(3, 1), startIndex, endIndex));
        // 元旦必须被判定为与新年重叠，否则可以造出真正重叠的数据
        assertTrue(MonthDayRangeUtils.isOverlapped(startIndex, endIndex,
                MonthDayRangeUtils.toIndex(1, 1), MonthDayRangeUtils.toIndex(1, 2)));
    }

    @Test
    @DisplayName("区间枚举 - 普通区间为连续索引")
    void enumerateDayIndexes_normal() {
        List<Integer> indexes = MonthDayRangeUtils.enumerateDayIndexes(1, 1, 1, 3);
        assertEquals(List.of(
                MonthDayRangeUtils.toIndex(1, 1),
                MonthDayRangeUtils.toIndex(1, 2),
                MonthDayRangeUtils.toIndex(1, 3)), indexes);
    }

    @Test
    @DisplayName("区间枚举 - 跨年区间绕行展开为「年末段 + 次年年初段（整体加一圈）」")
    void enumerateDayIndexes_crossYear() {
        // 12-30 → 01-02：12 月 30、31 日 + 次年 1 月 1、2 日（次年段整体加一圈 384）
        List<Integer> indexes = MonthDayRangeUtils.enumerateDayIndexes(12, 30, 1, 2);
        assertEquals(List.of(
                MonthDayRangeUtils.toIndex(12, 30),
                MonthDayRangeUtils.toIndex(12, 31),
                MonthDayRangeUtils.toIndex(1, 1) + 384,
                MonthDayRangeUtils.toIndex(1, 2) + 384), indexes);
        // 展开结果必须是连续递增的坐标区间，isOverlapped 的平移比对依赖这一性质
        for (int i = 1; i < indexes.size(); i++) {
            assertTrue(indexes.get(i) > indexes.get(i - 1));
        }
    }

    @Test
    @DisplayName("区间枚举 - 整月按实际天数展开（2 月按 29 天放宽）")
    void enumerateDayIndexes_wholeMonth() {
        // 月份式：2 月第一天到 2 月最后一天
        List<Integer> february = MonthDayRangeUtils.enumerateDayIndexes(2, 1, 2, 29);
        assertEquals(29, february.size());
        assertEquals(MonthDayRangeUtils.toIndex(2, 1), february.get(0));
        assertEquals(MonthDayRangeUtils.toIndex(2, 29), february.get(28));
    }

    @Test
    @DisplayName("落在区间内 - 普通区间命中区间内的日子，区间外不命中")
    void containsDay_normal() {
        int start = MonthDayRangeUtils.toIndex(3, 1);
        int end = MonthDayRangeUtils.toIndex(5, 31);
        assertTrue(MonthDayRangeUtils.containsDay(MonthDayRangeUtils.toIndex(3, 1), start, end));
        assertTrue(MonthDayRangeUtils.containsDay(MonthDayRangeUtils.toIndex(4, 15), start, end));
        assertTrue(MonthDayRangeUtils.containsDay(MonthDayRangeUtils.toIndex(5, 31), start, end));
        assertFalse(MonthDayRangeUtils.containsDay(MonthDayRangeUtils.toIndex(2, 28), start, end));
        assertFalse(MonthDayRangeUtils.containsDay(MonthDayRangeUtils.toIndex(6, 1), start, end));
    }

    @Test
    @DisplayName("落在区间内 - 跨年区间（新年 12-01 → 02-28）覆盖年末与年初")
    void containsDay_crossYear() {
        int start = MonthDayRangeUtils.toIndex(12, 1);
        int end = MonthDayRangeUtils.toIndex(2, 28);
        assertTrue(MonthDayRangeUtils.containsDay(MonthDayRangeUtils.toIndex(12, 1), start, end));
        assertTrue(MonthDayRangeUtils.containsDay(MonthDayRangeUtils.toIndex(12, 31), start, end));
        assertTrue(MonthDayRangeUtils.containsDay(MonthDayRangeUtils.toIndex(1, 1), start, end));
        assertTrue(MonthDayRangeUtils.containsDay(MonthDayRangeUtils.toIndex(2, 28), start, end));
        // 3 月 1 日与 11 月 30 日都不在新年区间内
        assertFalse(MonthDayRangeUtils.containsDay(MonthDayRangeUtils.toIndex(3, 1), start, end));
        assertFalse(MonthDayRangeUtils.containsDay(MonthDayRangeUtils.toIndex(11, 30), start, end));
    }

    @Test
    @DisplayName("重叠判定 - 普通区间与普通区间")
    void isOverlapped_normalVsNormal() {
        int aStart = MonthDayRangeUtils.toIndex(3, 1);
        int aEnd = MonthDayRangeUtils.toIndex(3, 31);
        // 与自身完全重叠
        assertTrue(MonthDayRangeUtils.isOverlapped(aStart, aEnd, aStart, aEnd));
        // 部分相交
        assertTrue(MonthDayRangeUtils.isOverlapped(aStart, aEnd,
                MonthDayRangeUtils.toIndex(3, 15), MonthDayRangeUtils.toIndex(4, 10)));
        // 完全包含
        assertTrue(MonthDayRangeUtils.isOverlapped(aStart, aEnd,
                MonthDayRangeUtils.toIndex(3, 10), MonthDayRangeUtils.toIndex(3, 20)));
        // 相邻但不重叠：3-01→3-31 与 4-01→4-30
        assertFalse(MonthDayRangeUtils.isOverlapped(aStart, aEnd,
                MonthDayRangeUtils.toIndex(4, 1), MonthDayRangeUtils.toIndex(4, 30)));
    }

    @Test
    @DisplayName("重叠判定 - 跨年区间与落在其内部的年初节日（易漏判的关键用例）")
    void isOverlapped_crossYearVsEarlyYear() {
        // 新年 12-01 → 02-28（跨年）
        int nyStart = MonthDayRangeUtils.toIndex(12, 1);
        int nyEnd = MonthDayRangeUtils.toIndex(2, 28);
        // 元旦 01-01 → 01-02 落在新年区间内，必须判为重叠
        assertTrue(MonthDayRangeUtils.isOverlapped(nyStart, nyEnd,
                MonthDayRangeUtils.toIndex(1, 1), MonthDayRangeUtils.toIndex(1, 2)));
        // 反向调用同样成立
        assertTrue(MonthDayRangeUtils.isOverlapped(
                MonthDayRangeUtils.toIndex(1, 1), MonthDayRangeUtils.toIndex(1, 2), nyStart, nyEnd));
        // 春节 01-20 → 02-05 与新年重叠
        assertTrue(MonthDayRangeUtils.isOverlapped(nyStart, nyEnd,
                MonthDayRangeUtils.toIndex(1, 20), MonthDayRangeUtils.toIndex(2, 5)));
        // 11-01 → 11-30 与新年不重叠（新年从 12-01 才开始）
        assertFalse(MonthDayRangeUtils.isOverlapped(nyStart, nyEnd,
                MonthDayRangeUtils.toIndex(11, 1), MonthDayRangeUtils.toIndex(11, 30)));
        // 03-01 → 06-30 与新年不重叠
        assertFalse(MonthDayRangeUtils.isOverlapped(nyStart, nyEnd,
                MonthDayRangeUtils.toIndex(3, 1), MonthDayRangeUtils.toIndex(6, 30)));
        // 12-31 → 01-01 与新年重叠（两端都落在新年内）
        assertTrue(MonthDayRangeUtils.isOverlapped(nyStart, nyEnd,
                MonthDayRangeUtils.toIndex(12, 31), MonthDayRangeUtils.toIndex(1, 1)));
    }

    @Test
    @DisplayName("重叠判定 - 两个跨年区间可重叠")
    void isOverlapped_crossYearVsCrossYear() {
        // 12-01 → 02-28 与 12-15 → 01-15 必然重叠
        assertTrue(MonthDayRangeUtils.isOverlapped(
                MonthDayRangeUtils.toIndex(12, 1), MonthDayRangeUtils.toIndex(2, 28),
                MonthDayRangeUtils.toIndex(12, 15), MonthDayRangeUtils.toIndex(1, 15)));
        // 12-01 → 12-31（不跨年）与 01-01 → 02-28（不跨年）不重叠，
        // 说明「跨年」必须由数据显式表达，而不是自动补齐
        assertFalse(MonthDayRangeUtils.isOverlapped(
                MonthDayRangeUtils.toIndex(12, 1), MonthDayRangeUtils.toIndex(12, 31),
                MonthDayRangeUtils.toIndex(1, 1), MonthDayRangeUtils.toIndex(2, 28)));
    }

    @Test
    @DisplayName("重叠判定 - 跨年区间与跨年区间的边界不重叠")
    void isOverlapped_crossYearBoundary() {
        // 12-01 → 12-31 与 01-01 → 02-28 都不跨年且分属首尾，不应重叠
        assertFalse(MonthDayRangeUtils.isOverlapped(
                MonthDayRangeUtils.toIndex(12, 1), MonthDayRangeUtils.toIndex(12, 31),
                MonthDayRangeUtils.toIndex(1, 1), MonthDayRangeUtils.toIndex(1, 31)));
        // 但把其中一个改成跨年（12-01 → 01-31）后，与 01-01 → 01-31 重叠
        assertTrue(MonthDayRangeUtils.isOverlapped(
                MonthDayRangeUtils.toIndex(12, 1), MonthDayRangeUtils.toIndex(1, 31),
                MonthDayRangeUtils.toIndex(1, 1), MonthDayRangeUtils.toIndex(1, 31)));
    }

    @Test
    @DisplayName("重叠判定 - 统一索引月间空隙不得造成假阳性（1-1~2-28 与 12-01~12-31 不重叠）")
    void isOverlapped_noFalsePositiveFromIndexGap() {
        // 1-31 的索引为 63、2-1 为 65，而 12 月的索引从 385 起，中间有空隙。
        // 早期「平移一圈做整数相交」的实现会因为空隙把这两段误判为重叠；逐日枚举的实现必须给出正确结论。
        assertFalse(MonthDayRangeUtils.isOverlapped(
                MonthDayRangeUtils.toIndex(1, 1), MonthDayRangeUtils.toIndex(2, 28),
                MonthDayRangeUtils.toIndex(12, 1), MonthDayRangeUtils.toIndex(12, 31)));
        // 相邻边界的另一侧同样不能误判：2-28 → 4-30 与 12-01 → 12-31 无交集
        assertFalse(MonthDayRangeUtils.isOverlapped(
                MonthDayRangeUtils.toIndex(2, 28), MonthDayRangeUtils.toIndex(4, 30),
                MonthDayRangeUtils.toIndex(12, 1), MonthDayRangeUtils.toIndex(12, 31)));
    }

    @Test
    @DisplayName("重叠判定 - 跨年区间与年内区间的各种组合")
    void isOverlapped_crossYearVsNormalCombinations() {
        int nyStart = MonthDayRangeUtils.toIndex(12, 1);
        int nyEnd = MonthDayRangeUtils.toIndex(2, 28);
        // 落在跨年区间起始段之前的 11 月 -> 不重叠
        assertFalse(MonthDayRangeUtils.isOverlapped(nyStart, nyEnd,
                MonthDayRangeUtils.toIndex(11, 1), MonthDayRangeUtils.toIndex(11, 30)));
        // 与跨年区间的 12 月段部分相交 -> 重叠
        assertTrue(MonthDayRangeUtils.isOverlapped(nyStart, nyEnd,
                MonthDayRangeUtils.toIndex(12, 15), MonthDayRangeUtils.toIndex(12, 20)));
        // 与跨年区间的次年段部分相交 -> 重叠
        assertTrue(MonthDayRangeUtils.isOverlapped(nyStart, nyEnd,
                MonthDayRangeUtils.toIndex(2, 1), MonthDayRangeUtils.toIndex(3, 10)));
        // 完全覆盖整个跨年区间 -> 重叠
        assertTrue(MonthDayRangeUtils.isOverlapped(nyStart, nyEnd,
                MonthDayRangeUtils.toIndex(1, 1), MonthDayRangeUtils.toIndex(12, 31)));
    }

    @Test
    @DisplayName("区间规范化 - 合法返回起止索引，非法返回 null")
    void normalizeRange() {
        int[] ok = MonthDayRangeUtils.normalizeRange(12, 1, 2, 28);
        assertNotNull(ok);
        assertEquals(MonthDayRangeUtils.toIndex(12, 1), ok[0]);
        assertEquals(MonthDayRangeUtils.toIndex(2, 28), ok[1]);
        // 跨年时起始索引大于结束索引
        assertTrue(ok[0] > ok[1]);

        assertNull(MonthDayRangeUtils.normalizeRange(0, 1, 2, 28));
        assertNull(MonthDayRangeUtils.normalizeRange(13, 1, 2, 28));
        assertNull(MonthDayRangeUtils.normalizeRange(1, 1, 4, 31));
        assertNull(MonthDayRangeUtils.normalizeRange(null, 1, 2, 28));
    }
}
