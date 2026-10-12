package com.miaoyu.barc.comment.controller;

import com.miaoyu.barc.annotation.IgnoreAuth;
import com.miaoyu.barc.comment.model.WorkCommentModel;
import com.miaoyu.barc.comment.model.WorkCommentReplyModel;
import com.miaoyu.barc.comment.service.WorkCommentService;
import com.miaoyu.barc.utils.J;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * 作品评论区
 * */
@RestController
@RequestMapping("/comment/work")
public class WorkCommentController {
    @Autowired
    private WorkCommentService workCommentService;

    @IgnoreAuth
    @GetMapping("/comments_by_work")
    public ResponseEntity<J> getCommentsByWorkControl(
            @RequestParam("work_id") String workId
    ) {
        return workCommentService.getCommentsByWorkService(workId);
    }

    @IgnoreAuth
    @GetMapping("/count_by_work")
    public ResponseEntity<J> getCommentCountByWorkControl(
            @RequestParam("work_id") String workId
    ) {
        return workCommentService.getCommentCountByWorkService(workId);
    }

    @IgnoreAuth
    @GetMapping("/comment_by_id")
    public ResponseEntity<J> getCommentByIdControl(
            @RequestParam("comment_id") String commentId
    ) {
        return workCommentService.getCommentDetail(commentId, null, false);
    }

    /** 举报处理读取独立取证入口，不能借公开详情查看隐藏作品。 */
    @GetMapping("/manage/detail")
    public ResponseEntity<J> getManagementDetail(HttpServletRequest request, @RequestParam("comment_id") String id) {
        return workCommentService.getCommentDetail(id, request.getAttribute("uuid").toString(), true);
    }

    /** 作品管理面板使用受控分页，避免公开门禁阻断审核取证或截断大量评论。 */
    @GetMapping("/manage/page")
    public ResponseEntity<J> getManagementPage(HttpServletRequest request, @RequestParam("work_id") String id,
            @RequestParam(defaultValue = "1") Integer page, @RequestParam(defaultValue = "10") Integer size) {
        return workCommentService.getManagementPage(request.getAttribute("uuid").toString(), id, page, size);
    }

    @GetMapping("/manage/replies/page")
    public ResponseEntity<J> getManagementReplies(HttpServletRequest request, @RequestParam("parent_id") String id,
            @RequestParam(defaultValue = "1") Integer page, @RequestParam(defaultValue = "5") Integer size) {
        return workCommentService.getManagementReplies(request.getAttribute("uuid").toString(), id, page, size);
    }

    /** 主评论与回复采用真正的服务端分页，保留原数组接口兼容旧客户端。 */
    @IgnoreAuth
    @GetMapping("/page")
    public ResponseEntity<J> getPage(@RequestParam("work_id") String id,
            @RequestParam(defaultValue = "1") Integer page, @RequestParam(defaultValue = "10") Integer size) {
        return workCommentService.getCommentsPage(id, page, size);
    }

    @IgnoreAuth
    @GetMapping("/replies/page")
    public ResponseEntity<J> getReplies(@RequestParam("parent_id") String id,
            @RequestParam(defaultValue = "1") Integer page, @RequestParam(defaultValue = "5") Integer size) {
        return workCommentService.getRepliesPage(id, page, size);
    }

    @PostMapping("/upload_comment")
    public ResponseEntity<J> uploadCommentControl(
            HttpServletRequest request,
            @RequestBody WorkCommentModel requestModel
            ) {
        return workCommentService.uploadCommentAndReplyService("comment", request.getAttribute("uuid").toString(), requestModel, null);
    }

    @PostMapping("/upload_reply")
    public ResponseEntity<J> uploadReplyControl(
            HttpServletRequest request,
            @RequestBody WorkCommentReplyModel requestModel
            ) {
        return workCommentService.uploadCommentAndReplyService("reply", request.getAttribute("uuid").toString(), null, requestModel);
    }

    @DeleteMapping("/delete_comment")
    public ResponseEntity<J> deleteCommentControl(
            HttpServletRequest request,
            @RequestParam("comment_id") String commentId
    ) {
        return workCommentService.deleteCommentService(
                request.getAttribute("uuid").toString(),
                null,
                commentId
        );
    }

    @DeleteMapping("/delete_reply")
    public ResponseEntity<J> deleteReplyControl(
            HttpServletRequest request,
            @RequestParam("reply_id") String replyId
    ) {
        return workCommentService.deleteReplyService(
                request.getAttribute("uuid").toString(),
                null,
                replyId
        );
    }
}
