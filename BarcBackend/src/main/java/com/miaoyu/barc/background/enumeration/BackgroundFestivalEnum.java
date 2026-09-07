package com.miaoyu.barc.background.enumeration;

import com.miaoyu.barc.utils.dto.ValueLabelDto;
import lombok.Getter;

import java.util.ArrayList;
import java.util.List;

/**
 * 背景图节日枚举
 * code：用于数据库 festival 字段，必须为英文小写
 * label：管理端展示的中文名
 * */
@Getter
public enum BackgroundFestivalEnum {
    NEW_YEAR("newyear", "新年");

    private final String code;
    private final String label;

    BackgroundFestivalEnum(String code, String label) {
        this.code = code;
        this.label = label;
    }

    public static BackgroundFestivalEnum fromCode(String code) {
        if (code == null || code.isEmpty()) {
            return null;
        }
        for (BackgroundFestivalEnum item : values()) {
            if (item.code.equalsIgnoreCase(code)) {
                return item;
            }
        }
        return null;
    }

    public static List<ValueLabelDto> options() {
        List<ValueLabelDto> list = new ArrayList<>();
        for (BackgroundFestivalEnum item : values()) {
            list.add(new ValueLabelDto(item.code, item.label));
        }
        return list;
    }
}
