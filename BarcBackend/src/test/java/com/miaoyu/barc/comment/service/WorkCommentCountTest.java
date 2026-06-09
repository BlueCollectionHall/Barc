package com.miaoyu.barc.comment.service;

import com.miaoyu.barc.annotation.IgnoreAuth;
import com.miaoyu.barc.comment.controller.WorkCommentController;
import com.miaoyu.barc.comment.mapper.WorkCommentMapper;
import com.miaoyu.barc.comment.mapper.WorkCommentReplyMapper;
import com.miaoyu.barc.comment.model.WorkCommentModel;
import com.miaoyu.barc.comment.model.WorkCommentReplyModel;
import com.miaoyu.barc.utils.J;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.bind.annotation.GetMapping;

import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WorkCommentCountTest {

    @Mock
    private WorkCommentMapper workCommentMapper;
    @Mock
    private WorkCommentReplyMapper workCommentReplyMapper;

    @InjectMocks
    private WorkCommentService workCommentService;

    @Test
    @DisplayName("作品评论计数应等于主评论数加所有回复数")
    void countByWork_ShouldReturnMainCommentCountPlusAllReplyCount() {
        WorkCommentModel firstComment = new WorkCommentModel();
        firstComment.setId("comment-1");
        WorkCommentModel secondComment = new WorkCommentModel();
        secondComment.setId("comment-2");
        when(workCommentMapper.selectByWorkId("work-1")).thenReturn(List.of(firstComment, secondComment));
        when(workCommentReplyMapper.selectByParentId("comment-1")).thenReturn(List.of(new WorkCommentReplyModel(), new WorkCommentReplyModel()));
        when(workCommentReplyMapper.selectByParentId("comment-2")).thenReturn(List.of(new WorkCommentReplyModel()));

        ResponseEntity<J> response = ReflectionTestUtils.invokeMethod(
                workCommentService,
                "getCommentCountByWorkService",
                "work-1"
        );

        assertNotNull(response);
        J body = response.getBody();
        assertNotNull(body);
        assertEquals(0, body.getCode());
        assertEquals(5, body.getData());
    }

    @Test
    @DisplayName("作品评论计数接口应公开使用 count_by_work 路由")
    void countByWorkControl_ShouldBePublicGetEndpoint() throws Exception {
        Method method = WorkCommentController.class.getMethod("getCommentCountByWorkControl", String.class);

        assertTrue(method.isAnnotationPresent(IgnoreAuth.class));
        GetMapping getMapping = method.getAnnotation(GetMapping.class);
        assertNotNull(getMapping);
        assertArrayEquals(new String[]{"/count_by_work"}, getMapping.value());
    }
}
