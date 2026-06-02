package com.miaoyu.barc.comment.controller;

import com.miaoyu.barc.annotation.IgnoreAuth;
import com.miaoyu.barc.annotation.SuchWorkCommentAnno;
import com.miaoyu.barc.comment.mapper.WorkCommentMapper;
import com.miaoyu.barc.comment.mapper.WorkCommentReplyMapper;
import com.miaoyu.barc.comment.model.WorkCommentModel;
import com.miaoyu.barc.comment.model.WorkCommentReplyModel;
import com.miaoyu.barc.comment.service.WorkCommentService;
import com.miaoyu.barc.response.ResourceR;
import com.miaoyu.barc.utils.J;
import java.util.HashMap;
import java.util.Map;
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
    @Autowired
    private WorkCommentMapper workCommentMapper;
    @Autowired
    private WorkCommentReplyMapper workCommentReplyMapper;

    @IgnoreAuth
    @GetMapping("/comments_by_work")
    public ResponseEntity<J> getCommentsByWorkControl(
            @RequestParam("work_id") String workId
    ) {
        return workCommentService.getCommentsByWorkService(workId);
    }

    @IgnoreAuth
    @GetMapping("/comment_by_id")
    public ResponseEntity<J> getCommentByIdControl(
            @RequestParam("comment_id") String commentId
    ) {
        // 先查主评论表
        WorkCommentModel comment = workCommentMapper.selectById(commentId);
        if (comment != null) {
            Map<String, Object> result = new HashMap<>();
            result.put("id", comment.getId());
            result.put("content", comment.getContent());
            result.put("created_at", comment.getCreated_at());
            result.put("work_id", comment.getWork_id());
            result.put("type", "comment");
            return ResponseEntity.ok(new ResourceR().resourceSuch(true, result));
        }
        // 再查回复表
        WorkCommentReplyModel reply = workCommentReplyMapper.selectById(commentId);
        if (reply != null) {
            Map<String, Object> result = new HashMap<>();
            result.put("id", reply.getId());
            result.put("content", reply.getContent());
            result.put("created_at", reply.getCreated_at());
            result.put("parent_id", reply.getParent_id());
            result.put("type", "reply");
            return ResponseEntity.ok(new ResourceR().resourceSuch(true, result));
        }
        return ResponseEntity.ok(new ResourceR().resourceSuch(false, null));
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
    @SuchWorkCommentAnno(commentAndReply = "comment", selectType = "id", index = 1)
    public ResponseEntity<J> deleteCommentControl(
            HttpServletRequest request,
            @RequestParam("comment_id") String commentId
    ) {
        return workCommentService.deleteCommentService(
                request.getAttribute("uuid").toString(),
                workCommentMapper.selectById(commentId).getAuthor(),
                commentId
        );
    }

    @DeleteMapping("/delete_reply")
    @SuchWorkCommentAnno(commentAndReply = "reply", selectType = "id", index = 1)
    public ResponseEntity<J> deleteReplyControl(
            HttpServletRequest request,
            @RequestParam("reply_id") String replyId
    ) {
        return workCommentService.deleteReplyService(
                request.getAttribute("uuid").toString(),
                workCommentReplyMapper.selectById(replyId).getAuthor(),
                replyId
        );
    }
}
