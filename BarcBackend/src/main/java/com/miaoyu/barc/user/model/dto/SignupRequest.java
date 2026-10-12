package com.miaoyu.barc.user.model.dto;

/** 注册只暴露业务输入，不允许客户端指定安全等级、身份、验证状态和主键。 */
public record SignupRequest(String username, String password, String email, String telephone, String unique_id, String code) { }
