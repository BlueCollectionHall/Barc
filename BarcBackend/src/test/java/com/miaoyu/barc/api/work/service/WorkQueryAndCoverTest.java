package com.miaoyu.barc.api.work.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.miaoyu.barc.api.file.service.ObjectStorageService;
import com.miaoyu.barc.api.mapper.*;
import com.miaoyu.barc.api.model.StudentModel;
import com.miaoyu.barc.api.service.*;
import com.miaoyu.barc.api.work.enumeration.*;
import com.miaoyu.barc.api.work.mapper.*;
import com.miaoyu.barc.api.work.model.*;
import com.miaoyu.barc.api.work.model.entity.WorkEntity;
import com.miaoyu.barc.exception.ApiException;
import com.miaoyu.barc.utils.dto.*;
import com.miaoyu.barc.utils.tencent.cos.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 对统一公开查询和本人分页执行真实投影；封面替换验证先绑定后回收及失败中断。 */
@ExtendWith(MockitoExtension.class)
class WorkQueryAndCoverTest {
    @Mock WorkMapper works;
    @Mock WorkCoverImageMapper covers;
    @Mock WorkReviewMapper reviews;
    @Mock StudentMapper students;
    @Mock StudentService studentService;
    @Mock CosService cos;
    @Mock ObjectStorageService storage;
    @Mock CosGcService garbage;
    @Spy CosConfig config = new CosConfig();
    @InjectMocks WorkService service;

    @Test void publicPageSharesFiltersSignsCoverAndReturnsOnlyPublicFields() {
        var model = work(); model.setReview_reason("内部审核原因"); model.setRestore_status(WorkStatusEnum.PRIVATE);
        when(works.selectByPage(eq(WorkStatusEnum.PUBLIC), eq(0), eq(10), anyMap())).thenReturn(List.of(model));
        when(works.countByPage(eq(WorkStatusEnum.PUBLIC), anyMap())).thenReturn(1L); when(covers.selectByWorkIds(List.of("w"))).thenReturn(List.of(cover()));
        when(cos.generateBatchSignedUrl(eq(List.of("old/key.png")), any(), eq(CosBucketConfigEnum.image))).thenReturn(List.of("signed-cover"));
        var dto = new PageRequestDto(); dto.setParams(Map.of("keyword", " 标题 ", "category_id", "c", "author_uuid", "a", "ignored", "DROP"));
        var page = (PageResultDto<?>) service.getWorkByPageService(WorkStatusEnum.PUBLIC, dto).getBody().getData();
        var json = new ObjectMapper().valueToTree(page); assertEquals(1, json.path("total").asInt()); assertEquals("signed-cover", json.path("list").get(0).path("cover_image").asText());
        assertFalse(json.path("list").get(0).has("review_reason")); assertFalse(json.path("list").get(0).has("restore_status"));
        verify(works).selectByPage(WorkStatusEnum.PUBLIC, 0, 10, Map.of("keyword", "标题", "category_id", "c", "author_uuid", "a"));
        service.getWorksByCategoryService("parent", WorkStatusEnum.PUBLIC, null);
        verify(works).selectByPage(WorkStatusEnum.PUBLIC, 0, 10, Map.of("category_id", "parent"));
    }

    @Test void legacyPublicReadPathsDelegateToOneBoundedFilterQuery() {
        service.getWorksAllService(WorkStatusEnum.PUBLIC); service.getWorksBySchoolService("s", WorkStatusEnum.PUBLIC);
        service.getWorksByClubService("c", WorkStatusEnum.PUBLIC); service.getWorksByStudentService("t", WorkStatusEnum.PUBLIC);
        service.getWorksByUuidService("a", WorkStatusEnum.PUBLIC); service.getWorksByUsernameService("username", WorkStatusEnum.PUBLIC);
        for (var filters : List.<Map<String, Object>>of(Map.of(), Map.of("school_id", "s"), Map.of("club_id", "c"), Map.of("student_id", "t"), Map.of("author_uuid", "a"), Map.of("author_username", "username")))
            verify(works).selectByPage(WorkStatusEnum.PUBLIC, 0, 100, filters);
        assertNotEquals(0, service.getWorksAllService(WorkStatusEnum.PRIVATE).getBody().getCode());
        assertNotEquals(0, service.getWorkByPageService(WorkStatusEnum.BAN, null).getBody().getCode());
    }

    @Test void recentWorkRejectsUnboundedDaysAndResolvesStudentImagesOnce() {
        var student = new StudentModel(); student.setId("s"); when(students.selectByRecentWork(7)).thenReturn(List.of(student));
        assertEquals(List.of(student), service.getNewWorkService(7, true).getBody().getData()); verify(studentService).resolveImages(student);
        service.getNewWorkService(7, false); verify(works).selectByPage(WorkStatusEnum.PUBLIC, 0, 100, Map.of("day", 7));
        assertThrows(ApiException.class, () -> service.getNewWorkService(0, true)); assertThrows(ApiException.class, () -> service.getNewWorkService(366, false));
        for (Object day : List.of("broken", 0, 366)) assertThrows(ApiException.class, () -> service.getPublicLegacyList(WorkStatusEnum.PUBLIC, Map.of("day", day), 1, 10));
        assertThrows(ApiException.class, () -> service.getPublicLegacyList(WorkStatusEnum.PUBLIC, Map.of("keyword", List.of("a")), 1, 10));
        assertThrows(ApiException.class, () -> service.getPublicLegacyList(WorkStatusEnum.PUBLIC, Map.of("keyword", "a".repeat(101)), 1, 10));
    }

    @Test void ownerPageKeepsRejectedReasonButOverwritesForgedAuthorFilter() throws Exception {
        var mapper = new ObjectMapper().findAndRegisterModules();
        var entity = mapper.readValue("""
          {"id":"w","title":"标题","description":"简介","view_count":1,"like_count":2,"author":"owner","author_nickname":"署名","is_claim":true,"status":"PRIVATE","student":"s","created_at":"2026-01-01T00:00:00","updated_at":"2026-01-02T00:00:00","content_updated_at":"2026-01-02T00:00:00","content_revision":3,"review_status":"REJECTED","review_reason":"请完善","reviewer_uuid":"reviewer","review_submitted_at":"2026-01-01T00:00:00","reviewed_at":"2026-01-02T00:00:00"}
          """, WorkEntity.class);
        when(works.countByUuidWithFilters(eq("owner"), isNull(), isNull(), anyMap())).thenReturn(11L);
        when(works.selectByUuidWithFilters(eq("owner"), isNull(), isNull(), anyMap())).thenReturn(List.of(entity));
        when(covers.selectByWorkIds(List.of("w"))).thenReturn(List.of(cover())); when(cos.generateBatchSignedUrl(anyList(), any(), any())).thenReturn(List.of("signed-cover"));
        var request = new PageRequestDto(); request.setParams(Map.of("author_uuid", "forged"));
        var result = service.getMyWorkPage("owner", null, null, request).getBody().getData(); var json = mapper.valueToTree(result);
        assertEquals(11, json.path("total").asInt()); assertEquals(2, json.path("total_page").asInt()); assertEquals("请完善", json.path("list").get(0).path("review_reason").asText());
        assertEquals("signed-cover", json.path("list").get(0).path("cover_image").asText()); assertEquals(3, json.path("list").get(0).path("content_revision").asInt());
        verify(works).selectByUuidWithFilters("owner", null, null, Map.of("author_uuid", "owner", "offset", 0, "limit", 10));
    }

    @Test void onlyOwnerCanReadCompatibilityDetailAndInvisibleStatesStayPrivate() {
        assertNotEquals(0, service.getWorkByIdWithMeService("owner", "w").getBody().getCode());
        var work = work(); when(works.selectById("w")).thenReturn(work);
        assertSame(work, service.getWorkByIdWithMeService("owner", "w").getBody().getData());
        assertNotEquals(0, service.getWorkByIdWithMeService("other", "w").getBody().getCode());
        for (var status : List.of(WorkStatusEnum.PRIVATE, WorkStatusEnum.BAN, WorkStatusEnum.OFF, WorkStatusEnum.DELETED)) { work.setStatus(status); assertNotEquals(0, service.getWorksByIdService("w").getBody().getCode()); }
        verifyNoInteractions(cos);
    }

    @Test void missingCoverIsCreatedAndVersionFailureStopsReview() {
        var work = work(); when(works.selectByIdForUpdate("w")).thenReturn(work);
        var file = new MockMultipartFile("cover", "a.png", "image/png", new byte[]{1});
        when(storage.upload(eq("owner"), eq(file), anyString(), anyLong())).thenReturn("new/key.png"); when(covers.insert(any())).thenReturn(true);
        when(works.touchContent("w")).thenReturn(true); when(reviews.insertPending("w")).thenReturn(true);
        assertEquals(0, service.replaceOwnerWorkCover("owner", "w", file).getBody().getCode());
        var order = inOrder(storage, covers, works, reviews); order.verify(storage).upload(eq("owner"), eq(file), anyString(), anyLong()); order.verify(storage).bind("owner", "new/key.png"); order.verify(covers).selectByWorkId("w"); order.verify(covers).insert(argThat(row -> row.getObject_key().equals("new/key.png") && row.getWork_id().equals("w"))); order.verify(works).touchContent("w");
        when(covers.insert(any())).thenReturn(false); assertThrows(ApiException.class, () -> service.replaceOwnerWorkCover("owner", "w", file)); verify(works, times(1)).touchContent("w"); verifyNoInteractions(garbage);
    }

    @Test void visibilityAndContentValidationRejectStaleOrMissingRelations() {
        var work = work(); when(works.selectByIdForUpdate("w")).thenReturn(work); when(works.updateStatus("w", WorkStatusEnum.PRIVATE, null)).thenReturn(true);
        assertEquals(0, service.updateOwnerWorkVisibility("owner", "w", WorkStatusEnum.PRIVATE).getBody().getCode());
        work.setStatus(WorkStatusEnum.BAN); assertNotEquals(0, service.updateOwnerWorkVisibility("owner", "w", WorkStatusEnum.PUBLIC).getBody().getCode());
        var update = work(); update.setExpected_revision(99L); assertThrows(ApiException.class, () -> service.validateContentUpdate(work, update));
        update.setExpected_revision(0L); update.setStudent("missing"); assertThrows(ApiException.class, () -> service.validateContentUpdate(work, update));
        update.setStudent(null); work.setStudent("s"); service.validateContentUpdate(work, update); assertEquals("s", update.getStudent());
    }

    private WorkModel work() { var value = new WorkModel(); value.setId("w"); value.setTitle("标题"); value.setAuthor("owner"); value.setUploader("uploader"); value.setStatus(WorkStatusEnum.PUBLIC); value.setReview_status(WorkReviewStatusEnum.APPROVED); return value; }
    private WorkCoverImageModel cover() { var value = new WorkCoverImageModel(); value.setId("cover"); value.setWork_id("w"); value.setObject_key("old/key.png"); return value; }
}
