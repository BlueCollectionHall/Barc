package com.miaoyu.barc.security;

import com.miaoyu.barc.exception.ApiException;
import com.miaoyu.barc.response.ErrorR;
import java.util.Locale;

/** 账号写入的长度与格式边界，早于哈希、查询和验证码消费。 */
public final class AccountInput {
    private AccountInput() { }
    public static String email(String value) {
        if (value == null || value.length() > 255 || !value.trim().matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+")) {
            throw new ApiException(new ErrorR().normal("邮箱格式无效"));
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }
    public static void password(String value) {
        if (value == null || value.length() < 8 || value.length() > 128) {
            throw new ApiException(new ErrorR().normal("密码长度须为 8 至 128 个字符"));
        }
    }
    public static String username(String value) {
        if (value == null || value.isBlank() || value.length() > 50 || !value.equals(value.trim())) {
            throw new ApiException(new ErrorR().normal("用户名须为 1 至 50 个字符且不能有首尾空格"));
        }
        return value;
    }
}
