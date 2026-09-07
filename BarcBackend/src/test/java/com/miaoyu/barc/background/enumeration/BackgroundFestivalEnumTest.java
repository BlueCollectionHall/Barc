package com.miaoyu.barc.background.enumeration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 背景图节日枚举单元测试
 */
class BackgroundFestivalEnumTest {

    @Test
    @DisplayName("fromCode - 合法 code 命中对应枚举")
    void fromCode_hit() {
        assertEquals(BackgroundFestivalEnum.NEW_YEAR, BackgroundFestivalEnum.fromCode("newyear"));
        assertEquals(BackgroundFestivalEnum.NEW_YEAR, BackgroundFestivalEnum.fromCode("NEWYEAR"));
    }

    @Test
    @DisplayName("fromCode - 空串/ null / 非法值返回 null")
    void fromCode_invalid() {
        assertNull(BackgroundFestivalEnum.fromCode(null));
        assertNull(BackgroundFestivalEnum.fromCode(""));
        assertNull(BackgroundFestivalEnum.fromCode("spring-festival"));
    }

    @Test
    @DisplayName("options - 返回全部节日选项")
    void options_count() {
        assertEquals(BackgroundFestivalEnum.values().length, BackgroundFestivalEnum.options().size());
    }
}
