package com.miaoyu.barc.feedback.service;

import com.miaoyu.barc.api.work.mapper.WorkMapper;
import com.miaoyu.barc.email.utils.SendEmailUtils;
import com.miaoyu.barc.feedback.enumeration.FeedbackStatusEnum;
import com.miaoyu.barc.feedback.enumeration.FeedbackTypeEnum;
import com.miaoyu.barc.feedback.mapper.FeedbackMapper;
import com.miaoyu.barc.feedback.model.FeedbackFormModel;
import com.miaoyu.barc.permission.ComparePermission;
import com.miaoyu.barc.permission.PermissionConst;
import com.miaoyu.barc.user.mapper.UserArchiveMapper;
import com.miaoyu.barc.user.mapper.UserBasicMapper;
import com.miaoyu.barc.user.model.UserArchiveModel;
import com.miaoyu.barc.utils.J;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FeedbackServiceWorkReviewTest {

    @Mock private UserBasicMapper userBasicMapper;
    @Mock private UserArchiveMapper userArchiveMapper;
    @Mock private WorkMapper workMapper;
    @Mock private SendEmailUtils sendEmailUtils;
    @Mock private FeedbackMapper feedbackMapper;
    @Mock private ComparePermission comparePermission;

    @InjectMocks
    private FeedbackService feedbackService;

    @Test
    @DisplayName("二级维护员可通过通用反馈服务审核 WORK 投诉并写入处理结果")
    void updateFeedbackService_WorkFeedback_ShouldUseGenericFeedbackUpdate() {
        UserArchiveModel manager = new UserArchiveModel();
        manager.setPermission(PermissionConst.SEC_MAINTAINER);
        FeedbackFormModel feedback = new FeedbackFormModel();
        feedback.setId("feedback-1");
        feedback.setType(FeedbackTypeEnum.WORK);
        feedback.setStatus(FeedbackStatusEnum.COMPLETED);
        feedback.setEcho("已处理作品投诉");
        when(feedbackMapper.selectById("feedback-1")).thenReturn(feedback);
        when(userArchiveMapper.selectByUuid("manager-1")).thenReturn(manager);
        when(comparePermission.has(PermissionConst.SEC_MAINTAINER, PermissionConst.SEC_MAINTAINER)).thenReturn(true);
        when(feedbackMapper.update(feedback)).thenReturn(true);

        ResponseEntity<J> response = feedbackService.updateFeedbackService("manager-1", feedback);

        J body = response.getBody();
        assertNotNull(body);
        assertEquals(0, body.getCode());
        verify(feedbackMapper).update(feedback);
    }
}
