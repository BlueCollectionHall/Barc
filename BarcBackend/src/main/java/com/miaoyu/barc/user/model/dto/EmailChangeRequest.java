package com.miaoyu.barc.user.model.dto;

/** 邮箱改绑须验证当前口令和新邮箱的一次性验证码。 */
public record EmailChangeRequest(String email, String current_password, String unique_id, String code) { }
