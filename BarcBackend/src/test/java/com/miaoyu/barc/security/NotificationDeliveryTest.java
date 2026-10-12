package com.miaoyu.barc.security;

import com.miaoyu.barc.comment.mapper.MessageBoardMapper;
import com.miaoyu.barc.comment.model.MessageBoardModel;
import com.miaoyu.barc.comment.service.*;
import com.miaoyu.barc.email.service.MailOutbox;
import com.miaoyu.barc.email.utils.SendEmailUtils;
import com.miaoyu.barc.user.enumeration.UserIdentityEnum;
import com.miaoyu.barc.user.mapper.*;
import com.miaoyu.barc.user.model.*;
import com.miaoyu.barc.utils.dto.*;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.javamail.JavaMailSender;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 留言写入结果与通知出箱保持一致；使用内存 MIME 验证传输，不发出真实邮件。 */
@ExtendWith(MockitoExtension.class)
class NotificationDeliveryTest {
    @Mock MessageBoardMapper messages;
    @Mock UserArchiveMapper archives;
    @Mock UserBasicMapper basics;
    @Mock MessageNotificationService notification;
    @Mock MailOutbox outbox;
    @Mock JavaMailSender sender;
    @InjectMocks MessageBoardService boards;
    @InjectMocks MessageNotificationService templates;
    @InjectMocks SendEmailUtils transport;

    @Test void publicListsClampSizeAndReportActualTotal() {
        when(messages.selectByLimit(100)).thenReturn(List.of(message()));
        assertEquals(1, ((List<?>) boards.getNewBoardMessagesService(999).getBody().getData()).size());
        when(messages.countAll()).thenReturn(21L); when(messages.selectPage(20, 20)).thenReturn(List.of(message()));
        var page = (PageResultDto<?>) boards.getPage(2, 20).getBody().getData();
        assertEquals(21L, page.getTotal()); assertEquals(2, page.getPage_num()); assertEquals(2, page.getTotal_page());
    }

    @Test void createUsesAuthenticatedAuthorAndReflectsDatabaseFailure() {
        when(messages.insert(any())).thenReturn(true, false);
        assertEquals(0, boards.uploadBoardMessageService("author", "  留言  ").getBody().getCode());
        assertNotEquals(0, boards.uploadBoardMessageService("author", "留言").getBody().getCode());
        verify(messages, times(2)).insert(argThat(row -> row.getAuthor().equals("author") && row.getContent().equals("留言") && row.getId().length() == 36));
    }

    @Test void deletionNotifiesOnlyAfterAdministratorActuallyDeletesAnotherAuthorsMessage() {
        when(messages.selectByIdForUpdate("m")).thenReturn(message());
        assertNotEquals(0, boards.deleteBoardMessageService("other", "m").getBody().getCode());
        moderator(); when(messages.delete("m")).thenReturn(false, true);
        assertNotEquals(0, boards.deleteBoardMessageService("admin", "m").getBody().getCode()); verifyNoInteractions(notification);
        assertEquals(0, boards.deleteBoardMessageService("admin", "m").getBody().getCode()); verify(notification).notifyMessageDeleted(any(), eq("admin"));
        assertEquals(0, boards.deleteBoardMessageService("author", "m").getBody().getCode());
        assertNotEquals(0, boards.deleteBoardMessageService("admin", "missing").getBody().getCode()); verifyNoMoreInteractions(notification);
    }

    @Test void managementPaginationAcceptsDefaultRequestAndEditsNotifyOnlyAfterSuccess() {
        moderator(); when(messages.countAdminByPage(any())).thenReturn(0L);
        var page = (PageResultDto<?>) boards.adminQueryBoardMessagesService("admin", null).getBody().getData();
        assertEquals(0L, page.getTotal()); assertEquals(1, page.getTotal_page());
        assertNotEquals(0, boards.adminUpdateBoardMessageService("admin", "missing", "正文", "原因").getBody().getCode());
        when(messages.selectByIdForUpdate("m")).thenReturn(message()); when(messages.updateById("m", "新正文")).thenReturn(false, true);
        assertNotEquals(0, boards.adminUpdateBoardMessageService("admin", "m", "新正文", null).getBody().getCode()); verifyNoInteractions(notification);
        assertEquals(0, boards.adminUpdateBoardMessageService("admin", "m", "  新正文  ", "原因").getBody().getCode());
        verify(notification).notifyMessageEdited(any(), eq("新正文"), eq("原因"), eq("admin"));
    }

    @Test void missingRecipientSkipsBothTemplatesWithoutCreatingOutboxJobs() {
        templates.notifyMessageDeleted(message(), "admin");
        when(basics.selectByUuid("author")).thenReturn(new UserBasicModel());
        templates.notifyMessageEdited(message(), "正文", null, "admin"); verifyNoInteractions(outbox);
    }

    @Test void templatesUseDatabaseRecipientAndSafeAdministratorFallback() {
        var author = new UserBasicModel(); author.setEmail("author@example.test"); when(basics.selectByUuid("author")).thenReturn(author);
        templates.notifyMessageDeleted(message(), "admin");
        verify(outbox).enqueue(eq("message-deleted:m"), eq("author@example.test"), eq("留言已被管理员删除"), contains("操作人：admin"));
        var admin = new UserBasicModel(); admin.setUsername("馆长"); when(basics.selectByUuid("admin")).thenReturn(admin);
        templates.notifyMessageEdited(message(), "新版", " ", "admin");
        templates.notifyMessageEdited(message(), "再改", "自定义原因", "admin");
        verify(outbox).enqueue(startsWith("message-edited:"), eq("author@example.test"), eq("留言已被管理员修改"), eq("原留言：原文；修改后：新版；操作人：馆长；原因：内容违规修改"));
        verify(outbox).enqueue(startsWith("message-edited:"), eq("author@example.test"), eq("留言已被管理员修改"), contains("原因：自定义原因"));
    }

    @Test void transportBuildsUtf8PlainTextAndReportsDeliveryFailure() throws Exception {
        MimeMessage mail = new MimeMessage(Session.getInstance(new Properties())); when(sender.createMimeMessage()).thenReturn(mail);
        assertTrue(transport.customEmail("author@example.test", "中文标题", "<script>只作文本</script>"));
        assertEquals("中文标题", mail.getSubject()); assertEquals("author@example.test", mail.getAllRecipients()[0].toString());
        assertEquals("<script>只作文本</script>", mail.getContent()); verify(sender).send(mail);
        doThrow(new IllegalStateException("测试传输失败")).when(sender).send(mail);
        assertFalse(transport.customEmail("author@example.test", "标题", "内容"));
    }

    private void moderator() { var user = new UserArchiveModel(); user.setIdentity(UserIdentityEnum.MANAGER); user.setPermission(2); when(archives.selectByUuid("admin")).thenReturn(user); }
    private MessageBoardModel message() { var model = new MessageBoardModel(); model.setId("m"); model.setAuthor("author"); model.setContent("原文"); return model; }
}
