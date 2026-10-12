package com.miaoyu.barc.user.model.vo;

import com.miaoyu.barc.user.enumeration.UserIdentityEnum;
import com.miaoyu.barc.user.model.UserArchiveModel;

/** 公开主页/列表白名单：保留角色展示与年龄，不公开完整生日和账号管理状态。 */
public record PublicUserView(String uuid, String username, String nickname, String avatar,
                             Integer gender, Integer age, UserIdentityEnum identity, Integer permission) {
    public static PublicUserView from(UserInfoVo user) {
        return new PublicUserView(user.getUuid(), user.getUsername(), user.getNickname(), user.getAvatar(),
                user.getGender(), user.getAge(), user.getIdentity(), user.getPermission());
    }
    public static PublicUserView from(UserArchiveModel user) {
        return new PublicUserView(user.getUuid(), null, user.getNickname(), user.getAvatar(),
                user.getGender(), user.getAge(), user.getIdentity(), user.getPermission());
    }
}
