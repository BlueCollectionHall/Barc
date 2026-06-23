package com.miaoyu.barc.api.work.service;

import com.miaoyu.barc.api.mapper.ClubMapper;
import com.miaoyu.barc.api.mapper.SchoolMapper;
import com.miaoyu.barc.api.mapper.StudentMapper;
import com.miaoyu.barc.api.model.SchoolClubModel;
import com.miaoyu.barc.api.model.SchoolModel;
import com.miaoyu.barc.api.model.StudentModel;
import com.miaoyu.barc.api.work.enumeration.WorkStatusEnum;
import com.miaoyu.barc.api.work.mapper.WorkCategoryMapper;
import com.miaoyu.barc.api.work.mapper.WorkCoverImageMapper;
import com.miaoyu.barc.api.work.mapper.WorkImageMapper;
import com.miaoyu.barc.api.work.mapper.WorkLikeMapper;
import com.miaoyu.barc.api.work.mapper.WorkMapper;
import com.miaoyu.barc.api.work.model.WorkCoverImageModel;
import com.miaoyu.barc.api.work.model.WorkEditDetailDto;
import com.miaoyu.barc.api.work.model.WorkImageModel;
import com.miaoyu.barc.api.work.model.WorkLikeModel;
import com.miaoyu.barc.api.work.model.WorkModel;
import com.miaoyu.barc.api.work.model.entity.WorkEntity;
import com.miaoyu.barc.user.mapper.UserArchiveMapper;
import com.miaoyu.barc.user.model.UserArchiveModel;
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
import java.util.List;

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

    @Test
    @DisplayName("作者作品列表返回签名后的封面URL")
    void getWorksByMeService_ShouldSignCoverImageFromCoverTable() {
        WorkEntity work = new WorkEntity();
        work.setId("work-1");
        work.setCover_image("legacy-raw-cover");
        when(workMapper.selectByUuid("user-1", WorkStatusEnum.PUBLIC)).thenReturn(List.of(work));
        when(workCoverImageMapper.selectByWorkIds(List.of("work-1"))).thenReturn(List.of(coverImage));
        when(cosService.generateBatchSignedUrl(eq(List.of("covers/work-1.png")), any(Date.class), eq(CosBucketConfigEnum.image)))
                .thenReturn(List.of("https://signed.example/cover.png"));

        ResponseEntity<J> response = workService.getWorksByMeService("user-1", WorkStatusEnum.PUBLIC);

        J body = response.getBody();
        assertNotNull(body);
        assertEquals(0, body.getCode());
        List<?> data = (List<?>) body.getData();
        WorkEntity signed = (WorkEntity) data.get(0);
        assertEquals("https://signed.example/cover.png", signed.getCover_image());
    }

    @Test
    @DisplayName("作品公开编辑详情仅作者可读取且复用管理端图片语义")
    void getOwnerWorkEditDetail_ShouldReturnSignedImagesAndDisplayMetadata() {
        WorkModel work = claimedWork("work-1", "user-1");
        work.setUploader("uploader-1");
        work.setStudent("student-1");
        WorkImageModel first = image("image-1", 1, "content/1.png");
        WorkImageModel zero = image("image-0", 0, "content/0.png");
        UserArchiveModel author = archive("作者昵称");
        UserArchiveModel uploader = archive("收录者昵称");
        StudentModel student = student("学生名", "school-1", "club-1");
        SchoolModel school = school("学园名");
        SchoolClubModel club = club("部团名");
        when(workMapper.selectById("work-1")).thenReturn(work);
        when(workCoverImageMapper.selectByWorkId("work-1")).thenReturn(coverImage);
        when(cosService.generateSignedUrl(eq("covers/work-1.png"), any(Date.class), eq(CosBucketConfigEnum.image)))
                .thenReturn("https://signed.example/cover.png");
        when(workImageMapper.selectByWorkId("work-1")).thenReturn(List.of(first, zero));
        when(cosService.generateBatchSignedUrl(eq(List.of("content/0.png", "content/1.png")), any(Date.class), eq(CosBucketConfigEnum.image)))
                .thenReturn(List.of("https://signed.example/0.png", "https://signed.example/1.png"));
        when(userArchiveMapper.selectByUuid("uploader-1")).thenReturn(uploader);
        when(userArchiveMapper.selectByUuid("user-1")).thenReturn(author);
        when(studentMapper.selectById("student-1")).thenReturn(student);
        when(schoolMapper.selectById("school-1")).thenReturn(school);
        when(clubMapper.selectById("club-1")).thenReturn(club);

        ResponseEntity<J> response = workService.getOwnerWorkEditDetail("user-1", "work-1");

        J body = response.getBody();
        assertNotNull(body);
        assertEquals(0, body.getCode());
        WorkEditDetailDto dto = (WorkEditDetailDto) body.getData();
        assertEquals(work, dto.getWork());
        assertEquals(null, dto.getWork().getCover_image());
        assertEquals("https://signed.example/cover.png", dto.getCover_image_url());
        assertEquals(List.of("https://signed.example/0.png", "https://signed.example/1.png"), dto.getContent_image_urls());
        assertEquals("收录者昵称", dto.getUploader_nickname());
        assertEquals("作者昵称", dto.getAuthor_display());
        assertEquals("学园名", dto.getSchool_name());
        assertEquals("部团名", dto.getClub_name());
        assertEquals("学生名", dto.getStudent_name());
    }

    @Test
    @DisplayName("作品公开编辑详情拒绝非作者或收录者读取")
    void getOwnerWorkEditDetail_WhenNotOwner_ShouldReturnUuidMismatchWithoutSigning() {
        when(workMapper.selectById("work-1")).thenReturn(claimedWork("work-1", "user-1"));

        ResponseEntity<J> response = workService.getOwnerWorkEditDetail("other-user", "work-1");

        J body = response.getBody();
        assertNotNull(body);
        assertEquals(1, body.getCode());
        assertEquals("用户UUID信息不匹配：User UUID information mismatch", body.getData());
        verifyNoInteractions(workCoverImageMapper, workImageMapper, cosService);
    }

    @Test
    @DisplayName("作者公开纯文字保存走拆分后的updateText路径")
    void updateOwnerWorkContent_ShouldUseTextOnlyMapper() {
        WorkModel existing = claimedWork("work-1", "user-1");
        WorkModel request = new WorkModel();
        request.setId("work-1");
        request.setTitle("新标题");
        request.setCover_image("must-not-persist");
        when(workMapper.selectById("work-1")).thenReturn(existing);
        when(workMapper.updateText(request)).thenReturn(true);

        ResponseEntity<J> response = workService.updateOwnerWorkContent("user-1", request);

        J body = response.getBody();
        assertNotNull(body);
        assertEquals(0, body.getCode());
        verify(workMapper).updateText(request);
        verify(workMapper, never()).update(any(WorkModel.class));
    }

    @Test
    @DisplayName("作者替换封面只更新work_cover_image指针并删除旧对象")
    void replaceOwnerWorkCover_ShouldUpdateCoverTableOnly() {
        WorkModel existing = claimedWork("work-1", "user-1");
        when(workMapper.selectById("work-1")).thenReturn(existing);
        when(workCoverImageMapper.selectByWorkId("work-1")).thenReturn(coverImage);
        when(cosService.uploadFile(any(), eq("/user-1/work_images/"), eq(CosBucketConfigEnum.image)))
                .thenReturn(new J(0, "文件上传成功", "covers/new.png"));
        when(workCoverImageMapper.update(any(WorkCoverImageModel.class))).thenReturn(true);

        ResponseEntity<J> response = workService.replaceOwnerWorkCover("user-1", "work-1", new org.springframework.mock.web.MockMultipartFile("cover_image", "new.png", "image/png", new byte[]{1}));

        J body = response.getBody();
        assertNotNull(body);
        assertEquals(0, body.getCode());
        verify(workCoverImageMapper).update(any(WorkCoverImageModel.class));
        verify(cosService).deleteFile("covers/work-1.png", CosBucketConfigEnum.image);
        verify(workMapper, never()).update(any(WorkModel.class));
    }

    private WorkModel work(String workId, WorkStatusEnum status) {
        WorkModel work = new WorkModel();
        work.setId(workId);
        work.setTitle("Test work");
        work.setStatus(status);
        work.setLike_count(3);
        return work;
    }

    private WorkModel claimedWork(String workId, String author) {
        WorkModel work = work(workId, WorkStatusEnum.PUBLIC);
        work.setAuthor(author);
        work.setIs_claim(true);
        return work;
    }

    private WorkImageModel image(String id, int sort, String objectKey) {
        WorkImageModel image = new WorkImageModel();
        image.setId(id);
        image.setWork_id("work-1");
        image.setSort(sort);
        image.setObject_key(objectKey);
        return image;
    }

    private UserArchiveModel archive(String nickname) {
        UserArchiveModel archive = new UserArchiveModel();
        archive.setNickname(nickname);
        return archive;
    }

    private StudentModel student(String name, String schoolId, String clubId) {
        StudentModel student = new StudentModel();
        student.setCn_name(name);
        student.setSchool(schoolId);
        student.setClub(clubId);
        return student;
    }

    private SchoolModel school(String name) {
        SchoolModel school = new SchoolModel();
        school.setCn_name(name);
        return school;
    }

    private SchoolClubModel club(String name) {
        SchoolClubModel club = new SchoolClubModel();
        club.setCn_name(name);
        return club;
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
