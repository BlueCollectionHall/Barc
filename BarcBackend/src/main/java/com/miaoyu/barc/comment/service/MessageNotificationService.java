package com.miaoyu.barc.comment.service;

import com.miaoyu.barc.comment.model.MessageBoardModel;
import com.miaoyu.barc.email.utils.SendEmailUtils;
import com.miaoyu.barc.user.mapper.UserBasicMapper;
import com.miaoyu.barc.user.model.UserBasicModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class MessageNotificationService {

    @Autowired
    private UserBasicMapper userBasicMapper;
    @Autowired
    private SendEmailUtils sendEmailUtils;

    @Async
    public void notifyMessageDeleted(MessageBoardModel messageBoard, String adminUuid) {
        UserBasicModel authorBasic = userBasicMapper.selectByUuid(messageBoard.getAuthor());
        if (authorBasic == null || authorBasic.getEmail() == null) {
            return;
        }
        UserBasicModel adminBasic = userBasicMapper.selectByUuid(adminUuid);
        String adminName = adminBasic != null ? adminBasic.getUsername() : adminUuid;
        sendEmailUtils.messageDeletedByAdminEmail(
            authorBasic.getEmail(),
            messageBoard.getContent(),
            "违反社区规定",
            adminName
        );
    }

    @Async
    public void notifyMessageEdited(MessageBoardModel messageBoard, String newContent, String reason, String adminUuid) {
        UserBasicModel authorBasic = userBasicMapper.selectByUuid(messageBoard.getAuthor());
        if (authorBasic == null || authorBasic.getEmail() == null) {
            return;
        }
        UserBasicModel adminBasic = userBasicMapper.selectByUuid(adminUuid);
        String adminName = adminBasic != null ? adminBasic.getUsername() : adminUuid;
        String useReason = (reason != null && !reason.isBlank()) ? reason : "内容违规修改";
        sendEmailUtils.messageEditedByAdminEmail(
            authorBasic.getEmail(),
            messageBoard.getContent(),
            newContent,
            useReason,
            adminName
        );
    }
}
