package com.miaoyu.barc.user.service;

import com.miaoyu.barc.exception.ApiException;
import com.miaoyu.barc.response.ErrorR;
import com.miaoyu.barc.security.RequestLimits;
import com.miaoyu.barc.user.mapper.VerificationCodeMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/** 各业务共用一次性验证码消费；调用方必须把消费与实际业务写入包在同一事务。 */
@Service
public class VerificationService {
    @Autowired private VerificationCodeMapper verificationCodeMapper;
    @Autowired private RequestLimits limits;

    public void consume(String id, String email, String scenario, String code) {
        if (id == null || id.length() > 36 || code == null || !code.matches("[0-9]{6}")) {
            throw new ApiException(new ErrorR().normal("验证码会话或格式无效"));
        }
        // 错误尝试限流不随业务事务回滚，避免反复回滚重新获得猜测次数。
        limits.require("verification:" + id, 5, 30 * 60 * 1000L);
        if (verificationCodeMapper.consume(id, email, scenario, code) != 1) {
            throw new ApiException(new ErrorR().normal("验证码错误、已使用或已过期，请重新获取"));
        }
    }
}
