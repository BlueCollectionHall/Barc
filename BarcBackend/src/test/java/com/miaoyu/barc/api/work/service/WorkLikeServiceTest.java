package com.miaoyu.barc.api.work.service;

import com.miaoyu.barc.api.work.enumeration.WorkStatusEnum;
import com.miaoyu.barc.api.work.enumeration.WorkReviewStatusEnum;
import com.miaoyu.barc.api.work.mapper.WorkLikeMapper;
import com.miaoyu.barc.api.work.mapper.WorkMapper;
import com.miaoyu.barc.api.work.model.WorkLikeModel;
import com.miaoyu.barc.api.work.model.WorkLikeToggleDto;
import com.miaoyu.barc.api.work.model.WorkModel;
import com.miaoyu.barc.utils.J;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * 作品点赞服务单元测试
 */
@ExtendWith(MockitoExtension.class)
class WorkLikeServiceTest {

    @Mock
    private WorkLikeMapper workLikeMapper;

    @Mock
    private WorkMapper workMapper;

    @InjectMocks
    private WorkLikeService workLikeService;

    private WorkModel work;
    private WorkLikeModel existingLike;

    @BeforeEach
    void setUp() {
        work = new WorkModel();
        work.setId("work-1");
        work.setLike_count(10);
        work.setStatus(WorkStatusEnum.PUBLIC);
        work.setReview_status(WorkReviewStatusEnum.APPROVED);

        existingLike = new WorkLikeModel();
        existingLike.setId("like-1");
        existingLike.setWork_id("work-1");
        existingLike.setUser_uuid("user-1");
    }

    @Test
    @DisplayName("首次点赞应插入关系并增加计数")
    void toggleLike_WhenNotLiked_ShouldInsertRelationAndIncreaseCount() {
        WorkModel latestWork = new WorkModel();
        latestWork.setId("work-1");
        latestWork.setLike_count(15);
        latestWork.setStatus(WorkStatusEnum.PUBLIC);
        when(workMapper.selectById("work-1")).thenReturn(work, latestWork);
        when(workLikeMapper.selectByWorkIdAndUserUuid("work-1", "user-1")).thenReturn(null).thenReturn(existingLike);
        when(workLikeMapper.insert(any(WorkLikeModel.class))).thenReturn(1);
        when(workMapper.incrementLikeCount("work-1")).thenReturn(1);

        ResponseEntity<J> response = workLikeService.toggleLike("user-1", dto("work-1"));

        J body = response.getBody();
        assertNotNull(body);
        assertEquals(0, body.getCode());
        Map<String, Object> data = data(body);
        assertEquals(true, data.get("liked"));
        assertEquals(15, data.get("like_count"));
        verify(workLikeMapper).insert(any(WorkLikeModel.class));
        verify(workMapper).incrementLikeCount("work-1");
        verify(workMapper, never()).decrementLikeCount("work-1");
        verify(workMapper, times(2)).selectById("work-1");
    }

    @Test
    @DisplayName("首次点赞时作品点赞数为空应按0增加并返回1")
    void toggleLike_WhenLikeCountNull_ShouldReturnOneAfterSuccessfulIncrement() {
        work.setLike_count(null);
        WorkModel latestWork = new WorkModel();
        latestWork.setId("work-1");
        latestWork.setLike_count(1);
        latestWork.setStatus(WorkStatusEnum.PUBLIC);
        when(workMapper.selectById("work-1")).thenReturn(work, latestWork);
        when(workLikeMapper.selectByWorkIdAndUserUuid("work-1", "user-1")).thenReturn(null).thenReturn(existingLike);
        when(workLikeMapper.insert(any(WorkLikeModel.class))).thenReturn(1);
        when(workMapper.incrementLikeCount("work-1")).thenReturn(1);

        ResponseEntity<J> response = workLikeService.toggleLike("user-1", dto("work-1"));

        J body = response.getBody();
        assertNotNull(body);
        assertEquals(0, body.getCode());
        Map<String, Object> data = data(body);
        assertEquals(true, data.get("liked"));
        assertEquals(1, data.get("like_count"));
        verify(workMapper).incrementLikeCount("work-1");
        verify(workMapper, never()).decrementLikeCount("work-1");
        verify(workMapper, times(2)).selectById("work-1");
    }

    @Test
    @DisplayName("已点赞再次切换应删除关系并降低计数")
    void toggleLike_WhenAlreadyLiked_ShouldDeleteRelationAndDecreaseCount() {
        WorkModel latestWork = new WorkModel();
        latestWork.setId("work-1");
        latestWork.setLike_count(7);
        latestWork.setStatus(WorkStatusEnum.PUBLIC);
        when(workMapper.selectById("work-1")).thenReturn(work, latestWork);
        when(workLikeMapper.selectByWorkIdAndUserUuid("work-1", "user-1")).thenReturn(existingLike).thenReturn(null);
        when(workLikeMapper.deleteByWorkIdAndUserUuid("work-1", "user-1")).thenReturn(1);
        when(workMapper.decrementLikeCount("work-1")).thenReturn(1);

        ResponseEntity<J> response = workLikeService.toggleLike("user-1", dto("work-1"));

        J body = response.getBody();
        assertNotNull(body);
        assertEquals(0, body.getCode());
        Map<String, Object> data = data(body);
        assertEquals(false, data.get("liked"));
        assertEquals(7, data.get("like_count"));
        verify(workLikeMapper).deleteByWorkIdAndUserUuid("work-1", "user-1");
        verify(workMapper).decrementLikeCount("work-1");
        verify(workMapper, never()).incrementLikeCount("work-1");
        verify(workMapper, times(2)).selectById("work-1");
    }

    @Test
    @DisplayName("作品不存在时应返回失败")
    void toggleLike_WhenWorkMissing_ShouldReturnFailure() {
        when(workMapper.selectById("missing")).thenReturn(null);

        ResponseEntity<J> response = workLikeService.toggleLike("user-1", dto("missing"));

        J body = response.getBody();
        assertNotNull(body);
        assertEquals(1, body.getCode());
        assertEquals("作品不存在", body.getData());
        verify(workLikeMapper, never()).insert(any(WorkLikeModel.class));
        verify(workMapper, never()).incrementLikeCount("missing");
        verify(workMapper, never()).decrementLikeCount("missing");
    }

    @Test
    @DisplayName("请求体为空时应返回普通错误")
    void toggleLike_WhenDtoNull_ShouldReturnFailure() {
        ResponseEntity<J> response = workLikeService.toggleLike("user-1", null);

        J body = response.getBody();
        assertNotNull(body);
        assertEquals(1, body.getCode());
        assertEquals("作品ID不能为空", body.getData());
        verifyNoInteractions(workMapper, workLikeMapper);
    }

    @Test
    @DisplayName("作品ID为空白时应返回普通错误")
    void toggleLike_WhenWorkIdBlank_ShouldReturnFailure() {
        ResponseEntity<J> response = workLikeService.toggleLike("user-1", dto("  "));

        J body = response.getBody();
        assertNotNull(body);
        assertEquals(1, body.getCode());
        assertEquals("作品ID不能为空", body.getData());
        verifyNoInteractions(workMapper, workLikeMapper);
    }

    @Test
    @DisplayName("用户UUID为空时应返回普通错误")
    void toggleLike_WhenUserUuidBlank_ShouldReturnFailure() {
        ResponseEntity<J> nullUserResponse = workLikeService.toggleLike(null, dto("work-1"));
        ResponseEntity<J> blankUserResponse = workLikeService.toggleLike("  ", dto("work-1"));

        J nullBody = nullUserResponse.getBody();
        J blankBody = blankUserResponse.getBody();
        assertNotNull(nullBody);
        assertNotNull(blankBody);
        assertEquals(1, nullBody.getCode());
        assertEquals(1, blankBody.getCode());
        assertEquals("用户未登录", nullBody.getData());
        assertEquals("用户未登录", blankBody.getData());
        verifyNoInteractions(workMapper, workLikeMapper);
    }

    @Test
    @DisplayName("不可访问作品不允许点赞")
    void toggleLike_WhenWorkInaccessible_ShouldReturnFailure() {
        WorkStatusEnum[] inaccessibleStatuses = {
                WorkStatusEnum.PRIVATE,
                WorkStatusEnum.BAN,
                WorkStatusEnum.OFF,
                WorkStatusEnum.DELETED
        };

        for (WorkStatusEnum status : inaccessibleStatuses) {
            WorkModel inaccessibleWork = new WorkModel();
            inaccessibleWork.setId(status.name());
            inaccessibleWork.setLike_count(10);
            inaccessibleWork.setStatus(status);
            when(workMapper.selectById(status.name())).thenReturn(inaccessibleWork);

            ResponseEntity<J> response = workLikeService.toggleLike("user-1", dto(status.name()));

            J body = response.getBody();
            assertNotNull(body);
            assertEquals(1, body.getCode());
            assertEquals("作品不可点赞", body.getData());
        }

        verify(workLikeMapper, never()).selectByWorkIdAndUserUuid(any(), any());
        verify(workLikeMapper, never()).insert(any(WorkLikeModel.class));
        verify(workMapper, never()).incrementLikeCount(any());
        verify(workMapper, never()).decrementLikeCount(any());
    }

    @Test
    @DisplayName("重复插入冲突时应兜底返回最新点赞状态和最新计数")
    void toggleLike_WhenInsertConflicts_ShouldReturnCurrentLikedState() {
        WorkModel latestWork = new WorkModel();
        latestWork.setId("work-1");
        latestWork.setLike_count(12);
        latestWork.setStatus(WorkStatusEnum.PUBLIC);
        when(workMapper.selectById("work-1")).thenReturn(work, latestWork);
        when(workLikeMapper.selectByWorkIdAndUserUuid("work-1", "user-1")).thenReturn(null, existingLike);
        when(workLikeMapper.insert(any(WorkLikeModel.class))).thenThrow(new DuplicateKeyException("duplicate"));

        ResponseEntity<J> response = workLikeService.toggleLike("user-1", dto("work-1"));

        J body = response.getBody();
        assertNotNull(body);
        assertEquals(0, body.getCode());
        Map<String, Object> data = data(body);
        assertEquals(true, data.get("liked"));
        assertEquals(12, data.get("like_count"));
        verify(workMapper, times(2)).selectById("work-1");
        verify(workMapper, never()).incrementLikeCount("work-1");
        verify(workMapper, never()).decrementLikeCount("work-1");
    }

    @Test
    @DisplayName("点赞计数增加0行时应兜底返回最新状态和计数")
    void toggleLike_WhenIncrementCountUpdatesZeroRows_ShouldReturnLatestStateAndCount() {
        WorkModel latestWork = new WorkModel();
        latestWork.setId("work-1");
        latestWork.setLike_count(10);
        latestWork.setStatus(WorkStatusEnum.PUBLIC);
        when(workMapper.selectById("work-1")).thenReturn(work, latestWork);
        when(workLikeMapper.selectByWorkIdAndUserUuid("work-1", "user-1")).thenReturn(null).thenReturn(existingLike);
        when(workLikeMapper.insert(any(WorkLikeModel.class))).thenReturn(1);
        when(workMapper.incrementLikeCount("work-1")).thenReturn(0);

        ResponseEntity<J> response = workLikeService.toggleLike("user-1", dto("work-1"));

        J body = response.getBody();
        assertNotNull(body);
        assertEquals(0, body.getCode());
        Map<String, Object> data = data(body);
        assertEquals(true, data.get("liked"));
        assertEquals(10, data.get("like_count"));
        verify(workMapper, times(2)).selectById("work-1");
        verify(workMapper).incrementLikeCount("work-1");
        verify(workMapper, never()).decrementLikeCount("work-1");
    }

    @Test
    @DisplayName("删除竞争导致删除0行时应兜底返回最新未点赞状态和最新计数")
    void toggleLike_WhenDeleteRaceRemovesRelation_ShouldReturnLatestStateAndCount() {
        WorkModel latestWork = new WorkModel();
        latestWork.setId("work-1");
        latestWork.setLike_count(8);
        latestWork.setStatus(WorkStatusEnum.PUBLIC);
        when(workMapper.selectById("work-1")).thenReturn(work, latestWork);
        when(workLikeMapper.selectByWorkIdAndUserUuid("work-1", "user-1")).thenReturn(existingLike).thenReturn(null);
        when(workLikeMapper.deleteByWorkIdAndUserUuid("work-1", "user-1")).thenReturn(0);

        ResponseEntity<J> response = workLikeService.toggleLike("user-1", dto("work-1"));

        J body = response.getBody();
        assertNotNull(body);
        assertEquals(0, body.getCode());
        Map<String, Object> data = data(body);
        assertEquals(false, data.get("liked"));
        assertEquals(8, data.get("like_count"));
        verify(workMapper, times(2)).selectById("work-1");
        verify(workMapper, never()).incrementLikeCount("work-1");
        verify(workMapper, never()).decrementLikeCount("work-1");
    }

    @Test
    @DisplayName("点赞计数减少0行时应兜底返回最新状态和计数")
    void toggleLike_WhenDecrementCountUpdatesZeroRows_ShouldReturnLatestStateAndCount() {
        WorkModel latestWork = new WorkModel();
        latestWork.setId("work-1");
        latestWork.setLike_count(10);
        latestWork.setStatus(WorkStatusEnum.PUBLIC);
        when(workMapper.selectById("work-1")).thenReturn(work, latestWork);
        when(workLikeMapper.selectByWorkIdAndUserUuid("work-1", "user-1")).thenReturn(existingLike).thenReturn(null);
        when(workLikeMapper.deleteByWorkIdAndUserUuid("work-1", "user-1")).thenReturn(1);
        when(workMapper.decrementLikeCount("work-1")).thenReturn(0);

        ResponseEntity<J> response = workLikeService.toggleLike("user-1", dto("work-1"));

        J body = response.getBody();
        assertNotNull(body);
        assertEquals(0, body.getCode());
        Map<String, Object> data = data(body);
        assertEquals(false, data.get("liked"));
        assertEquals(10, data.get("like_count"));
        verify(workMapper, times(2)).selectById("work-1");
        verify(workMapper, never()).incrementLikeCount("work-1");
        verify(workMapper).decrementLikeCount("work-1");
    }

    private WorkLikeToggleDto dto(String workId) {
        WorkLikeToggleDto dto = new WorkLikeToggleDto();
        dto.setWork_id(workId);
        return dto;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> data(J body) {
        return (Map<String, Object>) body.getData();
    }
}
