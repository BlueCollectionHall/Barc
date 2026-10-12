package com.miaoyu.barc;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import com.miaoyu.barc.api.scheduler.CosGcScheduler;
import com.miaoyu.barc.user.scheduler.UserBanScheduler;
import com.miaoyu.barc.utils.VerifyCodeCleanupService;
import com.miaoyu.barc.utils.tencent.cos.CosClient;
import org.springframework.mail.javamail.JavaMailSender;

/** 只检查完整容器装配；数据源固定为 H2，外部副作用与定时任务在测试中替换。 */
@SpringBootTest
@ActiveProfiles("test")
class BarcBackendApplicationTests {
    @MockitoBean private CosGcScheduler cosGcScheduler;
    @MockitoBean private com.miaoyu.barc.email.service.MailOutboxScheduler mailOutboxScheduler;
    @MockitoBean private UserBanScheduler userBanScheduler;
    @MockitoBean private VerifyCodeCleanupService verifyCodeCleanupService;
    @MockitoBean private CosClient cosClient;
    @MockitoBean private JavaMailSender javaMailSender;

    @Test
    void contextLoads() {
    }

}
