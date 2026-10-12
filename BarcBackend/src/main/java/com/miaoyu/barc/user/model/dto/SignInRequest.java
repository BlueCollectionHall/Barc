package com.miaoyu.barc.user.model.dto;

/** 登录凭据只接受 JSON 请求体，避免密码进入 URL、代理日志或浏览器历史。 */
public record SignInRequest(String type, String account, String password) { }
