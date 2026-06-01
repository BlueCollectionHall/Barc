package com.miaoyu.barc.email.utils;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.io.UnsupportedEncodingException;
import java.net.UnknownHostException;

@Service
public class SendEmailUtils {
    @Autowired
    private JavaMailSender mailSender;

    public boolean customEmail(String to, String subject, String content) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message);
            helper.setFrom(new InternetAddress("admin@barc.work", "蔚蓝收录馆")); // 必须与配置的username一致
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(content);
            mailSender.send(message);
        } catch (Exception e) {
            return false;
        }
        return true;
    }

    public boolean signupEmail(String to, String code, int minute) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message);
            helper.setFrom(new InternetAddress("admin@barc.work", "蔚蓝收录馆")); // 必须与配置的username一致
            helper.setTo(to);
            helper.setSubject("注册蔚蓝收录馆");
            helper.setText("Sensei！欢迎您注册蔚蓝收录馆账号。注册验证码是：" + code + "，有效期" + minute + "分钟！");
            mailSender.send(message);
        }  catch (jakarta.mail.MessagingException | UnsupportedEncodingException e) {
//            throw new RuntimeException(e);
            return false;
        }
        return true;
    }
    public boolean resetPasswordEmail(String to, String code, int minute) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message);
            helper.setFrom(new InternetAddress("admin@barc.work", "蔚蓝收录馆")); // 必须与配置的username一致
            helper.setTo(to);
            helper.setSubject("重置密码");
            helper.setText("Sensei！您正在重置密码，重置验证码是：" + code + "，有效期" + minute + "分钟！");
            mailSender.send(message);
        }  catch (jakarta.mail.MessagingException | UnsupportedEncodingException e) {
//            throw new RuntimeException(e);
            return false;
        }
        return true;
    }

    /**
     * 管理员删除留言通知
     */
    public boolean messageDeletedByAdminEmail(String to, String originalContent, String reason, String adminUsername) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message);
            helper.setFrom(new InternetAddress("admin@barc.work", "蔚蓝收录馆"));
            helper.setTo(to);
            helper.setSubject("您的蔚蓝收录馆留言已被管理员删除");
            helper.setText(
                "Sensei！您在蔚蓝收录馆留言板的留言已被管理员删除。\n\n" +
                "被删除的留言内容：" + originalContent + "\n" +
                "操作管理员：" + adminUsername + "\n" +
                "处理原因：" + reason + "\n" +
                "如有疑问，请联系蔚蓝收录馆管理团队。"
            );
            mailSender.send(message);
        } catch (Exception e) {
            return false;
        }
        return true;
    }

    /**
     * 管理员修改留言通知
     */
    public boolean messageEditedByAdminEmail(String to, String oldContent, String newContent, String reason, String adminUsername) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message);
            helper.setFrom(new InternetAddress("admin@barc.work", "蔚蓝收录馆"));
            helper.setTo(to);
            helper.setSubject("您的蔚蓝收录馆留言已被管理员修改");
            helper.setText(
                "Sensei！您在蔚蓝收录馆留言板的留言已被管理员修改。\n\n" +
                "原留言内容：" + oldContent + "\n" +
                "修改后内容：" + newContent + "\n" +
                "操作管理员：" + adminUsername + "\n" +
                "处理原因：" + reason + "\n" +
                "如有疑问，请联系蔚蓝收录馆管理团队。"
            );
            mailSender.send(message);
        } catch (Exception e) {
            return false;
        }
        return true;
    }
}
