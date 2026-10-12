package com.miaoyu.barc.security;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.miaoyu.barc.api.work.enumeration.WorkStatusEnum;
import com.miaoyu.barc.api.work.mapper.WorkMapper;
import com.miaoyu.barc.api.work.mapper.WorkOperationLogMapper;
import com.miaoyu.barc.api.work.model.WorkModel;
import com.miaoyu.barc.api.work.service.WorkCompatibilityService;
import com.miaoyu.barc.api.work.service.WorkDeletionService;
import com.miaoyu.barc.api.work.service.WorkManageService;
import com.miaoyu.barc.api.work.service.WorkService;
import com.miaoyu.barc.notice.mapper.NoticeMapper;
import com.miaoyu.barc.notice.model.NoticeModel;
import com.miaoyu.barc.notice.service.NoticeService;
import com.miaoyu.barc.trend.mapper.TrendChangeMapper;
import com.miaoyu.barc.trend.mapper.TrendImageMapper;
import com.miaoyu.barc.trend.mapper.TrendSelectMapper;
import com.miaoyu.barc.trend.model.TrendModel;
import com.miaoyu.barc.trend.service.TrendService;
import com.miaoyu.barc.user.enumeration.UserIdentityEnum;
import com.miaoyu.barc.user.mapper.UserArchiveMapper;
import com.miaoyu.barc.user.model.UserArchiveModel;
import com.miaoyu.barc.user.model.dto.UserProfileUpdateRequest;
import com.miaoyu.barc.user.service.UserService;
import com.miaoyu.barc.utils.J;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import java.time.LocalDate;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 将审计中的伪造作者、全字段越权与直接删文件链路锁成业务行为回归。 */
class ObjectOwnershipRegressionTest {
    private final UserArchiveMapper users = mock(UserArchiveMapper.class);
    private final WorkMapper works = mock(WorkMapper.class);
    private final WorkOperationLogMapper logs = mock(WorkOperationLogMapper.class);

    @Test void profileRequestCannotWriteRolesOrClientTimestamps() throws Exception {
        UserService service = new UserService();
        ReflectionTestUtils.setField(service, "userArchiveMapper", users);
        UserProfileUpdateRequest request = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                .readValue("{\"uuid\":\"owner\",\"nickname\":\"新昵称\",\"permission\":63,\"identity\":\"MANAGER\",\"updated_at\":\"forged\"}", UserProfileUpdateRequest.class);
        when(users.update(any())).thenReturn(true);
        assertEquals(0, service.editArchiveService("owner", request).getBody().getCode());
        ArgumentCaptor<UserArchiveModel> captured = ArgumentCaptor.forClass(UserArchiveModel.class);
        verify(users).update(captured.capture());
        assertEquals("新昵称", captured.getValue().getNickname());
        assertNull(captured.getValue().getPermission());
        assertNull(captured.getValue().getIdentity());
        assertNull(captured.getValue().getUpdated_at());
        request.setBirthday(LocalDate.now().plusDays(1));
        assertEquals(1, service.editArchiveService("owner", request).getBody().getCode());
        assertEquals(1, service.editArchiveService("another", request).getBody().getCode());
        assertEquals(1, service.editArchiveService("owner", null).getBody().getCode());
        verify(users, times(1)).update(any());
    }

    @Test void forgedNoticeAuthorDoesNotAuthorizeEdit() {
        NoticeMapper notices = mock(NoticeMapper.class);
        NoticeService service = new NoticeService();
        ReflectionTestUtils.setField(service, "noticeMapper", notices);
        ReflectionTestUtils.setField(service, "userArchiveMapper", users);
        NoticeModel stored = new NoticeModel(); stored.setId("notice"); stored.setAuthor("real-author");
        NoticeModel forged = new NoticeModel(); forged.setId("notice"); forged.setAuthor("attacker"); forged.setTitle("改名"); forged.setContent("正文");
        when(notices.selectById("notice")).thenReturn(stored);
        when(users.selectByUuid("attacker")).thenReturn(PermissionBoundaryTest.user(UserIdentityEnum.USER, 1));
        assertEquals(1, service.updateNoticeService("attacker", forged).getBody().getCode());
        verify(notices, never()).update(any());
        when(users.selectByUuid("real-author")).thenReturn(PermissionBoundaryTest.user(UserIdentityEnum.USER, 1));
        when(notices.update(any())).thenReturn(true);
        assertEquals(0, service.updateNoticeService("real-author", forged).getBody().getCode());
        ArgumentCaptor<NoticeModel> saved = ArgumentCaptor.forClass(NoticeModel.class);
        verify(notices).update(saved.capture());
        assertNull(saved.getValue().getAuthor());
        assertEquals("改名", saved.getValue().getTitle());
        when(users.selectByUuid("manager")).thenReturn(PermissionBoundaryTest.user(UserIdentityEnum.MANAGER, 32));
        assertEquals(0, service.updateNoticeService("manager", forged).getBody().getCode());
        assertEquals(1, service.updateNoticeService("manager", null).getBody().getCode());
    }

    @ParameterizedTest @ValueSource(strings = {"author", "uploader", "manager"})
    void authorizedWorkDeletionOnlyChangesStatusAndWritesAudit(String actor) {
        WorkDeletionService service = deletionService();
        WorkModel work = work();
        when(works.selectByIdForUpdate("work")).thenReturn(work);
        when(users.selectByUuid(actor)).thenReturn(PermissionBoundaryTest.user(
                actor.equals("manager") ? UserIdentityEnum.MANAGER : UserIdentityEnum.USER, 4));
        when(works.softDelete("work")).thenReturn(true);
        when(logs.insert(any())).thenReturn(1);
        assertEquals(0, service.delete(actor, "work").getBody().getCode());
        verify(works).softDelete("work");
        verify(works, never()).delete(any());
        verify(works, never()).update(any());
        verify(logs).insert(argThat(entry -> "DELETE".equals(entry.getOperation_type()) && actor.equals(entry.getOperator_uuid())));
    }

    @Test void workDeletionRejectsUnrelatedUserAndHandlesMissingOrAlreadyDeleted() {
        WorkDeletionService service = deletionService();
        when(works.selectByIdForUpdate("work")).thenReturn(work());
        when(users.selectByUuid("outsider")).thenReturn(PermissionBoundaryTest.user(UserIdentityEnum.USER, 4));
        assertEquals(1, service.delete("outsider", "work").getBody().getCode());
        assertEquals(1, service.delete("missing", "work").getBody().getCode());
        assertEquals(1, service.delete("outsider", "missing").getBody().getCode());
        WorkModel deleted = work(); deleted.setStatus(WorkStatusEnum.DELETED);
        when(works.selectByIdForUpdate("work")).thenReturn(deleted);
        when(users.selectByUuid("author")).thenReturn(PermissionBoundaryTest.user(UserIdentityEnum.USER, 1));
        assertEquals(0, service.delete("author", "work").getBody().getCode());
        verify(works, never()).softDelete(any()); verifyNoInteractions(logs);
        deleted.setStatus(WorkStatusEnum.PUBLIC);
        assertEquals(1, service.delete("author", "work").getBody().getCode());
        verifyNoInteractions(logs);
    }

    @Test void unrelatedTrendDeletionDoesNotTouchImagesOrStorage() {
        TrendSelectMapper select = mock(TrendSelectMapper.class);
        TrendChangeMapper change = mock(TrendChangeMapper.class);
        TrendImageMapper images = mock(TrendImageMapper.class);
        TrendService service = new TrendService();
        var imageService = mock(com.miaoyu.barc.trend.service.TrendImageService.class);
        ReflectionTestUtils.setField(service, "imageService", imageService);
        ReflectionTestUtils.setField(service, "trendSelectMapper", select);
        ReflectionTestUtils.setField(service, "trendChangeMapper", change);
        ReflectionTestUtils.setField(service, "trendImageMapper", images);
        ReflectionTestUtils.setField(service, "userArchiveMapper", users);
        TrendModel trend = new TrendModel(); trend.setAuthor("author");
        when(select.selectByIdForUpdate("trend")).thenReturn(trend);
        when(users.selectByUuid("outsider")).thenReturn(PermissionBoundaryTest.user(UserIdentityEnum.USER, 1));
        assertEquals(1, service.deleteTrendService("outsider", "trend").getBody().getCode());
        assertEquals(1, service.deleteTrendService("outsider", "missing").getBody().getCode());
        verifyNoInteractions(change, images, imageService);
        when(users.selectByUuid("author")).thenReturn(PermissionBoundaryTest.user(UserIdentityEnum.USER, 1));
        when(images.selectByTrendId("trend")).thenReturn(List.of());
        when(change.delete("trend")).thenReturn(true);
        assertEquals(0, service.deleteTrendService("author", "trend").getBody().getCode());
    }

    @Test void legacyEditDelegatesToRoleSpecificReviewAndAuditFlows() {
        WorkService owner = mock(WorkService.class);
        WorkManageService manager = mock(WorkManageService.class);
        WorkCompatibilityService compatibility = new WorkCompatibilityService();
        ReflectionTestUtils.setField(compatibility, "workService", owner);
        ReflectionTestUtils.setField(compatibility, "workManageService", manager);
        ReflectionTestUtils.setField(compatibility, "userArchiveMapper", users);
        WorkModel request = work();
        when(users.selectByUuid("author")).thenReturn(PermissionBoundaryTest.user(UserIdentityEnum.USER, 4));
        when(users.selectByUuid("manager")).thenReturn(PermissionBoundaryTest.user(UserIdentityEnum.MANAGER, 4));
        ResponseEntity<J> response = ResponseEntity.ok(new J(0, "ok", null));
        when(owner.updateOwnerWorkContent("author", request)).thenReturn(response);
        when(manager.updateWorkContent("manager", request)).thenReturn(response);
        assertSame(response, compatibility.update("author", request));
        assertSame(response, compatibility.update("manager", request));
        verify(owner, never()).updateOwnerWorkContent("manager", request);
        verify(manager, never()).updateWorkContent("author", request);
    }

    private WorkDeletionService deletionService() {
        WorkDeletionService service = new WorkDeletionService();
        ReflectionTestUtils.setField(service, "workMapper", works);
        ReflectionTestUtils.setField(service, "userArchiveMapper", users);
        ReflectionTestUtils.setField(service, "operationLogMapper", logs);
        return service;
    }

    private WorkModel work() {
        WorkModel work = new WorkModel(); work.setId("work"); work.setAuthor("author");
        work.setUploader("uploader"); work.setStatus(WorkStatusEnum.PUBLIC); return work;
    }
}
