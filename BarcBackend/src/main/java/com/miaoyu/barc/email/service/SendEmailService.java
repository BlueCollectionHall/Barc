package com.miaoyu.barc.email.service;

import com.miaoyu.barc.response.EmailR;
import com.miaoyu.barc.response.ErrorR;
import com.miaoyu.barc.response.UserR;
import com.miaoyu.barc.user.mapper.UserBasicMapper;
import com.miaoyu.barc.user.mapper.VerificationCodeMapper;
import com.miaoyu.barc.user.model.UserBasicModel;
import com.miaoyu.barc.user.model.VerificationCodeModel;
import com.miaoyu.barc.utils.GenerateCode;
import com.miaoyu.barc.utils.GenerateUUID;
import com.miaoyu.barc.utils.J;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.util.Objects;

@Service
public class SendEmailService {
    @Autowired
    private VerificationCodeMapper verificationCodeMapper;
    @Autowired
    private MailDispatcher mailDispatcher;
    @Autowired
    private UserBasicMapper userBasicMapper;

    /**
     * 发送注册验证码。
     *
     * <p>性能约定：本方法只负责「生成验证码 → 落库 → 立即返回 unique_id」，
     * 真正的 SMTP 发信交给 {@link MailDispatcher#dispatchSignupEmail} 异步执行。
     * 原因是同步 SMTP 实测耗时 12~13 秒，超过前端 axios 的 10 秒超时，
     * 会让前端拿不到 unique_id 从而使注册链路断掉。</p>
     *
     * <p>同一邮箱的旧注册验证码会被作废（标记 used），避免出现
     * 「新的 unique_id 搭配旧邮件里的验证码」这种必然报错的组合。</p>
     *
     * @param email 接收验证码的邮箱
     * @return code=0 且 data 为该次验证码的唯一 ID
     */
    public ResponseEntity<J> sendSignupEmailService(String email) {
        String code = new GenerateCode().code(6);
        VerificationCodeModel vc = new VerificationCodeModel();
        vc.setUnique_id(new GenerateUUID().getUuid36l());
        vc.setCode(code);
        vc.setScenario("Signup");
        vc.setUsername(email);
        boolean insert = verificationCodeMapper.insert(vc, 30);
        if (!insert) {
            return ResponseEntity.ok(new ErrorR().normal("获取失败"));
        }
        // 落库成功后再作废该邮箱此前的注册验证码，避免作废了却没能发出新码
        invalidatePreviousSignupCodes(email, vc.getUnique_id());
        // 异步发信：接口不等待 SMTP，保证 unique_id 能及时返回前端
        mailDispatcher.dispatchSignupEmail(email, code, 30);
        return ResponseEntity.ok(new EmailR().rKeyEmail(vc.getUnique_id()));
    }

    /**
     * 作废同一邮箱此前未使用的注册验证码。
     *
     * <p>并发或重复点击「获取验证码」时，最新一条记录就是本次刚插入的记录，应当跳过不处理。</p>
     *
     * @param email 邮箱
     * @param currentUniqueId 本次新签发的验证码唯一 ID
     */
    private void invalidatePreviousSignupCodes(String email, String currentUniqueId) {
        VerificationCodeModel latest = verificationCodeMapper.selectLatestByUsernameAndScenario(email, "Signup");
        if (Objects.isNull(latest) || Objects.isNull(latest.getUnique_id())) {
            return;
        }
        if (latest.getUnique_id().equals(currentUniqueId)) {
            return;
        }
        // 极端情况下库中最新一条不是本次插入的记录（例如并发插入），仍然按 unique_id 精确作废
        verificationCodeMapper.updateUsed(latest.getUnique_id());
    }

    /**
     * 发送重置密码验证码。
     *
     * <p>与注册保持一致：先落库并立即返回，邮件异步发送。</p>
     *
     * @param email 接收验证码的邮箱
     * @return code=0 且 data 为该次验证码的唯一 ID
     */
    public ResponseEntity<J> sendResetPasswordEmailService(String email) {
        UserBasicModel userBasic = userBasicMapper.selectByEmail(email);
        if (Objects.isNull(userBasic)) {
            return ResponseEntity.ok(new UserR().noSuchUser());
        }
        String code = new GenerateCode().code(6);
        VerificationCodeModel vc = new VerificationCodeModel();
        String unique_id = new GenerateUUID().getUuid36l();
        vc.setUnique_id(unique_id);
        vc.setCode(code);
        vc.setScenario("ResetPassword");
        vc.setUsername(email);
        boolean insert = verificationCodeMapper.insert(vc, 5);
        if (!insert) {
            return ResponseEntity.ok(new ErrorR().normal("获取失败"));
        }
        mailDispatcher.dispatchResetPasswordEmail(email, code, 5);
        return ResponseEntity.ok(new EmailR().rKeyEmail(unique_id));
    }
}
