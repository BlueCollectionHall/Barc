package com.miaoyu.barc.security;

import com.miaoyu.barc.background.controller.BackgroundController;
import com.miaoyu.barc.background.service.BackgroundImageService;
import com.miaoyu.barc.festival.controller.FestivalController;
import com.miaoyu.barc.festival.service.FestivalService;
import com.miaoyu.barc.feedback.controller.FeedbackController;
import com.miaoyu.barc.feedback.enumeration.*;
import com.miaoyu.barc.feedback.service.FeedbackService;
import com.miaoyu.barc.notice.controller.NoticeController;
import com.miaoyu.barc.notice.service.NoticeService;
import com.miaoyu.barc.trend.controller.*;
import com.miaoyu.barc.trend.service.*;
import com.miaoyu.barc.utils.J;
import java.nio.charset.StandardCharsets;
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

/** 背景配置、公告、动态与反馈使用真实 MVC 绑定，保留公开读取和受控写入的独立服务调用。 */
class PlatformHttpContractTest {
    BackgroundImageService backgrounds = apiMock(BackgroundImageService.class);
    FestivalService festivals = apiMock(FestivalService.class);
    FeedbackService feedback = apiMock(FeedbackService.class);
    NoticeService notices = apiMock(NoticeService.class);
    TrendService trends = apiMock(TrendService.class);
    TrendImageService images = apiMock(TrendImageService.class);
    MockMvc mvc;
    @BeforeEach void setup() {
        var background = new BackgroundController(); var festival = new FestivalController(); var report = new FeedbackController(); var notice = new NoticeController(); var trend = new TrendController(); var image = new TrendImageController();
        ReflectionTestUtils.setField(background, "backgroundImageService", backgrounds); ReflectionTestUtils.setField(festival, "festivalService", festivals); ReflectionTestUtils.setField(report, "feedbackService", feedback);
        ReflectionTestUtils.setField(notice, "noticeService", notices); ReflectionTestUtils.setField(trend, "trendService", trends); ReflectionTestUtils.setField(image, "trendImageService", images); mvc = mvc(background, festival, report, notice, trend, image);
    }
    @Test void backgroundsBindModuleSceneFullOrderAndEnabledFlag() throws Exception {
        ok(get("/api/background/modules").requestAttr("uuid", "actor")); verify(backgrounds).modulesService("actor");
        ok(get("/api/background/admin/list").requestAttr("uuid", "actor").param("module", "home")); verify(backgrounds).listService("actor", "home");
        ok(multipart("/api/background/upload").file(file("file")).requestAttr("uuid", "actor").param("module", "home").param("time_period", "morning").param("festival", "f")); verify(backgrounds).uploadService(eq("actor"), any(), eq("home"), eq("morning"), eq("f"));
        ok(put("/api/background/reorder").requestAttr("uuid", "actor").contentType(MediaType.APPLICATION_JSON).content("[{\"id\":\"a\",\"sort_order\":2},{\"id\":\"b\",\"sort_order\":1}]")); verify(backgrounds).reorderService(eq("actor"), argThat(items -> items.size() == 2 && items.get(0).getSort_order() == 2));
        ok(put("/api/background/a/enabled").requestAttr("uuid", "actor").param("enabled", "false")); verify(backgrounds).setEnabledService("actor", "a", false);
        ok(put("/api/background/a/scene").requestAttr("uuid", "actor").contentType(MediaType.APPLICATION_JSON).content("{\"time_period\":\"night\",\"festival\":\"f\"}")); verify(backgrounds).updateSceneService("actor", "a", "night", "f");
        ok(delete("/api/background/a").requestAttr("uuid", "actor")); verify(backgrounds).deleteService("actor", "a");
        when(backgrounds.publicAllService()).thenReturn(new J(0, "ok", Map.of())); ok(get("/api/background/all"));
    }
    @Test void festivalOptionsRemainSeparateFromManagementStatistics() throws Exception {
        ok(get("/api/festival/admin/list").requestAttr("uuid", "actor")); verify(festivals).listService("actor");
        ok(get("/api/festival/options").requestAttr("uuid", "actor")); verify(festivals).optionsService("actor");
        String body = "{\"name\":\"跨年\",\"start_month\":12,\"start_day\":31,\"end_month\":1,\"end_day\":1}";
        ok(post("/api/festival/admin/create").requestAttr("uuid", "actor").contentType(MediaType.APPLICATION_JSON).content(body)); verify(festivals).createService(eq("actor"), argThat(item -> item.getStart_month() == 12 && item.getEnd_month() == 1));
        ok(put("/api/festival/admin/f").requestAttr("uuid", "actor").contentType(MediaType.APPLICATION_JSON).content(body)); verify(festivals).updateService(eq("actor"), eq("f"), any());
        ok(put("/api/festival/admin/f/enabled").requestAttr("uuid", "actor").param("enabled", "true")); verify(festivals).setEnabledService("actor", "f", true);
        ok(delete("/api/festival/admin/f").requestAttr("uuid", "actor")); verify(festivals).deleteService("actor", "f");
        when(festivals.publicAllService()).thenReturn(new J(0, "ok", java.util.List.of())); ok(get("/api/festival/all"));
    }
    @Test void noticeReadsAndWritesPreserveResponseAndSanitizeRichTextAtBinding() throws Exception {
        ok(get("/notice/latest").param("day", "7")); verify(notices).latestNoticeService(7);
        ok(post("/notice/notices_by_page").contentType(MediaType.APPLICATION_JSON).content("{\"page_num\":2}")); verify(notices).noticesByPageService(argThat(dto -> dto.getPage_num() == 2));
        ok(get("/notice/only").param("notice_id", "n")); verify(notices).getNoticeOnlyService("n");
        String body = "{\"id\":\"n\",\"title\":\"公告\",\"content\":\"<p>正文</p><script>alert(1)</script>\",\"author\":\"forged\"}";
        ok(post("/notice/upload").requestAttr("uuid", "actor").contentType(MediaType.APPLICATION_JSON).content(body)); verify(notices).uploadNoticeService(eq("actor"), argThat(row -> !row.getContent().contains("script")));
        ok(put("/notice/update").requestAttr("uuid", "actor").contentType(MediaType.APPLICATION_JSON).content(body)); verify(notices).updateNoticeService(eq("actor"), argThat(row -> row.getId().equals("n")));
        ok(delete("/notice/delete").requestAttr("uuid", "actor").param("notice_id", "n")); verify(notices).deleteNoticeService("actor", "n");
    }
    @Test void trendsRouteLegacyReadsThroughBoundedQueriesAndReturnLatest() throws Exception {
        ok(get("/trend/all")); verify(trends).getList(null, null, 1, 100);
        ok(get("/trend/latest")); verify(trends).getLatest(); ok(get("/trend/new").param("count", "3")); verify(trends).getNewTrendService(3);
        ok(get("/trend/trends_by_keyword").param("keyword", "词")); verify(trends).getList(null, "词", 1, 20);
        ok(get("/trend/trends_by_uuid").param("uuid", "target")); verify(trends).getList("target", null, 1, 20);
        ok(get("/trend/trends_by_me").requestAttr("uuid", "actor")); verify(trends).getList("actor", null, 1, 20);
        ok(get("/trend/page").param("author", "target").param("keyword", "词").param("page", "2")); verify(trends).getPage("target", "词", 2, 20);
        ok(get("/trend/only").param("trend_id", "t")); verify(trends).getTrendOnlyService("t");
    }
    @Test void trendMultipartUsesJsonPartAndSeparatesAuthenticatedAuthor() throws Exception {
        var form = new MockMultipartFile("form", "form.json", "application/json", "{\"content\":\"动态\",\"author\":\"forged\"}".getBytes(StandardCharsets.UTF_8));
        ok(multipart("/trend/upload").file(form).requestAttr("uuid", "actor")); verify(trends).uploadTrendService(eq("actor"), argThat(model -> model.getContent().equals("动态")), argThat(files -> files.length == 0));
        ok(delete("/trend/delete").requestAttr("uuid", "actor").param("trend_id", "t")); verify(trends).deleteTrendService("actor", "t");
        ok(multipart("/trend/image/upload").file(file("file")).requestAttr("uuid", "actor").param("trend_id", "t")); verify(images).uploadTrendImageService(eq("actor"), eq("t"), any());
        ok(delete("/trend/image/delete").requestAttr("uuid", "actor").param("trend_image_id", "i")); verify(images).deleteTrendImageService("actor", "i");
    }
    @Test void feedbackQueriesKeepTypeOwnershipAndManagementSwitchExplicit() throws Exception {
        ok(get("/feedback/types")); ok(get("/feedback/status"));
        ok(get("/feedback/feedbacks_by_type").requestAttr("uuid", "actor").param("type", "WORK").param("is_manager", "true").param("count", "5")); verify(feedback).getLegacyList("actor", FeedbackTypeEnum.WORK, true, 5);
        ok(get("/feedback/page").requestAttr("uuid", "actor").param("type", "BUG")); verify(feedback).getPage("actor", FeedbackTypeEnum.BUG, null, false, 1, 20);
        ok(get("/feedback/only_with_manager").requestAttr("uuid", "actor").param("feedback_id", "f")); verify(feedback).getFeedbackOnlyWithManagerService("actor", "f");
        ok(get("/feedback/only_with_me").requestAttr("uuid", "actor").param("feedback_id", "f")); verify(feedback).getFeedbackOnlyWithMeService("actor", "f");
    }
    @Test void feedbackSubmissionUsesConnectionIpAndOptionalAuthenticatedIdentity() throws Exception {
        ok(post("/feedback/upload").header("X-Forwarded-For", "198.51.100.1").contentType(MediaType.APPLICATION_JSON).content("{\"type\":\"BUG\",\"content\":\"故障\",\"author\":\"forged\",\"ip\":\"forged\",\"status\":\"COMPLETED\"}").with(request -> { request.setRemoteAddr("127.0.0.8"); return request; }));
        verify(feedback).uploadFeedbackService(isNull(), eq("127.0.0.8"), argThat(body -> body.type() == FeedbackTypeEnum.BUG && body.content().equals("故障")));
        ok(put("/feedback/update").requestAttr("uuid", "actor").contentType(MediaType.APPLICATION_JSON).content("{\"id\":\"f\",\"echo\":\"处理结果\",\"status\":\"COMPLETED\"}")); verify(feedback).updateFeedbackService(eq("actor"), argThat(body -> body.id().equals("f") && body.echo().equals("处理结果")));
        ok(delete("/feedback/delete").requestAttr("uuid", "actor").param("feedback_id", "f")); verify(feedback).deleteFeedbackService("actor", "f");
    }
    private MockMultipartFile file(String name) { return new MockMultipartFile(name, "a.png", "image/png", new byte[]{1}); }
    private void ok(RequestBuilder request) throws Exception { mvc.perform(request).andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0)); }
}
