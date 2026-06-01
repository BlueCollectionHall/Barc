package com.miaoyu.barc.comment.controller;

import com.miaoyu.barc.annotation.IgnoreAuth;
import com.miaoyu.barc.comment.service.MessageBoardService;
import com.miaoyu.barc.utils.J;
import com.miaoyu.barc.utils.dto.PageRequestDto;
import java.util.List;
import java.util.Map;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * 留言板
 * */
@RestController
@RequestMapping("/comment/message_board")
public class MessageBoardController {
    @Autowired
    private MessageBoardService messageBoardService;

    @IgnoreAuth
    @GetMapping("/all")
    public ResponseEntity<J> getAllBoardMessagesControl() {
        return messageBoardService.getAllBoardMessagesService();
    }

    @IgnoreAuth
    @GetMapping("/new")
    public ResponseEntity<J> getNewBoardMessagesControl(@RequestParam("limit") Integer limit) {
        return messageBoardService.getNewBoardMessagesService(limit);
    }

    @GetMapping("/upload")
    public ResponseEntity<J> uploadBoardMessageControl(
            HttpServletRequest request,
            @RequestParam("content") String content
    ) {
        return messageBoardService.uploadBoardMessageService(request.getAttribute("uuid").toString(), content);
    }

    @DeleteMapping("/delete")
    public ResponseEntity<J> deleteBoardMessageControl(
            HttpServletRequest request,
            @RequestParam("message_id") String messageId
    ) {
        return messageBoardService.deleteBoardMessageService(request.getAttribute("uuid").toString(), messageId);
    }

    /**
     * 管理员分页查询留言
     */
    @PostMapping("/admin/list")
    public ResponseEntity<J> adminListControl(
            HttpServletRequest request,
            @RequestBody PageRequestDto dto
    ) {
        return messageBoardService.adminQueryBoardMessagesService(
            request.getAttribute("uuid").toString(), dto
        );
    }

    /**
     * 管理员编辑留言
     */
    @PutMapping("/admin/update")
    public ResponseEntity<J> adminUpdateControl(
            HttpServletRequest request,
            @RequestBody Map<String, Object> body
    ) {
        String messageId = (String) body.get("message_id");
        String content = (String) body.get("content");
        String reason = (String) body.getOrDefault("reason", "");
        return messageBoardService.adminUpdateBoardMessageService(
            request.getAttribute("uuid").toString(), messageId, content, reason
        );
    }

    /**
     * 管理员批量删除留言
     */
    @DeleteMapping("/admin/batch_delete")
    public ResponseEntity<J> adminBatchDeleteControl(
            HttpServletRequest request,
            @RequestBody Map<String, Object> body
    ) {
        @SuppressWarnings("unchecked")
        List<String> messageIds = (List<String>) body.get("message_ids");
        String reason = (String) body.getOrDefault("reason", "");
        return messageBoardService.adminBatchDeleteBoardMessagesService(
            request.getAttribute("uuid").toString(), messageIds, reason
        );
    }
}
