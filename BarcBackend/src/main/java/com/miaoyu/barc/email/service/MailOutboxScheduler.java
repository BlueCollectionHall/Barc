package com.miaoyu.barc.email.service;

import com.miaoyu.barc.email.mapper.MailOutboxMapper;
import com.miaoyu.barc.email.utils.SendEmailUtils;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 提交后的任务按租约投递。SMTP 无事务确认协议，崩溃恢复极端情况下可能重复投递。 */
@Component
public class MailOutboxScheduler {
    @Autowired private MailOutboxMapper mapper;
    @Autowired private SendEmailUtils sender;

    @Scheduled(fixedDelay = 10000, initialDelay = 10000)
    public void deliver() {
        for (var mail : mapper.pending()) {
            String lease = UUID.randomUUID().toString();
            if (mapper.claim(mail.getId(), lease) != 1) continue;
            try {
                if (sender.customEmail(mail.getRecipient(), mail.getSubject(), mail.getBody())) {
                    mapper.sent(mail.getId(), lease);
                } else mapper.retry(mail.getId(), lease);
            } catch (RuntimeException failure) {
                // 只记录重试状态，不将验证码、邮箱或正文写入日志。
                mapper.retry(mail.getId(), lease);
            }
        }
    }
}
