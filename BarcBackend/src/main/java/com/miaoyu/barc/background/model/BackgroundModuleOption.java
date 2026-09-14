package com.miaoyu.barc.background.model;

import lombok.Getter;
import lombok.Setter;

/**
 * 背景模块选项（管理端下拉展示用）
 * 对应 {@link com.miaoyu.barc.background.enumeration.BackgroundModuleEnum} 的每一项。
 * value：模块 code（与数据库 module 字段 / COS 路径一致）
 * label：管理端展示的中文名
 * sceneEnabled：该模块是否启用「时段 + 节日」场景化筛选
 */
@Getter
@Setter
public class BackgroundModuleOption {
    private String value;
    private String label;
    /** 是否启用「时段 + 节日」场景化筛选 */
    private boolean sceneEnabled;

    public BackgroundModuleOption(String value, String label, boolean sceneEnabled) {
        this.value = value;
        this.label = label;
        this.sceneEnabled = sceneEnabled;
    }
}
