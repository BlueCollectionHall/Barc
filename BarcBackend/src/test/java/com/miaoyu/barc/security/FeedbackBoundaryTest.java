package com.miaoyu.barc.security;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.miaoyu.barc.feedback.model.*;
import com.miaoyu.barc.feedback.enumeration.*;
import com.miaoyu.barc.feedback.service.FeedbackService;
import com.miaoyu.barc.feedback.mapper.FeedbackMapper;
import com.miaoyu.barc.api.work.mapper.WorkMapper;
import com.miaoyu.barc.comment.mapper.*;
import com.miaoyu.barc.email.service.MailOutbox;
import com.miaoyu.barc.exception.ApiException;
import com.miaoyu.barc.user.mapper.*;
import com.miaoyu.barc.user.model.*;
import com.miaoyu.barc.user.enumeration.UserIdentityEnum;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 反馈可匿名，但作者、状态、回执和来源均不得由匿名 JSON 冒充。 */
@ExtendWith(MockitoExtension.class)
class FeedbackBoundaryTest {
    @Mock UserBasicMapper users;
    @Mock UserArchiveMapper archives;
    @Mock WorkMapper works;
    @Mock WorkCommentMapper comments;
    @Mock WorkCommentReplyMapper replies;
    @Mock MessageBoardMapper messages;
    @Mock FeedbackMapper feedback;
    @Mock MailOutbox mail;
    @InjectMocks FeedbackService service;

    @Test void forgedJsonFieldsAreDiscardedAndAnonymousDoesNotTriggerMail() throws Exception {
        var json = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        var request = json.readValue("{\"type\":\"BUG\",\"content\":\"错误信息\",\"author\":\"victim\",\"echo\":\"已处理\",\"status\":\"COMPLETED\",\"ipv4\":\"8.8.8.8\",\"email\":\"anon@example.com\"}", FeedbackCreateRequest.class);
        when(feedback.insert(any())).thenReturn(true);
        assertEquals(0, service.uploadFeedbackService(null, "127.0.0.1", request).getBody().getCode());
        ArgumentCaptor<FeedbackFormModel> saved = ArgumentCaptor.forClass(FeedbackFormModel.class);
        verify(feedback).insert(saved.capture());
        assertNull(saved.getValue().getAuthor()); assertNull(saved.getValue().getEcho());
        assertEquals(FeedbackStatusEnum.PENDING, saved.getValue().getStatus());
        assertEquals("127.0.0.1", saved.getValue().getIpv4());
        verifyNoInteractions(users, mail);
    }

    @Test void verifiedIdentityOwnsFeedbackRegardlessOfContactField() {
        UserBasicModel owner = new UserBasicModel(); owner.setEmail("owner@example.com");
        when(users.selectByUuid("owner")).thenReturn(owner); when(feedback.insert(any())).thenReturn(true);
        service.uploadFeedbackService("owner", "::1", new FeedbackCreateRequest(FeedbackTypeEnum.OTHER, null, "内容", "victim@example.com"));
        verify(feedback).insert(argThat(model -> "owner".equals(model.getAuthor()) && "owner@example.com".equals(model.getEmail()) && "::1".equals(model.getIpv6())));
        verify(mail).enqueue(anyString(), eq("owner@example.com"), anyString(), anyString());
    }

    @Test void targetValidationDoesNotDependOnEmail() {
        for (FeedbackTypeEnum type : new FeedbackTypeEnum[]{FeedbackTypeEnum.WORK, FeedbackTypeEnum.USER, FeedbackTypeEnum.COMMENT, FeedbackTypeEnum.MESSAGE_BOARD}) {
            assertEquals(1, service.uploadFeedbackService(null, null, new FeedbackCreateRequest(type, "missing", "投诉", null)).getBody().getCode());
        }
        verify(feedback, never()).insert(any()); verifyNoInteractions(mail);
        assertThrows(ApiException.class, () -> service.uploadFeedbackService(null, null, new FeedbackCreateRequest(null, null, "", null)));
    }

    @Test void replyCanBeReportedThroughUnifiedCommentType() {
        when(replies.selectById("reply")).thenReturn(new com.miaoyu.barc.comment.model.WorkCommentReplyModel());
        when(feedback.insert(any())).thenReturn(true);
        assertEquals(0, service.uploadFeedbackService(null, null, new FeedbackCreateRequest(FeedbackTypeEnum.COMMENT, "reply", "投诉回复", null)).getBody().getCode());
        verify(feedback).insert(argThat(item -> "reply".equals(item.getTarget_id()) && item.getType() == FeedbackTypeEnum.COMMENT));
    }

    @Test void detailIsReadOnlyAndAnonymousFeedbackCannotBeClaimedAsOwn() {
        var stored = pending(); manager();
        service.getFeedbackOnlyWithManagerService("manager", "f");
        assertEquals(FeedbackStatusEnum.PENDING, stored.getStatus());
        assertEquals(1, service.getFeedbackOnlyWithMeService("user", "f").getBody().getCode());
        verify(feedback, never()).update(any()); verifyNoInteractions(mail);
    }

    @Test void resultUsesStoredRecipientAndConcurrentOrTerminalUpdatesCannotOverwrite() {
        var stored = pending(); manager(); stored.setEmail("stored@example.com");
        var request = new FeedbackUpdateRequest("f", "完成", FeedbackStatusEnum.COMPLETED, FeedbackStatusEnum.PENDING);
        when(feedback.transition("f", FeedbackStatusEnum.PENDING, FeedbackStatusEnum.COMPLETED, "完成")).thenReturn(0, 1);
        assertThrows(ApiException.class, () -> service.updateFeedbackService("manager", request)); verifyNoInteractions(mail);
        assertEquals(0, service.updateFeedbackService("manager", request).getBody().getCode());
        verify(mail).enqueue(eq("feedback-result:f"), eq("stored@example.com"), anyString(), contains("完成"));
        stored.setStatus(FeedbackStatusEnum.COMPLETED); stored.setEcho("完成");
        service.updateFeedbackService("manager", request);
        verify(mail, times(1)).enqueue(anyString(), anyString(), anyString(), anyString());
        assertThrows(ApiException.class, () -> service.updateFeedbackService("manager", new FeedbackUpdateRequest("f", "重开", FeedbackStatusEnum.PROCESSING, null)));
    }

    private FeedbackFormModel pending() {
        var stored = new FeedbackFormModel(); stored.setId("f"); stored.setType(FeedbackTypeEnum.WORK); stored.setStatus(FeedbackStatusEnum.PENDING);
        when(feedback.selectById("f")).thenReturn(stored); return stored;
    }
    private void manager() {
        var manager = new UserArchiveModel(); manager.setIdentity(UserIdentityEnum.MANAGER); manager.setPermission(4);
        when(archives.selectByUuid("manager")).thenReturn(manager);
    }
}
