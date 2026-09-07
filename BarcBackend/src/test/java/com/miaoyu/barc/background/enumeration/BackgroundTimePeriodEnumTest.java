package com.miaoyu.barc.background.enumeration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 背景图时段枚举单元测试
 */
class BackgroundTimePeriodEnumTest {

    @Test
    @DisplayName("fromCode - 合法 code 命中对应枚举")
    void fromCode_hit() {
        assertEquals(BackgroundTimePeriodEnum.DAY, BackgroundTimePeriodEnum.fromCode("day"));
        assertEquals(BackgroundTimePeriodEnum.EVENTING, BackgroundTimePeriodEnum.fromCode("eventing"));
        assertEquals(BackgroundTimePeriodEnum.NIGHT, BackgroundTimePeriodEnum.fromCode("NIGHT"));
    }

    @Test
    @DisplayName("fromCode - 大小写不敏感")
    void fromCode_caseInsensitive() {
        assertEquals(BackgroundTimePeriodEnum.DAY, BackgroundTimePeriodEnum.fromCode("DAY"));
    }

    @Test
    @DisplayName("fromCode - 空串/ null / 非法值返回 null")
    void fromCode_invalid() {
        assertNull(BackgroundTimePeriodEnum.fromCode(null));
        assertNull(BackgroundTimePeriodEnum.fromCode(""));
        assertNull(BackgroundTimePeriodEnum.fromCode("noon"));
    }

    @Test
    @DisplayName("options - 返回全部时段选项")
    void options_count() {
        assertEquals(BackgroundTimePeriodEnum.values().length, BackgroundTimePeriodEnum.options().size());
    }
}
