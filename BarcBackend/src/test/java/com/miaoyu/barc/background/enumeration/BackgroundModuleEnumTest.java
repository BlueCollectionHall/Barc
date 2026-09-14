package com.miaoyu.barc.background.enumeration;

import com.miaoyu.barc.background.model.BackgroundModuleOption;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 背景图模块枚举单元测试
 */
class BackgroundModuleEnumTest {

    @Test
    @DisplayName("fromCode - 合法 code 命中对应枚举")
    void fromCode_hit() {
        assertEquals(BackgroundModuleEnum.HOME, BackgroundModuleEnum.fromCode("home"));
        assertEquals(BackgroundModuleEnum.SIGN, BackgroundModuleEnum.fromCode("sign"));
        assertEquals(BackgroundModuleEnum.MESSAGE_BOARD, BackgroundModuleEnum.fromCode("MESSAGEBOARD"));
    }

    @Test
    @DisplayName("fromCode - 大小写不敏感")
    void fromCode_caseInsensitive() {
        assertEquals(BackgroundModuleEnum.HOME, BackgroundModuleEnum.fromCode("HOME"));
    }

    @Test
    @DisplayName("fromCode - 空串/ null / 非法值返回 null")
    void fromCode_invalid() {
        assertNull(BackgroundModuleEnum.fromCode(null));
        assertNull(BackgroundModuleEnum.fromCode(""));
        assertNull(BackgroundModuleEnum.fromCode("bad"));
    }

    @Test
    @DisplayName("student 已废弃：fromCode 不再命中")
    void studentRemoved() {
        assertNull(BackgroundModuleEnum.fromCode("student"));
    }

    @Test
    @DisplayName("options - 返回全部模块且携带 sceneEnabled")
    void options_carrySceneEnabled() {
        List<BackgroundModuleOption> options = BackgroundModuleEnum.options();
        assertEquals(BackgroundModuleEnum.values().length, options.size());
        // 首页为唯一启用场景化的模块
        BackgroundModuleOption home = options.stream()
                .filter(opt -> "home".equals(opt.getValue()))
                .findFirst().orElse(null);
        assertNotNull(home);
        assertTrue(home.isSceneEnabled());
        // 其余模块均不启用场景化
        for (BackgroundModuleOption opt : options) {
            if (!"home".equals(opt.getValue())) {
                assertFalse(opt.isSceneEnabled(),
                        "模块 " + opt.getValue() + " 不应启用场景化");
            }
        }
        // 不应出现废弃的 student
        assertTrue(options.stream().noneMatch(opt -> "student".equals(opt.getValue())));
    }
}
