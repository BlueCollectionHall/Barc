package com.miaoyu.barc.email.controller;

import com.miaoyu.barc.email.service.SendEmailService;
import com.miaoyu.barc.utils.J;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/email/send")
public class SendEmailController {
    @Autowired
    private SendEmailService sendEmailService;

    /**发送注册验证码
     * @param email 电子邮箱
     * @return 验证码唯一ID*/
    @GetMapping("/signup")
    public ResponseEntity<J> sendSignupEmailControl(@RequestParam("email") String email) {
        return sendEmailService.sendSignupEmailService(email);
    }

    /**发送重置密码验证码
     * @param email 电子邮箱
     * @return 验证码唯一ID*/
    @GetMapping("/reset_password")
    public ResponseEntity<J> sendResetPasswordEmailControl(@RequestParam("email") String email) {
        return sendEmailService.sendResetPasswordEmailService(email);
    }
}
