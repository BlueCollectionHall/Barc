package com.miaoyu.barc.api.work.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.miaoyu.barc.annotation.RequireUserAndPermissionAnno;
import com.miaoyu.barc.api.work.enumeration.WorkStatusEnum;
import com.miaoyu.barc.api.work.enumeration.WorkReviewStatusEnum;
import com.miaoyu.barc.api.work.mapper.WorkClaimMapper;
import com.miaoyu.barc.api.work.mapper.WorkMapper;
import com.miaoyu.barc.api.work.mapper.WorkOperationLogMapper;
import com.miaoyu.barc.api.work.mapper.WorkReviewMapper;
import com.miaoyu.barc.api.work.model.WorkClaimListDto;
import com.miaoyu.barc.api.work.model.WorkClaimModel;
import com.miaoyu.barc.api.work.model.WorkEditDetailDto;
import com.miaoyu.barc.api.work.model.WorkModel;
import com.miaoyu.barc.api.work.model.WorkSearchView;
import com.miaoyu.barc.api.work.model.WorkOperationLogModel;
import com.miaoyu.barc.permission.PermissionConst;
import com.miaoyu.barc.response.ChangeR;
import com.miaoyu.barc.response.ErrorR;
import com.miaoyu.barc.response.ResourceR;
import com.miaoyu.barc.response.UserR;
import com.miaoyu.barc.user.enumeration.OfficialAccountEnum;
import com.miaoyu.barc.user.enumeration.UserIdentityEnum;
import com.miaoyu.barc.user.mapper.UserArchiveMapper;
import com.miaoyu.barc.user.mapper.UserBasicMapper;
import com.miaoyu.barc.user.model.UserArchiveModel;
import com.miaoyu.barc.user.model.UserBasicModel;
import com.miaoyu.barc.user.service.OfficialAccountService;
import com.miaoyu.barc.utils.GenerateUUID;
import com.miaoyu.barc.utils.J;
import com.miaoyu.barc.utils.dto.PageRequestDto;
import com.miaoyu.barc.utils.dto.PageResultDto;
import com.miaoyu.barc.utils.pojo.PageInitPojo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;

import java.util.*;

/**
 * 管理端作品管理服务
 * 权限：所有方法使用 SEC_MAINTAINER(4) 位运算检查
 * 注意：每个方法签名中 String 第一个参数必须是操作者UUID（供AOP切面校验权限）
 */
@Slf4j
@Service
public class WorkManageService {
    // 等待业务行锁后读取最新提交数据，避免鉴权读在可重复读隔离下固定旧快照。
    @Autowired private com.miaoyu.barc.user.service.UserSearchService userSearchService;

    public static final String OP_BAN = "BAN";
    public static final String OP_OFF = "OFF";
    public static final String OP_DELETE = "DELETE";
    public static final String OP_RESTORE = "RESTORE";
    public static final String OP_EDIT = "EDIT";
    public static final String OP_CLAIM_APPROVE = "CLAIM_APPROVE";
    public static final String OP_CLAIM_REVOKE = "CLAIM_REVOKE";
    public static final String OP_CLAIM_ASSIGN = "CLAIM_ASSIGN";
    public static final String OP_REVIEW_APPROVE = "REVIEW_APPROVE";
    public static final String OP_REVIEW_REJECT = "REVIEW_REJECT";

    @Autowired private WorkMapper workMapper;
    @Autowired private WorkClaimMapper workClaimMapper;
    @Autowired private WorkOperationLogMapper workOperationLogMapper;
    @Autowired private WorkReviewMapper workReviewMapper;
    @Autowired private UserBasicMapper userBasicMapper;
    @Autowired private UserArchiveMapper userArchiveMapper;
    @Autowired private com.miaoyu.barc.email.service.MailOutbox mailOutbox;
    @Autowired private WorkService workService;
    @Autowired private OfficialAccountService officialAccountService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    // ==================== 作品列表与详情 ====================

    @RequireUserAndPermissionAnno({@RequireUserAndPermissionAnno.Check(identity = UserIdentityEnum.MANAGER, targetPermission = PermissionConst.SEC_MAINTAINER, isHasElseUpper = true, isSuchElseRequire = false)})
    public ResponseEntity<J> getWorkListForManage(String uuid, PageRequestDto dto) {
        // 与公开分页采用同一输入容量；管理身份不能绕过关键词长度限制。
        if (dto == null) dto = new PageRequestDto();
        PageInitPojo pageInit = new PageInitPojo(dto);
        int offset = pageInit.getOffset();
        int pageSize = pageInit.getPageSize();
        int pageNum = pageInit.getPageNum();
        Map<String, Object> params = dto.getParams() != null ? dto.getParams() : new HashMap<>();
        String status = params.get("status") != null ? params.get("status").toString() : null;
        String keyword = com.miaoyu.barc.utils.RequestInput.search(params.get("keyword"), "关键词", 100);

        List<WorkModel> works = workMapper.selectByPageForManage(status, keyword, offset, pageSize, dto.getSort_field(), dto.getSort_order());
        Long total = workMapper.countByPageForManage(status, keyword);

        // 批量签名封面图（复用 WorkService 的共享方法）
        if (!works.isEmpty()) workService.loopSignatureWorkCover(works);

        int totalPage = (int) Math.ceil((double) total / pageSize);
        return ResponseEntity.ok(new ResourceR().resourceSuch(true, new PageResultDto<>(total, works, pageNum, pageSize, totalPage == 0 ? 1 : totalPage)));
    }

    @RequireUserAndPermissionAnno({@RequireUserAndPermissionAnno.Check(identity = UserIdentityEnum.MANAGER, targetPermission = PermissionConst.SEC_MAINTAINER, isHasElseUpper = true, isSuchElseRequire = false)})
    public ResponseEntity<J> getWorkDetailForManage(String uuid, String workId) {
        WorkModel work = workMapper.selectById(workId);
        if (work == null) return ResponseEntity.ok(new ResourceR().resourceSuch(false, null));
        return ResponseEntity.ok(new ResourceR().resourceSuch(true, work));
    }

    // ==================== 上传审核 ====================

    /**
     * 获取上传审核队列。这里必须使用 SEC_MAINTAINER 权限位，不接受“数值更高”替代该位。
     */
    @RequireUserAndPermissionAnno({@RequireUserAndPermissionAnno.Check(
            identity = UserIdentityEnum.MANAGER,
            targetPermission = PermissionConst.SEC_MAINTAINER,
            isHasElseUpper = true,
            isSuchElseRequire = false)})
    public ResponseEntity<J> getReviewList(String uuid, WorkReviewStatusEnum reviewStatus, PageRequestDto dto) {
        if (dto == null) dto = new PageRequestDto();
        PageInitPojo pageInit = new PageInitPojo(dto);
        Map<String, Object> params = dto.getParams() != null ? dto.getParams() : new HashMap<>();
        String keyword = com.miaoyu.barc.utils.RequestInput.search(params.get("keyword"), "关键词", 100);
        WorkReviewStatusEnum safeStatus = reviewStatus == null ? WorkReviewStatusEnum.PENDING : reviewStatus;
        List<WorkModel> works = workMapper.selectByPageForReview(
                safeStatus, keyword, pageInit.getOffset(), pageInit.getPageSize());
        Long total = workMapper.countByPageForReview(safeStatus, keyword);
        if (!works.isEmpty()) workService.loopSignatureWorkCover(works);
        int totalPage = (int) Math.ceil((double) total / pageInit.getPageSize());
        return ResponseEntity.ok(new ResourceR().resourceSuch(true,
                new PageResultDto<>(total, works, pageInit.getPageNum(), pageInit.getPageSize(),
                        totalPage == 0 ? 1 : totalPage)));
    }

    /**
     * 静默记录审核结论：不调用邮件组件，仅更新审核记录并写入作品操作日志。
     */
    @RequireUserAndPermissionAnno({@RequireUserAndPermissionAnno.Check(
            identity = UserIdentityEnum.MANAGER,
            targetPermission = PermissionConst.SEC_MAINTAINER,
            isHasElseUpper = true,
            isSuchElseRequire = false)})
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ResponseEntity<J> reviewWork(String uuid, String workId, boolean approved, String reason, Long expectedRevision) {
        WorkModel work = workMapper.selectByIdForUpdate(workId);
        if (work == null) return ResponseEntity.ok(new ResourceR().resourceSuch(false, null));
        if (expectedRevision == null || !expectedRevision.equals(work.getContent_revision())) {
            return ResponseEntity.ok(new ErrorR().normal("作品内容已变化，请刷新并重新查看后审核"));
        }
        if (work.getStatus() == WorkStatusEnum.DELETED) return ResponseEntity.ok(new ErrorR().normal("已删除作品不能审核"));
        if (work.getReview_status() != WorkReviewStatusEnum.PENDING) {
            return ResponseEntity.ok(new ErrorR().normal("只能处理审核中的作品"));
        }

        String normalizedReason = reason == null ? "" : reason.trim();
        if (!approved && normalizedReason.isEmpty()) {
            return ResponseEntity.ok(new ErrorR().normal("拒绝作品时必须填写原因"));
        }
        if (normalizedReason.length() > 500) {
            return ResponseEntity.ok(new ErrorR().normal("审核原因不能超过500字"));
        }

        WorkReviewStatusEnum result = approved
                ? WorkReviewStatusEnum.APPROVED
                : WorkReviewStatusEnum.REJECTED;
        String storedReason = approved ? null : normalizedReason;
        if (!workReviewMapper.review(workId, result, storedReason, uuid)) {
            return ResponseEntity.ok(new ErrorR().normal("审核状态已变化，请刷新后重试"));
        }

        recordLog(workId, uuid, approved ? OP_REVIEW_APPROVE : OP_REVIEW_REJECT,
                Map.of("result", result.name(), "reason", normalizedReason, "content_revision", expectedRevision.toString()));
        return ResponseEntity.ok(new ChangeR().udu(true, 3));
    }

    /** 获取作品编辑详情（含封面/内容图签名、作者/收录者信息、学园/部团/学生名） */
    @RequireUserAndPermissionAnno({@RequireUserAndPermissionAnno.Check(identity = UserIdentityEnum.MANAGER, targetPermission = PermissionConst.SEC_MAINTAINER, isHasElseUpper = true, isSuchElseRequire = false)})
    public ResponseEntity<J> getWorkEditDetail(String uuid, String workId) {
        WorkModel work = workMapper.selectById(workId);
        if (work == null) return ResponseEntity.ok(new ResourceR().resourceSuch(false, null));

        return ResponseEntity.ok(new ResourceR().resourceSuch(true, workService.buildWorkEditDetailDto(work)));
    }

    /** 认领证据读取拥有独立的最小授权边界。 */
    @RequireUserAndPermissionAnno({@RequireUserAndPermissionAnno.Check(identity = UserIdentityEnum.MANAGER,
        targetPermission = PermissionConst.SEC_MAINTAINER | PermissionConst.DISCIPLINARY_COMMITTEE,
        isHasElseUpper = true, isSuchElseRequire = false)})
    public ResponseEntity<J> getClaimWorkDetail(String uuid, String workId) {
        WorkModel work = workMapper.selectById(workId);
        if (work == null) return ResponseEntity.ok(new ResourceR().resourceSuch(false, null));
        return ResponseEntity.ok(new ResourceR().resourceSuch(true, workService.buildWorkEditDetailDto(work)));
    }

    // ==================== 作品状态管理 ====================

    @RequireUserAndPermissionAnno({@RequireUserAndPermissionAnno.Check(identity = UserIdentityEnum.MANAGER, targetPermission = PermissionConst.SEC_MAINTAINER, isHasElseUpper = true, isSuchElseRequire = false)})
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ResponseEntity<J> updateWorkStatus(String uuid, String workId, WorkStatusEnum newStatus, String remark) {
        WorkModel work = workMapper.selectByIdForUpdate(workId);
        if (work == null) return ResponseEntity.ok(new ResourceR().resourceSuch(false, null));
        WorkStatusEnum oldStatus = work.getStatus();
        String operationType;

        if (newStatus == WorkStatusEnum.PUBLIC) {
            if (oldStatus != WorkStatusEnum.BAN && oldStatus != WorkStatusEnum.OFF && oldStatus != WorkStatusEnum.DELETED)
                return ResponseEntity.ok(new ErrorR().normal("只有封禁/下架/已删除的作品才能恢复"));
            operationType = OP_RESTORE;
        } else if (newStatus == WorkStatusEnum.BAN) {
            if (oldStatus != WorkStatusEnum.PUBLIC && oldStatus != WorkStatusEnum.PRIVATE)
                return ResponseEntity.ok(new ErrorR().normal("只能封禁公开或私有的作品"));
            operationType = OP_BAN;
        } else if (newStatus == WorkStatusEnum.OFF) {
            if (oldStatus != WorkStatusEnum.PUBLIC && oldStatus != WorkStatusEnum.PRIVATE)
                return ResponseEntity.ok(new ErrorR().normal("只能下架公开或私有的作品"));
            operationType = OP_OFF;
        } else if (newStatus == WorkStatusEnum.DELETED) {
            operationType = OP_DELETE;
        } else {
            return ResponseEntity.ok(new ErrorR().normal("不支持的状态转换"));
        }

        com.miaoyu.barc.utils.RequestInput.optional(remark, "处理说明", 500);
        WorkStatusEnum restore = work.getRestore_status();
        if (newStatus == WorkStatusEnum.PUBLIC) {
            // 老记录没有原可见性时保守恢复为私有；客户端 PUBLIC 表示恢复命令。
            newStatus = restore == WorkStatusEnum.PUBLIC ? WorkStatusEnum.PUBLIC : WorkStatusEnum.PRIVATE;
            restore = null;
        } else if (oldStatus == WorkStatusEnum.PUBLIC || oldStatus == WorkStatusEnum.PRIVATE) restore = oldStatus;
        if (!workMapper.updateStatus(workId, newStatus, restore)) throw com.miaoyu.barc.utils.RequestInput.invalid("作品状态保存失败");
        work.setStatus(newStatus);

        recordLog(workId, uuid, operationType, Map.of("remark", remark != null ? remark : "", "old_status", oldStatus.name(), "new_status", newStatus.name()));
        notifyAuthor(work, operationType, remark);
        return ResponseEntity.ok(new ChangeR().udu(true, 3));
    }

    // ==================== 作品内容修改 ====================

    @RequireUserAndPermissionAnno({@RequireUserAndPermissionAnno.Check(identity = UserIdentityEnum.MANAGER, targetPermission = PermissionConst.SEC_MAINTAINER, isHasElseUpper = true, isSuchElseRequire = false)})
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ResponseEntity<J> updateWorkContent(String uuid, WorkModel requestModel) {
        WorkModel existing = workMapper.selectByIdForUpdate(requestModel.getId());
        if (existing == null) return ResponseEntity.ok(new ResourceR().resourceSuch(false, null));
        workService.validateContentUpdate(existing, requestModel);
        // 管理代改不自动提审，但同样递增版本，使其他审核员的旧页面失效。
        if (!workMapper.updateText(requestModel) || !workMapper.touchContent(requestModel.getId())) {
            throw com.miaoyu.barc.utils.RequestInput.invalid("作品内容保存失败");
        }
        recordLog(requestModel.getId(), uuid, OP_EDIT, Map.of("remark", "管理员修改作品内容"));
        return ResponseEntity.ok(new ChangeR().udu(true, 3));
    }

    // ==================== 认领管理 ====================

    // 2026-10：权限位由「仅 SEC_MAINTAINER」放宽为「SEC_MAINTAINER 或 DISCIPLINARY_COMMITTEE」。
    // 原因：用户端两条只读认领接口（/api/work/claim/all_claims、/claims_by_work）已随重复清理删除，
    // 而它们原本允许风纪委员查看认领申请；不放宽的话，风纪委员会永久失去认领查看入口。
    // 实现依据：ComparePermission.has(your, target) 即 (your & target) != 0，
    // 因此 target 传两个权限位的「按位或」，天然就是 OR 语义（持有 4 或持有 1 均通过）。
    @RequireUserAndPermissionAnno({@RequireUserAndPermissionAnno.Check(
            identity = UserIdentityEnum.MANAGER,
            targetPermission = PermissionConst.SEC_MAINTAINER | PermissionConst.DISCIPLINARY_COMMITTEE,
            isHasElseUpper = true,
            isSuchElseRequire = false)})
    public ResponseEntity<J> getClaimsList(String uuid) {
        // 旧数组入口保留首批100条；新版分页入口供管理页完整遍历。
        return ResponseEntity.ok(new ResourceR().resourceSuch(true, workClaimMapper.selectPage("PENDING", null, null, 0, 100)));
    }

    @RequireUserAndPermissionAnno({@RequireUserAndPermissionAnno.Check(identity = UserIdentityEnum.MANAGER,
        targetPermission = PermissionConst.SEC_MAINTAINER | PermissionConst.DISCIPLINARY_COMMITTEE,
        isHasElseUpper = true, isSuchElseRequire = false)})
    public ResponseEntity<J> getClaimsPage(String uuid, String status, String workId, Integer page, Integer size) {
        if (status != null && !Set.of("PENDING", "APPROVED", "REJECTED", "SUPERSEDED").contains(status)) throw com.miaoyu.barc.utils.RequestInput.invalid("认领状态无效");
        PageInitPojo p = new PageInitPojo(page, size);
        long total = workClaimMapper.countPage(status, workId, null);
        return ResponseEntity.ok(new ResourceR().resourceSuch(true, new PageResultDto<>(total,
            workClaimMapper.selectPage(status, workId, null, p.getOffset(), p.getPageSize()),
            p.getPageNum(), p.getPageSize(), Math.max(1, (int) Math.ceil((double) total / p.getPageSize())))));
    }

    @RequireUserAndPermissionAnno({
        // 两种认领治理角色任一即可；不能写成两个必须同时满足的 Check。
        @RequireUserAndPermissionAnno.Check(identity = UserIdentityEnum.MANAGER, targetPermission = PermissionConst.DISCIPLINARY_COMMITTEE | PermissionConst.SEC_MAINTAINER, isHasElseUpper = true, isSuchElseRequire = false)
    })
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ResponseEntity<J> approveClaim(String uuid, String claimId, boolean approved) {
        WorkClaimModel initial = workClaimMapper.selectById(claimId);
        if (initial == null) return ResponseEntity.ok(new ErrorR().normal("认领申请不存在"));
        WorkModel work = workMapper.selectByIdForUpdate(initial.getWork_id());
        WorkClaimModel claim = workClaimMapper.selectByIdForUpdate(claimId);
        if (work == null || claim == null) throw com.miaoyu.barc.utils.RequestInput.invalid("作品或申请不存在");
        if (!"PENDING".equals(claim.getStatus())) return ResponseEntity.ok(new ErrorR().normal("申请已处理，请刷新查看结果"));
        if (approved && (Boolean.TRUE.equals(work.getIs_claim()) || work.getStatus() != WorkStatusEnum.PUBLIC || work.getReview_status() != WorkReviewStatusEnum.APPROVED)) {
            throw com.miaoyu.barc.utils.RequestInput.invalid("作品已认领或已删除");
        }
        String result = approved ? "APPROVED" : "REJECTED";
        if (!workClaimMapper.resolve(claimId, result, uuid, approved ? "认领通过" : "认领未通过")) throw com.miaoyu.barc.utils.RequestInput.invalid("申请状态已变化");
        if (approved) {
            if (userArchiveMapper.selectByUuid(claim.getApplicant_uuid()) == null) throw com.miaoyu.barc.utils.RequestInput.invalid("申请人已不存在");
            if (!workMapper.updateAttribution(work.getId(), claim.getApplicant_uuid(), true)) throw com.miaoyu.barc.utils.RequestInput.invalid("作品归属保存失败");
            workClaimMapper.closeOtherPending(work.getId(), uuid);
        }
        recordLog(work.getId(), uuid, OP_CLAIM_APPROVE, Map.of("applicant_uuid", claim.getApplicant_uuid(), "result", result));
        UserBasicModel applicant = userBasicMapper.selectByUuid(claim.getApplicant_uuid());
        if (applicant != null && applicant.getEmail() != null && !applicant.getEmail().isBlank()) {
            mailOutbox.enqueue("claim:" + claimId, applicant.getEmail(), "作品认领申请处理结果",
                "您对作品《" + work.getTitle() + "》的认领申请" + (approved ? "已通过。" : "未通过。"));
        }
        return ResponseEntity.ok(new ChangeR().udu(true, 3));
    }

    @RequireUserAndPermissionAnno({@RequireUserAndPermissionAnno.Check(identity = UserIdentityEnum.MANAGER, targetPermission = PermissionConst.SEC_MAINTAINER, isHasElseUpper = true, isSuchElseRequire = false)})
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ResponseEntity<J> revokeClaim(String uuid, String workId) {
        WorkModel work = workMapper.selectByIdForUpdate(workId);
        if (work == null) return ResponseEntity.ok(new ResourceR().resourceSuch(false, null));
        if (!work.getIs_claim()) return ResponseEntity.ok(new ErrorR().normal("该作品未被认领"));
        // 归属账号必须在改动作品之前解析：未配置时直接阻断，不落任何脏归属
        String officialUuid = officialAccountService.findBoundUuid(OfficialAccountEnum.COLLECTION_ASSISTANT);
        if (officialUuid == null) {
            return ResponseEntity.ok(new ErrorR().normal(
                    "官方账号未配置：未认领收录作品的临时归属账号尚未在「用户 → 官方账号」中绑定，无法撤销认领。"));
        }
        work.setIs_claim(false);
        work.setAuthor(officialUuid);
        if (!workMapper.updateAttribution(workId, work.getAuthor(), Boolean.TRUE.equals(work.getIs_claim()))) throw com.miaoyu.barc.utils.RequestInput.invalid("作品归属保存失败");
        recordLog(workId, uuid, OP_CLAIM_REVOKE, Map.of("remark", "管理员撤销认领"));
        return ResponseEntity.ok(new ChangeR().udu(true, 3));
    }

    @RequireUserAndPermissionAnno({@RequireUserAndPermissionAnno.Check(identity = UserIdentityEnum.MANAGER, targetPermission = PermissionConst.SEC_MAINTAINER, isHasElseUpper = true, isSuchElseRequire = false)})
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ResponseEntity<J> assignAuthor(String uuid, String workId, String authorUuid) {
        WorkModel work = workMapper.selectByIdForUpdate(workId);
        if (work == null) return ResponseEntity.ok(new ResourceR().resourceSuch(false, null));
        if (userArchiveMapper.selectByUuid(authorUuid) == null) return ResponseEntity.ok(new UserR().noSuchUser());
        work.setIs_claim(true);
        work.setAuthor(authorUuid);
        if (!workMapper.updateAttribution(workId, work.getAuthor(), Boolean.TRUE.equals(work.getIs_claim()))) throw com.miaoyu.barc.utils.RequestInput.invalid("作品归属保存失败");
        workClaimMapper.closeOtherPending(workId, uuid);
        recordLog(workId, uuid, OP_CLAIM_ASSIGN, Map.of("assigned_author_uuid", authorUuid));
        return ResponseEntity.ok(new ChangeR().udu(true, 3));
    }

    // 与 getClaimsList 同一处放宽：风纪委员也要能按作品查认领历史（原用户端 claims_by_work 的等价能力）
    @RequireUserAndPermissionAnno({@RequireUserAndPermissionAnno.Check(
            identity = UserIdentityEnum.MANAGER,
            targetPermission = PermissionConst.SEC_MAINTAINER | PermissionConst.DISCIPLINARY_COMMITTEE,
            isHasElseUpper = true,
            isSuchElseRequire = false)})
    public ResponseEntity<J> getClaimHistory(String uuid, String workId) {
        return ResponseEntity.ok(new ResourceR().resourceSuch(true, workClaimMapper.selectPage(null, workId, null, 0, 100)));
    }

    // 说明（2026-10 下线）：原「投诉处理」区块的 getComplaintsList / processComplaint 两个方法
    // 直接读写已废弃表 feedback_work（见 AGENTS.md 第六章：作品投诉统一走 feedback 表、type = WORK），
    // 管理端前端也已迁移到 GET /feedback/feedbacks_by_type 与 PUT /feedback/update，
    // 因此连同对应的 Controller 端点、Mapper、Model 一并整块移除。
    //
    // 新的作品投诉处理链路：FeedbackController / FeedbackService（作用于 feedback 表）。

    // ==================== 操作日志 ====================

    @RequireUserAndPermissionAnno({@RequireUserAndPermissionAnno.Check(identity = UserIdentityEnum.MANAGER, targetPermission = PermissionConst.SEC_MAINTAINER, isHasElseUpper = true, isSuchElseRequire = false)})
    public ResponseEntity<J> getOperationLogs(String uuid, PageRequestDto dto) {
        if (dto == null) dto = new PageRequestDto();
        PageInitPojo pageInit = new PageInitPojo(dto);
        int offset = pageInit.getOffset(), pageSize = pageInit.getPageSize(), pageNum = pageInit.getPageNum();
        Map<String, Object> params = dto.getParams() != null ? dto.getParams() : new HashMap<>();
        String operationType = params.get("operation_type") != null ? params.get("operation_type").toString() : null;

        List<WorkOperationLogModel> logs;
        Long total;
        if (operationType != null && !operationType.isEmpty()) {
            logs = workOperationLogMapper.selectByTypeAndPage(operationType, offset, pageSize);
            total = workOperationLogMapper.countByType(operationType);
        } else {
            logs = workOperationLogMapper.selectByPage(offset, pageSize);
            total = workOperationLogMapper.countAll();
        }
        int totalPage = (int) Math.ceil((double) total / pageSize);
        return ResponseEntity.ok(new ResourceR().resourceSuch(true, new PageResultDto<>(total, logs, pageNum, pageSize, totalPage == 0 ? 1 : totalPage)));
    }

    // ==================== 内部工具 ====================

    /**
     * 搜索作品（供指派作者场景），返回最多 10 条。
     *
     * 鉴权说明：这是管理端「认领管理」的辅助搜索口，必须做权限校验。
     * 采用「比大小」判定（isHasElseUpper = false，即 permission >= DISCIPLINARY_COMMITTEE），
     * 等价于「只要身份是 MANAGER 即可」——所有管理者权限值都 ≥ 1。
     * uuid 必须是第 1 个形参，否则 AOP 切面的 uuidIndex=0 会取到 keyword。
     */
    @RequireUserAndPermissionAnno({@RequireUserAndPermissionAnno.Check(
            identity = UserIdentityEnum.MANAGER,
            targetPermission = PermissionConst.DISCIPLINARY_COMMITTEE,
            isHasElseUpper = false,
            isSuchElseRequire = false)})
    public List<WorkSearchView> searchWorks(String uuid, String keyword) {
        String normalized = com.miaoyu.barc.utils.RequestInput.search(keyword, "关键词", 100);
        if (normalized == null) return List.of();
        // 复用已有有界查询；传输层仅保留选择器真正需要的公开标识和标题。
        return workMapper.selectByPageForManage(null, normalized, 0, 10, "created_at", "DESC")
                .stream().map(work -> new WorkSearchView(work.getId(), work.getTitle())).toList();
    }

    /** 搜索用户（按UUID/用户名/昵称），返回最多15条；鉴权同 searchWorks */
    @RequireUserAndPermissionAnno({@RequireUserAndPermissionAnno.Check(
            identity = UserIdentityEnum.MANAGER,
            targetPermission = PermissionConst.DISCIPLINARY_COMMITTEE,
            isHasElseUpper = false,
            isSuchElseRequire = false)})
    public List<com.miaoyu.barc.user.model.vo.UserSearchView> searchUsers(String uuid, String keyword) {
        return userSearchService.search(keyword);
    }

    private void recordLog(String workId, String operatorUuid, String operationType, Map<String, String> detail) {
        try {
            WorkOperationLogModel logEntry = new WorkOperationLogModel();
            logEntry.setId(new GenerateUUID().getUuid36l());
            logEntry.setWork_id(workId);
            logEntry.setOperator_uuid(operatorUuid);
            logEntry.setOperation_type(operationType);
            logEntry.setDetail(objectMapper.writeValueAsString(detail));
            if (workOperationLogMapper.insert(logEntry) != 1) throw com.miaoyu.barc.utils.RequestInput.invalid("操作审计保存失败");
        } catch (JsonProcessingException e) { throw new IllegalStateException("操作日志序列化失败", e); }
    }

    /** 通知任务与状态变更同事务保存，队列失败必须向外传播。 */
    private void notifyAuthor(WorkModel work, String operationType, String remark) {
            UserBasicModel author = userBasicMapper.selectByUuid(work.getAuthor());
            if (author != null && author.getEmail() != null) {
                String opName = switch (operationType) { case OP_BAN -> "封禁"; case OP_OFF -> "下架"; case OP_DELETE -> "删除"; case OP_RESTORE -> "恢复"; default -> operationType; };
                mailOutbox.enqueue("work-status:" + java.util.UUID.randomUUID(), author.getEmail(), "您的作品已被" + opName, "您在蔚蓝收录馆中收录的作品《" + work.getTitle() + "》已被" + opName + "。" + (remark != null ? "处理备注：" + remark : ""));
            }

    }
}
