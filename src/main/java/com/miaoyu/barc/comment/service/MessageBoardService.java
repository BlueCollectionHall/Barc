package com.miaoyu.barc.comment.service;

import com.miaoyu.barc.annotation.RequireSelfOrPermissionAnno;
import com.miaoyu.barc.annotation.RequireUserAndPermissionAnno;
import com.miaoyu.barc.comment.mapper.MessageBoardMapper;
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

    @RequireSelfOrPermissionAnno(targetPermission = PermissionConst.FIR_MAINTAINER)
    public ResponseEntity<J> deleteBoardMessageService(String uuid, String messageId) {
        MessageBoardModel messageBoard = messageBoardMapper.selectById(messageId);
        if (messageBoard.getAuthor().equals(uuid) || PermissionConst.FIR_MAINTAINER <= userArchiveMapper.selectByUuid(uuid).getPermission()) {
            boolean delete = messageBoardMapper.delete(messageId);
            if (delete) {
                return ResponseEntity.ok(new ChangeR().udu(true, 1));
            }
            return ResponseEntity.ok(new ChangeR().udu(false, 1));
        }
        return ResponseEntity.ok(new ChangeR().udu(false, 1));
    }
}
