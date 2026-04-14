package com.miaoyu.barc;

import com.miaoyu.barc.annotation.IgnoreAuth;
import com.miaoyu.barc.response.HelloR;
import com.miaoyu.barc.response.SuccessR;
import com.miaoyu.barc.utils.J;
import com.miaoyu.barc.utils.tencent.cos.CosService;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Date;

@RestController
public class HelloWorldController {
    @GetMapping("/hello")
    public ResponseEntity<J> hello() {
        return ResponseEntity.ok(new HelloR().Hello());
    }

    @Autowired
    private JavaMailSender mailSender;
    @GetMapping("/email")
    public ResponseEntity<String> sendTestEmail() {
        try {
//            SimpleMailMessage message = new SimpleMailMessage();
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message);
            helper.setFrom(new InternetAddress("admin@barc.work", "蔚蓝收录馆")); // 必须与配置的username一致
            helper.setTo("1260352143@qq.com");
            helper.setSubject("测试邮件");
            helper.setText("这是一封测试邮件");
            mailSender.send(message);
            return ResponseEntity.ok("邮件发送成功");
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(200).body("邮件发送失败: " + e.getMessage());
        }
    }

    @Autowired
    private HelloWorldService helloWorldService;

    @GetMapping("/hello/test_permission_anno")
    public ResponseEntity<J> testPermissionAnno(HttpServletRequest request) {
        return helloWorldService.testPermissionAnno(request.getAttribute("uuid").toString());
    }

    @Autowired
    private CosService cosService;
    @GetMapping("/test_cos")
    @IgnoreAuth
    public ResponseEntity<J> testCos(
            @RequestParam("key") String keyName
    ) {
        String s = cosService.generateSignedUrl(keyName, new Date(System.currentTimeMillis() + 60 * 1000), null);
        return ResponseEntity.ok(new SuccessR().normal(s));
    }
}
