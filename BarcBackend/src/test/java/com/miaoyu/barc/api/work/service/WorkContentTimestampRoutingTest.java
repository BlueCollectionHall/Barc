package com.miaoyu.barc.api.work.service;

import com.miaoyu.barc.api.mapper.ClubMapper;
import com.miaoyu.barc.api.mapper.SchoolMapper;
import com.miaoyu.barc.api.mapper.StudentMapper;
import com.miaoyu.barc.api.work.enumeration.WorkStatusEnum;
import com.miaoyu.barc.api.work.enumeration.WorkReviewStatusEnum;
import com.miaoyu.barc.api.work.mapper.WorkCategoryMapper;
import com.miaoyu.barc.api.work.mapper.WorkClaimMapper;
import com.miaoyu.barc.api.work.mapper.WorkCoverImageMapper;
import com.miaoyu.barc.api.work.mapper.WorkImageMapper;
import com.miaoyu.barc.api.work.mapper.WorkLikeMapper;
import com.miaoyu.barc.api.work.mapper.WorkMapper;
import com.miaoyu.barc.api.work.mapper.WorkOperationLogMapper;
import com.miaoyu.barc.api.work.mapper.WorkReviewMapper;
import com.miaoyu.barc.api.work.model.WorkCoverImageModel;
import com.miaoyu.barc.api.work.model.WorkModel;
import com.miaoyu.barc.email.utils.SendEmailUtils;
import com.miaoyu.barc.feedback.mapper.WorkFeedbackMapper;
import com.miaoyu.barc.user.mapper.UserArchiveMapper;
import com.miaoyu.barc.user.mapper.UserBasicMapper;
import com.miaoyu.barc.utils.J;
import com.miaoyu.barc.utils.JwtService;
import com.miaoyu.barc.utils.minio.MinioObjects;
import com.miaoyu.barc.utils.tencent.cos.CosBucketConfigEnum;
import com.miaoyu.barc.utils.tencent.cos.CosService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WorkContentTimestampRoutingTest {

    @Mock private WorkMapper workMapper;
    @Mock private UserArchiveMapper userArchiveMapper;
    @Mock private StudentMapper studentMapper;
    @Mock private SchoolMapper schoolMapper;
    @Mock private ClubMapper clubMapper;
    @Mock private MinioObjects minioObjects;
    @Mock private WorkImageMapper workImageMapper;
    @Mock private WorkCoverImageMapper workCoverImageMapper;
    @Mock private CosService cosService;
    @Mock private WorkCategoryMapper workCategoryMapper;
    @Mock private WorkLikeMapper workLikeMapper;
    @Mock private JwtService jwtService;
    @Mock private WorkViewService workViewService;
    @Mock private WorkReviewMapper workReviewMapper;

    @Mock private WorkClaimMapper workClaimMapper;
    @Mock private WorkOperationLogMapper workOperationLogMapper;
    @Mock private WorkFeedbackMapper workFeedbackMapper;
    @Mock private UserBasicMapper userBasicMapper;
    @Mock private SendEmailUtils sendEmailUtils;

    @InjectMocks
    private WorkService workService;

    @InjectMocks
    private WorkManageService workManageService;

    @Test
    @DisplayName("作者侧内容修改应走 updateText，避免误走通用 update 刷新错误路径")
    void updateOwnerWorkContent_ShouldCallUpdateTextOnly() {
        WorkModel existingWork = ownerWork("work-1", WorkStatusEnum.PUBLIC);
        WorkModel requestModel = new WorkModel();
        requestModel.setId("work-1");
        requestModel.setTitle("新的标题");

        when(workMapper.selectById("work-1")).thenReturn(existingWork);
        when(workMapper.updateText(requestModel)).thenReturn(true);

        ResponseEntity<J> response = workService.updateOwnerWorkContent("owner-1", requestModel);

        assertSuccess(response);
        verify(workMapper).updateText(requestModel);
        verify(workMapper, never()).update(any(WorkModel.class));
    }

    @Test
    @DisplayName("作者侧公开私有切换应继续走通用 update，而不是内容修改 updateText")
    void updateOwnerWorkVisibility_ShouldCallUpdateOnly() {
        WorkModel existingWork = ownerWork("work-1", WorkStatusEnum.PUBLIC);
        when(workMapper.selectById("work-1")).thenReturn(existingWork);
        when(workMapper.update(existingWork)).thenReturn(true);

        ResponseEntity<J> response = workService.updateOwnerWorkVisibility("owner-1", "work-1", WorkStatusEnum.PRIVATE);

        assertSuccess(response);
        assertEquals(WorkStatusEnum.PRIVATE, existingWork.getStatus());
        verify(workMapper).update(existingWork);
        verify(workMapper, never()).updateText(any(WorkModel.class));
    }

    @Test
    @DisplayName("管理端内容修改应走 updateText，锁定内容更新时间刷新路径")
    void updateWorkContent_ForManage_ShouldCallUpdateTextOnly() {
        WorkModel requestModel = new WorkModel();
        requestModel.setId("work-1");
        requestModel.setTitle("管理员修正文案");

        when(workMapper.selectById("work-1")).thenReturn(new WorkModel());
        when(workMapper.updateText(requestModel)).thenReturn(true);

        ResponseEntity<J> response = workManageService.updateWorkContent("manager-1", requestModel);

        assertSuccess(response);
        verify(workMapper).updateText(requestModel);
        verify(workMapper, never()).update(any(WorkModel.class));
    }

    @Test
    @DisplayName("管理端状态修改应继续走通用 update，避免误走内容更新时间刷新路径")
    void updateWorkStatus_ForManage_ShouldCallUpdateOnly() {
        WorkModel work = new WorkModel();
        work.setId("work-1");
        work.setTitle("测试作品");
        work.setAuthor("author-1");
        work.setStatus(WorkStatusEnum.PUBLIC);

        when(workMapper.selectById("work-1")).thenReturn(work);
        when(workMapper.update(work)).thenReturn(true);

        ResponseEntity<J> response = workManageService.updateWorkStatus("manager-1", "work-1", WorkStatusEnum.BAN, "违规说明");

        assertSuccess(response);
        assertEquals(WorkStatusEnum.BAN, work.getStatus());
        verify(workMapper).update(work);
        verify(workMapper, never()).updateText(any(WorkModel.class));
    }

    @Test
    @DisplayName("公开详情仍应委托 WorkViewService 记录浏览")
    void getWorksByIdService_WhenPublicDetailSucceeds_ShouldRecordViewThroughService() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        WorkModel work = ownerWork("work-1", WorkStatusEnum.PUBLIC);
        WorkCoverImageModel cover = new WorkCoverImageModel();
        cover.setId("cover-1");
        cover.setWork_id("work-1");
        cover.setObject_key("covers/work-1.png");

        when(workMapper.selectById("work-1")).thenReturn(work);
        when(workCoverImageMapper.selectByWorkId("work-1")).thenReturn(cover);
        when(cosService.generateSignedUrl(anyString(), any(Date.class), eq(CosBucketConfigEnum.image)))
                .thenReturn("https://signed.example/cover.png");

        ResponseEntity<J> response = workService.getWorksByIdService(request, "work-1");

        assertSuccess(response);
        verify(workViewService).recordViewIfNeeded(eq(request), any(WorkModel.class));
    }

    private WorkModel ownerWork(String workId, WorkStatusEnum status) {
        WorkModel work = new WorkModel();
        work.setId(workId);
        work.setAuthor("owner-1");
        work.setUploader("uploader-1");
        work.setStatus(status);
        work.setReview_status(WorkReviewStatusEnum.APPROVED);
        return work;
    }

    private void assertSuccess(ResponseEntity<J> response) {
        J body = response.getBody();
        assertNotNull(body);
        assertEquals(0, body.getCode());
    }
}
