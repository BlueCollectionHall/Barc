package com.miaoyu.barc;

import com.miaoyu.barc.response.HelloR;
import com.miaoyu.barc.utils.J;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
// 诊断接口仅在维护者显式启用 local 环境时注册，线上不暴露测试能力。
@Profile("local")
public class HelloWorldController {
    @GetMapping("/hello")
    public ResponseEntity<J> hello() {
        return ResponseEntity.ok(new HelloR().Hello());
    }

    // 邮件发送必须通过带业务校验与频率限制的正式服务；不提供公网测试邮件入口。

    @Autowired
    private HelloWorldService helloWorldService;

    @GetMapping("/hello/test_permission_anno")
    public ResponseEntity<J> testPermissionAnno(HttpServletRequest request) {
        return helloWorldService.testPermissionAnno(request.getAttribute("uuid").toString());
    }
}
