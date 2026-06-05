package com.miaoyu.barc.api.work.controller;

import com.miaoyu.barc.annotation.IgnoreAuth;
import com.miaoyu.barc.api.work.model.WorkLikeToggleDto;
import com.miaoyu.barc.api.work.service.WorkLikeService;
import com.miaoyu.barc.utils.J;
import com.miaoyu.barc.utils.dto.PageRequestDto;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

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

    @Test
    @DisplayName("按用户名查询喜欢作品列表应公开并委托服务层")
    void listLikedWorksByUsernameControl_ShouldBePublicAndDelegateToService() throws Exception {
        String username = "alice";
        PageRequestDto pageRequestDto = new PageRequestDto();
        ResponseEntity<J> expectedResponse = ResponseEntity.ok(new J(0, "success", null));
        when(workLikeService.listLikedWorksByUsername(username, pageRequestDto)).thenReturn(expectedResponse);

        ResponseEntity<J> response = workLikeController.listLikedWorksByUsernameControl(username, pageRequestDto);

        assertSame(expectedResponse, response);
        verify(workLikeService).listLikedWorksByUsername(username, pageRequestDto);

        Method method = WorkLikeController.class.getMethod(
                "listLikedWorksByUsernameControl",
                String.class,
                PageRequestDto.class
        );
        assertTrue(method.isAnnotationPresent(IgnoreAuth.class));
        PostMapping postMapping = method.getAnnotation(PostMapping.class);
        assertNotNull(postMapping);
        assertArrayEquals(new String[]{"/list_by_username"}, postMapping.value());
    }

    @Test
    @DisplayName("按用户名查询喜欢作品列表缺少请求体时应委托服务层默认分页")
    void listLikedWorksByUsernameControl_WhenBodyMissing_ShouldDelegateWithNullPageRequest() throws Exception {
        String username = "alice";
        ResponseEntity<J> expectedResponse = ResponseEntity.ok(new J(0, "success", null));
        when(workLikeService.listLikedWorksByUsername(username, null)).thenReturn(expectedResponse);

        ResponseEntity<J> response = workLikeController.listLikedWorksByUsernameControl(username, null);

        assertSame(expectedResponse, response);
        verify(workLikeService).listLikedWorksByUsername(username, null);

        RequestBody requestBody = pageRequestBodyAnnotation();
        assertNotNull(requestBody);
        assertFalse(requestBody.required());
    }

    private RequestBody pageRequestBodyAnnotation() throws NoSuchMethodException {
        Method method = WorkLikeController.class.getMethod(
                "listLikedWorksByUsernameControl",
                String.class,
                PageRequestDto.class
        );
        for (Annotation annotation : method.getParameterAnnotations()[1]) {
            if (annotation instanceof RequestBody requestBody) {
                return requestBody;
            }
        }
        return null;
    }
}
