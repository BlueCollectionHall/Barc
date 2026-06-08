package com.miaoyu.barc.api.work.service;

import com.miaoyu.barc.api.mapper.ClubMapper;
import com.miaoyu.barc.api.mapper.SchoolMapper;
import com.miaoyu.barc.api.mapper.StudentMapper;
import com.miaoyu.barc.api.work.enumeration.WorkStatusEnum;
import com.miaoyu.barc.api.work.mapper.WorkCategoryMapper;
import com.miaoyu.barc.api.work.mapper.WorkCoverImageMapper;
import com.miaoyu.barc.api.work.mapper.WorkImageMapper;
import com.miaoyu.barc.api.work.mapper.WorkLikeMapper;
import com.miaoyu.barc.api.work.mapper.WorkMapper;
import com.miaoyu.barc.api.work.model.WorkCoverImageModel;
import com.miaoyu.barc.api.work.model.entity.WorkEntity;
import com.miaoyu.barc.user.mapper.UserArchiveMapper;
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

import java.util.Date;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WorkServiceOwnerFilterTest {

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

    @InjectMocks
    private WorkService workService;

    @Test
    @DisplayName("作者作品列表应按当前状态传递可选筛选条件并继续签名封面")
    void getWorksByMeService_WithFilters_ShouldFilterWithinStatusAndSignCover() {
        WorkEntity work = new WorkEntity();
        work.setId("work-1");
        work.setCover_image("legacy-raw-cover");
        Map<String, Object> filters = Map.of(
                "keyword", "泳装",
                "school", "阿拜多斯",
                "club", "对策委员会",
                "student", "白子"
        );
        WorkCoverImageModel coverImage = new WorkCoverImageModel();
        coverImage.setWork_id("work-1");
        coverImage.setObject_key("covers/work-1.png");
        when(workMapper.selectByUuidWithFilters(eq("user-1"), eq(WorkStatusEnum.PUBLIC), anyMap())).thenReturn(List.of(work));
        when(workCoverImageMapper.selectByWorkIds(List.of("work-1"))).thenReturn(List.of(coverImage));
        when(cosService.generateBatchSignedUrl(eq(List.of("covers/work-1.png")), any(Date.class), eq(CosBucketConfigEnum.image)))
                .thenReturn(List.of("https://signed.example/cover.png"));

        ResponseEntity<J> response = workService.getWorksByMeService("user-1", WorkStatusEnum.PUBLIC, filters);

        J body = response.getBody();
        assertNotNull(body);
        assertEquals(0, body.getCode());
        List<?> data = (List<?>) body.getData();
        WorkEntity signed = (WorkEntity) data.get(0);
        assertEquals("https://signed.example/cover.png", signed.getCover_image());
        verify(workMapper).selectByUuidWithFilters(eq("user-1"), eq(WorkStatusEnum.PUBLIC), argThat(condition ->
                "泳装".equals(condition.get("keyword"))
                        && "阿拜多斯".equals(condition.get("school"))
                        && "对策委员会".equals(condition.get("club"))
                        && "白子".equals(condition.get("student"))
                        && "user-1".equals(condition.get("author_uuid"))));
    }
}
