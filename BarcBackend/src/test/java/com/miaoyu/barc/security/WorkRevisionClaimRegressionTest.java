package com.miaoyu.barc.security;

import com.fasterxml.jackson.databind.*;
import com.miaoyu.barc.api.work.enumeration.*;
import com.miaoyu.barc.api.work.mapper.*;
import com.miaoyu.barc.api.work.model.*;
import com.miaoyu.barc.api.work.service.*;
import com.miaoyu.barc.email.service.MailOutbox;
import com.miaoyu.barc.exception.ApiException;
import com.miaoyu.barc.user.mapper.*;
import com.miaoyu.barc.user.model.UserArchiveModel;
import com.miaoyu.barc.utils.pojo.PageInitPojo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 验证旧审核页面、状态恢复、认领重复提交以及审计失败的业务边界。 */
@ExtendWith(MockitoExtension.class)
class WorkRevisionClaimRegressionTest {
    @Mock WorkMapper works;
    @Mock WorkReviewMapper reviews;
    @Mock WorkClaimMapper claims;
    @Mock WorkOperationLogMapper logs;
    @Mock UserBasicMapper basics;
    @Mock UserArchiveMapper users;
    @Mock MailOutbox outbox;
    @Mock WorkService ownerService;
    @InjectMocks WorkManageService manage;
    @InjectMocks WorkClaimService claimant;

    @Test void staleReviewCannotApproveUnseenRevision() {
        when(works.selectByIdForUpdate("work")).thenReturn(work());
        assertEquals(1, manage.reviewWork("manager", "work", true, null, 6L).getBody().getCode());
        verifyNoInteractions(reviews, logs, outbox);
    }

    @Test void matchedReviewPersistsVersionInAuditAndFailurePropagates() {
        when(works.selectByIdForUpdate("work")).thenReturn(work());
        when(reviews.review("work", WorkReviewStatusEnum.APPROVED, null, "manager")).thenReturn(true);
        // 日志写入0行会抛出，真实数据库事务将在集成测试验证整笔回滚。
        assertThrows(ApiException.class, () -> manage.reviewWork("manager", "work", true, null, 7L));
        verify(logs).insert(argThat(log -> log.getDetail().contains("7") && log.getDetail().contains("content_revision")));
    }

    @Test void restoringPrivateWorkPreservesPrivacy() {
        WorkModel work = work(); work.setStatus(WorkStatusEnum.BAN); work.setRestore_status(WorkStatusEnum.PRIVATE);
        when(works.selectByIdForUpdate("work")).thenReturn(work);
        when(works.updateStatus("work", WorkStatusEnum.PRIVATE, null)).thenReturn(true);
        when(logs.insert(any())).thenReturn(1);
        assertEquals(0, manage.updateWorkStatus("manager", "work", WorkStatusEnum.PUBLIC, "复核通过").getBody().getCode());
        verify(works).updateStatus("work", WorkStatusEnum.PRIVATE, null);
        verify(works, never()).update(any());
    }

    @Test void missingHistoricalVisibilityRestoresPrivately() {
        WorkModel work = work(); work.setStatus(WorkStatusEnum.DELETED);
        when(works.selectByIdForUpdate("work")).thenReturn(work);
        when(works.updateStatus("work", WorkStatusEnum.PRIVATE, null)).thenReturn(true);
        when(logs.insert(any())).thenReturn(1);
        assertEquals(0, manage.updateWorkStatus("manager", "work", WorkStatusEnum.PUBLIC, null).getBody().getCode());
    }

    @Test void claimApprovalKeepsHistoryAndOnlyUpdatesAttribution() {
        WorkModel work = work(); work.setReview_status(WorkReviewStatusEnum.APPROVED);
        WorkClaimModel claim = claim();
        when(claims.selectById("claim")).thenReturn(claim);
        when(works.selectByIdForUpdate("work")).thenReturn(work);
        when(claims.selectByIdForUpdate("claim")).thenReturn(claim);
        when(claims.resolve("claim", "APPROVED", "manager", "认领通过")).thenReturn(true);
        when(users.selectByUuid("applicant")).thenReturn(new UserArchiveModel());
        when(works.updateAttribution("work", "applicant", true)).thenReturn(true);
        when(logs.insert(any())).thenReturn(1);
        assertEquals(0, manage.approveClaim("manager", "claim", true).getBody().getCode());
        InOrder order = inOrder(works, claims);
        order.verify(claims).selectById("claim");
        order.verify(works).selectByIdForUpdate("work");
        order.verify(claims).selectByIdForUpdate("claim");
        verify(claims).closeOtherPending("work", "manager");
        verify(works, never()).update(any());
    }

    @Test void resolvedClaimCannotBeProcessedTwice() {
        WorkClaimModel claim = claim(); claim.setStatus("REJECTED");
        when(claims.selectById("claim")).thenReturn(claim);
        when(works.selectByIdForUpdate("work")).thenReturn(work());
        when(claims.selectByIdForUpdate("claim")).thenReturn(claim);
        assertEquals(1, manage.approveClaim("manager", "claim", true).getBody().getCode());
        verify(claims, never()).resolve(any(), any(), any(), any());
        verifyNoInteractions(logs, outbox);
    }

    @Test void repeatedPendingSubmissionReturnsExistingApplication() {
        WorkModel work = work(); work.setReview_status(WorkReviewStatusEnum.APPROVED);
        WorkClaimModel existing = claim();
        when(works.selectByIdForUpdate("work")).thenReturn(work);
        when(claims.selectPending("work", "applicant")).thenReturn(existing);
        assertSame(existing, claimant.uploadWorkClaimService("applicant", claim()).getBody().getData());
        verify(claims, never()).insert(any());
    }

    @Test void submissionOverwritesForgedApplicantAndStatus() {
        WorkModel work = work(); work.setReview_status(WorkReviewStatusEnum.APPROVED);
        WorkClaimModel request = claim(); request.setApplicant_uuid("victim"); request.setStatus("APPROVED");
        when(works.selectByIdForUpdate("work")).thenReturn(work);
        when(claims.insert(any())).thenReturn(true);
        claimant.uploadWorkClaimService("applicant", request);
        verify(claims).insert(argThat(c -> "applicant".equals(c.getApplicant_uuid()) && "PENDING".equals(c.getStatus()) && !"claim".equals(c.getId())));
    }

    @Test void privateOrClaimedWorkRejectsApplicationsBeforeWrites() {
        WorkModel work = work(); work.setStatus(WorkStatusEnum.PRIVATE);
        when(works.selectByIdForUpdate("work")).thenReturn(work);
        assertThrows(ApiException.class, () -> claimant.uploadWorkClaimService("applicant", claim()));
        verifyNoInteractions(claims);
    }

    @Test void requestWhiteListAndStrictBooleanRejectUnsafeCoercion() throws Exception {
        ObjectMapper json = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        var content = json.readValue("{\"id\":\"work\",\"title\":\"标题\",\"is_claim\":true,\"author\":\"victim\"}", WorkCommands.Content.class).toModel();
        assertNull(content.getIs_claim()); assertNull(content.getAuthor());
        for (String approved : new String[]{"\"true\"", "1", "null", "\"no\""}) {
            assertThrows(Exception.class, () -> json.readValue("{\"work_id\":\"work\",\"expected_revision\":7,\"approved\":" + approved + "}", WorkCommands.Review.class));
        }
        assertTrue(json.readValue("{\"work_id\":\"work\",\"expected_revision\":7,\"approved\":true}", WorkCommands.Review.class).approved());
    }

    @Test void pageBoundsPreventOverflowWithoutDroppingPagination() {
        PageInitPojo page = new PageInitPojo(2, 9999);
        assertEquals(100, page.getPageSize()); assertEquals(100, page.getOffset());
        assertThrows(ApiException.class, () -> new PageInitPojo(Integer.MAX_VALUE, Integer.MAX_VALUE));
        assertEquals(0, new PageInitPojo(0, -1).getOffset());
    }

    private WorkModel work() {
        WorkModel work = new WorkModel(); work.setId("work"); work.setTitle("作品"); work.setAuthor("owner");
        work.setContent_revision(7L); work.setStatus(WorkStatusEnum.PUBLIC); work.setReview_status(WorkReviewStatusEnum.PENDING); work.setIs_claim(false); return work;
    }
    private WorkClaimModel claim() {
        WorkClaimModel claim = new WorkClaimModel(); claim.setId("claim"); claim.setWork_id("work"); claim.setApplicant_uuid("applicant"); return claim;
    }
}
