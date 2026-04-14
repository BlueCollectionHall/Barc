package com.miaoyu.barc.feedback.service;

import com.miaoyu.barc.annotation.RequireUserAndPermissionAnno;
import com.miaoyu.barc.api.work.mapper.WorkMapper;
import com.miaoyu.barc.api.work.model.WorkModel;
import com.miaoyu.barc.email.utils.SendEmailUtils;
import com.miaoyu.barc.feedback.enumeration.FeedbackStatusEnum;
import com.miaoyu.barc.feedback.enumeration.FeedbackTypeEnum;
import com.miaoyu.barc.feedback.mapper.FeedbackMapper;
import com.miaoyu.barc.feedback.model.FeedbackFormModel;
import com.miaoyu.barc.permission.ComparePermission;
import com.miaoyu.barc.permission.PermissionConst;
import com.miaoyu.barc.response.*;
import com.miaoyu.barc.user.enumeration.UserIdentityEnum;
import com.miaoyu.barc.user.mapper.UserArchiveMapper;
import com.miaoyu.barc.user.mapper.UserBasicMapper;
import com.miaoyu.barc.user.model.UserArchiveModel;
import com.miaoyu.barc.user.model.UserBasicModel;
import com.miaoyu.barc.utils.GenerateUUID;
import com.miaoyu.barc.utils.J;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

@Service
public class FeedbackService {
    private static final Logger log = LoggerFactory.getLogger(FeedbackService.class);
    @Autowired
    private UserBasicMapper userBasicMapper;
    @Autowired
    private UserArchiveMapper userArchiveMapper;
    @Autowired
    private WorkMapper workMapper;
    @Autowired
    private SendEmailUtils sendEmailUtils;
    @Autowired
    private FeedbackMapper feedbackMapper;
    @Autowired
    private ComparePermission comparePermission;

    @RequireUserAndPermissionAnno({@RequireUserAndPermissionAnno.Check(isSuchElseRequire = false, identity = UserIdentityEnum.MANAGER)})
    public ResponseEntity<J> getFeedbacksByTypeWithManagerService(String managerUuid, FeedbackTypeEnum typeEnum) {
        return ResponseEntity.ok(new ResourceR().resourceSuch(true, feedbackMapper.selectByType(typeEnum)));
    }
    @RequireUserAndPermissionAnno({@RequireUserAndPermissionAnno.Check()})
    public ResponseEntity<J> getFeedbacksByTypeWithMeService(String uuid, FeedbackTypeEnum typeEnum) {
        return ResponseEntity.ok(new ResourceR().resourceSuch(true, feedbackMapper.selectByTypeAndAuthor(typeEnum, uuid)));
    }

    @RequireUserAndPermissionAnno({@RequireUserAndPermissionAnno.Check(isSuchElseRequire = false, identity = UserIdentityEnum.MANAGER)})
    public ResponseEntity<J> getFeedbackOnlyWithManagerService(String managerUuid, String feedbackId) {
        FeedbackFormModel feedback = feedbackMapper.selectById(feedbackId);
        if (feedback == null) {
            return ResponseEntity.ok(new ResourceR().resourceSuch(false, null));
        }
        if (feedback.getStatus() == FeedbackStatusEnum.PENDING) {
            feedback.setStatus(FeedbackStatusEnum.PROCESSING);
            feedbackMapper.update(feedback);
        }
        return ResponseEntity.ok(new ResourceR().resourceSuch(true, feedback));
    }

    @RequireUserAndPermissionAnno({@RequireUserAndPermissionAnno.Check()})
    public ResponseEntity<J> getFeedbackOnlyWithMeService(String uuid, String feedbackId) {
        FeedbackFormModel feedback = feedbackMapper.selectById(feedbackId);
        if (feedback == null) {
            return ResponseEntity.ok(new ResourceR().resourceSuch(false, null));
        }
        if (feedback.getAuthor().equals(uuid)) {
            return ResponseEntity.ok(new ResourceR().resourceSuch(true, feedback));
        }
        return ResponseEntity.ok(new UserR().insufficientAccountPermission());
    }

    public ResponseEntity<J> uploadFeedbackService(FeedbackFormModel requestModel) {
        // UUID存在
        if (requestModel.getAuthor() != null) {
            UserBasicModel userBasic = userBasicMapper.selectByUuid(requestModel.getAuthor());
            // 用户不存在
            if (userBasic == null) {
                return ResponseEntity.ok(new UserR().noSuchUser());
            }
            requestModel.setEmail(userBasic.getEmail());
        }
        requestModel.setId(new GenerateUUID().getUuid36l());
        boolean insert = feedbackMapper.insert(requestModel);
        if (insert) {
            if (requestModel.getEmail() == null) {
                return ResponseEntity.ok(new SuccessR().normal("投诉成功，无邮件通知"));
            }
            boolean isSent;
            switch (requestModel.getType()) {
                case WORK: {
                    WorkModel work = workMapper.selectById(requestModel.getTarget_id());
                    if (work == null) {
                        return noResourceRejectFeedback(requestModel);
                    }
                    isSent = sendEmailUtils.customEmail(requestModel.getEmail(),
                            "投诉反馈已收到",
                            "您对B.A.R.C.中收录作品：《" + work.getTitle() + "》的投诉反馈文件，已经送达风纪委员会，处理结果会以电子邮箱的方式通知给您！您也可在[个人中心 -> 内容管理 -> 投诉反馈]中查看进度。");
                    break;
                } case USER: {
                    UserArchiveModel userArchive = userArchiveMapper.selectByUuid(requestModel.getTarget_id());
                    if (userArchive == null) {
                        return noResourceRejectFeedback(requestModel);
                    }
                    isSent = sendEmailUtils.customEmail(requestModel.getEmail(),
                            "投诉反馈已收到",
                            "您对B.A.R.C.中用户：【" + userArchive.getNickname() + "】的投诉反馈文件，已经送达风纪委员会，处理结果会以电子邮箱的方式通知给您！您也可在[个人中心 -> 内容管理 -> 投诉反馈]中查看进度。");
                    break;
                } case COMMENT, MESSAGE_BOARD: {
                    isSent = sendEmailUtils.customEmail(requestModel.getEmail(),
                            "投诉反馈已收到",
                            "您对B.A.R.C.中指定评论、留言、消息的投诉反馈文件，已经送达风纪委员会，处理结果会以电子邮箱的方式通知给您！您也可在[个人中心 -> 内容管理 -> 投诉反馈]中查看进度。");
                    break;
                } default: {
                    isSent = sendEmailUtils.customEmail(requestModel.getEmail(),
                            "投诉反馈已收到",
                            "您在B.A.R.C.中提交的反馈，已经送达工程部，处理结果会以电子邮件的方式通知您，您也可在[个人中心 -> 内容管理 -> 投诉反馈]中查看进度。");
                    break;
                }
            }
            if (isSent) {
                return ResponseEntity.ok(new ChangeR().udu(true, 1));
            }
            return ResponseEntity.ok(new SuccessR().normal("投诉成功，但邮件未发送！"));
        } else {
            return ResponseEntity.ok(new ChangeR().udu(false, 1));
        }
    }

    @RequireUserAndPermissionAnno({
            @RequireUserAndPermissionAnno.Check(isSuchElseRequire = false, identity = UserIdentityEnum.MANAGER)
    })
    public ResponseEntity<J> updateFeedbackService(String managerUuid, FeedbackFormModel requestModel) {
        UserArchiveModel managerArchive = userArchiveMapper.selectByUuid(managerUuid);
        return switch (requestModel.getType()) {
            case COMMENT, MESSAGE_BOARD -> {
                if (comparePermission.has(managerArchive.getPermission(), PermissionConst.FIR_MAINTAINER) || comparePermission.compare(managerArchive.getPermission(), PermissionConst.ADMINISTRATOR)) yield updateFeedback(requestModel);
                yield ResponseEntity.ok(new UserR().insufficientAccountPermission());
            }
            case WORK -> {
                if (comparePermission.has(managerArchive.getPermission(), PermissionConst.SEC_MAINTAINER) || comparePermission.compare(managerArchive.getPermission(), PermissionConst.ADMINISTRATOR)) {
                    yield updateFeedback(requestModel);
                }
                yield ResponseEntity.ok(new UserR().insufficientAccountPermission());
            }
            case USER -> {
                if (comparePermission.has(managerArchive.getPermission(), PermissionConst.THI_MAINTAINER) || comparePermission.compare(managerArchive.getPermission(), PermissionConst.ADMINISTRATOR)) yield updateFeedback(requestModel);
                yield ResponseEntity.ok(new UserR().insufficientAccountPermission());
            }
            case BUG, SUGGESTION, OTHER -> {
                if (comparePermission.has(managerArchive.getPermission(), PermissionConst.ADMINISTRATOR) || comparePermission.compare(managerArchive.getPermission(), PermissionConst.ADMINISTRATOR)) yield updateFeedback(requestModel);
                yield ResponseEntity.ok(new UserR().insufficientAccountPermission());
            }
        };
    }
    @RequireUserAndPermissionAnno({@RequireUserAndPermissionAnno.Check(isSuchElseRequire = false, identity = UserIdentityEnum.MANAGER, targetPermission = PermissionConst.ADMINISTRATOR)})
    public ResponseEntity<J> deleteFeedbackService(String managerUuid, String feedbackId) {
        boolean delete = feedbackMapper.delete(feedbackId);
        if (delete) {
            return ResponseEntity.ok(new ChangeR().udu(true, 1));
        }
        return ResponseEntity.ok(new ChangeR().udu(false, 1));
    }


    private ResponseEntity<J> updateFeedback(FeedbackFormModel model) {
        boolean update = feedbackMapper.update(model);
        if (update) {
            if (model.getEmail() == null) {
                return ResponseEntity.ok(new ChangeR().udu(true, 3));
            }
            boolean isSent = sendEmailUtils.customEmail(model.getEmail(),
                    "投诉处理完成",
                    "您发起的投诉反馈，编号：" + model.getId() + "，已完成处理，处理结果：" + model.getStatus().getName() + "，管理员信息：" + model.getEcho());
            if (isSent) {
                return ResponseEntity.ok(new ChangeR().udu(true, 1));
            }
            return ResponseEntity.ok(new SuccessR().normal("修改成功，但邮件未发送！"));
        }
        return ResponseEntity.ok(new ChangeR().udu(false, 3));
    }

    private ResponseEntity<J> noResourceRejectFeedback(FeedbackFormModel model) {
        model.setStatus(FeedbackStatusEnum.REJECTED);
        model.setEcho("内容已不存在，系统自动驳回并关闭该反馈单。");
        boolean update = feedbackMapper.update(model);
        if (update) {
            return ResponseEntity.ok(new ErrorR().normal("内容已不存在，系统自动驳回投诉"));
        }
        return ResponseEntity.ok(new ErrorR().normal("内容已不存在，不必再投诉"));
    }
}
