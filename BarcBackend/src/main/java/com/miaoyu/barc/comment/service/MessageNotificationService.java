package com.miaoyu.barc.comment.service;

import com.miaoyu.barc.comment.model.MessageBoardModel;
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
    private com.miaoyu.barc.email.service.MailOutbox mailOutbox;

    public void notifyMessageDeleted(MessageBoardModel messageBoard, String adminUuid) {
        UserBasicModel authorBasic = userBasicMapper.selectByUuid(messageBoard.getAuthor());
        if (authorBasic == null || authorBasic.getEmail() == null) {
            return;
        }
        UserBasicModel adminBasic = userBasicMapper.selectByUuid(adminUuid);
        String adminName = adminBasic != null ? adminBasic.getUsername() : adminUuid;
        // 与管理员删除处于同一事务；业务回滚时不会留下通知任务。
        mailOutbox.enqueue("message-deleted:" + messageBoard.getId(), authorBasic.getEmail(), "留言已被管理员删除",
                "原留言：" + messageBoard.getContent() + "；操作人：" + adminName + "；原因：违反社区规定");
    }

    public void notifyMessageEdited(MessageBoardModel messageBoard, String newContent, String reason, String adminUuid) {
        UserBasicModel authorBasic = userBasicMapper.selectByUuid(messageBoard.getAuthor());
        if (authorBasic == null || authorBasic.getEmail() == null) {
            return;
        }
        UserBasicModel adminBasic = userBasicMapper.selectByUuid(adminUuid);
        String adminName = adminBasic != null ? adminBasic.getUsername() : adminUuid;
        String useReason = (reason != null && !reason.isBlank()) ? reason : "内容违规修改";
        mailOutbox.enqueue("message-edited:" + java.util.UUID.randomUUID(), authorBasic.getEmail(), "留言已被管理员修改",
                "原留言：" + messageBoard.getContent() + "；修改后：" + newContent + "；操作人：" + adminName + "；原因：" + useReason);
    }
}
