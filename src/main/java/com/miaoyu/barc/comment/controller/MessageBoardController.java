package com.miaoyu.barc.comment.controller;

import com.miaoyu.barc.annotation.IgnoreAuth;
import com.miaoyu.barc.comment.service.MessageBoardService;
import com.miaoyu.barc.utils.J;
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
}
