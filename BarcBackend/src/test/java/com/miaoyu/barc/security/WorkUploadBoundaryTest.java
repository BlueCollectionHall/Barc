package com.miaoyu.barc.security;

import com.miaoyu.barc.api.file.service.ObjectStorageService;
import com.miaoyu.barc.api.mapper.StudentMapper;
import com.miaoyu.barc.api.model.StudentModel;
import com.miaoyu.barc.api.work.mapper.*;
import com.miaoyu.barc.api.work.model.WorkModel;
import com.miaoyu.barc.api.work.service.WorkService;
import com.miaoyu.barc.exception.ApiException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 作者/收录者白名单与失败传播；真正事务回滚另由隔离数据库集成测试覆盖。 */
@ExtendWith(MockitoExtension.class)
class WorkUploadBoundaryTest {
    @Mock WorkMapper works;
    @Mock WorkReviewMapper reviews;
    @Mock StudentMapper students;
    @Mock WorkCoverImageMapper covers;
    @Mock WorkImageMapper images;
    @Mock ObjectStorageService storage;
    @InjectMocks WorkService service;

    @Test void forgedCollaboratorIsOverwrittenAndFailedImageInsertThrows() {
        var model = new WorkModel(); model.setTitle("作品"); model.setContent("正文"); model.setStudent("student"); model.setIs_claim(true); model.setUploader("attacker"); model.setAuthor("victim");
        when(students.selectById("student")).thenReturn(new StudentModel()); when(works.insert(any())).thenReturn(true); when(reviews.insertPending(anyString())).thenReturn(true);
        when(storage.upload(anyString(), any(), anyString(), anyLong())).thenReturn("cover.png", "image.png"); when(covers.insert(any())).thenReturn(true);
        var image = new MockMultipartFile("file", "a.png", "image/png", TestImages.png());
        assertThrows(ApiException.class, () -> service.uploadWorkService("owner", null, model, image, new MultipartFile[]{image}));
        verify(works).insert(argThat(work -> "owner".equals(work.getAuthor()) && "owner".equals(work.getUploader())));
        verify(works, never()).delete(anyString()); verify(storage).bind("owner", "image.png");
    }

    @Test void malformedFilesAreRejectedBeforeAnyDatabaseWrite() {
        var model = new WorkModel(); model.setTitle("作品"); model.setStudent("student"); model.setIs_claim(true);
        when(students.selectById("student")).thenReturn(new StudentModel());
        var fake = new MockMultipartFile("file", "x.png", "image/png", "not an image".getBytes());
        assertThrows(ApiException.class, () -> service.uploadWorkService("owner", null, model, fake, null));
        verify(works, never()).insert(any()); verifyNoInteractions(storage, images, covers, reviews);
    }
}
