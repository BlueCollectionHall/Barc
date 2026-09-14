package com.miaoyu.barc.background.enumeration;

import com.miaoyu.barc.background.model.BackgroundModuleOption;
import lombok.Getter;

import java.util.ArrayList;
import java.util.List;

/**
 * 背景图模块枚举
 * code：用于 COS 路径（client/bg/{code}/...）与数据库 module 字段，必须为英文小写
 * label：管理端展示的中文名
 * sceneEnabled：是否启用「时段 + 节日」场景化筛选。
 *   - true：该模块用户端会按时段/节日挑选背景图（当前仅首页 home 启用）。
 *   - false：该模块用户端按启用顺序纯随机轮播，管理端不展示「时段/节日」标签列。
 *
 * 注意：student（学生详情）模块已移除 —— 学生详情页与首页共用背景图，不再作为独立可配置模块。
 * 若未来要让某模块启用场景化，只需把该枚举的 sceneEnabled 置为 true，
 * 并将用户端对应组件改走 fetchModuleBackgroundItems + pickBackgroundByScene 即可。
 * */
@Getter
public enum BackgroundModuleEnum {
    SIGN("sign", "登录/注册", false),
    HOME("home", "首页", true),
    JOIN("join", "加入我们", false),
    FEEDBACK("feedback", "意见反馈", false),
    MESSAGE_BOARD("messageboard", "留言板", false);

    private final String code;
    private final String label;
    /** 是否启用「时段 + 节日」场景化筛选 */
    private final boolean sceneEnabled;

    BackgroundModuleEnum(String code, String label, boolean sceneEnabled) {
        this.code = code;
        this.label = label;
        this.sceneEnabled = sceneEnabled;
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

    /**
     * 返回全部模块选项（供管理端下拉展示），每项携带 value / label / sceneEnabled
     */
    public static List<BackgroundModuleOption> options() {
        List<BackgroundModuleOption> list = new ArrayList<>();
        for (BackgroundModuleEnum item : values()) {
            list.add(new BackgroundModuleOption(item.code, item.label, item.sceneEnabled));
        }
        return list;
    }
}
