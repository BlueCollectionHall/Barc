package com.miaoyu.barc.email.service;

import com.miaoyu.barc.email.utils.SendEmailUtils;
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
    private SendEmailUtils sendEmailUtils;
    @Autowired
    private UserBasicMapper userBasicMapper;

    public ResponseEntity<J> sendSignupEmailService(String email) {
        String code = new GenerateCode().code(6);
        VerificationCodeModel vc = new VerificationCodeModel();
        vc.setUnique_id(new GenerateUUID().getUuid36l());
        vc.setCode(code);
        vc.setScenario("Signup");
        vc.setUsername(email);
        boolean insert = verificationCodeMapper.insert(vc, 30);
        if (insert) {
            boolean b = sendEmailUtils.signupEmail(email, code, 30);
            if (b) {
                return ResponseEntity.ok(new EmailR().rKeyEmail(vc.getUnique_id()));
            } else {
                return ResponseEntity.ok(new EmailR().email(false));
            }
        } else {
            return ResponseEntity.ok(new ErrorR().normal("获取失败"));
        }
    }

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
        verificationCodeMapper.insert(vc, 5);
        boolean b = sendEmailUtils.resetPasswordEmail(email, code, 5);
        if (!b) {
            return ResponseEntity.ok(new EmailR().email(false));
        }
        return ResponseEntity.ok(new EmailR().rKeyEmail(unique_id));
    }
}
