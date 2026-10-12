package com.miaoyu.barc.user.model.dto;

/** 改密凭据在请求体传输，邮箱必须等于当前认证账号的已绑定邮箱。 */
public record PasswordResetRequest(String email, String unique_id, String code, String password) { }
