package com.miaoyu.barc.user.enumeration;

import lombok.Getter;

/**
 * 官方业务指定账号的业务键。
 *
 * 设计约定：
 * 1、{@code displayName} 是「业务名」，与具体绑定账号的昵称解耦：例如业务名「未认领收录作品临时归属」
 *    当前绑定到昵称为「蔚蓝收录认领助手」的账号，账号改名不影响业务名；
 * 2、落库的业务键取 {@code name()}，因此新增业务只需在此追加枚举值，管理端列表会自动出现，无需改表；
 * 3、不提供「管理端自由新增业务键」能力：后端调用点必须能静态引用枚举，才能保证类型安全。
 */
@Getter
public enum OfficialAccountEnum {

    /**
     * 未认领收录作品的临时归属账号：搬运收录上传、管理员撤销认领时写入 work.author。
     *
     * 说明文案（{@code description}）会直接渲染在管理端表格的「说明」列里，列宽有限，
     * 因此必须保持简短、一行内展示完整，禁止堆砌实现细节（如 author 字段名）。
     */
    COLLECTION_ASSISTANT("未认领收录作品临时归属",
            "搬运收录与撤销认领时，未认领作品临时归属该账号。");

    /** 业务名（管理端展示，与绑定账号昵称无关） */
    private final String displayName;
    /** 业务说明（管理端展示） */
    private final String description;

    OfficialAccountEnum(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }

    /**
     * 按业务键解析业务枚举。
     *
     * @param name 业务键（即枚举 name()，允许前后空格）
     * @return 匹配的枚举；为空或不合法时返回 null，由调用方拒绝该请求
     */
    public static OfficialAccountEnum fromName(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        String trimmed = name.trim();
        for (OfficialAccountEnum item : values()) {
            if (item.name().equals(trimmed)) {
                return item;
            }
        }
        return null;
    }
}
