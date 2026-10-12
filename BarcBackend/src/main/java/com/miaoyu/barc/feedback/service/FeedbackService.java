package com.miaoyu.barc.feedback.service;

import com.miaoyu.barc.annotation.RequireUserAndPermissionAnno;
import com.miaoyu.barc.api.work.mapper.WorkMapper;
import com.miaoyu.barc.comment.mapper.MessageBoardMapper;
import com.miaoyu.barc.comment.mapper.WorkCommentMapper;
import com.miaoyu.barc.email.service.MailOutbox;
import com.miaoyu.barc.exception.ApiException;
import com.miaoyu.barc.feedback.enumeration.*;
import com.miaoyu.barc.feedback.mapper.FeedbackMapper;
import com.miaoyu.barc.feedback.model.*;
import com.miaoyu.barc.permission.PermissionConst;
import com.miaoyu.barc.permission.PermissionPolicy;
import com.miaoyu.barc.response.*;
import com.miaoyu.barc.security.AccountInput;
import com.miaoyu.barc.user.enumeration.UserIdentityEnum;
import com.miaoyu.barc.user.mapper.UserArchiveMapper;
import com.miaoyu.barc.user.mapper.UserBasicMapper;
import com.miaoyu.barc.user.model.UserArchiveModel;
import com.miaoyu.barc.utils.GenerateUUID;
import com.miaoyu.barc.utils.J;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 统一反馈业务：提交使用可信身份，查看只读，处理以数据库状态条件原子推进。 */
@Service
public class FeedbackService {
    @Autowired private UserBasicMapper userBasicMapper;
    @Autowired private UserArchiveMapper userArchiveMapper;
    @Autowired private WorkMapper workMapper;
    @Autowired private WorkCommentMapper workCommentMapper;
    @Autowired private com.miaoyu.barc.comment.mapper.WorkCommentReplyMapper workCommentReplyMapper;
    @Autowired private MessageBoardMapper messageBoardMapper;
    @Autowired private FeedbackMapper feedbackMapper;
    @Autowired private MailOutbox mailOutbox;

    @RequireUserAndPermissionAnno({@RequireUserAndPermissionAnno.Check(isSuchElseRequire = false, identity = UserIdentityEnum.MANAGER)})
    public ResponseEntity<J> getFeedbacksByTypeWithManagerService(String uuid, FeedbackTypeEnum type) {
        if (!canManageFeedback(userArchiveMapper.selectByUuid(uuid), type)) return denied();
        return getLegacyList(uuid, type, true, 100);
    }

    @RequireUserAndPermissionAnno({@RequireUserAndPermissionAnno.Check()})
    public ResponseEntity<J> getFeedbacksByTypeWithMeService(String uuid, FeedbackTypeEnum type) {
        return getLegacyList(uuid, type, false, 100);
    }

    /** 旧数组入口保留响应形状并落实 count；不再用零或大数请求全表。 */
    public ResponseEntity<J> getLegacyList(String uuid, FeedbackTypeEnum type, boolean management, Integer count) {
        if (uuid == null || type == null) throw invalid("用户和反馈类型不能为空");
        if (management && !canManageFeedback(userArchiveMapper.selectByUuid(uuid), type)) return denied();
        var init = new com.miaoyu.barc.utils.pojo.PageInitPojo(1, count == null || count <= 0 ? 100 : count);
        return ResponseEntity.ok(new ResourceR().resourceSuch(true, feedbackMapper.selectPage(type, null, management ? null : uuid, 0, init.getPageSize())));
    }

    /** 可继续翻页的反馈查询；类型治理权限与单条处理相同。 */
    public ResponseEntity<J> getPage(String uuid, FeedbackTypeEnum type, FeedbackStatusEnum status, boolean management, Integer page, Integer size) {
        if (uuid == null || type == null) throw invalid("用户和反馈类型不能为空");
        if (management && !canManageFeedback(userArchiveMapper.selectByUuid(uuid), type)) return denied();
        var init = new com.miaoyu.barc.utils.pojo.PageInitPojo(page, size);
        String author = management ? null : uuid;
        long total = feedbackMapper.countPage(type, status, author);
        var result = new com.miaoyu.barc.utils.dto.PageResultDto<>(total,
                feedbackMapper.selectPage(type, status, author, init.getOffset(), init.getPageSize()),
                init.getPageNum(), init.getPageSize(), Math.max(1, (int) Math.ceil((double) total / init.getPageSize())));
        return ResponseEntity.ok(new ResourceR().resourceSuch(true, result));
    }

    @RequireUserAndPermissionAnno({@RequireUserAndPermissionAnno.Check(isSuchElseRequire = false, identity = UserIdentityEnum.MANAGER)})
    public ResponseEntity<J> getFeedbackOnlyWithManagerService(String uuid, String id) {
        FeedbackFormModel feedback = feedbackMapper.selectById(id);
        if (feedback == null) return missing();
        if (!canManageFeedback(userArchiveMapper.selectByUuid(uuid), feedback.getType())) return denied();
        // GET 仅查看；接单通过 update 的 PROCESSING 命令完成，不回写旧快照。
        return ResponseEntity.ok(new ResourceR().resourceSuch(true, feedback));
    }

    @RequireUserAndPermissionAnno({@RequireUserAndPermissionAnno.Check()})
    public ResponseEntity<J> getFeedbackOnlyWithMeService(String uuid, String id) {
        FeedbackFormModel feedback = feedbackMapper.selectById(id);
        if (feedback == null) return missing();
        if (!Objects.equals(uuid, feedback.getAuthor())) return denied();
        return ResponseEntity.ok(new ResourceR().resourceSuch(true, feedback));
    }

    /** 所有目标在入库前校验，无邮箱的匿名请求也不能跳过校验。 */
    @Transactional
    public ResponseEntity<J> uploadFeedbackService(String authenticatedUuid, String remoteAddress, FeedbackCreateRequest request) {
        if (request == null || request.type() == null || request.content() == null || request.content().isBlank() || request.content().length() > 5000) {
            throw invalid("反馈类型和内容必填，内容最长 5000 字");
        }
        String target = request.target_id();
        if (target != null && target.length() > 100) throw invalid("反馈目标无效");
        boolean exists = switch (request.type()) {
            case WORK -> target != null && workMapper.selectById(target) != null;
            case USER -> target != null && userArchiveMapper.selectByUuid(target) != null;
            // 评论举报同时接收主评论与子回复 ID，管理取证入口支持相同契约。
            case COMMENT -> target != null && (workCommentMapper.selectById(target) != null || workCommentReplyMapper.selectById(target) != null);
            case MESSAGE_BOARD -> target != null && messageBoardMapper.selectById(target) != null;
            default -> true;
        };
        if (!exists) return missing();
        FeedbackFormModel feedback = new FeedbackFormModel();
        feedback.setId(new GenerateUUID().getUuid36l());
        feedback.setType(request.type()); feedback.setTarget_id(target); feedback.setContent(request.content().trim());
        feedback.setStatus(FeedbackStatusEnum.PENDING); feedback.setEcho(null);
        if (authenticatedUuid != null) {
            var user = userBasicMapper.selectByUuid(authenticatedUuid);
            if (user == null) throw new ApiException(new UserR().noSuchUser());
            feedback.setAuthor(authenticatedUuid); feedback.setEmail(user.getEmail());
        } else if (request.email() != null && !request.email().isBlank()) {
            feedback.setEmail(AccountInput.email(request.email()));
        }
        // 不信任 X-Forwarded-For 等公网请求头；部署可通过容器可信代理配置提供真实连接地址。
        if (remoteAddress != null && remoteAddress.length() <= 45) {
            if (remoteAddress.contains(":")) feedback.setIpv6(remoteAddress); else feedback.setIpv4(remoteAddress);
        }
        if (!feedbackMapper.insert(feedback)) throw invalid("反馈提交失败");
        // 匿名联系邮箱未经验证，不发送自动确认信，避免借反馈向任意地址批量发信。
        if (authenticatedUuid != null) mailOutbox.enqueue("feedback-created:" + feedback.getId(), feedback.getEmail(),
                "投诉反馈已收到", "您的反馈已收到，编号：" + feedback.getId() + "。可在个人中心查看处理进度。");
        return ResponseEntity.ok(new ChangeR().udu(true, 1));
    }

    /** 同一处理结果重复提交幂等；终态不得回退，旧页面覆盖会得到明确冲突提示。 */
    @Transactional
    @RequireUserAndPermissionAnno({@RequireUserAndPermissionAnno.Check(isSuchElseRequire = false, identity = UserIdentityEnum.MANAGER)})
    public ResponseEntity<J> updateFeedbackService(String uuid, FeedbackUpdateRequest request) {
        if (request == null || request.id() == null || request.status() == null || (request.echo() != null && request.echo().length() > 5000)) throw invalid("处理参数无效");
        FeedbackFormModel stored = feedbackMapper.selectById(request.id());
        if (stored == null) return missing();
        if (!canManageFeedback(userArchiveMapper.selectByUuid(uuid), stored.getType())) return denied();
        if (stored.getStatus() == request.status() && Objects.equals(stored.getEcho(), request.echo())) return ResponseEntity.ok(new ChangeR().udu(true, 3));
        if (stored.getStatus() == FeedbackStatusEnum.COMPLETED || stored.getStatus() == FeedbackStatusEnum.REJECTED
                || request.status() == FeedbackStatusEnum.PENDING || stored.getStatus() == request.status()
                || (request.expected_status() != null && request.expected_status() != stored.getStatus())) throw invalid("反馈状态已变化，请刷新后重试");
        if (feedbackMapper.transition(stored.getId(), stored.getStatus(), request.status(), request.echo()) != 1) throw invalid("反馈已被其他管理员处理，请刷新后重试");
        if (request.status() == FeedbackStatusEnum.COMPLETED || request.status() == FeedbackStatusEnum.REJECTED) {
            mailOutbox.enqueue("feedback-result:" + stored.getId(), stored.getEmail(), "投诉处理完成",
                    "您的反馈 " + stored.getId() + " 已处理，结果：" + request.status().getName() + "。管理员回复：" + Objects.toString(request.echo(), "无"));
        }
        return ResponseEntity.ok(new ChangeR().udu(true, 3));
    }

    @RequireUserAndPermissionAnno({@RequireUserAndPermissionAnno.Check(isSuchElseRequire = false, identity = UserIdentityEnum.MANAGER, targetPermission = PermissionConst.ADMINISTRATOR)})
    public ResponseEntity<J> deleteFeedbackService(String uuid, String id) {
        return ResponseEntity.ok(new ChangeR().udu(feedbackMapper.delete(id), 1));
    }

    private boolean canManageFeedback(UserArchiveModel manager, FeedbackTypeEnum type) {
        if (type == null) return false;
        if (PermissionPolicy.matches(manager, UserIdentityEnum.MANAGER, PermissionConst.ADMINISTRATOR, false)) return true;
        int role = switch (type) {
            case COMMENT, MESSAGE_BOARD -> PermissionConst.FIR_MAINTAINER;
            case WORK -> PermissionConst.SEC_MAINTAINER;
            case USER -> PermissionConst.THI_MAINTAINER;
            default -> 0;
        };
        return PermissionPolicy.matches(manager, UserIdentityEnum.MANAGER, role, true);
    }

    private ResponseEntity<J> denied() { return ResponseEntity.ok(new UserR().insufficientAccountPermission()); }
    private ResponseEntity<J> missing() { return ResponseEntity.ok(new ResourceR().resourceSuch(false, null)); }
    private ApiException invalid(String message) { return new ApiException(new ErrorR().normal(message)); }
}
