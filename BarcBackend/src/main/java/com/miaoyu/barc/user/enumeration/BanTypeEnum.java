package com.miaoyu.barc.user.enumeration;

/**
 * 封号类型枚举
 * 
 * 与safe_level值对应：
 * - RISK_FREEZE(0): 风险冻结，safe_level=0
 * - TEMPORARY_BAN(1): 临时封号，safe_level=-1
 * - VIOLATION_BAN(2): 违规封号，safe_level=-2
 * - SOFT_DELETE(3): 软删除，safe_level=-3
 */
public enum BanTypeEnum {
    RISK_FREEZE(0, "风险冻结", 0),
    TEMPORARY_BAN(1, "临时封号", -1),
    VIOLATION_BAN(2, "违规封号", -2),
    SOFT_DELETE(3, "软删除", -3);

    private final int code;
    private final String description;
    private final int safeLevelValue;

    BanTypeEnum(int code, String description, int safeLevelValue) {
        this.code = code;
        this.description = description;
        this.safeLevelValue = safeLevelValue;
    }

    public int getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }

    public int getSafeLevelValue() {
        return safeLevelValue;
    }

    /**
     * 根据code获取枚举
     */
    public static BanTypeEnum fromCode(int code) {
        for (BanTypeEnum type : values()) {
            if (type.code == code) {
                return type;
            }
        }
        throw new IllegalArgumentException("无效的封号类型代码: " + code);
    }

    /**
     * 根据safe_level值获取封号状态描述
     */
    public static String getStatusBySafeLevel(int safeLevel) {
        if (safeLevel >= 0) {
            return "正常";
        }
        for (BanTypeEnum type : values()) {
            if (type.safeLevelValue == safeLevel) {
                return type.description;
            }
        }
        return "未知状态";
    }
}
