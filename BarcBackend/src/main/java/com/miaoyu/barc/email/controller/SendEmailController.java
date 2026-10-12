package com.miaoyu.barc.email.controller;

import com.miaoyu.barc.annotation.IgnoreAuth;
import com.miaoyu.barc.email.service.SendEmailService;
import com.miaoyu.barc.user.model.dto.EmailChangeRequest;
import com.miaoyu.barc.utils.J;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/** 发信属于写操作；公开注册/改密发码显式免登录，改绑必须认证且确认原密码。 */
@RestController
@RequestMapping("/email/send")
public class SendEmailController {
    @Autowired private SendEmailService sendEmailService;
    public record EmailRequest(String email) { }

    @IgnoreAuth @PostMapping("/signup")
    public ResponseEntity<J> sendSignupEmailControl(@RequestBody EmailRequest request) {
        return sendEmailService.sendSignupEmailService(request.email());
    }
    @IgnoreAuth @PostMapping("/reset_password")
    public ResponseEntity<J> sendResetPasswordEmailControl(@RequestBody EmailRequest request) {
        return sendEmailService.sendResetPasswordEmailService(request.email());
    }
    @PostMapping("/change_email")
    public ResponseEntity<J> sendChangeEmailControl(HttpServletRequest context, @RequestBody EmailChangeRequest request) {
        return sendEmailService.sendChangeEmail(context.getAttribute("uuid").toString(), request.email(), request.current_password());
    }
}
