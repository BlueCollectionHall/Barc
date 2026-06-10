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

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FeedbackServicePermissionTest {

    @Mock private UserBasicMapper userBasicMapper;
    @Mock private UserArchiveMapper userArchiveMapper;
    @Mock private WorkMapper workMapper;
    @Mock private SendEmailUtils sendEmailUtils;
    @Mock private FeedbackMapper feedbackMapper;
    @Mock private ComparePermission comparePermission;

    @InjectMocks
    private FeedbackService feedbackService;

    @Test
    @DisplayName("二级维护员不可读取 USER 类型反馈列表")
    void getFeedbacksByTypeWithManagerService_UserFeedback_ShouldRejectSecondMaintainer() {
        UserArchiveModel manager = managerWithPermission(PermissionConst.SEC_MAINTAINER);
        when(userArchiveMapper.selectByUuid("manager-1")).thenReturn(manager);

        ResponseEntity<J> response = feedbackService.getFeedbacksByTypeWithManagerService("manager-1", FeedbackTypeEnum.USER);

        J body = response.getBody();
        assertNotNull(body);
        assertEquals(1, body.getCode());
        verify(feedbackMapper, never()).selectByType(FeedbackTypeEnum.USER);
    }

    @Test
    @DisplayName("三级维护员可读取 USER 类型反馈列表")
    void getFeedbacksByTypeWithManagerService_UserFeedback_ShouldAllowThirdMaintainer() {
        UserArchiveModel manager = managerWithPermission(PermissionConst.THI_MAINTAINER);
        lenient().when(userArchiveMapper.selectByUuid("manager-1")).thenReturn(manager);
        lenient().when(comparePermission.has(PermissionConst.THI_MAINTAINER, PermissionConst.THI_MAINTAINER)).thenReturn(true);
        when(feedbackMapper.selectByType(FeedbackTypeEnum.USER)).thenReturn(List.of(new FeedbackFormModel()));

        ResponseEntity<J> response = feedbackService.getFeedbacksByTypeWithManagerService("manager-1", FeedbackTypeEnum.USER);

        J body = response.getBody();
        assertNotNull(body);
        assertEquals(0, body.getCode());
        verify(feedbackMapper).selectByType(FeedbackTypeEnum.USER);
    }

    @Test
    @DisplayName("三级维护员不可读取 BUG 类型反馈列表")
    void getFeedbacksByTypeWithManagerService_BugFeedback_ShouldRejectThirdMaintainer() {
        UserArchiveModel manager = managerWithPermission(PermissionConst.THI_MAINTAINER);
        when(userArchiveMapper.selectByUuid("manager-1")).thenReturn(manager);

        ResponseEntity<J> response = feedbackService.getFeedbacksByTypeWithManagerService("manager-1", FeedbackTypeEnum.BUG);

        J body = response.getBody();
        assertNotNull(body);
        assertEquals(1, body.getCode());
        verify(feedbackMapper, never()).selectByType(FeedbackTypeEnum.BUG);
    }

    @Test
    @DisplayName("二级维护员不可查看 USER 类型反馈详情且不得将 PENDING 改为 PROCESSING")
    void getFeedbackOnlyWithManagerService_UserPendingFeedback_ShouldRejectAndKeepPending() {
        UserArchiveModel manager = managerWithPermission(PermissionConst.SEC_MAINTAINER);
        FeedbackFormModel feedback = new FeedbackFormModel();
        feedback.setId("feedback-1");
        feedback.setType(FeedbackTypeEnum.USER);
        feedback.setStatus(FeedbackStatusEnum.PENDING);
        when(userArchiveMapper.selectByUuid("manager-1")).thenReturn(manager);
        when(feedbackMapper.selectById("feedback-1")).thenReturn(feedback);

        ResponseEntity<J> response = feedbackService.getFeedbackOnlyWithManagerService("manager-1", "feedback-1");

        J body = response.getBody();
        assertNotNull(body);
        assertEquals(1, body.getCode());
        assertEquals(FeedbackStatusEnum.PENDING, feedback.getStatus());
        verify(feedbackMapper, never()).update(feedback);
    }

    @Test
    @DisplayName("三级维护员不可处理 BUG 类型反馈")
    void updateFeedbackService_BugFeedback_ShouldRejectThirdMaintainer() {
        UserArchiveModel manager = managerWithPermission(PermissionConst.THI_MAINTAINER);
        FeedbackFormModel feedback = new FeedbackFormModel();
        feedback.setId("feedback-1");
        feedback.setType(FeedbackTypeEnum.BUG);
        feedback.setStatus(FeedbackStatusEnum.COMPLETED);
        when(feedbackMapper.selectById("feedback-1")).thenReturn(feedback);
        when(userArchiveMapper.selectByUuid("manager-1")).thenReturn(manager);

        ResponseEntity<J> response = feedbackService.updateFeedbackService("manager-1", feedback);

        J body = response.getBody();
        assertNotNull(body);
        assertEquals(1, body.getCode());
        verify(feedbackMapper, never()).update(feedback);
    }

    @Test
    @DisplayName("通用反馈更新必须按已存储类型鉴权而非请求类型")
    void updateFeedbackService_SpoofedWorkTypeForStoredUserFeedback_ShouldRejectSecondMaintainer() {
        UserArchiveModel manager = managerWithPermission(PermissionConst.SEC_MAINTAINER);
        FeedbackFormModel request = new FeedbackFormModel();
        request.setId("feedback-1");
        request.setType(FeedbackTypeEnum.WORK);
        request.setStatus(FeedbackStatusEnum.COMPLETED);
        FeedbackFormModel stored = new FeedbackFormModel();
        stored.setId("feedback-1");
        stored.setType(FeedbackTypeEnum.USER);
        stored.setStatus(FeedbackStatusEnum.PENDING);
        when(userArchiveMapper.selectByUuid("manager-1")).thenReturn(manager);
        lenient().when(comparePermission.has(PermissionConst.SEC_MAINTAINER, PermissionConst.SEC_MAINTAINER)).thenReturn(true);
        lenient().when(feedbackMapper.selectById("feedback-1")).thenReturn(stored);
        lenient().when(feedbackMapper.update(request)).thenReturn(true);

        ResponseEntity<J> response = feedbackService.updateFeedbackService("manager-1", request);

        J body = response.getBody();
        assertNotNull(body);
        assertEquals(1, body.getCode());
        verify(feedbackMapper).selectById("feedback-1");
        verify(feedbackMapper, never()).update(request);
    }

    private UserArchiveModel managerWithPermission(Integer permission) {
        UserArchiveModel manager = new UserArchiveModel();
        manager.setPermission(permission);
        return manager;
    }
}
