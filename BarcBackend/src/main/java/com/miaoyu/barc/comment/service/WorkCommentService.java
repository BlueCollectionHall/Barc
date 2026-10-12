package com.miaoyu.barc.comment.service;

import com.miaoyu.barc.api.work.enumeration.WorkReviewStatusEnum;
import com.miaoyu.barc.api.work.enumeration.WorkStatusEnum;
import com.miaoyu.barc.api.work.mapper.WorkMapper;
import com.miaoyu.barc.api.work.model.WorkModel;
import com.miaoyu.barc.comment.mapper.WorkCommentMapper;
import com.miaoyu.barc.comment.mapper.WorkCommentReplyMapper;
import com.miaoyu.barc.comment.model.WorkCommentModel;
import com.miaoyu.barc.comment.model.WorkCommentReplyModel;
import com.miaoyu.barc.comment.pojo.WorkCommentPojo;
import com.miaoyu.barc.permission.PermissionPolicy;
import com.miaoyu.barc.permission.PermissionConst;
import com.miaoyu.barc.user.enumeration.UserIdentityEnum;
import com.miaoyu.barc.response.ChangeR;
import com.miaoyu.barc.response.ResourceR;
import com.miaoyu.barc.response.UserR;
import com.miaoyu.barc.user.mapper.UserArchiveMapper;
import com.miaoyu.barc.utils.J;
import com.miaoyu.barc.utils.RequestInput;
import com.miaoyu.barc.utils.dto.PageResultDto;
import com.miaoyu.barc.utils.pojo.PageInitPojo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import java.util.stream.Collectors;

/** 评论的公开可见性、分页、发布与治理共用同一服务边界。 */
@Service
public class WorkCommentService {
    @Autowired private WorkCommentMapper workCommentMapper;
    @Autowired private WorkCommentReplyMapper workCommentReplyMapper;
    @Autowired private UserArchiveMapper userArchiveMapper;
    @Autowired private WorkMapper workMapper;

    /** 旧数组入口保留形状，容量固定；完整数据通过分页读取。 */
    public ResponseEntity<J> getCommentsByWorkService(String workId) {
        requirePublic(workId, false);
        return found(loadPage(workId, new PageInitPojo(1, 100)));
    }

    /** 主评论页附带回复预览和真实回复数。 */
    public ResponseEntity<J> getCommentsPage(String workId, Integer page, Integer size) {
        requirePublic(workId, false);
        PageInitPojo init = new PageInitPojo(page, size);
        return found(page(workCommentMapper.countParents(workId), loadPage(workId, init), init));
    }

    /** 回复页复验父评论和作品可见性，不能通过已知评论 ID 绕过公开门禁。 */
    public ResponseEntity<J> getRepliesPage(String parentId, Integer page, Integer size) {
        WorkCommentModel parent = requireParent(parentId, false);
        requirePublic(parent.getWork_id(), false);
        PageInitPojo init = new PageInitPojo(page, size);
        return found(page(workCommentReplyMapper.countByParentId(parentId),
                workCommentReplyMapper.selectPage(parentId, init.getOffset(), init.getPageSize()), init));
    }

    /** 作品管理员或讨论治理人员可分页检查隐藏作品；该权限不授予删除能力。 */
    public ResponseEntity<J> getManagementPage(String operator, String workId, Integer page, Integer size) {
        if (!canInspect(operator)) return denied();
        if (workMapper.selectById(workId) == null) return missing();
        PageInitPojo init = new PageInitPojo(page, size);
        return found(page(workCommentMapper.countParents(workId), loadPage(workId, init), init));
    }

    /** 管理回复分页独立核验身份，公开回复入口继续要求作品公开且审核通过。 */
    public ResponseEntity<J> getManagementReplies(String operator, String parentId, Integer page, Integer size) {
        if (!canInspect(operator)) return denied();
        requireParent(parentId, false);
        PageInitPojo init = new PageInitPojo(page, size);
        return found(page(workCommentReplyMapper.countByParentId(parentId),
                workCommentReplyMapper.selectPage(parentId, init.getOffset(), init.getPageSize()), init));
    }

    private boolean canInspect(String operator) {
        var account = userArchiveMapper.selectByUuid(operator);
        return PermissionPolicy.canModerateDiscussion(account)
                || PermissionPolicy.matches(account, UserIdentityEnum.MANAGER, PermissionConst.SEC_MAINTAINER, true);
    }

    /** 仅用 SQL 聚合获取主评论与回复总数。 */
    public ResponseEntity<J> getCommentCountByWorkService(String workId) {
        requirePublic(workId, false);
        return found(workCommentMapper.countWithReplies(workId));
    }

    /** 公开详情与管理取证复用投影，管理模式必须由真实操作者权限放行。 */
    public ResponseEntity<J> getCommentDetail(String commentId, String operator, boolean management) {
        if (management && !PermissionPolicy.canModerateDiscussion(userArchiveMapper.selectByUuid(operator))) return denied();
        WorkCommentModel comment = workCommentMapper.selectById(commentId);
        WorkCommentReplyModel reply = comment == null ? workCommentReplyMapper.selectById(commentId) : null;
        if (comment == null && reply == null) return missing();
        WorkCommentModel parent = comment == null ? workCommentMapper.selectById(reply.getParent_id()) : comment;
        if (parent == null) return missing();
        if (!management) requirePublic(parent.getWork_id(), false);
        Map<String, Object> result = new HashMap<>();
        result.put("id", commentId); result.put("work_id", parent.getWork_id());
        result.put("content", comment == null ? reply.getContent() : comment.getContent());
        result.put("created_at", comment == null ? reply.getCreated_at() : comment.getCreated_at());
        result.put("type", comment == null ? "reply" : "comment");
        if (reply != null) result.put("parent_id", reply.getParent_id());
        return found(result);
    }

    /** 写入只采纳允许字段；锁作品后再锁父评论，避免下架或删除时插入孤立回复。 */
    @Transactional
    public ResponseEntity<J> uploadCommentAndReplyService(String type, String uuid, WorkCommentModel input, WorkCommentReplyModel replyInput) {
        boolean inserted;
        if ("comment".equals(type)) {
            if (input == null) throw RequestInput.invalid("评论不能为空");
            String content = RequestInput.required(input.getContent(), "评论", 2000);
            String workId = RequestInput.required(input.getWork_id(), "作品编号", 64);
            requirePublic(workId, true);
            WorkCommentModel model = new WorkCommentModel();
            model.setId(UUID.randomUUID().toString()); model.setAuthor(uuid); model.setWork_id(workId); model.setContent(content);
            inserted = workCommentMapper.insert(model);
        } else if ("reply".equals(type)) {
            if (replyInput == null) throw RequestInput.invalid("回复不能为空");
            String content = RequestInput.required(replyInput.getContent(), "回复", 2000);
            String parentId = RequestInput.required(replyInput.getParent_id(), "父评论编号", 64);
            WorkCommentModel initial = requireParent(parentId, false);
            requirePublic(initial.getWork_id(), true);
            WorkCommentModel parent = requireParent(parentId, true);
            String target = RequestInput.optional(replyInput.getReply_user(), "回复用户", 64);
            if (target != null && !target.isBlank() && !target.equals(parent.getAuthor())
                    && workCommentReplyMapper.countByParentAndAuthor(parentId, target) == 0) throw RequestInput.invalid("回复对象不属于当前评论");
            WorkCommentReplyModel model = new WorkCommentReplyModel();
            model.setId(UUID.randomUUID().toString()); model.setAuthor(uuid); model.setParent_id(parentId);
            model.setReply_user(target == null || target.isBlank() ? null : target); model.setContent(content);
            inserted = workCommentReplyMapper.insert(model);
        } else throw RequestInput.invalid("评论类型无效");
        if (!inserted) throw RequestInput.invalid("评论保存失败");
        return ResponseEntity.ok(new ChangeR().udu(true, 1));
    }

    /** 删除只信任数据库作者，兼容形参 authorUuid 不再参与授权。 */
    @Transactional
    public ResponseEntity<J> deleteCommentService(String uuid, String authorUuid, String commentId) {
        WorkCommentModel comment = workCommentMapper.selectByIdForUpdate(commentId);
        if (comment == null) return missing();
        if (!canDelete(uuid, comment.getAuthor())) return denied();
        workCommentReplyMapper.deleteByParentId(commentId);
        if (!workCommentMapper.delete(commentId)) throw RequestInput.invalid("评论删除失败");
        return ResponseEntity.ok(new ChangeR().udu(true, 2));
    }

    /** 回复删除与主评论使用同一授权规则。 */
    @Transactional
    public ResponseEntity<J> deleteReplyService(String uuid, String authorUuid, String replyId) {
        WorkCommentReplyModel reply = workCommentReplyMapper.selectById(replyId);
        if (reply == null) return missing();
        if (!canDelete(uuid, reply.getAuthor())) return denied();
        return ResponseEntity.ok(new ChangeR().udu(workCommentReplyMapper.deleteById(replyId), 2));
    }

    private List<WorkCommentPojo> loadPage(String workId, PageInitPojo init) {
        List<WorkCommentPojo> comments = workCommentMapper.selectPage(workId, init.getOffset(), init.getPageSize());
        if (comments.isEmpty()) return comments;
        Map<String, List<WorkCommentReplyModel>> grouped = workCommentReplyMapper.selectPreview(comments.stream().map(WorkCommentModel::getId).toList())
                .stream().collect(Collectors.groupingBy(WorkCommentReplyModel::getParent_id));
        comments.forEach(c -> c.setReplies(grouped.getOrDefault(c.getId(), List.of())));
        return comments;
    }
    private WorkCommentModel requireParent(String id, boolean lock) {
        WorkCommentModel parent = lock ? workCommentMapper.selectByIdForUpdate(id) : workCommentMapper.selectById(id);
        if (parent == null) throw RequestInput.invalid("评论不存在");
        return parent;
    }
    private void requirePublic(String id, boolean lock) {
        WorkModel work = lock ? workMapper.selectByIdForUpdate(id) : workMapper.selectById(id);
        if (work == null || work.getStatus() != WorkStatusEnum.PUBLIC || work.getReview_status() != WorkReviewStatusEnum.APPROVED)
            throw RequestInput.invalid("作品不可查看或评论");
    }
    private boolean canDelete(String operator, String author) {
        return operator != null && (operator.equals(author) || PermissionPolicy.canModerateDiscussion(userArchiveMapper.selectByUuid(operator)));
    }
    private <T> PageResultDto<T> page(long total, List<T> list, PageInitPojo init) {
        return new PageResultDto<>(total, list, init.getPageNum(), init.getPageSize(), Math.max(1, (int) Math.ceil((double) total / init.getPageSize())));
    }
    private ResponseEntity<J> found(Object data) { return ResponseEntity.ok(new ResourceR().resourceSuch(true, data)); }
    private ResponseEntity<J> missing() { return ResponseEntity.ok(new ResourceR().resourceSuch(false, null)); }
    private ResponseEntity<J> denied() { return ResponseEntity.ok(new UserR().insufficientAccountPermission()); }
}
