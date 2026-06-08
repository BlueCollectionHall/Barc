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
import com.miaoyu.barc.api.work.model.WorkModel;
import com.miaoyu.barc.user.mapper.UserArchiveMapper;
import com.miaoyu.barc.utils.J;
import com.miaoyu.barc.utils.JwtService;
import com.miaoyu.barc.utils.minio.MinioObjects;
import com.miaoyu.barc.utils.tencent.cos.CosService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WorkServiceOwnerStatusTest {

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

    @InjectMocks
    private WorkService workService;

    @Test
    @DisplayName("作者只能将自己的作品从公开切换为私有")
    void updateOwnerWorkVisibility_PublicToPrivate_ShouldUpdateOwnedWork() {
        WorkModel work = new WorkModel();
        work.setId("work-1");
        work.setAuthor("owner-1");
        work.setStatus(WorkStatusEnum.PUBLIC);
        when(workMapper.selectById("work-1")).thenReturn(work);
        when(workMapper.update(work)).thenReturn(true);

        ResponseEntity<J> response = workService.updateOwnerWorkVisibility("owner-1", "work-1", WorkStatusEnum.PRIVATE);

        J body = response.getBody();
        assertNotNull(body);
        assertEquals(0, body.getCode());
        assertEquals(WorkStatusEnum.PRIVATE, work.getStatus());
        verify(workMapper).update(work);
    }

    @Test
    @DisplayName("作者公开/私有切换不能越权恢复被封禁作品")
    void updateOwnerWorkVisibility_BanToPublic_ShouldRejectAdminStatus() {
        WorkModel work = new WorkModel();
        work.setId("work-1");
        work.setAuthor("owner-1");
        work.setStatus(WorkStatusEnum.BAN);
        when(workMapper.selectById("work-1")).thenReturn(work);

        ResponseEntity<J> response = workService.updateOwnerWorkVisibility("owner-1", "work-1", WorkStatusEnum.PUBLIC);

        J body = response.getBody();
        assertNotNull(body);
        assertEquals(1, body.getCode());
        assertEquals(WorkStatusEnum.BAN, work.getStatus());
        verify(workMapper, never()).update(work);
    }
}
