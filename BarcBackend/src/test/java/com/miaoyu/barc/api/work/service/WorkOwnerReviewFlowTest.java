package com.miaoyu.barc.api.work.service;

import com.miaoyu.barc.api.work.enumeration.WorkReviewStatusEnum;
import com.miaoyu.barc.api.work.mapper.WorkMapper;
import com.miaoyu.barc.api.work.mapper.WorkReviewMapper;
import com.miaoyu.barc.api.work.model.WorkModel;
import com.miaoyu.barc.api.work.model.WorkReviewModel;
import com.miaoyu.barc.utils.J;
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
class WorkOwnerReviewFlowTest {
    @Mock
    private WorkMapper workMapper;
    @Mock
    private WorkReviewMapper workReviewMapper;

    @InjectMocks
    private WorkService workService;

    @Test
    @DisplayName("驳回作品保存修改时保持驳回状态并保留再次提审入口")
    void rejectedWorkEdit_ShouldNotAutomaticallyResubmit() {
        WorkModel stored = ownedWork("owner-1");
        WorkModel update = new WorkModel();
        update.setId("work-1");
        WorkReviewModel review = review(WorkReviewStatusEnum.REJECTED);
        when(workMapper.selectById("work-1")).thenReturn(stored);
        when(workMapper.updateText(update)).thenReturn(true);
        when(workReviewMapper.selectByWorkId("work-1")).thenReturn(review);

        ResponseEntity<J> response = workService.updateOwnerWorkContent("owner-1", update);

        assertSuccess(response);
        verify(workReviewMapper, never()).submitForReview("work-1");
        verify(workReviewMapper, never()).resubmitRejected("work-1");
    }

    @Test
    @DisplayName("作品所有者可以将驳回作品再次提交为待审")
    void rejectedWorkOwner_ShouldResubmitToPending() {
        when(workMapper.selectById("work-1")).thenReturn(ownedWork("owner-1"));
        when(workReviewMapper.selectByWorkId("work-1"))
                .thenReturn(review(WorkReviewStatusEnum.REJECTED));
        when(workReviewMapper.resubmitRejected("work-1")).thenReturn(true);

        ResponseEntity<J> response = workService.resubmitRejectedWork("owner-1", "work-1");

        assertSuccess(response);
        verify(workReviewMapper).resubmitRejected("work-1");
    }

    @Test
    @DisplayName("非作品所有者不能再次提审")
    void rejectedWorkNonOwner_ShouldBeRejectedBeforeReviewUpdate() {
        when(workMapper.selectById("work-1")).thenReturn(ownedWork("owner-1"));

        ResponseEntity<J> response = workService.resubmitRejectedWork("other-user", "work-1");

        assertError(response, "用户UUID信息不匹配：User UUID information mismatch");
        verify(workReviewMapper, never()).selectByWorkId("work-1");
        verify(workReviewMapper, never()).resubmitRejected("work-1");
    }

    @Test
    @DisplayName("待审或已通过作品不能调用再次提审")
    void nonRejectedWork_ShouldNotResubmit() {
        when(workMapper.selectById("work-1")).thenReturn(ownedWork("owner-1"));
        when(workReviewMapper.selectByWorkId("work-1"))
                .thenReturn(review(WorkReviewStatusEnum.PENDING));

        ResponseEntity<J> response = workService.resubmitRejectedWork("owner-1", "work-1");

        assertError(response, "只有审核未通过的作品可以再次提审");
        verify(workReviewMapper, never()).resubmitRejected("work-1");
    }

    private WorkModel ownedWork(String ownerUuid) {
        WorkModel work = new WorkModel();
        work.setId("work-1");
        work.setAuthor(ownerUuid);
        return work;
    }

    private WorkReviewModel review(WorkReviewStatusEnum status) {
        WorkReviewModel review = new WorkReviewModel();
        review.setWork_id("work-1");
        review.setStatus(status);
        review.setRejection_reason(status == WorkReviewStatusEnum.REJECTED ? "请修改封面" : null);
        return review;
    }

    private void assertSuccess(ResponseEntity<J> response) {
        J body = response.getBody();
        assertNotNull(body);
        assertEquals(0, body.getCode());
    }

    private void assertError(ResponseEntity<J> response, String message) {
        J body = response.getBody();
        assertNotNull(body);
        assertEquals(1, body.getCode());
        assertEquals(message, body.getData());
    }
}
