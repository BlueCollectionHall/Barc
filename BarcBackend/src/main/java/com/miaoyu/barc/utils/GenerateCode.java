package com.miaoyu.barc.utils;

import java.security.SecureRandom;

public class GenerateCode {
    private static final SecureRandom RANDOM = new SecureRandom();
    /** 验证码属于认证凭据，使用密码学随机源，且限制长度避免非预期内存消耗。 */
    public String code(int length){
        if (length < 1 || length > 32) throw new IllegalArgumentException("验证码长度无效");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < length; i++) {
            int randomNum = RANDOM.nextInt(10);
            sb.append(randomNum);
        }
        return sb.toString();
    }
}
