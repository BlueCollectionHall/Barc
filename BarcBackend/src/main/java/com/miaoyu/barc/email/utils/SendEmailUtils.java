package com.miaoyu.barc.email.utils;

import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

/** 邮件出箱的唯一传输适配器；验证码和通知模板由各业务构造后落库，禁止在业务事务中直接发信。 */
@Service
public class SendEmailUtils {
    @Autowired private JavaMailSender mailSender;

    /** 仅报告本次投递结果，重试、次数与下次时间由 MailOutboxScheduler 管理。 */
    public boolean customEmail(String to, String subject, String content) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message);
            helper.setFrom(new InternetAddress("admin@barc.work", "蔚蓝收录馆"));
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(content);
            mailSender.send(message);
            return true;
        } catch (Exception failure) {
            return false;
        }
    }
}
