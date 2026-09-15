package com.miaoyu.barc.festival.utils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 「月日区间」工具类
 *
 * 背景：
 * 节日区间不论「月份式」（某月第一天至某月最后一天）还是「日期式」（精确某月某日到某月某日），
 * 持久化本质都是「某月某日 → 某月某日」。为便于比较与排序，这里把「月 + 日」压缩成一个
 * 统一索引：月 * 32 + 日。
 *
 * 统一索引的性质：
 * 1、取值区间为 [33, 415]（1 月 1 日 = 33，12 月 31 日 = 415），整数单调，便于比较与排序；
 * 2、相邻两月之间留有间隙（每月最多 31 天 < 32），保证不同月的索引绝不会相撞；
 * 3、跨年区间用「起始索引 > 结束索引」表达（如新年 12-01 → 02-28）。
 *
 * ⚠️ 为什么区间重叠不能用统一索引做「平移一圈」的数值比较：
 * 统一索引在相邻月之间刻意留了空隙（如 1-31 = 63，而 2-1 = 65），
 * 因此把某个区间平移一圈后，其端点并不能与另一圈上的真实日期严格对齐，
 * 例如 [1-1, 2-28] 与 [12-01, 12-31] 会被误判成重叠。
 * 所以本类对「两区间是否重叠」一律采用逐日枚举的精确判定：
 * 一年最多 366 天、节日数量极少，这点开销可以忽略，换来的是判定绝对准确、无边界漏洞。
 *
 * 相对地，「某一天是否落在区间内」（containsDay）只使用单个索引比较，
 * 由于索引与月日一一对应且连续区间单调，该判定是精确的，可以放心使用。
 */
public final class MonthDayRangeUtils {

    /** 月份权重基数：每月按 32 进位，保证每月最多 31 天不会溢出到下一个月 */
    public static final int MONTH_WEIGHT = 32;
    /** 一整圈的索引跨度：12 * 32，用于把跨年区间展开到「第二圈」的坐标上 */
    private static final int CYCLE = 12 * MONTH_WEIGHT;
    /** 一年中最后一天（12-31）的统一索引，跨年区间第一圈的枚举上界 */
    private static final int MAX_DAY_INDEX = 12 * MONTH_WEIGHT + 31;

    private MonthDayRangeUtils() {
    }

    /**
     * 计算统一索引：月 * 32 + 日
     *
     * @param month 月份 1-12
     * @param day   日期 1-31
     */
    public static int toIndex(int month, int day) {
        return month * MONTH_WEIGHT + day;
    }

    /**
     * 判断月份是否合法（1-12）
     */
    public static boolean isValidMonth(Integer month) {
        return month != null && month >= 1 && month <= 12;
    }

    /**
     * 判断「月 + 日」是否为合法日期（含大小月与闰年 2 月）
     *
     * @param month 月份 1-12
     * @param day   日期
     * @param year  用于判断闰年；传 null 时 2 月按 29 天放宽处理
     */
    public static boolean isValidMonthDay(Integer month, Integer day, Integer year) {
        if (!isValidMonth(month) || day == null || day < 1) {
            return false;
        }
        return day <= maxDayOfMonth(month, year);
    }

    /**
     * 某月的最大天数；year 为 null 时 2 月按闰年上限 29 处理（避免误拒合法的 2-29）
     */
    public static int maxDayOfMonth(int month, Integer year) {
        switch (month) {
            case 2:
                // 未指定年份时按 29 放宽，避免把闰年才出现的 2-29 判成非法
                if (year == null) {
                    return 29;
                }
                boolean leap = (year % 4 == 0 && year % 100 != 0) || year % 400 == 0;
                return leap ? 29 : 28;
            case 4:
            case 6:
            case 9:
            case 11:
                return 30;
            default:
                return 31;
        }
    }

    /**
     * 判断该区间是否为跨年环绕区间（起始索引 > 结束索引）
     */
    public static boolean isCrossYear(int startIndex, int endIndex) {
        return startIndex > endIndex;
    }

    /**
     * 枚举区间覆盖的全部「月日」统一索引（跨年区间会自动绕行展开）。
     *
     * 约定（与 {@link #isOverlapped} 使用同一套坐标，因此不存在语义差异）：
     * 1、只产出真实存在的日期，绝不包含月度空隙上的索引（如 2 月的 30、31 日）；
     * 2、跨年区间绕行到次年的那一段整体加一圈（+384），使结果成为一条连续递增的坐标序列。
     *
     * 例：[12-01, 02-28] → [385 … 415, 417 … 476]，
     * 即 12 月整月 + 次年 1 月整月 + 次年 2 月 1-28 日。
     *
     * @param startMonth 起始月
     * @param startDay   起始日
     * @param endMonth   结束月
     * @param endDay     结束日
     */
    public static List<Integer> enumerateDayIndexes(int startMonth, int startDay, int endMonth, int endDay) {
        int startIndex = toIndex(startMonth, startDay);
        int endIndex = toIndex(endMonth, endDay);
        List<Integer> indexes = new ArrayList<>();
        if (startIndex <= endIndex) {
            // 普通区间：一次性走完，无需绕行
            for (int index = startIndex; index <= endIndex; index++) {
                if (isRealDayIndex(index)) {
                    indexes.add(index);
                }
            }
            return indexes;
        }
        // 跨年区间：第一圈从起始日走到年末（12-31）
        for (int index = startIndex; index <= MAX_DAY_INDEX; index++) {
            if (isRealDayIndex(index)) {
                indexes.add(index);
            }
        }
        // 第二圈从年初（1-1）走到结束日，整体加一圈，坐标落到第一圈之后
        for (int index = MONTH_WEIGHT + 1; index <= endIndex; index++) {
            if (isRealDayIndex(index)) {
                indexes.add(index + CYCLE);
            }
        }
        return indexes;
    }

    /**
     * 判断统一索引是否对应真实存在的一天。
     *
     * 每月最多 31 天，而进位基数是 32，因此对 32 取余为 0 的索引落在月度空隙上（例如 64 = 2 月 0 日），
     * 必须排除，否则重叠判定会把不存在的日期也算进覆盖范围。
     */
    private static boolean isRealDayIndex(int index) {
        return index % MONTH_WEIGHT != 0;
    }

    /**
     * 判断某一天是否落在指定区间内（跨年区间自动绕行判定）
     *
     * @param dayIndex   当天的统一索引：月 * 32 + 日
     * @param startIndex 区间起始统一索引
     * @param endIndex   区间结束统一索引（小于起始表示跨年）
     */
    public static boolean containsDay(int dayIndex, int startIndex, int endIndex) {
        if (isCrossYear(startIndex, endIndex)) {
            // 跨年区间：当天落在 [起始日, 年末] 或 [年初, 结束日] 均视为命中
            return dayIndex >= startIndex || dayIndex <= endIndex;
        }
        return dayIndex >= startIndex && dayIndex <= endIndex;
    }

    /**
     * 判断两个「可能跨年」的区间是否重叠重合（逐日枚举精确判定）
     *
     * 判定思路：把区间 A 覆盖的每一天枚举出来放进集合 A，再枚举区间 B 的每一天，
     * 只要其中某一天（或其前后平移一圈后的等价坐标）出现在集合 A 中，两个区间就有重合的日子。
     *
     * 为什么必须逐日比对，而不能把区间简化成一对端点做整数相交：
     * 统一索引在相邻月之间留有空隙（1-31 = 63，2-1 = 65），「端点区间」会把这些不存在的索引
     * 也算进覆盖范围，从而把 [1-01, 2-28] 与 [3-01, 4-30] 这类毫无交集的两个区间误判为重叠。
     * 逐日枚举只保留真实存在的日期，因此不存在这类假阳性。
     *
     * 开销：一年最多 366 天，节日数量极少，新增 / 修改节日时各调用一次，完全可以接受。
     */
    public static boolean isOverlapped(int startIndexA, int endIndexA, int startIndexB, int endIndexB) {
        Set<Integer> daysOfA = toDayIndexSet(startIndexA, endIndexA);
        for (int dayIndexB : toDayIndexSet(startIndexB, endIndexB)) {
            // 同一圈、前一圈、后一圈：只要任一等价坐标命中，即说明两者在某个年度上落在同一天
            if (daysOfA.contains(dayIndexB)
                    || daysOfA.contains(dayIndexB - CYCLE)
                    || daysOfA.contains(dayIndexB + CYCLE)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 把区间覆盖的每一天转换成统一索引的集合（跨年区间会自动绕行展开，跨年段加一圈 +384）
     *
     * 直接复用 {@link #enumerateDayIndexes}，保证「枚举」与「重叠判定」两处使用的是同一套坐标与过滤规则。
     */
    private static Set<Integer> toDayIndexSet(int startIndex, int endIndex) {
        // 反推回「月 / 日」后交给枚举方法，避免两处各写一套遍历逻辑
        return new HashSet<>(enumerateDayIndexes(
                startIndex / MONTH_WEIGHT, startIndex % MONTH_WEIGHT,
                endIndex / MONTH_WEIGHT, endIndex % MONTH_WEIGHT));
    }

    /**
     * 校验并规范化一个区间：非法时返回 null
     *
     * @return 规范化后的起始/结束索引数组（非跨年时顺序不变；跨年时保持 start > end 的交错语义）
     */
    public static int[] normalizeRange(Integer startMonth, Integer startDay, Integer endMonth, Integer endDay) {
        if (!isValidMonthDay(startMonth, startDay, null) || !isValidMonthDay(endMonth, endDay, null)) {
            return null;
        }
        return new int[]{ toIndex(startMonth, startDay), toIndex(endMonth, endDay) };
    }
}
