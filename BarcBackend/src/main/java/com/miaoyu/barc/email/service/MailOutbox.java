package com.miaoyu.barc.email.service;

import com.miaoyu.barc.email.mapper.MailOutboxMapper;
import java.time.LocalDateTime;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/** 在调用者事务内记录邮件任务；不在业务线程直接调用 SMTP。 */
@Service
public class MailOutbox {
    @Autowired private MailOutboxMapper mapper;

    public void enqueue(String eventKey, String recipient, String subject, String body) {
        enqueue(eventKey, recipient, subject, body, null);
    }

    /** 验证码邮件带过期时间，避免故障恢复后发送已经失效的验证码。 */
    public void enqueue(String eventKey, String recipient, String subject, String body, Integer validityMinutes) {
        if (recipient == null || recipient.isBlank()) return;
        mapper.enqueue(UUID.randomUUID().toString(), eventKey, recipient, subject, body, validityMinutes);
    }
}
