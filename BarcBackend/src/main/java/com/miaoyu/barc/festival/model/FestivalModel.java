package com.miaoyu.barc.festival.model;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 节日模型
 *
 * 存储约定（月份式与日期式统一）：
 * 1、不论「月份式」（某月第一天至某月最后一天）还是「日期式」（精确某月某日到某月某日），
 *    落库一律是「某月某日 → 某月某日」，即 start_month/start_day/end_month/end_day；
 * 2、start_index/end_index 为派生列（月份 * 32 + 日），由服务端计算后落库，
 *    用于用一条 SQL 做区间重叠判定；end_index < start_index 表示该区间跨年环绕；
 * 3、enabled = false 表示「关闭」：保留数据与背景图绑定，仅不参与用户端节日判定。
 */
@Getter
@Setter
public class FestivalModel {
    private String id;
    private String name;
    /** 起始月 1-12 */
    private Integer start_month;
    /** 起始日 1-31 */
    private Integer start_day;
    /** 结束月 1-12 */
    private Integer end_month;
    /** 结束日 1-31 */
    private Integer end_day;
    /** 统一索引：起始月 * 32 + 起始日（非数据库手工维护列，由服务端计算） */
    private Integer start_index;
    /** 统一索引：结束月 * 32 + 结束日（小于 start_index 表示跨年） */
    private Integer end_index;
    /** 是否启用：false 表示已关闭，保留数据与绑定关系 */
    private Boolean enabled;
    private String created_by;
    private LocalDateTime created_at;
    private LocalDateTime updated_at;
    private LocalDateTime deleted_at;
    /**
     * 绑定该节日的背景图数量（非数据库列，仅管理端列表展示与删除确认使用）
     */
    private Integer bound_backgrounds;
}
