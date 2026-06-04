package com.miaoyu.barc.api.work.service;

import com.miaoyu.barc.api.mapper.StudentMapper;
import com.miaoyu.barc.api.work.enumeration.WorkStatusEnum;
import com.miaoyu.barc.api.work.mapper.WorkCategoryMapper;
import com.miaoyu.barc.api.work.mapper.WorkCoverImageMapper;
import com.miaoyu.barc.api.work.mapper.WorkImageMapper;
import com.miaoyu.barc.api.work.mapper.WorkLikeMapper;
import com.miaoyu.barc.api.work.mapper.WorkMapper;
import com.miaoyu.barc.api.work.model.WorkCoverImageModel;
import com.miaoyu.barc.api.work.model.WorkLikeModel;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WorkServiceLikeStateTest {

    @Mock
    private WorkMapper workMapper;
    @Mock
    private UserArchiveMapper userArchiveMapper;
    @Mock
    private StudentMapper studentMapper;
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

    @InjectMocks
    private WorkService workService;

    private WorkModel publicWork;
    private WorkCoverImageModel coverImage;
    private WorkLikeModel existingLike;

    @BeforeEach
    void setUp() {
        publicWork = work("work-1", WorkStatusEnum.PUBLIC);

        coverImage = new WorkCoverImageModel();
        coverImage.setId("cover-1");
        coverImage.setWork_id("work-1");
        coverImage.setObject_key("covers/work-1.png");

        existingLike = new WorkLikeModel();
        existingLike.setId("like-1");
        existingLike.setWork_id("work-1");
        existingLike.setUser_uuid("user-1");
    }

    @Test
    @DisplayName("公开详情无Authorization时仍可访问并返回未点赞状态")
    void getWorksByIdService_WhenNoAuthorization_ShouldReturnFalseAndSignCover() {
        when(workMapper.selectById("work-1")).thenReturn(publicWork);
        mockCoverSigning();

        ResponseEntity<J> response = workService.getWorksByIdService(requestWithoutAuth(), "work-1");

        WorkModel data = successfulWork(response);
        assertEquals(false, data.getLiked_by_current_user());
        assertEquals("https://signed.example/cover.png", data.getCover_image());
        verifyNoInteractions(jwtService, workLikeMapper);
    }

    @Test
    @DisplayName("公开详情携带有效原始JWT且已点赞时返回已点赞状态")
    void getWorksByIdService_WhenValidRawJwtAndLiked_ShouldReturnTrue() {
        when(workMapper.selectById("work-1")).thenReturn(publicWork);
        mockCoverSigning();
        when(jwtService.jwtParser("valid-jwt")).thenReturn(new J(0, "Token成功", "user-1"));
        when(workLikeMapper.selectByWorkIdAndUserUuid("work-1", "user-1")).thenReturn(existingLike);

        ResponseEntity<J> response = workService.getWorksByIdService(requestWithAuth("valid-jwt"), "work-1");

        WorkModel data = successfulWork(response);
        assertEquals(true, data.getLiked_by_current_user());
        assertEquals("https://signed.example/cover.png", data.getCover_image());
        verify(workLikeMapper).selectByWorkIdAndUserUuid("work-1", "user-1");
    }

    @Test
    @DisplayName("公开详情携带失效JWT时降级为匿名且不查询点赞关系")
    void getWorksByIdService_WhenInvalidJwt_ShouldDegradeToAnonymous() {
        when(workMapper.selectById("work-1")).thenReturn(publicWork);
        mockCoverSigning();
        when(jwtService.jwtParser("expired-jwt")).thenReturn(new J(1, "令牌已过期！", "令牌已过期！"));

        ResponseEntity<J> response = workService.getWorksByIdService(requestWithAuth("expired-jwt"), "work-1");

        WorkModel data = successfulWork(response);
        assertEquals(false, data.getLiked_by_current_user());
        verify(workLikeMapper, never()).selectByWorkIdAndUserUuid(anyString(), anyString());
    }

    @Test
    @DisplayName("公开详情携带解析异常JWT时降级为匿名且不返回401")
    void getWorksByIdService_WhenJwtParserThrows_ShouldDegradeToAnonymous() {
        when(workMapper.selectById("work-1")).thenReturn(publicWork);
        mockCoverSigning();
        when(jwtService.jwtParser("malformed-jwt")).thenThrow(new RuntimeException("bad jwt"));

        ResponseEntity<J> response = workService.getWorksByIdService(requestWithAuth("malformed-jwt"), "work-1");

        WorkModel data = successfulWork(response);
        assertEquals(false, data.getLiked_by_current_user());
        verify(workLikeMapper, never()).selectByWorkIdAndUserUuid(anyString(), anyString());
    }

    @Test
    @DisplayName("不可访问作品保留原有状态拦截且不签名封面或查询点赞")
    void getWorksByIdService_WhenWorkBanned_ShouldPreserveStatusGate() {
        WorkModel bannedWork = work("work-1", WorkStatusEnum.BAN);
        when(workMapper.selectById("work-1")).thenReturn(bannedWork);

        ResponseEntity<J> response = workService.getWorksByIdService(requestWithAuth("valid-jwt"), "work-1");

        J body = response.getBody();
        assertNotNull(body);
        assertEquals(1, body.getCode());
        assertEquals("作品已被封禁", body.getData());
        verifyNoInteractions(jwtService, workCoverImageMapper, cosService, workLikeMapper);
    }

    @Test
    @DisplayName("公开详情缺少封面指针时返回受控错误且不继续签名或查询点赞")
    void getWorksByIdService_WhenCoverPointerMissing_ShouldReturnControlledError() {
        when(workMapper.selectById("work-1")).thenReturn(publicWork);
        when(workCoverImageMapper.selectByWorkId("work-1")).thenReturn(null);

        ResponseEntity<J> response = workService.getWorksByIdService(requestWithAuth("valid-jwt"), "work-1");

        J body = response.getBody();
        assertNotNull(body);
        assertEquals(1, body.getCode());
        assertEquals("作品封面图不存在", body.getData());
        verifyNoInteractions(jwtService, cosService, workLikeMapper);
    }

    private WorkModel work(String workId, WorkStatusEnum status) {
        WorkModel work = new WorkModel();
        work.setId(workId);
        work.setTitle("Test work");
        work.setStatus(status);
        work.setLike_count(3);
        return work;
    }

    private MockHttpServletRequest requestWithoutAuth() {
        return new MockHttpServletRequest();
    }

    private MockHttpServletRequest requestWithAuth(String token) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", token);
        return request;
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
