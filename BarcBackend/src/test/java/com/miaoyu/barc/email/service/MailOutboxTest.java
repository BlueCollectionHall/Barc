package com.miaoyu.barc.email.service;

import com.miaoyu.barc.email.mapper.MailOutboxMapper;
import com.miaoyu.barc.email.utils.SendEmailUtils;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.mockito.Mockito.*;

/** 覆盖投递成功、失败重试和并发抢占；不连接 SMTP。 */
@ExtendWith(MockitoExtension.class)
class MailOutboxTest {
    @Mock MailOutboxMapper mapper;
    @Mock SendEmailUtils sender;
    @InjectMocks MailOutboxScheduler scheduler;
    @InjectMocks MailOutbox outbox;

    @Test void queuesBusinessEventAndSkipsAbsentRecipient() {
        outbox.enqueue("e", "user@example.com", "标题", "正文");
        verify(mapper).enqueue(anyString(), eq("e"), eq("user@example.com"), eq("标题"), eq("正文"), isNull());
        outbox.enqueue("e", null, "", ""); outbox.enqueue("e", " ", "", "");
        verifyNoMoreInteractions(mapper); verifyNoInteractions(sender);
    }

    @Test void leasePreventsConcurrentSendAndFailuresRemainRetryable() {
        var row = new MailOutboxMapper.Mail(); row.setId("m"); row.setRecipient("user@example.com"); row.setSubject("标题"); row.setBody("内容");
        when(mapper.pending()).thenReturn(List.of(row)); when(mapper.claim(eq("m"), anyString())).thenReturn(0, 1, 1, 1);
        scheduler.deliver(); verifyNoInteractions(sender);
        when(sender.customEmail(anyString(), anyString(), anyString())).thenReturn(false, true).thenThrow(new IllegalStateException("断网"));
        scheduler.deliver(); scheduler.deliver(); scheduler.deliver();
        verify(mapper, times(2)).retry(eq("m"), anyString()); verify(mapper).sent(eq("m"), anyString());
    }
}
