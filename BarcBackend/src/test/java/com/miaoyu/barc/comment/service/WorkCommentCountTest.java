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

    @Mock private com.miaoyu.barc.api.work.mapper.WorkMapper workMapper;

    @InjectMocks
    private WorkCommentService workCommentService;

    @Test
    @DisplayName("作品评论计数应等于主评论数加所有回复数")
    void countByWork_ShouldReturnMainCommentCountPlusAllReplyCount() {
        // 可见性先于聚合，计数不再按每条主评论读取回复。
        var work = new com.miaoyu.barc.api.work.model.WorkModel();
        work.setStatus(com.miaoyu.barc.api.work.enumeration.WorkStatusEnum.PUBLIC);
        work.setReview_status(com.miaoyu.barc.api.work.enumeration.WorkReviewStatusEnum.APPROVED);
        when(workMapper.selectById("work-1")).thenReturn(work);
        when(workCommentMapper.countWithReplies("work-1")).thenReturn(5L);

        ResponseEntity<J> response = ReflectionTestUtils.invokeMethod(
                workCommentService,
                "getCommentCountByWorkService",
                "work-1"
        );

        assertNotNull(response);
        J body = response.getBody();
        assertNotNull(body);
        assertEquals(0, body.getCode());
        assertEquals(5L, body.getData());
        org.mockito.Mockito.verifyNoInteractions(workCommentReplyMapper);
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
