package com.miaoyu.barc.api.work.controller;

import com.miaoyu.barc.api.work.model.WorkLikeToggleDto;
import com.miaoyu.barc.api.work.service.WorkLikeService;
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
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * 作品点赞控制器单元测试
 */
@ExtendWith(MockitoExtension.class)
class WorkLikeControllerTest {

    @Mock
    private WorkLikeService workLikeService;

    @InjectMocks
    private WorkLikeController workLikeController;

    @Test
    @DisplayName("请求缺少用户UUID时应返回普通错误")
    void toggleLikeControl_WhenUuidMissing_ShouldReturnFailure() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        WorkLikeToggleDto dto = new WorkLikeToggleDto();
        dto.setWork_id("work-1");

        ResponseEntity<J> response = workLikeController.toggleLikeControl(request, dto);

        J body = response.getBody();
        assertNotNull(body);
        assertEquals(1, body.getCode());
        assertEquals("用户未登录", body.getData());
        verifyNoInteractions(workLikeService);
    }
}
