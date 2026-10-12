package com.miaoyu.barc.user.model.vo;

/** 用户选择器只返回可公开的定位和展示字段。 */
public record UserSearchView(String uuid, String username, String nickname, String avatar) {
    public static UserSearchView from(UserInfoVo user) {
        return new UserSearchView(user.getUuid(), user.getUsername(), user.getNickname(), user.getAvatar());
    }
}
