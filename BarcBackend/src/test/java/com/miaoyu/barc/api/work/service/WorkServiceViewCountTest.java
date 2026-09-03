package com.miaoyu.barc.api.work.service;

import com.miaoyu.barc.api.mapper.ClubMapper;
import com.miaoyu.barc.api.mapper.SchoolMapper;
import com.miaoyu.barc.api.mapper.StudentMapper;
import com.miaoyu.barc.api.work.enumeration.WorkStatusEnum;
import com.miaoyu.barc.api.work.enumeration.WorkReviewStatusEnum;
import com.miaoyu.barc.api.work.mapper.WorkCategoryMapper;
import com.miaoyu.barc.api.work.mapper.WorkCoverImageMapper;
import com.miaoyu.barc.api.work.mapper.WorkImageMapper;
import com.miaoyu.barc.api.work.mapper.WorkLikeMapper;
import com.miaoyu.barc.api.work.mapper.WorkMapper;
import com.miaoyu.barc.api.work.model.WorkCoverImageModel;
import com.miaoyu.barc.api.work.model.WorkModel;
import com.miaoyu.barc.user.mapper.UserArchiveMapper;
import com.miaoyu.barc.utils.J;
import com.miaoyu.barc.utils.JwtService;
import com.miaoyu.barc.utils.minio.MinioObjects;
import com.miaoyu.barc.utils.tencent.cos.CosBucketConfigEnum;
import com.miaoyu.barc.utils.tencent.cos.CosService;
import org.junit.jupiter.api.BeforeEach;
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
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WorkServiceViewCountTest {

    @Mock
    private WorkMapper workMapper;
    @Mock
    private UserArchiveMapper userArchiveMapper;
    @Mock
    private StudentMapper studentMapper;
    @Mock
    private SchoolMapper schoolMapper;
    @Mock
    private ClubMapper clubMapper;
    @Mock
    private MinioObjects minioObjects;
    @Mock
    private WorkImageMapper workImageMapper;
    @Mock
    private WorkCoverImageMapper workCoverImageMapper;
    @Mock
    private CosService cosService;
    @Mock
    private WorkCategoryMapper workCategoryMapper;
    @Mock
    private WorkLikeMapper workLikeMapper;
    @Mock
    private JwtService jwtService;
    @Mock
    private WorkViewService workViewService;

    @InjectMocks
    private WorkService workService;

    private WorkModel publicWork;
    private WorkCoverImageModel coverImage;

    @BeforeEach
    void setUp() {
        publicWork = work("work-1", WorkStatusEnum.PUBLIC);

        coverImage = new WorkCoverImageModel();
        coverImage.setId("cover-1");
        coverImage.setWork_id("work-1");
        coverImage.setObject_key("covers/work-1.png");
    }

    @Test
    @DisplayName("公开详情成功返回时记录一次浏览")
    void getWorksByIdService_WhenPublicDetailSucceeds_ShouldRecordView() {
        MockHttpServletRequest request = requestWithoutAuth();
        when(workMapper.selectById("work-1")).thenReturn(publicWork);
        mockCoverSigning();

        ResponseEntity<J> response = workService.getWorksByIdService(request, "work-1");

        WorkModel data = successfulWork(response);
        assertEquals("https://signed.example/cover.png", data.getCover_image());
        verify(workViewService).recordViewIfNeeded(eq(request), any(WorkModel.class));
    }

    @Test
    @DisplayName("不可访问作品不会记录浏览")
    void getWorksByIdService_WhenWorkBanned_ShouldNotRecordView() {
        WorkModel bannedWork = work("work-1", WorkStatusEnum.BAN);
        when(workMapper.selectById("work-1")).thenReturn(bannedWork);

        ResponseEntity<J> response = workService.getWorksByIdService(requestWithoutAuth(), "work-1");

        J body = response.getBody();
        assertNotNull(body);
        assertEquals(1, body.getCode());
        assertEquals("作品已被封禁", body.getData());
        verifyNoInteractions(workViewService);
    }

    @Test
    @DisplayName("浏览记录异常不影响公开详情成功返回")
    void getWorksByIdService_WhenViewRecordingThrows_ShouldStillReturnSuccess() {
        MockHttpServletRequest request = requestWithoutAuth();
        when(workMapper.selectById("work-1")).thenReturn(publicWork);
        mockCoverSigning();
        doThrow(new RuntimeException("view write failed"))
                .when(workViewService).recordViewIfNeeded(eq(request), any(WorkModel.class));

        ResponseEntity<J> response = workService.getWorksByIdService(request, "work-1");

        WorkModel data = successfulWork(response);
        assertEquals("https://signed.example/cover.png", data.getCover_image());
        verify(workViewService).recordViewIfNeeded(eq(request), any(WorkModel.class));
    }

    private WorkModel work(String workId, WorkStatusEnum status) {
        WorkModel work = new WorkModel();
        work.setId(workId);
        work.setTitle("Test work");
        work.setStatus(status);
        work.setReview_status(WorkReviewStatusEnum.APPROVED);
        work.setLike_count(3);
        return work;
    }

    private MockHttpServletRequest requestWithoutAuth() {
        return new MockHttpServletRequest();
    }

    private void mockCoverSigning() {
        when(workCoverImageMapper.selectByWorkId("work-1")).thenReturn(coverImage);
        when(cosService.generateSignedUrl(anyString(), any(Date.class), eq(CosBucketConfigEnum.image)))
                .thenReturn("https://signed.example/cover.png");
    }

    private WorkModel successfulWork(ResponseEntity<J> response) {
        J body = response.getBody();
        assertNotNull(body);
        assertEquals(0, body.getCode());
        return (WorkModel) body.getData();
    }
}
