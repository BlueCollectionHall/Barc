package com.miaoyu.barc.comment.service;

import com.miaoyu.barc.annotation.RequireSelfOrPermissionAnno;
import com.miaoyu.barc.comment.mapper.WorkCommentMapper;
import com.miaoyu.barc.comment.mapper.WorkCommentReplyMapper;
import com.miaoyu.barc.comment.model.WorkCommentModel;
import com.miaoyu.barc.comment.model.WorkCommentReplyModel;
import com.miaoyu.barc.comment.pojo.WorkCommentPojo;
import com.miaoyu.barc.permission.PermissionConst;
import com.miaoyu.barc.response.ChangeR;
import com.miaoyu.barc.response.ResourceR;
import com.miaoyu.barc.user.enumeration.UserIdentityEnum;
import com.miaoyu.barc.user.mapper.UserArchiveMapper;
import com.miaoyu.barc.utils.GenerateUUID;
import com.miaoyu.barc.utils.J;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class WorkCommentService {
    @Autowired
    private WorkCommentMapper workCommentMapper;
    @Autowired
    private WorkCommentReplyMapper workCommentReplyMapper;
    @Autowired
    private UserArchiveMapper userArchiveMapper;

    public ResponseEntity<J> getCommentsByWorkService(String workId) {
        List<WorkCommentModel> commentModels = workCommentMapper.selectByWorkId(workId);
        List<WorkCommentPojo> commentPojos = new ArrayList<>();
        for (WorkCommentModel commentModel : commentModels) {
            List<WorkCommentReplyModel> commentReplies = workCommentReplyMapper.selectByParentId(commentModel.getId());
            WorkCommentPojo commentPojo = new WorkCommentPojo();
            BeanUtils.copyProperties(commentModel, commentPojo);
            commentPojo.setReplies(commentReplies);
            commentPojos.add(commentPojo);
        }
        return ResponseEntity.ok(new ResourceR().resourceSuch(true, commentPojos));
    }

    public ResponseEntity<J> uploadCommentAndReplyService(String type, String uuid, WorkCommentModel commentModel, WorkCommentReplyModel replyModel) {
        switch (type) {
            case "comment": {
                commentModel.setAuthor(uuid);
                commentModel.setId(new GenerateUUID().getUuid36l());
                boolean insert = workCommentMapper.insert(commentModel);
                if (insert) {
                    return ResponseEntity.ok(new ChangeR().udu(true, 1));
                }
                return ResponseEntity.ok(new ChangeR().udu(false, 1));
            } case "reply": {
                replyModel.setId(new GenerateUUID().getUuid36l());
                replyModel.setAuthor(uuid);
                boolean insert = workCommentReplyMapper.insert(replyModel);
                if (insert) {
                    return ResponseEntity.ok(new ChangeR().udu(true, 1));
                }
                return ResponseEntity.ok(new ChangeR().udu(false, 1));
            } default: {
                return ResponseEntity.ok(new ChangeR().udu(false, 1));
            }
        }
    }

    @RequireSelfOrPermissionAnno(isHasElseUpper = true, identity = UserIdentityEnum.MANAGER, targetPermission = PermissionConst.FIR_MAINTAINER)
    public ResponseEntity<J> deleteCommentService(String uuid, String authorUuid, String commentId) {
        return ResponseEntity.ok(new ChangeR().udu(workCommentMapper.delete(commentId), 2));
    }

    @RequireSelfOrPermissionAnno(isHasElseUpper = true, identity = UserIdentityEnum.MANAGER, targetPermission = PermissionConst.FIR_MAINTAINER)
    public ResponseEntity<J> deleteReplyService(String uuid, String authorUuid, String replyId) {
        WorkCommentReplyModel reply = workCommentReplyMapper.selectById(replyId);
        if (reply.getAuthor().equals(uuid) || PermissionConst.FIR_MAINTAINER <= userArchiveMapper.selectByUuid(uuid).getPermission()) {
            boolean delete = workCommentReplyMapper.deleteById(replyId);
            if (delete) {
                return ResponseEntity.ok(new ChangeR().udu(true, 2));
            }
            return ResponseEntity.ok(new ChangeR().udu(false, 2));
        }
        return ResponseEntity.ok(new ChangeR().udu(false, 2));
    }
}
