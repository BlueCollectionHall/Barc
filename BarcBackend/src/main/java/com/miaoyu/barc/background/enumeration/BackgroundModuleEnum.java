package com.miaoyu.barc.background.enumeration;

import com.miaoyu.barc.utils.dto.ValueLabelDto;
import lombok.Getter;

import java.util.ArrayList;
import java.util.List;

/**
 * 背景图模块枚举
 * code：用于 COS 路径（client/bg/{code}/...）与数据库 module 字段，必须为英文小写
 * label：管理端展示的中文名
 * */
@Getter
public enum BackgroundModuleEnum {
    SIGN("sign", "登录/注册"),
    HOME("home", "首页"),
    JOIN("join", "加入我们"),
    FEEDBACK("feedback", "意见反馈"),
    MESSAGE_BOARD("messageboard", "留言板"),
    STUDENT("student", "学生详情");

    private final String code;
    private final String label;

    BackgroundModuleEnum(String code, String label) {
        this.code = code;
        this.label = label;
    }

    public static BackgroundModuleEnum fromCode(String code) {
        if (code == null || code.isEmpty()) {
            return null;
        }
        for (BackgroundModuleEnum item : values()) {
            if (item.code.equalsIgnoreCase(code)) {
                return item;
            }
        }
        return null;
    }

    public static List<ValueLabelDto> options() {
        List<ValueLabelDto> list = new ArrayList<>();
        for (BackgroundModuleEnum item : values()) {
            list.add(new ValueLabelDto(item.code, item.label));
        }
        return list;
    }
}
