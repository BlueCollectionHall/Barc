package com.miaoyu.barc.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.miaoyu.barc.api.controller.*;
import com.miaoyu.barc.api.service.*;
import com.miaoyu.barc.api.work.controller.WorkClaimController;
import com.miaoyu.barc.api.work.mapper.WorkClaimMapper;
import com.miaoyu.barc.api.work.model.WorkClaimListDto;
import com.miaoyu.barc.api.work.service.WorkClaimService;
import com.miaoyu.barc.friendlink.controller.FriendLinkController;
import com.miaoyu.barc.friendlink.service.FriendLinkService;
import com.miaoyu.barc.utils.J;
import java.util.List;
import org.junit.jupiter.api.*;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.*;
import static com.miaoyu.barc.security.HttpContractSupport.*;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** 分类、友链和本人认领的读写合同；申请状态和操作者均不能由上传表单伪造。 */
class CatalogAndClaimsHttpTest {
    CategoryService categories = apiMock(CategoryService.class);
    CategoryManageService manage = apiMock(CategoryManageService.class);
    FriendLinkService friends = apiMock(FriendLinkService.class);
    WorkClaimService claims = apiMock(WorkClaimService.class);
    MockMvc mvc;
    @BeforeEach void setup() {
        var publicCategory = new CategoryController(); var management = new CategoryManageController(); var friend = new FriendLinkController(); var claim = new WorkClaimController();
        ReflectionTestUtils.setField(publicCategory, "categoryService", categories); ReflectionTestUtils.setField(management, "categoryManageService", manage); ReflectionTestUtils.setField(friend, "friendLinkService", friends); ReflectionTestUtils.setField(claim, "workClaimService", claims); mvc = mvc(publicCategory, management, friend, claim);
    }
    @Test void categoryReadShapesAndManagedMovesStaySeparate() throws Exception {
        ok(get("/api/category/all")); verify(categories).getCategoriesService(true, null);
        ok(get("/api/category/all_top_categories")); verify(categories).getAllTopCategoriesService();
        ok(get("/api/category/all_not_graded")); verify(categories).getCategoriesAllNotGradedService();
        ok(get("/api/category/categories_by_category").param("category_id", "c")); verify(categories).getCategoriesService(false, "c");
        ok(get("/api/category/manage/list").requestAttr("uuid", "actor")); verify(manage).listService("actor");
        String form = "{\"id\":\"c\",\"name\":\"分类\",\"parent_id\":\"p\",\"sort\":2}";
        ok(post("/api/category/manage/create").requestAttr("uuid", "actor").contentType(MediaType.APPLICATION_JSON).content(form)); verify(manage).createService(eq("actor"), argThat(body -> body.getParent_id().equals("p")));
        ok(put("/api/category/manage/c").requestAttr("uuid", "actor").contentType(MediaType.APPLICATION_JSON).content(form)); verify(manage).updateService(eq("actor"), eq("c"), any());
        ok(put("/api/category/manage/c/sort").requestAttr("uuid", "actor").param("direction", "up")); verify(manage).moveSortService("actor", "c", "up");
        ok(delete("/api/category/manage/c").requestAttr("uuid", "actor")); verify(manage).deleteService("actor", "c");
    }
    @Test void friendWritesUseKeysAndAuthenticatedActor() throws Exception {
        ok(multipart("/api/friend-link/upload").file(new MockMultipartFile("file", "a.png", "image/png", new byte[]{1})).requestAttr("uuid", "actor")); verify(friends).uploadService(eq("actor"), any());
        ok(get("/api/friend-link/admin/list").requestAttr("uuid", "actor")); verify(friends).listService("actor");
        String body = "{\"title\":\"友链\",\"url\":\"https://example.test\",\"intro\":\"介绍\",\"logo_key\":\"client/friend/a.png\"}";
        ok(post("/api/friend-link/admin/create").requestAttr("uuid", "actor").contentType(MediaType.APPLICATION_JSON).content(body)); verify(friends).createService(eq("actor"), argThat(item -> item.getTitle().equals("友链")));
        ok(put("/api/friend-link/admin/f").requestAttr("uuid", "actor").contentType(MediaType.APPLICATION_JSON).content(body)); verify(friends).updateService(eq("actor"), eq("f"), any());
        ok(put("/api/friend-link/admin/f/move").requestAttr("uuid", "actor").param("direction", "down")); verify(friends).moveService("actor", "f", "down");
        ok(delete("/api/friend-link/admin/f").requestAttr("uuid", "actor")); verify(friends).deleteService("actor", "f");
        when(friends.publicAllService()).thenReturn(new J(0, "ok", List.of())); ok(get("/api/friend-link/all"));
    }
    @Test void claimSubmissionDoesNotBindForgedApplicantOrResult() throws Exception {
        ok(post("/api/work/claim/upload").requestAttr("uuid", "actor").contentType(MediaType.APPLICATION_JSON).content("{\"work_id\":\"w\",\"statement\":\"证据\",\"applicant_uuid\":\"forged\",\"status\":\"APPROVED\",\"reviewer_uuid\":\"forged\"}"));
        verify(claims).uploadWorkClaimService(eq("actor"), argThat(row -> row.getWork_id().equals("w") && row.getStatement().equals("证据") && row.getApplicant_uuid() == null && row.getReviewer_uuid() == null && row.getStatus().equals("PENDING")));
        ok(get("/api/work/claim/mine").requestAttr("uuid", "actor").param("work_id", "w")); verify(claims).getMyClaims("actor", "w", 1, 20);
        ok(post("/api/work/claim/claim_check").requestAttr("uuid", "actor").param("work_claim_id", "c")); verify(claims).checkWorkClaimService("actor", "c");
    }
    @Test void myClaimPageRetainsClosedResultAndFiltersAuthenticatedApplicant() throws Exception {
        var mapper = mock(WorkClaimMapper.class); var service = new WorkClaimService(); ReflectionTestUtils.setField(service, "workClaimMapper", mapper); var json = new ObjectMapper();
        var row = json.readValue("""
          {"id":"c","work_id":"w","work_title":"作品","applicant_uuid":"actor","applicant_nickname":"昵称","applicant_username":"username","created_at":"2026-01-01 00:00:00","status":"REJECTED","statement":"证据","reviewer_uuid":"manager","processed_at":"2026-01-02 00:00:00","result_reason":"未通过"}
          """, WorkClaimListDto.class);
        when(mapper.countPage(null, "w", "actor")).thenReturn(21L); when(mapper.selectPage(null, "w", "actor", 20, 20)).thenReturn(List.of(row));
        var result = json.valueToTree(service.getMyClaims("actor", "w", 2, 20).getBody().getData());
        assertEquals(21, result.path("total").asInt()); assertEquals(2, result.path("page_num").asInt()); assertEquals("REJECTED", result.path("list").get(0).path("status").asText());
        assertEquals("未通过", result.path("list").get(0).path("result_reason").asText()); assertEquals("作品", result.path("list").get(0).path("work_title").asText());
        service.getClaimsByUuidControl("actor"); verify(mapper).selectPage(null, null, "actor", 0, 100);
    }
    private void ok(RequestBuilder request) throws Exception { mvc.perform(request).andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0)); }
}
