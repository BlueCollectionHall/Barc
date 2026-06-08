package com.miaoyu.barc.api.work.controller;

import com.miaoyu.barc.api.work.mapper.WorkImageMapper;
import com.miaoyu.barc.api.work.mapper.WorkMapper;
import com.miaoyu.barc.api.work.model.WorkImageModel;
import com.miaoyu.barc.api.work.model.WorkModel;
import com.miaoyu.barc.api.work.service.WorkImageService;
import com.miaoyu.barc.response.ChangeR;
import com.miaoyu.barc.utils.J;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WorkImageControllerOwnerDeleteTest {

    @Mock
    private WorkImageService workImageService;
    @Mock
    private WorkImageMapper workImageMapper;
    @Mock
    private WorkMapper workMapper;

    @InjectMocks
    private WorkImageController workImageController;

    @Test
    @DisplayName("收录者删除画廊图片时按本人UUID进入权限校验并保留删除行为")
    void deleteWorkImageControl_WhenUploaderOwnsWork_ShouldAuthorizeWithRequesterUuid() {
        WorkImageModel image = new WorkImageModel();
        image.setId("image-1");
        image.setWork_id("work-1");
        WorkModel work = new WorkModel();
        work.setId("work-1");
        work.setAuthor("author-1");
        work.setUploader("uploader-1");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute("uuid", "uploader-1");
        when(workImageMapper.selectById("image-1")).thenReturn(image);
        when(workMapper.selectById("work-1")).thenReturn(work);
        when(workImageService.deleteWorkImageService("uploader-1", "uploader-1", "image-1"))
                .thenReturn(ResponseEntity.ok(new ChangeR().udu(true, 2)));

        ResponseEntity<J> response = workImageController.deleteWorkImageControl(request, "image-1");

        J body = response.getBody();
        assertNotNull(body);
        assertEquals(0, body.getCode());
        verify(workImageService).deleteWorkImageService("uploader-1", "uploader-1", "image-1");
    }
}
