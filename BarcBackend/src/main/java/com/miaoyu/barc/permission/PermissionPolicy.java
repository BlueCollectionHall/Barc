package com.miaoyu.barc.permission;

import com.miaoyu.barc.user.enumeration.UserIdentityEnum;
import com.miaoyu.barc.user.model.UserArchiveModel;

/** 权限取值边界；不改变宪法规定的“比大小”和“1匹配”计算规则。 */
public final class PermissionPolicy {
    private PermissionPolicy() { }

    /** 只接受对应身份已有角色位的非空组合，负数与未知高位均无效。 */
    public static boolean isValid(UserIdentityEnum identity, Integer permission) {
        if (identity == null || permission == null || permission <= 0) return false;
        int allowed = identity == UserIdentityEnum.MANAGER ? 63 : 7;
        return (permission & ~allowed) == 0;
    }

    /** 手写业务授权也必须先确认身份与权限值有效，再执行约定的比较方式。 */
    public static boolean matches(UserArchiveModel user, UserIdentityEnum identity, int target, boolean anyBit) {
        if (user == null || user.getIdentity() != identity || !isValid(identity, user.getPermission())) return false;
        return anyBit ? (user.getPermission() & target) != 0 : user.getPermission() >= target;
    }

    /** 评论、留言及对应举报统一由一级管理员角色位或副馆长及以上治理。 */
    public static boolean canModerateDiscussion(UserArchiveModel user) {
        return matches(user, UserIdentityEnum.MANAGER, PermissionConst.FIR_MAINTAINER, true)
                || matches(user, UserIdentityEnum.MANAGER, PermissionConst.ADMINISTRATOR, false);
    }
}
