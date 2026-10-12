package com.miaoyu.barc.security;

import com.miaoyu.barc.comment.controller.*;
import com.miaoyu.barc.comment.service.*;
import java.util.List;
import org.junit.jupiter.api.*;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.*;
import static com.miaoyu.barc.security.HttpContractSupport.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** 讨论区分页、举报取证和写请求合同；旧留言 GET 写入必须失效。 */
class DiscussionHttpContractTest {
    WorkCommentService comments = apiMock(WorkCommentService.class);
    MessageBoardService messages = apiMock(MessageBoardService.class);
    MockMvc mvc;
    @BeforeEach void setup() { var work = new WorkCommentController(); var board = new MessageBoardController(); ReflectionTestUtils.setField(work, "workCommentService", comments); ReflectionTestUtils.setField(board, "messageBoardService", messages); mvc = mvc(work, board); }
    @Test void publicCommentReadsUsePublicGateAndDefaultPageSizes() throws Exception {
        ok(get("/comment/work/comments_by_work").param("work_id", "w")); verify(comments).getCommentsByWorkService("w");
        ok(get("/comment/work/count_by_work").param("work_id", "w")); verify(comments).getCommentCountByWorkService("w");
        ok(get("/comment/work/comment_by_id").param("comment_id", "c")); verify(comments).getCommentDetail("c", null, false);
        ok(get("/comment/work/page").param("work_id", "w")); verify(comments).getCommentsPage("w", 1, 10);
        ok(get("/comment/work/replies/page").param("parent_id", "c")); verify(comments).getRepliesPage("c", 1, 5);
    }
    @Test void managementReadsCarryAuthenticatedActorToIndependentGate() throws Exception {
        ok(get("/comment/work/manage/detail").requestAttr("uuid", "actor").param("comment_id", "c")); verify(comments).getCommentDetail("c", "actor", true);
        ok(get("/comment/work/manage/page").requestAttr("uuid", "actor").param("work_id", "w")); verify(comments).getManagementPage("actor", "w", 1, 10);
        ok(get("/comment/work/manage/replies/page").requestAttr("uuid", "actor").param("parent_id", "c").param("page", "2")); verify(comments).getManagementReplies("actor", "c", 2, 5);
    }
    @Test void writesPassActorSeparatelyAndDeletionDoesNotTrustClaimedAuthor() throws Exception {
        ok(post("/comment/work/upload_comment").requestAttr("uuid", "actor").contentType(MediaType.APPLICATION_JSON).content("{\"work_id\":\"w\",\"content\":\"评论\",\"author\":\"forged\"}")); verify(comments).uploadCommentAndReplyService(eq("comment"), eq("actor"), argThat(model -> model.getContent().equals("评论")), isNull());
        ok(post("/comment/work/upload_reply").requestAttr("uuid", "actor").contentType(MediaType.APPLICATION_JSON).content("{\"parent_id\":\"c\",\"content\":\"回复\"}")); verify(comments).uploadCommentAndReplyService(eq("reply"), eq("actor"), isNull(), argThat(model -> model.getParent_id().equals("c")));
        ok(delete("/comment/work/delete_comment").requestAttr("uuid", "actor").param("comment_id", "c").param("author_uuid", "forged")); verify(comments).deleteCommentService("actor", null, "c");
        ok(delete("/comment/work/delete_reply").requestAttr("uuid", "actor").param("reply_id", "r")); verify(comments).deleteReplyService("actor", null, "r");
    }
    @Test void boardReadsPaginateAndCreationUsesPostJsonOnly() throws Exception {
        ok(get("/comment/message_board/new").param("limit", "5")); verify(messages).getNewBoardMessagesService(5);
        ok(get("/comment/message_board/page")); verify(messages).getPage(1, 20);
        ok(post("/comment/message_board/upload").requestAttr("uuid", "actor").contentType(MediaType.APPLICATION_JSON).content("{\"content\":\" 留言 \",\"author\":\"forged\"}")); verify(messages).uploadBoardMessageService("actor", " 留言 ");
        mvc.perform(get("/comment/message_board/upload").requestAttr("uuid", "actor").param("content", "不应保存")).andExpect(status().isMethodNotAllowed());
        ok(delete("/comment/message_board/delete").requestAttr("uuid", "actor").param("message_id", "m")); verify(messages).deleteBoardMessageService("actor", "m");
    }
    @Test void boardManagementBindsReasonsAndBatchIdsWithoutReusingClientIdentity() throws Exception {
        ok(post("/comment/message_board/admin/list").requestAttr("uuid", "actor").contentType(MediaType.APPLICATION_JSON).content("{\"page_num\":2}")); verify(messages).adminQueryBoardMessagesService(eq("actor"), argThat(dto -> dto.getPage_num() == 2));
        ok(put("/comment/message_board/admin/update").requestAttr("uuid", "actor").contentType(MediaType.APPLICATION_JSON).content("{\"message_id\":\"m\",\"content\":\"新文\",\"reason\":\"原因\"}")); verify(messages).adminUpdateBoardMessageService("actor", "m", "新文", "原因");
        ok(delete("/comment/message_board/admin/batch_delete").requestAttr("uuid", "actor").contentType(MediaType.APPLICATION_JSON).content("{\"message_ids\":[\"a\",\"b\"],\"reason\":\"原因\"}")); verify(messages).adminBatchDeleteBoardMessagesService("actor", List.of("a", "b"), "原因");
    }
    private void ok(RequestBuilder request) throws Exception { mvc.perform(request).andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0)); }
}
