package com.miaoyu.barc.comment.service;

import com.miaoyu.barc.annotation.RequireSelfOrPermissionAnno;
import com.miaoyu.barc.comment.service.MessageNotificationService;
import com.miaoyu.barc.email.utils.SendEmailUtils;
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
    private SendEmailUtils sendEmailUtils;
    @Autowired
    private MessageNotificationService notificationService;

    public ResponseEntity<J> getAllBoardMessagesService() {
        return ResponseEntity.ok(new ResourceR().resourceSuch(true, messageBoardMapper.selectAll()));
    }

    public ResponseEntity<J> getNewBoardMessagesService(Integer limit) {
        if (Objects.isNull(limit)) {
            return ResponseEntity.status(403).body(new ResourceR().resourceSuch(false, null));
        }
        return ResponseEntity.ok(new ResourceR().resourceSuch(true, messageBoardMapper.selectByLimit(limit)));
    }

    @RequireUserAndPermissionAnno({@RequireUserAndPermissionAnno.Check()})
    public ResponseEntity<J> uploadBoardMessageService(String uuid, String content) {
        MessageBoardModel messageBoard = new MessageBoardModel();
        messageBoard.setContent(content);
        messageBoard.setAuthor(uuid);
        messageBoard.setId(new GenerateUUID().getUuid36l());
        boolean insert = messageBoardMapper.insert(messageBoard);
        if (insert) {
            return ResponseEntity.ok(new ChangeR().udu(true, 1));
        }
        return ResponseEntity.ok(new ChangeR().udu(false, 1));
    }

    public ResponseEntity<J> deleteBoardMessageService(String uuid, String messageId) {
        MessageBoardModel messageBoard = messageBoardMapper.selectById(messageId);
        if (messageBoard == null) {
            return ResponseEntity.ok(new ChangeR().udu(false, 1));
        }
        UserArchiveModel userArchive = userArchiveMapper.selectByUuid(uuid);
        boolean isAuthor = messageBoard.getAuthor().equals(uuid);
        boolean isAdmin = userArchive != null
                && userArchive.getIdentity().equals(UserIdentityEnum.MANAGER)
                && (userArchive.getPermission() & PermissionConst.FIR_MAINTAINER) != 0;
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
        isSuchElseRequire = false,
        identity = UserIdentityEnum.MANAGER,
        targetPermission = PermissionConst.FIR_MAINTAINER,
        isHasElseUpper = true
    )})
    public ResponseEntity<J> adminQueryBoardMessagesService(String uuid, PageRequestDto dto) {
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
        isSuchElseRequire = false,
        identity = UserIdentityEnum.MANAGER,
        targetPermission = PermissionConst.FIR_MAINTAINER,
        isHasElseUpper = true
    )})
    public ResponseEntity<J> adminUpdateBoardMessageService(String uuid, String messageId, String content, String reason) {
        MessageBoardModel messageBoard = messageBoardMapper.selectById(messageId);
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
        isSuchElseRequire = false,
        identity = UserIdentityEnum.MANAGER,
        targetPermission = PermissionConst.FIR_MAINTAINER,
        isHasElseUpper = true
    )})
    public ResponseEntity<J> adminBatchDeleteBoardMessagesService(String uuid, List<String> messageIds, String reason) {
        // 先查出所有要被删除的留言（用于邮件通知）
        List<MessageBoardModel> messages = new java.util.ArrayList<>();
        for (String id : messageIds) {
            MessageBoardModel msg = messageBoardMapper.selectById(id);
            if (msg != null) {
                messages.add(msg);
            }
        }
        boolean deleted = messageBoardMapper.batchDelete(messageIds);
        if (deleted) {
            for (MessageBoardModel msg : messages) {
                if (!msg.getAuthor().equals(uuid)) {
                    notificationService.notifyMessageDeleted(msg, uuid);
                }
            }
            return ResponseEntity.ok(new ChangeR().udu(true, 2));
        }
        return ResponseEntity.ok(new ChangeR().udu(false, 2));
    }
}
