package com.miaoyu.barc.comment.controller;

import com.miaoyu.barc.annotation.IgnoreAuth;
import com.miaoyu.barc.comment.service.MessageBoardService;
import com.miaoyu.barc.comment.model.MessageCommands;
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

    // 说明（2026-10 清理）：原 @IgnoreAuth 的 GET /comment/message_board/all 已删除。
    // 它与下面的 /new 同表、同投影（MessageBoardModel 全字段），却无排序也无条数上限；
    // /new 有容量上限；完整历史通过 /page 翻页读取。
    @IgnoreAuth
    @GetMapping("/new")
    public ResponseEntity<J> getNewBoardMessagesControl(@RequestParam("limit") Integer limit) {
        return messageBoardService.getNewBoardMessagesService(limit);
    }

    @PostMapping("/upload")
    public ResponseEntity<J> uploadBoardMessageControl(
            HttpServletRequest request,
            @RequestBody MessageCommands.Create body
    ) {
        return messageBoardService.uploadBoardMessageService(request.getAttribute("uuid").toString(), body.content());
    }

    /** 完整留言历史分页读取，最新若干条入口仍兼容。 */
    @IgnoreAuth
    @GetMapping("/page")
    public ResponseEntity<J> getPage(@RequestParam(defaultValue = "1") Integer page, @RequestParam(defaultValue = "20") Integer size) {
        return messageBoardService.getPage(page, size);
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
            @RequestBody MessageCommands.Update body
    ) {
        return messageBoardService.adminUpdateBoardMessageService(
            request.getAttribute("uuid").toString(), body.message_id(), body.content(), body.reason()
        );
    }

    /**
     * 管理员批量删除留言
     */
    @DeleteMapping("/admin/batch_delete")
    public ResponseEntity<J> adminBatchDeleteControl(
            HttpServletRequest request,
            @RequestBody MessageCommands.BatchDelete body
    ) {
        return messageBoardService.adminBatchDeleteBoardMessagesService(
            request.getAttribute("uuid").toString(), body.message_ids(), body.reason()
        );
    }
}
