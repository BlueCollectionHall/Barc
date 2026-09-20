package com.miaoyu.barc.email.service;

import com.miaoyu.barc.AsyncConfig;
import com.miaoyu.barc.email.utils.SendEmailUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * 邮件异步发送器。
 *
 * <p>为什么单独成 Bean：{@code @Async} 依赖 Spring 代理生效，
 * 同一个 Bean 内部的自调用不会走代理，等于仍是同步执行。
 * 把发信动作放在独立 Bean 中，{@link SendEmailService} 通过注入调用，
 * 才能真正做到「接口先返回、邮件随后异步发送」。</p>
 *
 * <p>业务影响：SMTP 同步发信实测耗时 12~13 秒，超过前端 axios 的 10 秒超时，
 * 会让发码接口的 unique_id 无法返回给前端，进而使注册链路整体失败。</p>
 */
@Service
public class MailDispatcher {

    @Autowired
    private SendEmailUtils sendEmailUtils;

    /**
     * 异步发送注册验证码邮件。
     *
     * @param email 收件邮箱
     * @param code 验证码
     * @param minute 有效期（分钟）
     */
    @Async(AsyncConfig.MAIL_TASK_EXECUTOR)
    public void dispatchSignupEmail(String email, String code, int minute) {
        sendEmailUtils.signupEmail(email, code, minute);
    }

    /**
     * 异步发送重置密码验证码邮件。
     *
     * @param email 收件邮箱
     * @param code 验证码
     * @param minute 有效期（分钟）
     */
    @Async(AsyncConfig.MAIL_TASK_EXECUTOR)
    public void dispatchResetPasswordEmail(String email, String code, int minute) {
        sendEmailUtils.resetPasswordEmail(email, code, minute);
    }
}
