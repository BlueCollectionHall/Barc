package com.miaoyu.barc.comment.service;

import com.miaoyu.barc.annotation.RequireSelfOrPermissionAnno;
import com.miaoyu.barc.comment.service.MessageNotificationService;
import com.miaoyu.barc.response.SuccessR;
import com.miaoyu.barc.user.enumeration.UserIdentityEnum;
import com.miaoyu.barc.user.mapper.UserBasicMapper;
import com.miaoyu.barc.user.model.UserBasicModel;
import com.miaoyu.barc.user.model.UserArchiveModel;
import com.miaoyu.barc.utils.dto.PageRequestDto;
import com.miaoyu.barc.utils.dto.PageResultDto;
import com.miaoyu.barc.utils.pojo.PageInitPojo;
import java.util.List;
import com.miaoyu.barc.annotation.RequireUserAndPermissionAnno;
import com.miaoyu.barc.comment.mapper.MessageBoardMapper;
import com.miaoyu.barc.comment.model.MessageBoardAdminVo;
import com.miaoyu.barc.comment.model.MessageBoardModel;
import com.miaoyu.barc.permission.PermissionConst;
import com.miaoyu.barc.permission.PermissionPolicy;
import com.miaoyu.barc.utils.RequestInput;
import com.miaoyu.barc.response.ChangeR;
import com.miaoyu.barc.response.ResourceR;
import com.miaoyu.barc.user.mapper.UserArchiveMapper;
import com.miaoyu.barc.utils.GenerateUUID;
import com.miaoyu.barc.utils.J;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.util.Objects;

@Service
public class MessageBoardService {
    @Autowired
    private MessageBoardMapper messageBoardMapper;
    @Autowired
    private UserArchiveMapper userArchiveMapper;
    @Autowired
    private UserBasicMapper userBasicMapper;
    @Autowired
    private MessageNotificationService notificationService;

    /** 最新若干条保留数组合同，容量固定上限 100。 */
    public ResponseEntity<J> getNewBoardMessagesService(Integer limit) {
        var init = new PageInitPojo(1, limit);
        return ResponseEntity.ok(new ResourceR().resourceSuch(true, messageBoardMapper.selectByLimit(init.getPageSize())));
    }

    /** 公开留言页，用稳定顺序读取历史并返回真实总数。 */
    public ResponseEntity<J> getPage(Integer page, Integer size) {
        var init = new PageInitPojo(page, size);
        long total = messageBoardMapper.countAll();
        return ResponseEntity.ok(new SuccessR().normal(new PageResultDto<>(total,
                messageBoardMapper.selectPage(init.getOffset(), init.getPageSize()), init.getPageNum(), init.getPageSize(),
                Math.max(1, (int) Math.ceil((double) total / init.getPageSize())))));
    }

    @RequireUserAndPermissionAnno({@RequireUserAndPermissionAnno.Check()})
    public ResponseEntity<J> uploadBoardMessageService(String uuid, String content) {
        MessageBoardModel messageBoard = new MessageBoardModel();
        messageBoard.setContent(RequestInput.required(content, "留言", 2000));
        messageBoard.setAuthor(uuid);
        messageBoard.setId(new GenerateUUID().getUuid36l());
        boolean insert = messageBoardMapper.insert(messageBoard);
        if (insert) {
            return ResponseEntity.ok(new ChangeR().udu(true, 1));
        }
        return ResponseEntity.ok(new ChangeR().udu(false, 1));
    }

    @org.springframework.transaction.annotation.Transactional
    public ResponseEntity<J> deleteBoardMessageService(String uuid, String messageId) {
        MessageBoardModel messageBoard = messageBoardMapper.selectByIdForUpdate(messageId);
        if (messageBoard == null) {
            return ResponseEntity.ok(new ChangeR().udu(false, 1));
        }
        UserArchiveModel userArchive = userArchiveMapper.selectByUuid(uuid);
        boolean isAuthor = messageBoard.getAuthor().equals(uuid);
        boolean isAdmin = PermissionPolicy.canModerateDiscussion(userArchive);
        if (!isAuthor && !isAdmin) {
            return ResponseEntity.ok(new ChangeR().udu(false, 1));
        }
        boolean deleted = messageBoardMapper.delete(messageId);
        if (deleted) {
            // 管理员删除时发送邮件通知（非作者自己删除）
            if (isAdmin && !isAuthor) {
                notificationService.notifyMessageDeleted(messageBoard, uuid);
            }
            return ResponseEntity.ok(new ChangeR().udu(true, 1));
        }
        return ResponseEntity.ok(new ChangeR().udu(false, 1));
    }

    @RequireUserAndPermissionAnno({@RequireUserAndPermissionAnno.Check(
        isSuchElseRequire = true
    )})
    public ResponseEntity<J> adminQueryBoardMessagesService(String uuid, PageRequestDto dto) {
        requireModerator(uuid);
        if (dto == null) dto = new PageRequestDto();
        PageInitPojo init = new PageInitPojo(dto);
        @SuppressWarnings({"unchecked", "rawtypes"})
        List<MessageBoardAdminVo> models = (List) messageBoardMapper.selectAdminByPage(
            init.getOffset(), init.getPageSize(), dto.getParams()
        );
        Long total = messageBoardMapper.countAdminByPage(dto.getParams());
        int mathTotalPage = (int) Math.ceil((double) total / init.getPageSize());
        Integer totalPage = mathTotalPage == 0 ? 1 : mathTotalPage;
        return ResponseEntity.ok(
            new SuccessR().normal(
                new PageResultDto<>(total, models, init.getPageNum(), init.getPageSize(), totalPage)
            )
        );
    }

    @RequireUserAndPermissionAnno({@RequireUserAndPermissionAnno.Check(
        isSuchElseRequire = true
    )})
    @org.springframework.transaction.annotation.Transactional
    public ResponseEntity<J> adminUpdateBoardMessageService(String uuid, String messageId, String content, String reason) {
        requireModerator(uuid);
        messageId = RequestInput.required(messageId, "留言编号", 64);
        content = RequestInput.required(content, "留言", 2000);
        RequestInput.optional(reason, "处理原因", 2000);
        MessageBoardModel messageBoard = messageBoardMapper.selectByIdForUpdate(messageId);
        if (messageBoard == null) {
            return ResponseEntity.ok(new ChangeR().udu(false, 3));
        }
        boolean updated = messageBoardMapper.updateById(messageId, content);
        if (updated) {
            notificationService.notifyMessageEdited(messageBoard, content, reason, uuid);
            return ResponseEntity.ok(new ChangeR().udu(true, 3));
        }
        return ResponseEntity.ok(new ChangeR().udu(false, 3));
    }

    @RequireUserAndPermissionAnno({@RequireUserAndPermissionAnno.Check(
        isSuchElseRequire = true
    )})
    @org.springframework.transaction.annotation.Transactional
    public ResponseEntity<J> adminBatchDeleteBoardMessagesService(String uuid, List<String> messageIds, String reason) {
        requireModerator(uuid);
        RequestInput.optional(reason, "处理原因", 2000);
        if (messageIds == null || messageIds.isEmpty() || messageIds.size() > 100)
            throw RequestInput.invalid("请选择 1 至 100 条留言");
        var uniqueIds = new java.util.TreeSet<String>();
        for (String id : messageIds) if (!uniqueIds.add(RequestInput.required(id, "留言编号", 64)))
            throw RequestInput.invalid("留言编号不能重复");
        // 固定锁顺序避免相互交叉的批量请求死锁；所有目标存在后才写入。
        List<MessageBoardModel> messages = new java.util.ArrayList<>();
        for (String id : uniqueIds) {
            var model = messageBoardMapper.selectByIdForUpdate(id);
            if (model == null) throw RequestInput.invalid("留言已变化，请刷新");
            messages.add(model);
        }
        if (messageBoardMapper.batchDelete(new java.util.ArrayList<>(uniqueIds)) != uniqueIds.size())
            throw RequestInput.invalid("批量删除失败");
        for (var model : messages) if (!model.getAuthor().equals(uuid)) notificationService.notifyMessageDeleted(model, uuid);
        return ResponseEntity.ok(new ChangeR().udu(true, 2));
    }

    /** 身份与角色位同时校验，评论和留言治理保持一致。 */
    private void requireModerator(String uuid) {
        if (!PermissionPolicy.canModerateDiscussion(userArchiveMapper.selectByUuid(uuid)))
            throw new com.miaoyu.barc.exception.ApiException(new com.miaoyu.barc.response.UserR().insufficientAccountPermission());
    }
}
