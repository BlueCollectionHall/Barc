package com.miaoyu.barc.email.service;

import com.miaoyu.barc.exception.ApiException;
import com.miaoyu.barc.response.EmailR;
import com.miaoyu.barc.response.ErrorR;
import com.miaoyu.barc.response.UserR;
import com.miaoyu.barc.security.AccountInput;
import com.miaoyu.barc.user.mapper.UserBasicMapper;
import com.miaoyu.barc.user.mapper.VerificationCodeMapper;
import com.miaoyu.barc.user.model.VerificationCodeModel;
import com.miaoyu.barc.user.service.AccountSecurityService;
import com.miaoyu.barc.utils.GenerateCode;
import com.miaoyu.barc.utils.GenerateUUID;
import com.miaoyu.barc.utils.J;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 同一邮箱/场景串行签发验证码，事务提交后才异步发信。 */
@Service
public class SendEmailService {
    @Autowired private VerificationCodeMapper verificationCodeMapper;
    @Autowired private MailOutbox mailOutbox;
    @Autowired private UserBasicMapper userBasicMapper;
    @Autowired private AccountSecurityService accountSecurityService;

    @Transactional
    public ResponseEntity<J> sendSignupEmailService(String email) {
        String normalized = AccountInput.email(email);
        if (userBasicMapper.selectByEmail(normalized) != null) return ResponseEntity.ok(new ErrorR().normal("邮箱已被使用"));
        return issue(normalized, "Signup", 30);
    }

    @Transactional
    public ResponseEntity<J> sendResetPasswordEmailService(String email) {
        String normalized = AccountInput.email(email);
        if (userBasicMapper.selectByEmail(normalized) == null) return ResponseEntity.ok(new UserR().noSuchUser());
        return issue(normalized, "ResetPassword", 5);
    }

    @Transactional
    public ResponseEntity<J> sendChangeEmail(String uuid, String email, String currentPassword) {
        accountSecurityService.verifyCurrentPassword(uuid, currentPassword);
        String normalized = AccountInput.email(email);
        if (userBasicMapper.selectByEmail(normalized) != null) return ResponseEntity.ok(new ErrorR().normal("邮箱已被使用"));
        return issue(normalized, "ChangeEmail", 5);
    }

    private ResponseEntity<J> issue(String email, String scenario, int minutes) {
        verificationCodeMapper.ensureIssueGuard(email, scenario);
        if (verificationCodeMapper.tryMarkIssued(email, scenario) != 1) {
            throw new ApiException(new ErrorR().normal("验证码发送过于频繁，请一分钟后重试"));
        }
        // 先在持锁事务中作废旧码，再插入新码；任一步失败整个事务回滚。
        verificationCodeMapper.invalidatePrevious(email, scenario);
        VerificationCodeModel code = new VerificationCodeModel();
        code.setUnique_id(new GenerateUUID().getUuid36l());
        code.setCode(new GenerateCode().code(6));
        code.setUsername(email); code.setScenario(scenario);
        if (!verificationCodeMapper.insert(code, minutes)) throw new ApiException(new ErrorR().normal("验证码生成失败"));
        // 持久任务和验证码同事务落库，SMTP 故障可重试；回滚时两者同时撤销。
        mailOutbox.enqueue("verification:" + code.getUnique_id(), email, "蔚蓝收录馆验证码",
                "您的 " + scenario + " 验证码为 " + code.getCode() + "，有效期 " + minutes + " 分钟，请勿向他人提供。",
                minutes);
        return ResponseEntity.ok(new EmailR().rKeyEmail(code.getUnique_id()));
    }
}
