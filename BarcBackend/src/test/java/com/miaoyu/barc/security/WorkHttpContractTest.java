package com.miaoyu.barc.security;

import com.miaoyu.barc.api.work.controller.*;
import com.miaoyu.barc.api.work.enumeration.*;
import com.miaoyu.barc.api.work.service.*;
import java.util.Map;
import org.junit.jupiter.api.*;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.*;
import static com.miaoyu.barc.security.HttpContractSupport.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** 作品双端 HTTP 合同：认证 UUID 不受请求覆盖，正文白名单与管理命令分开绑定。 */
class WorkHttpContractTest {
    WorkService owner = apiMock(WorkService.class);
    WorkManageService manage = apiMock(WorkManageService.class);
    WorkDeletionService deletion = apiMock(WorkDeletionService.class);
    WorkCompatibilityService compatibility = apiMock(WorkCompatibilityService.class);
    MockMvc mvc;

    @BeforeEach void setup() {
        var publicController = new WorkController(); var manageController = new WorkManageController();
        ReflectionTestUtils.setField(publicController, "workService", owner); ReflectionTestUtils.setField(publicController, "workDeletionService", deletion); ReflectionTestUtils.setField(publicController, "workCompatibilityService", compatibility);
        ReflectionTestUtils.setField(manageController, "workManageService", manage); mvc = mvc(publicController, manageController);
    }

    @Test void publicListsBindFiltersAndLegacyDefaults() throws Exception {
        ok(get("/api/work/new").param("day", "7").param("is_student_list", "true")); verify(owner).getNewWorkService(7, true);
        ok(get("/api/work/all").param("status", "PUBLIC")); verify(owner).getPublicLegacyList(WorkStatusEnum.PUBLIC, Map.of(), 1, 100);
        for (String field : new String[]{"school", "club", "student"}) {
            ok(get("/api/work/works_by_" + field).param("status", "PUBLIC").param(field + "_id", "id").param("page", "2").param("size", "20"));
            verify(owner).getPublicLegacyList(WorkStatusEnum.PUBLIC, Map.of(field + "_id", "id"), 2, 20);
        }
        ok(post("/api/work/works_by_page").param("status", "PUBLIC").contentType(MediaType.APPLICATION_JSON).content("{\"page_num\":2,\"params\":{\"keyword\":\"标题\"}}"));
        verify(owner).getWorkByPageService(eq(WorkStatusEnum.PUBLIC), argThat(dto -> dto.getPage_num() == 2 && dto.getParams().get("keyword").equals("标题")));
        ok(post("/api/work/works_by_category").param("status", "PUBLIC").param("category_id", "c").contentType(MediaType.APPLICATION_JSON).content("{}")); verify(owner).getWorksByCategoryService(eq("c"), eq(WorkStatusEnum.PUBLIC), any());
    }

    @Test void ownerPagesAlwaysUseAuthenticationContextAndPublicAuthorUsesQuery() throws Exception {
        ok(get("/api/work/works_by_me").requestAttr("uuid", "actor").param("uuid", "forged").param("status", "PRIVATE").param("review_status", "REJECTED").param("keyword", "词").param("school", "s").param("club", "c").param("student", "t"));
        verify(owner).getWorksByMeService("actor", WorkStatusEnum.PRIVATE, WorkReviewStatusEnum.REJECTED, Map.of("keyword", "词", "school", "s", "club", "c", "student", "t"));
        ok(post("/api/work/mine/page").requestAttr("uuid", "actor").contentType(MediaType.APPLICATION_JSON).content("{\"params\":{\"author_uuid\":\"forged\"}}")); verify(owner).getMyWorkPage(eq("actor"), isNull(), isNull(), any());
        ok(get("/api/work/works_by_uuid").param("uuid", "public-author").param("status", "PUBLIC")); verify(owner).getWorksByUuidService(eq("public-author"), eq(WorkStatusEnum.PUBLIC), anyMap());
    }

    @Test void detailRoutesKeepOwnerAndPublicServicesSeparate() throws Exception {
        ok(get("/api/work/only").param("work_id", "w")); verify(owner).getWorksByIdService(any(), eq("w"));
        ok(get("/api/work/only_by_me").requestAttr("uuid", "actor").param("work_id", "w")); verify(owner).getWorkByIdWithMeService("actor", "w");
        ok(get("/api/work/edit-detail").requestAttr("uuid", "actor").param("work_id", "w")); verify(owner).getOwnerWorkEditDetail("actor", "w");
    }

    @Test void contentCommandsDiscardPrivilegeFieldsAndRetainExpectedVersion() throws Exception {
        String body = "{\"id\":\"w\",\"title\":\" 标题 \",\"content\":\"正文\",\"expected_revision\":7,\"author\":\"forged\",\"status\":\"PUBLIC\",\"review_status\":\"APPROVED\"}";
        ok(put("/api/work/edit-update").requestAttr("uuid", "actor").contentType(MediaType.APPLICATION_JSON).content(body));
        verify(owner).updateOwnerWorkContent(eq("actor"), argThat(model -> model.getTitle().equals("标题") && model.getExpected_revision() == 7L && model.getAuthor() == null && model.getStatus() == null && model.getReview_status() == null));
        ok(put("/api/work/update").requestAttr("uuid", "actor").contentType(MediaType.APPLICATION_JSON).content(body)); verify(compatibility).update(eq("actor"), argThat(model -> model.getAuthor() == null));
        ok(put("/api/work/manage/update").requestAttr("uuid", "actor").contentType(MediaType.APPLICATION_JSON).content(body)); verify(manage).updateWorkContent(eq("actor"), argThat(model -> model.getStatus() == null));
    }

    @Test void ownerWriteCommandsBindBodyAndDeletionUsesAuthenticatedActor() throws Exception {
        ok(post("/api/work/review/resubmit").requestAttr("uuid", "actor").contentType(MediaType.APPLICATION_JSON).content("{\"work_id\":\"w\"}")); verify(owner).resubmitRejectedWork("actor", "w");
        ok(put("/api/work/visibility").requestAttr("uuid", "actor").contentType(MediaType.APPLICATION_JSON).content("{\"work_id\":\"w\",\"status\":\"PRIVATE\"}")); verify(owner).updateOwnerWorkVisibility("actor", "w", WorkStatusEnum.PRIVATE);
        ok(delete("/api/work/delete").requestAttr("uuid", "actor").param("work_id", "w").param("uuid", "forged")); verify(deletion).delete("actor", "w");
    }

    @Test void multipartUploadBindsOptionalGalleryAndCoverReplacementUsesPut() throws Exception {
        var cover = new MockMultipartFile("cover_image", "a.png", "image/png", new byte[]{1});
        ok(multipart("/api/work/upload").file(cover).requestAttr("uuid", "actor").param("form", "{\"title\":\"标题\",\"is_claim\":true}").param("category_id", "c"));
        verify(owner).uploadWorkService(eq("actor"), eq("c"), argThat(model -> model.getTitle().equals("标题")), any(), argThat(files -> files.length == 0));
        ok(multipart("/api/work/cover/replace").file(cover).requestAttr("uuid", "actor").param("work_id", "w").with(request -> { request.setMethod("PUT"); return request; }));
        verify(owner).replaceOwnerWorkCover(eq("actor"), eq("w"), any());
    }

    @Test void managementReadRoutesPreservePaginationAndEvidenceBoundary() throws Exception {
        ok(post("/api/work/manage/list").requestAttr("uuid", "actor").contentType(MediaType.APPLICATION_JSON).content("{}")); verify(manage).getWorkListForManage(eq("actor"), any());
        ok(post("/api/work/manage/reviews").requestAttr("uuid", "actor").contentType(MediaType.APPLICATION_JSON).content("{}")); verify(manage).getReviewList(eq("actor"), eq(WorkReviewStatusEnum.PENDING), any());
        ok(get("/api/work/manage/detail").requestAttr("uuid", "actor").param("work_id", "w")); verify(manage).getWorkDetailForManage("actor", "w");
        ok(get("/api/work/manage/edit-detail").requestAttr("uuid", "actor").param("work_id", "w")); verify(manage).getWorkEditDetail("actor", "w");
        ok(get("/api/work/manage/claim/detail").requestAttr("uuid", "actor").param("work_id", "w")); verify(manage).getClaimWorkDetail("actor", "w");
        ok(get("/api/work/manage/claims").requestAttr("uuid", "actor")); verify(manage).getClaimsList("actor");
        ok(get("/api/work/manage/claims/page").requestAttr("uuid", "actor").param("status", "APPROVED").param("work_id", "w")); verify(manage).getClaimsPage("actor", "APPROVED", "w", 1, 20);
        ok(get("/api/work/manage/claim/history").requestAttr("uuid", "actor").param("work_id", "w")); verify(manage).getClaimHistory("actor", "w");
    }

    @Test void managementWriteCommandsRequireExplicitBooleanAndVersion() throws Exception {
        ok(post("/api/work/manage/review").requestAttr("uuid", "actor").contentType(MediaType.APPLICATION_JSON).content("{\"work_id\":\"w\",\"approved\":false,\"reason\":\"原因\",\"expected_revision\":2}")); verify(manage).reviewWork("actor", "w", false, "原因", 2L);
        for (String body : new String[]{"{\"work_id\":\"w\",\"approved\":true}", "{\"work_id\":\"w\",\"approved\":\"false\",\"expected_revision\":2}", "{\"work_id\":\"w\",\"expected_revision\":2}"}) {
            mvc.perform(post("/api/work/manage/review").requestAttr("uuid", "actor").contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value(1));
        }
        verify(manage, times(1)).reviewWork(any(), any(), anyBoolean(), any(), any());
        ok(put("/api/work/manage/status").requestAttr("uuid", "actor").contentType(MediaType.APPLICATION_JSON).content("{\"work_id\":\"w\",\"status\":\"PUBLIC\",\"remark\":\"恢复\"}")); verify(manage).updateWorkStatus("actor", "w", WorkStatusEnum.PUBLIC, "恢复");
        ok(post("/api/work/manage/claim/approve").requestAttr("uuid", "actor").contentType(MediaType.APPLICATION_JSON).content("{\"claim_id\":\"c\",\"approved\":true}")); verify(manage).approveClaim("actor", "c", true);
        ok(post("/api/work/manage/claim/revoke").requestAttr("uuid", "actor").contentType(MediaType.APPLICATION_JSON).content("{\"work_id\":\"w\"}")); verify(manage).revokeClaim("actor", "w");
        ok(post("/api/work/manage/claim/assign").requestAttr("uuid", "actor").contentType(MediaType.APPLICATION_JSON).content("{\"work_id\":\"w\",\"author_uuid\":\"target\"}")); verify(manage).assignAuthor("actor", "w", "target");
    }

    @Test void managementSearchAndLogsUseActorAndRequestedKeyword() throws Exception {
        ok(post("/api/work/manage/logs").requestAttr("uuid", "actor").contentType(MediaType.APPLICATION_JSON).content("{\"params\":{\"operation_type\":\"EDIT\"}}")); verify(manage).getOperationLogs(eq("actor"), argThat(dto -> dto.getParams().get("operation_type").equals("EDIT")));
        when(manage.searchWorks("actor", "作品")).thenReturn(java.util.List.of(new com.miaoyu.barc.api.work.model.WorkSearchView("w", "作品")));
        mvc.perform(get("/api/work/manage/search-work").requestAttr("uuid", "actor").param("keyword", "作品"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data[0].id").value("w"))
                .andExpect(jsonPath("$.data[0].title").value("作品")).andExpect(jsonPath("$.data[0]", org.hamcrest.Matchers.aMapWithSize(2)));
        verify(manage).searchWorks("actor", "作品");
        ok(get("/api/work/manage/search-user").requestAttr("uuid", "actor").param("keyword", "用户")); verify(manage).searchUsers("actor", "用户");
    }

    private void ok(RequestBuilder request) throws Exception { mvc.perform(request).andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0)); }
}
