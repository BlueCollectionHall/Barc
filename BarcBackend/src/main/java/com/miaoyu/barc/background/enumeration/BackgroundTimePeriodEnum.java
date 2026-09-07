package com.miaoyu.barc.background.enumeration;

import com.miaoyu.barc.utils.dto.ValueLabelDto;
import lombok.Getter;

import java.util.ArrayList;
import java.util.List;

/**
 * 背景图时段枚举
 * code：用于数据库 time_period 字段，必须为英文小写
 * label：管理端展示的中文名
 * */
@Getter
public enum BackgroundTimePeriodEnum {
    DAY("day", "白天"),
    EVENTING("eventing", "傍晚"),
    NIGHT("night", "夜晚");

    private final String code;
    private final String label;

    BackgroundTimePeriodEnum(String code, String label) {
        this.code = code;
        this.label = label;
    }

    public static BackgroundTimePeriodEnum fromCode(String code) {
        if (code == null || code.isEmpty()) {
            return null;
        }
        for (BackgroundTimePeriodEnum item : values()) {
            if (item.code.equalsIgnoreCase(code)) {
                return item;
            }
        }
        return null;
    }

    public static List<ValueLabelDto> options() {
        List<ValueLabelDto> list = new ArrayList<>();
        for (BackgroundTimePeriodEnum item : values()) {
            list.add(new ValueLabelDto(item.code, item.label));
        }
        return list;
    }
}
