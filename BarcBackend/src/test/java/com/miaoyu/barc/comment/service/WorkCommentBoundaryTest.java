package com.miaoyu.barc.comment.service;

import com.miaoyu.barc.api.work.enumeration.*;
import com.miaoyu.barc.api.work.mapper.WorkMapper;
import com.miaoyu.barc.api.work.model.WorkModel;
import com.miaoyu.barc.comment.mapper.*;
import com.miaoyu.barc.comment.model.*;
import com.miaoyu.barc.comment.pojo.WorkCommentPojo;
import com.miaoyu.barc.exception.ApiException;
import com.miaoyu.barc.user.enumeration.UserIdentityEnum;
import com.miaoyu.barc.user.mapper.UserArchiveMapper;
import com.miaoyu.barc.user.model.UserArchiveModel;
import com.miaoyu.barc.utils.dto.PageResultDto;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 验证隐藏作品无旁路、真实作者授权以及有界回复读取。 */
@ExtendWith(MockitoExtension.class)
class WorkCommentBoundaryTest {
    @Mock WorkCommentMapper workCommentMapper;
    @Mock WorkCommentReplyMapper workCommentReplyMapper;
    @Mock WorkMapper workMapper;
    @Mock UserArchiveMapper userArchiveMapper;
    @InjectMocks WorkCommentService service;

    @ParameterizedTest @ValueSource(strings={"PRIVATE", "DELETED"})
    void hiddenWorkDeniesListCountAndDetail(String status) {
        when(workMapper.selectById("w")).thenReturn(work(WorkStatusEnum.valueOf(status)));
        when(workCommentMapper.selectById("c")).thenReturn(comment());
        assertThrows(ApiException.class, () -> service.getCommentsByWorkService("w"));
        assertThrows(ApiException.class, () -> service.getCommentCountByWorkService("w"));
        assertThrows(ApiException.class, () -> service.getCommentDetail("c", null, false));
        verify(workCommentMapper, never()).countWithReplies(any()); verifyNoInteractions(workCommentReplyMapper);
    }
    @Test void pendingWorkCannotReceiveReply() {
        var work = work(WorkStatusEnum.PUBLIC); work.setReview_status(WorkReviewStatusEnum.PENDING);
        when(workCommentMapper.selectById("c")).thenReturn(comment()); when(workMapper.selectByIdForUpdate("w")).thenReturn(work);
        assertThrows(ApiException.class, () -> service.uploadCommentAndReplyService("reply", "u", null, reply()));
        verify(workCommentReplyMapper, never()).insert(any());
    }
    @Test void replyRequiresParentAndParticipant() {
        when(workCommentMapper.selectById("c")).thenReturn(comment()); when(workCommentMapper.selectByIdForUpdate("c")).thenReturn(comment());
        when(workMapper.selectByIdForUpdate("w")).thenReturn(work(WorkStatusEnum.PUBLIC));
        var input = reply(); input.setReply_user("unrelated");
        assertThrows(ApiException.class, () -> service.uploadCommentAndReplyService("reply", "u", null, input));
        verify(workCommentReplyMapper, never()).insert(any());
    }
    @Test void replyIgnoresForgedAuthorAndId() {
        when(workCommentMapper.selectById("c")).thenReturn(comment()); when(workCommentMapper.selectByIdForUpdate("c")).thenReturn(comment());
        when(workMapper.selectByIdForUpdate("w")).thenReturn(work(WorkStatusEnum.PUBLIC)); when(workCommentReplyMapper.insert(any())).thenReturn(true);
        var input = reply(); input.setId("forged"); input.setAuthor("victim"); input.setReply_user("owner");
        assertEquals(0, service.uploadCommentAndReplyService("reply", "u", null, input).getBody().getCode());
        var captor = ArgumentCaptor.forClass(WorkCommentReplyModel.class); verify(workCommentReplyMapper).insert(captor.capture());
        assertEquals("u", captor.getValue().getAuthor()); assertNotEquals("forged", captor.getValue().getId());
    }
    @Test void invalidContentDoesNotTouchDatabase() {
        var input = new WorkCommentModel(); input.setWork_id("w"); input.setContent(" ");
        assertThrows(ApiException.class, () -> service.uploadCommentAndReplyService("comment", "u", input, null));
        verifyNoInteractions(workMapper, workCommentMapper, workCommentReplyMapper);
    }
    @Test void claimedAuthorArgumentCannotDeleteOthers() {
        when(workCommentMapper.selectByIdForUpdate("c")).thenReturn(comment());
        assertNotEquals(0, service.deleteCommentService("attacker", "attacker", "c").getBody().getCode());
        verify(workCommentMapper, never()).delete(any()); verifyNoInteractions(workCommentReplyMapper);
    }
    @ParameterizedTest @ValueSource(ints={1,4,8})
    void unrelatedManagerRolesCannotDeleteOrReadEvidence(int permission) {
        when(workCommentMapper.selectByIdForUpdate("c")).thenReturn(comment()); when(userArchiveMapper.selectByUuid("m")).thenReturn(manager(permission));
        assertNotEquals(0, service.deleteCommentService("m", "m", "c").getBody().getCode());
        assertNotEquals(0, service.getCommentDetail("c", "m", true).getBody().getCode());
        verify(workCommentMapper, never()).delete(any());
    }
    @ParameterizedTest @ValueSource(ints={2,16,32})
    void discussionManagersCanReadHiddenEvidence(int permission) {
        when(userArchiveMapper.selectByUuid("m")).thenReturn(manager(permission)); when(workCommentMapper.selectById("c")).thenReturn(comment());
        assertEquals(0, service.getCommentDetail("c", "m", true).getBody().getCode()); verifyNoInteractions(workMapper);
    }
    @Test void pageBatchesReplyPreviewsAndKeepsTotal() {
        when(workMapper.selectById("w")).thenReturn(work(WorkStatusEnum.PUBLIC));
        var first = comment(); first.setReply_count(90L); var second = comment(); second.setId("c2"); second.setReply_count(0L);
        when(workCommentMapper.selectPage("w", 10, 10)).thenReturn(List.of(first, second)); when(workCommentMapper.countParents("w")).thenReturn(12L);
        when(workCommentReplyMapper.selectPreview(List.of("c", "c2"))).thenReturn(List.of(reply()));
        var page = (PageResultDto<?>) service.getCommentsPage("w", 2, 10).getBody().getData();
        assertEquals(12L, page.getTotal()); assertEquals(90L, first.getReply_count()); assertEquals(1, first.getReplies().size()); assertTrue(second.getReplies().isEmpty());
        verify(workCommentReplyMapper, times(1)).selectPreview(anyList());
    }
    @Test void authorDeleteFailurePropagatesForTransactionRollback() {
        when(workCommentMapper.selectByIdForUpdate("c")).thenReturn(comment());
        assertThrows(ApiException.class, () -> service.deleteCommentService("owner", null, "c"));
        verify(workCommentReplyMapper).deleteByParentId("c");
    }
    /** 正常发布也要覆盖，确保白名单字段能保存且失败写入会传播。 */
    @Test void mainCommentWritesAuthenticatedAuthorAndFailureIsNotSuccess() {
        when(workMapper.selectByIdForUpdate("w")).thenReturn(work(WorkStatusEnum.PUBLIC)); when(workCommentMapper.insert(any())).thenReturn(true, false);
        var input = comment(); input.setId("forged"); input.setAuthor("forged"); input.setContent("  正文  ");
        assertEquals(0, service.uploadCommentAndReplyService("comment", "real", input, null).getBody().getCode());
        verify(workCommentMapper).insert(argThat(row -> row.getAuthor().equals("real") && row.getContent().equals("正文") && !row.getId().equals("forged")));
        assertThrows(ApiException.class, () -> service.uploadCommentAndReplyService("comment", "real", input, null));
    }
    @Test void publicRepliesPageAndReplyDetailRecheckParentWork() {
        when(workCommentMapper.selectById("c")).thenReturn(comment()); when(workMapper.selectById("w")).thenReturn(work(WorkStatusEnum.PUBLIC));
        when(workCommentReplyMapper.countByParentId("c")).thenReturn(12L); when(workCommentReplyMapper.selectPage("c", 5, 5)).thenReturn(List.of(reply()));
        var page = (PageResultDto<?>) service.getRepliesPage("c", 2, 5).getBody().getData(); assertEquals(12L, page.getTotal()); assertEquals(3, page.getTotal_page());
        when(workCommentReplyMapper.selectById("r")).thenReturn(reply());
        var detail = (Map<?, ?>) service.getCommentDetail("r", null, false).getBody().getData(); assertEquals("reply", detail.get("type")); assertEquals("c", detail.get("parent_id")); assertEquals("w", detail.get("work_id"));
    }
    @Test void commentAndReplyDeletesReflectRealOwnershipAndAffectedRows() {
        when(workCommentMapper.selectByIdForUpdate("c")).thenReturn(comment()); when(workCommentMapper.delete("c")).thenReturn(true);
        assertEquals(0, service.deleteCommentService("owner", null, "c").getBody().getCode());
        var reply = reply(); reply.setAuthor("owner"); when(workCommentReplyMapper.selectById("r")).thenReturn(reply);
        assertNotEquals(0, service.deleteReplyService("other", "other", "r").getBody().getCode());
        when(workCommentReplyMapper.deleteById("r")).thenReturn(true, false);
        assertEquals(0, service.deleteReplyService("owner", null, "r").getBody().getCode()); assertNotEquals(0, service.deleteReplyService("owner", null, "r").getBody().getCode());
        assertNotEquals(0, service.deleteReplyService("owner", null, "missing").getBody().getCode());
    }
    @ParameterizedTest @ValueSource(ints={2,4,16,32})
    void inspectionManagersCanPageHiddenCommentsAndReplies(int permission) {
        when(userArchiveMapper.selectByUuid("m")).thenReturn(manager(permission));
        when(workMapper.selectById("w")).thenReturn(work(WorkStatusEnum.PRIVATE));
        when(workCommentMapper.selectPage("w", 10, 10)).thenReturn(List.of(comment()));
        when(workCommentMapper.countParents("w")).thenReturn(21L);
        when(workCommentMapper.selectById("c")).thenReturn(comment());
        when(workCommentReplyMapper.countByParentId("c")).thenReturn(12L);
        when(workCommentReplyMapper.selectPage("c", 5, 5)).thenReturn(List.of(reply()));
        var page = (PageResultDto<?>) service.getManagementPage("m", "w", 2, 10).getBody().getData();
        assertEquals(21L, page.getTotal()); assertEquals(1, page.getList().size());
        var replies = (PageResultDto<?>) service.getManagementReplies("m", "c", 2, 5).getBody().getData();
        assertEquals(12L, replies.getTotal()); assertEquals(1, replies.getList().size());
    }
    @ParameterizedTest @ValueSource(ints={1,8,64,-1})
    void unrelatedRolesCannotUseManagementPagination(int permission) {
        when(userArchiveMapper.selectByUuid("m")).thenReturn(manager(permission));
        assertNotEquals(0, service.getManagementPage("m", "w", 1, 10).getBody().getCode());
        assertNotEquals(0, service.getManagementReplies("m", "c", 1, 5).getBody().getCode());
        verifyNoInteractions(workMapper, workCommentMapper, workCommentReplyMapper);
    }
    @Test void ordinaryUserWithSameRoleBitsCannotInspect() {
        var account = manager(4); account.setIdentity(UserIdentityEnum.USER);
        when(userArchiveMapper.selectByUuid("u")).thenReturn(account);
        assertNotEquals(0, service.getManagementPage("u", "w", 1, 10).getBody().getCode());
        verifyNoInteractions(workMapper, workCommentMapper, workCommentReplyMapper);
    }
    private WorkModel work(WorkStatusEnum status) { var w = new WorkModel(); w.setStatus(status); w.setReview_status(WorkReviewStatusEnum.APPROVED); return w; }
    private WorkCommentPojo comment() { var c = new WorkCommentPojo(); c.setId("c"); c.setWork_id("w"); c.setAuthor("owner"); return c; }
    private WorkCommentReplyModel reply() { var r = new WorkCommentReplyModel(); r.setParent_id("c"); r.setContent("回复"); return r; }
    private UserArchiveModel manager(int permission) { var u = new UserArchiveModel(); u.setIdentity(UserIdentityEnum.MANAGER); u.setPermission(permission); return u; }
}
