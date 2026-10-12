package com.miaoyu.barc.api.work.service;

import com.miaoyu.barc.api.work.mapper.WorkImageMapper;
import com.miaoyu.barc.api.work.mapper.WorkMapper;
import com.miaoyu.barc.api.work.model.WorkImageModel;
import com.miaoyu.barc.api.work.model.WorkModel;
import com.miaoyu.barc.utils.J;
import com.miaoyu.barc.utils.tencent.cos.CosBucketConfigEnum;
import com.miaoyu.barc.utils.tencent.cos.CosService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WorkImageServiceOwnerFlowTest {

    @Mock
    private WorkImageMapper workImageMapper;
    @Mock
    private WorkMapper workMapper;
    @Mock
    private CosService cosService;
    @Mock
    private WorkService workService;
    @Mock
    private com.miaoyu.barc.api.file.service.ObjectStorageService objectStorage;

    @InjectMocks
    private WorkImageService workImageService;

    @Test
    @DisplayName("作者追加画廊图片时使用末尾sort并写入work_image")
    void uploadOwnerWorkImage_ShouldAppendAfterCurrentMaxSort() {
        WorkModel work = new WorkModel();
        work.setId("work-1");
        work.setAuthor("user-1");
        when(workMapper.selectByIdForUpdate("work-1")).thenReturn(work);
        when(workImageMapper.selectByWorkId("work-1")).thenReturn(List.of(image("old-0", 0), image("old-3", 3)));
        // 新上传经过持久补偿层；授权时已经持有作品行锁。
        when(objectStorage.upload(eq("user-1"), any(), eq("uploads/user-1/work_images/"), org.mockito.ArgumentMatchers.anyLong()))
                .thenReturn("content/new.png");
        when(workImageMapper.insert(any(WorkImageModel.class))).thenReturn(true);

        ResponseEntity<J> response = workImageService.uploadWorkImageService("user-1", "work-1", new MockMultipartFile("file", "new.png", "image/png", new byte[]{1}));

        J body = response.getBody();
        assertNotNull(body);
        assertEquals(0, body.getCode());
        verify(workImageMapper).insert(org.mockito.ArgumentMatchers.argThat(image ->
                "work-1".equals(image.getWork_id())
                        && image.getSort() == 4
                        && "content/new.png".equals(image.getObject_key())));
        verify(workService).submitForReview("work-1");
    }

    @Test
    @DisplayName("非作者追加画廊图片时不上传也不写库")
    void uploadOwnerWorkImage_WhenNotOwner_ShouldRejectBeforeUpload() {
        WorkModel work = new WorkModel();
        work.setId("work-1");
        work.setAuthor("user-1");
        when(workMapper.selectByIdForUpdate("work-1")).thenReturn(work);

        ResponseEntity<J> response = workImageService.uploadWorkImageService("other-user", "work-1", new MockMultipartFile("file", "new.png", "image/png", new byte[]{1}));

        J body = response.getBody();
        assertNotNull(body);
        assertEquals(1, body.getCode());
        verify(cosService, never()).uploadFile(any(), any(), any());
        org.mockito.Mockito.verifyNoInteractions(objectStorage);
        verify(workImageMapper, never()).insert(any(WorkImageModel.class));
    }

    private WorkImageModel image(String id, int sort) {
        WorkImageModel image = new WorkImageModel();
        image.setId(id);
        image.setWork_id("work-1");
        image.setSort(sort);
        image.setObject_key(id + ".png");
        return image;
    }
}
