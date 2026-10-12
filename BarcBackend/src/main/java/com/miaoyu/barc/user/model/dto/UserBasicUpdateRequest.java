package com.miaoyu.barc.user.model.dto;

/** 基础资料普通编辑不能改变邮箱；改用户名须再次确认当前口令。 */
public record UserBasicUpdateRequest(String uuid, String username, String email, String telephone, String current_password) { }
