package com.miaoyu.barc.api.work.service;

import com.miaoyu.barc.api.work.enumeration.*;
import com.miaoyu.barc.api.work.mapper.*;
import com.miaoyu.barc.api.work.model.*;
import com.miaoyu.barc.email.service.MailOutbox;
import com.miaoyu.barc.exception.ApiException;
import com.miaoyu.barc.user.enumeration.OfficialAccountEnum;
import com.miaoyu.barc.user.mapper.*;
import com.miaoyu.barc.user.model.*;
import com.miaoyu.barc.user.service.*;
import com.miaoyu.barc.utils.dto.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 管理查询、恢复与认领撤销的业务回归；鉴权切面另由权限边界测试验证。 */
@ExtendWith(MockitoExtension.class)
class WorkManagementLifecycleTest {
    @Mock WorkMapper works;
    @Mock WorkClaimMapper claims;
    @Mock WorkOperationLogMapper logs;
    @Mock WorkReviewMapper reviews;
    @Mock UserBasicMapper basics;
    @Mock UserArchiveMapper users;
    @Mock MailOutbox outbox;
    @Mock WorkService shared;
    @Mock OfficialAccountService official;
    @Mock UserSearchService search;
    @InjectMocks WorkManageService service;

    @Test void managementPageClampsSizeAndSignsTheReturnedBatch() {
        var dto = page(Map.of("status", "PRIVATE", "keyword", "  检索  ")); dto.setPage_num(2); dto.setPage_size(9999);
        var work = work(); when(works.selectByPageForManage("PRIVATE", "检索", 100, 100, null, null)).thenReturn(List.of(work));
        when(works.countByPageForManage("PRIVATE", "检索")).thenReturn(101L);
        var result = (PageResultDto<?>) service.getWorkListForManage("admin", dto).getBody().getData();
        assertEquals(101L, result.getTotal()); assertEquals(2, result.getTotal_page()); assertEquals(List.of(work), result.getList()); verify(shared).loopSignatureWorkCover(List.of(work));
        assertEquals(0L, ((PageResultDto<?>) service.getWorkListForManage("admin", null).getBody().getData()).getTotal());
    }

    @Test void reviewQueueDefaultsToPendingAndRejectsMalformedSearch() {
        var model = work(); when(works.selectByPageForReview(WorkReviewStatusEnum.PENDING, null, 0, 10)).thenReturn(List.of(model));
        when(works.countByPageForReview(WorkReviewStatusEnum.PENDING, null)).thenReturn(1L);
        assertEquals(1L, ((PageResultDto<?>) service.getReviewList("admin", null, null).getBody().getData()).getTotal()); verify(shared).loopSignatureWorkCover(List.of(model));
        assertThrows(ApiException.class, () -> service.getReviewList("admin", null, page(Map.of("keyword", List.of("a")))));
        assertThrows(ApiException.class, () -> service.getWorkListForManage("admin", page(Map.of("keyword", "a".repeat(101)))));
    }

    @Test void detailsShareProjectionAndMissingWorkReturnsBusinessFailure() {
        assertNotEquals(0, service.getWorkDetailForManage("admin", "w").getBody().getCode());
        assertNotEquals(0, service.getWorkEditDetail("admin", "w").getBody().getCode());
        assertNotEquals(0, service.getClaimWorkDetail("admin", "w").getBody().getCode());
        var model = work(); var detail = new WorkEditDetailDto(); detail.setWork(model); when(works.selectById("w")).thenReturn(model); when(shared.buildWorkEditDetailDto(model)).thenReturn(detail);
        assertSame(model, service.getWorkDetailForManage("admin", "w").getBody().getData());
        assertSame(detail, service.getWorkEditDetail("admin", "w").getBody().getData()); assertSame(detail, service.getClaimWorkDetail("admin", "w").getBody().getData());
    }

    @Test void statusChangesRememberVisibilityAndNotifyDatabaseRecipient() {
        var model = work(); model.setStatus(WorkStatusEnum.PRIVATE); when(works.selectByIdForUpdate("w")).thenReturn(model);
        when(works.updateStatus(eq("w"), any(), nullable(WorkStatusEnum.class))).thenReturn(true); when(logs.insert(any())).thenReturn(1);
        var author = new UserBasicModel(); author.setEmail("author@example.test"); when(basics.selectByUuid("author")).thenReturn(author);
        assertEquals(0, service.updateWorkStatus("admin", "w", WorkStatusEnum.OFF, "原因").getBody().getCode()); verify(works).updateStatus("w", WorkStatusEnum.OFF, WorkStatusEnum.PRIVATE);
        model.setRestore_status(WorkStatusEnum.PRIVATE);
        assertEquals(0, service.updateWorkStatus("admin", "w", WorkStatusEnum.PUBLIC, null).getBody().getCode()); verify(works).updateStatus("w", WorkStatusEnum.PRIVATE, null);
        assertEquals(0, service.updateWorkStatus("admin", "w", WorkStatusEnum.BAN, null).getBody().getCode());
        assertEquals(0, service.updateWorkStatus("admin", "w", WorkStatusEnum.DELETED, null).getBody().getCode());
        verify(outbox, times(4)).enqueue(startsWith("work-status:"), eq("author@example.test"), startsWith("您的作品已被"), contains("《标题》"));
    }

    @Test void invalidStatusTransitionsNeverWriteOrNotify() {
        assertNotEquals(0, service.updateWorkStatus("admin", "missing", WorkStatusEnum.BAN, null).getBody().getCode());
        var model = work(); when(works.selectByIdForUpdate("w")).thenReturn(model);
        assertNotEquals(0, service.updateWorkStatus("admin", "w", WorkStatusEnum.PUBLIC, null).getBody().getCode());
        assertNotEquals(0, service.updateWorkStatus("admin", "w", WorkStatusEnum.PRIVATE, null).getBody().getCode());
        model.setStatus(WorkStatusEnum.DELETED);
        assertNotEquals(0, service.updateWorkStatus("admin", "w", WorkStatusEnum.BAN, null).getBody().getCode());
        assertNotEquals(0, service.updateWorkStatus("admin", "w", WorkStatusEnum.OFF, null).getBody().getCode());
        verify(works, never()).updateStatus(any(), any(), any()); verifyNoInteractions(logs, outbox);
    }

    @Test void administratorContentEditRequiresVersionWriteAndAudit() {
        var model = work(); when(works.selectByIdForUpdate("w")).thenReturn(model); when(works.updateText(model)).thenReturn(true); when(works.touchContent("w")).thenReturn(true); when(logs.insert(any())).thenReturn(1);
        assertEquals(0, service.updateWorkContent("admin", model).getBody().getCode()); verify(shared).validateContentUpdate(model, model);
        verify(logs).insert(argThat(row -> row.getOperation_type().equals("EDIT") && row.getOperator_uuid().equals("admin")));
        when(works.touchContent("w")).thenReturn(false); assertThrows(ApiException.class, () -> service.updateWorkContent("admin", model));
        var missing = new WorkModel(); missing.setId("missing"); assertNotEquals(0, service.updateWorkContent("admin", missing).getBody().getCode()); verifyNoInteractions(outbox);
    }

    @Test void claimListHistoryAndPaginationKeepClosedApplications() {
        service.getClaimsList("admin"); verify(claims).selectPage("PENDING", null, null, 0, 100);
        service.getClaimHistory("admin", "w"); verify(claims).selectPage(null, "w", null, 0, 100);
        when(claims.countPage("REJECTED", "w", null)).thenReturn(101L);
        var result = (PageResultDto<?>) service.getClaimsPage("admin", "REJECTED", "w", 2, 1000).getBody().getData(); assertEquals(2, result.getTotal_page());
        verify(claims).selectPage("REJECTED", "w", null, 100, 100); assertThrows(ApiException.class, () -> service.getClaimsPage("admin", "UNKNOWN", null, 1, 20));
    }

    @Test void revokeDoesNotMutateUntilOfficialAttributionIsConfigured() {
        assertNotEquals(0, service.revokeClaim("admin", "missing").getBody().getCode());
        var model = work(); when(works.selectByIdForUpdate("w")).thenReturn(model);
        assertNotEquals(0, service.revokeClaim("admin", "w").getBody().getCode());
        model.setIs_claim(true); assertNotEquals(0, service.revokeClaim("admin", "w").getBody().getCode()); assertEquals("author", model.getAuthor());
        when(official.findBoundUuid(OfficialAccountEnum.COLLECTION_ASSISTANT)).thenReturn("official"); when(works.updateAttribution("w", "official", false)).thenReturn(true); when(logs.insert(any())).thenReturn(1);
        assertEquals(0, service.revokeClaim("admin", "w").getBody().getCode()); verify(logs).insert(argThat(row -> row.getOperation_type().equals("CLAIM_REVOKE")));
    }

    @Test void assignRequiresRealUserClosesPendingClaimsAndWritesAudit() {
        assertNotEquals(0, service.assignAuthor("admin", "missing", "target").getBody().getCode());
        when(works.selectByIdForUpdate("w")).thenReturn(work()); assertNotEquals(0, service.assignAuthor("admin", "w", "target").getBody().getCode());
        when(users.selectByUuid("target")).thenReturn(new UserArchiveModel()); when(works.updateAttribution("w", "target", true)).thenReturn(true); when(logs.insert(any())).thenReturn(1);
        assertEquals(0, service.assignAuthor("admin", "w", "target").getBody().getCode()); verify(claims).closeOtherPending("w", "admin");
        when(works.updateAttribution("w", "target", true)).thenReturn(false); assertThrows(ApiException.class, () -> service.assignAuthor("admin", "w", "target"));
    }

    @Test void logsHonorTypeFilterAndSearchDoesNotEnumerateOnBlankInput() {
        service.getOperationLogs("admin", null); verify(logs).selectByPage(0, 10); verify(logs).countAll();
        when(logs.countByType("EDIT")).thenReturn(12L); var result = (PageResultDto<?>) service.getOperationLogs("admin", page(Map.of("operation_type", "EDIT"))).getBody().getData(); assertEquals(2, result.getTotal_page()); verify(logs).selectByTypeAndPage("EDIT", 0, 10);
        assertTrue(service.searchWorks("admin", "  ").isEmpty());
        var match = work(); match.setContent("管理正文不可出现在选择器"); match.setCover_image("private/key.png");
        when(works.selectByPageForManage(null, "标题", 0, 10, "created_at", "DESC")).thenReturn(List.of(match));
        assertEquals(List.of(new WorkSearchView("w", "标题")), service.searchWorks("admin", "  标题  "));
        verify(works).selectByPageForManage(null, "标题", 0, 10, "created_at", "DESC");
        assertThrows(ApiException.class, () -> service.searchWorks("admin", "a".repeat(101))); service.searchUsers("admin", "作者"); verify(search).search("作者");
    }

    private PageRequestDto page(Map<String, Object> params) { var dto = new PageRequestDto(); dto.setParams(params); return dto; }
    private WorkModel work() { var value = new WorkModel(); value.setId("w"); value.setTitle("标题"); value.setAuthor("author"); value.setStatus(WorkStatusEnum.PUBLIC); value.setIs_claim(false); return value; }
}
