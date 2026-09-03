package com.miaoyu.barc.api.work.service;

import com.miaoyu.barc.annotation.RequireUserAndPermissionAnno;
import com.miaoyu.barc.api.work.enumeration.WorkReviewStatusEnum;
import com.miaoyu.barc.api.work.mapper.WorkMapper;
import com.miaoyu.barc.api.work.mapper.WorkOperationLogMapper;
import com.miaoyu.barc.api.work.mapper.WorkReviewMapper;
import com.miaoyu.barc.api.work.model.WorkModel;
import com.miaoyu.barc.api.work.model.WorkOperationLogModel;
import com.miaoyu.barc.email.utils.SendEmailUtils;
import com.miaoyu.barc.permission.PermissionConst;
import com.miaoyu.barc.utils.J;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WorkManageReviewServiceTest {

    @Mock private WorkMapper workMapper;
    @Mock private WorkReviewMapper workReviewMapper;
    @Mock private WorkOperationLogMapper workOperationLogMapper;
    @Mock private SendEmailUtils sendEmailUtils;

    @InjectMocks
    private WorkManageService service;

    @Test
    @DisplayName("通过上传审核应更新审核记录、写日志且不发送邮件")
    void reviewWork_WhenApproved_ShouldPersistSilently() {
        WorkModel work = pendingWork();
        when(workMapper.selectById("work-1")).thenReturn(work);
        when(workReviewMapper.review(
                "work-1", WorkReviewStatusEnum.APPROVED, null, "manager-1")).thenReturn(true);

        ResponseEntity<J> response = service.reviewWork("manager-1", "work-1", true, null);

        assertSuccess(response);
        verify(workReviewMapper).review(
                "work-1", WorkReviewStatusEnum.APPROVED, null, "manager-1");
        verify(workOperationLogMapper).insert(any(WorkOperationLogModel.class));
        verifyNoInteractions(sendEmailUtils);
    }

    @Test
    @DisplayName("拒绝审核必须填写用户可见原因")
    void reviewWork_WhenRejectedWithoutReason_ShouldRejectBeforeWrite() {
        when(workMapper.selectById("work-1")).thenReturn(pendingWork());

        ResponseEntity<J> response = service.reviewWork("manager-1", "work-1", false, "   ");

        J body = response.getBody();
        assertNotNull(body);
        assertEquals(1, body.getCode());
        verify(workReviewMapper, never()).review(any(), any(), any(), any());
        verify(workOperationLogMapper, never()).insert(any());
        verifyNoInteractions(sendEmailUtils);
    }

    @Test
    @DisplayName("上传审核权限必须使用SEC_MAINTAINER严格位检查")
    void reviewWork_ShouldDeclareStrictPermissionBitCheck() throws NoSuchMethodException {
        Method method = WorkManageService.class.getMethod(
                "reviewWork", String.class, String.class, boolean.class, String.class);
        RequireUserAndPermissionAnno annotation = method.getAnnotation(RequireUserAndPermissionAnno.class);

        assertNotNull(annotation);
        RequireUserAndPermissionAnno.Check check = annotation.value()[0];
        assertEquals(PermissionConst.SEC_MAINTAINER, check.targetPermission());
        assertTrue(check.isHasElseUpper(), "作品审核不能使用权限值大小比较");
    }

    private WorkModel pendingWork() {
        WorkModel work = new WorkModel();
        work.setId("work-1");
        work.setTitle("待审作品");
        work.setReview_status(WorkReviewStatusEnum.PENDING);
        return work;
    }

    private void assertSuccess(ResponseEntity<J> response) {
        J body = response.getBody();
        assertNotNull(body);
        assertEquals(0, body.getCode());
    }
}
