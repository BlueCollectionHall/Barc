package com.miaoyu.barc.api.work.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.miaoyu.barc.annotation.RequireUserAndPermissionAnno;
import com.miaoyu.barc.api.mapper.ClubMapper;
import com.miaoyu.barc.api.mapper.SchoolMapper;
import com.miaoyu.barc.api.mapper.StudentMapper;
import com.miaoyu.barc.api.model.SchoolClubModel;
import com.miaoyu.barc.api.model.SchoolModel;
import com.miaoyu.barc.api.model.StudentModel;
import com.miaoyu.barc.api.work.enumeration.WorkStatusEnum;
import com.miaoyu.barc.api.work.constant.WorkAttributionConst;
import com.miaoyu.barc.api.work.enumeration.WorkReviewStatusEnum;
import com.miaoyu.barc.api.work.mapper.WorkClaimMapper;
import com.miaoyu.barc.api.work.mapper.WorkCoverImageMapper;
import com.miaoyu.barc.api.work.mapper.WorkImageMapper;
import com.miaoyu.barc.api.work.mapper.WorkMapper;
import com.miaoyu.barc.api.work.mapper.WorkOperationLogMapper;
import com.miaoyu.barc.api.work.mapper.WorkReviewMapper;
import com.miaoyu.barc.api.work.model.WorkClaimListDto;
import com.miaoyu.barc.api.work.model.WorkClaimModel;
import com.miaoyu.barc.api.work.model.WorkCoverImageModel;
import com.miaoyu.barc.api.work.model.WorkEditDetailDto;
import com.miaoyu.barc.api.work.model.WorkImageModel;
import com.miaoyu.barc.api.work.model.WorkModel;
import com.miaoyu.barc.api.work.model.WorkOperationLogModel;
import com.miaoyu.barc.email.utils.SendEmailUtils;
import com.miaoyu.barc.feedback.mapper.WorkFeedbackMapper;
import com.miaoyu.barc.feedback.model.WorkFeedbackModel;
import com.miaoyu.barc.permission.PermissionConst;
import com.miaoyu.barc.response.ChangeR;
import com.miaoyu.barc.response.ErrorR;
import com.miaoyu.barc.response.ResourceR;
import com.miaoyu.barc.response.UserR;
import com.miaoyu.barc.user.enumeration.UserIdentityEnum;
import com.miaoyu.barc.user.mapper.UserArchiveMapper;
import com.miaoyu.barc.user.mapper.UserBasicMapper;
import com.miaoyu.barc.user.model.UserArchiveModel;
import com.miaoyu.barc.user.model.UserBasicModel;
import com.miaoyu.barc.user.model.vo.UserInfoVo;
import com.miaoyu.barc.utils.GenerateUUID;
import com.miaoyu.barc.utils.J;
import com.miaoyu.barc.utils.dto.PageRequestDto;
import com.miaoyu.barc.utils.dto.PageResultDto;
import com.miaoyu.barc.utils.pojo.PageInitPojo;
import com.miaoyu.barc.utils.tencent.cos.CosBucketConfigEnum;
import com.miaoyu.barc.utils.tencent.cos.CosService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 管理端作品管理服务
 * 权限：所有方法使用 SEC_MAINTAINER(4) 位运算检查
 * 注意：每个方法签名中 String 第一个参数必须是操作者UUID（供AOP切面校验权限）
 */
@Slf4j
@Service
public class WorkManageService {

    public static final String OP_BAN = "BAN";
    public static final String OP_OFF = "OFF";
    public static final String OP_DELETE = "DELETE";
    public static final String OP_RESTORE = "RESTORE";
    public static final String OP_EDIT = "EDIT";
    public static final String OP_CLAIM_APPROVE = "CLAIM_APPROVE";
    public static final String OP_CLAIM_REVOKE = "CLAIM_REVOKE";
    public static final String OP_CLAIM_ASSIGN = "CLAIM_ASSIGN";
    public static final String OP_COMPLAINT_PROCESS = "COMPLAINT_PROCESS";
    public static final String OP_REVIEW_APPROVE = "REVIEW_APPROVE";
    public static final String OP_REVIEW_REJECT = "REVIEW_REJECT";

    @Autowired private WorkMapper workMapper;
    @Autowired private WorkClaimMapper workClaimMapper;
    @Autowired private WorkOperationLogMapper workOperationLogMapper;
    @Autowired private WorkReviewMapper workReviewMapper;
    @Autowired private WorkFeedbackMapper workFeedbackMapper;
    @Autowired private UserBasicMapper userBasicMapper;
    @Autowired private UserArchiveMapper userArchiveMapper;
    @Autowired private SendEmailUtils sendEmailUtils;
    @Autowired private WorkService workService;
    @Autowired private WorkAttributionService workAttributionService;
    @Autowired private WorkImageMapper workImageMapper;
    @Autowired private CosService cosService;
    @Autowired private WorkCoverImageMapper workCoverImageMapper;
    @Autowired private StudentMapper studentMapper;
    @Autowired private SchoolMapper schoolMapper;
    @Autowired private ClubMapper clubMapper;

    private final ObjectMapper objectMapper = new ObjectMapper();

    // ==================== 作品列表与详情 ====================

    @RequireUserAndPermissionAnno({@RequireUserAndPermissionAnno.Check(identity = UserIdentityEnum.MANAGER, targetPermission = PermissionConst.SEC_MAINTAINER, isHasElseUpper = true, isSuchElseRequire = false)})
    public ResponseEntity<J> getWorkListForManage(String uuid, PageRequestDto dto) {
        PageInitPojo pageInit = new PageInitPojo(dto);
        int offset = pageInit.getOffset();
        int pageSize = pageInit.getPageSize();
        int pageNum = pageInit.getPageNum();
        Map<String, Object> params = dto.getParams() != null ? dto.getParams() : new HashMap<>();
        String status = params.get("status") != null ? params.get("status").toString() : null;
        String keyword = params.get("keyword") != null ? params.get("keyword").toString() : null;

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
        PageInitPojo pageInit = new PageInitPojo(dto);
        Map<String, Object> params = dto.getParams() != null ? dto.getParams() : new HashMap<>();
        String keyword = params.get("keyword") != null ? params.get("keyword").toString().trim() : null;
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
    @Transactional
    public ResponseEntity<J> reviewWork(String uuid, String workId, boolean approved, String reason) {
        WorkModel work = workMapper.selectById(workId);
        if (work == null) return ResponseEntity.ok(new ResourceR().resourceSuch(false, null));
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
                Map.of("result", result.name(), "reason", normalizedReason));
        return ResponseEntity.ok(new ChangeR().udu(true, 3));
    }

    /** 获取作品编辑详情（含封面/内容图签名、作者/收录者信息、学园/部团/学生名） */
    @RequireUserAndPermissionAnno({@RequireUserAndPermissionAnno.Check(identity = UserIdentityEnum.MANAGER, targetPermission = PermissionConst.SEC_MAINTAINER, isHasElseUpper = true, isSuchElseRequire = false)})
    public ResponseEntity<J> getWorkEditDetail(String uuid, String workId) {
        WorkModel work = workMapper.selectById(workId);
        if (work == null) return ResponseEntity.ok(new ResourceR().resourceSuch(false, null));

        // 1. 获取封面图 object_key 并签名（不污染 WorkModel.cover_image）
        String coverUrl = "";
        WorkCoverImageModel cover = workCoverImageMapper.selectByWorkId(workId);
        if (cover != null) {
            coverUrl = cosService.generateSignedUrl(cover.getObject_key(),
                    new Date(System.currentTimeMillis() + 60 * 1000), CosBucketConfigEnum.image);
        }

        // 2. 签名内容图
        List<WorkImageModel> images = workImageMapper.selectByWorkId(workId);
        List<String> contentUrls = List.of();
        List<WorkEditDetailDto.ContentImageDto> contentImages = List.of();
        if (!images.isEmpty()) {
            List<WorkImageModel> sortedImages = images.stream().sorted(Comparator.comparing(WorkImageModel::getSort)).toList();
            List<String> keys = sortedImages.stream().map(WorkImageModel::getObject_key).toList();
            contentUrls = cosService.generateBatchSignedUrl(keys,
                    new Date(System.currentTimeMillis() + 60 * 1000), CosBucketConfigEnum.image);
            List<String> signedUrls = contentUrls;
            contentImages = new java.util.ArrayList<>();
            for (int i = 0; i < sortedImages.size(); i++) {
                WorkImageModel image = sortedImages.get(i);
                WorkEditDetailDto.ContentImageDto imageDto = new WorkEditDetailDto.ContentImageDto();
                imageDto.setId(image.getId());
                imageDto.setSort(image.getSort());
                imageDto.setUrl(signedUrls.get(i));
                contentImages.add(imageDto);
            }
        }

        // 3. 收录者昵称
        String uploaderNickname = workAttributionService.resolveUploaderNickname(work);

        // 4. 归属者显示
        // author 是平台内当前归属；author_nickname 只是站外原作者署名，不能用于“暂归属”。
        String authorDisplay = workAttributionService.resolvePlatformOwnerNickname(work);

        // 5. 学园/部团/学生名
        String schoolName = "", clubName = "", studentName = "";
        if (work.getStudent() != null && !work.getStudent().isEmpty()) {
            StudentModel stu = studentMapper.selectById(work.getStudent());
            if (stu != null) {
                studentName = stu.getCn_name();
                if (stu.getSchool() != null) {
                    SchoolModel sch = schoolMapper.selectById(stu.getSchool());
                    if (sch != null) schoolName = sch.getCn_name();
                }
                if (stu.getClub() != null) {
                    SchoolClubModel cl = clubMapper.selectById(stu.getClub());
                    if (cl != null) clubName = cl.getCn_name();
                }
            }
        }

        // 6. 组装 DTO
        WorkEditDetailDto dto = new WorkEditDetailDto();
        dto.setWork(work);
        dto.setCover_image_url(coverUrl);
        dto.setContent_image_urls(contentUrls);
        dto.setContent_images(contentImages);
        dto.setUploader_nickname(uploaderNickname);
        dto.setAuthor_display(authorDisplay);
        dto.setSchool_name(schoolName);
        dto.setClub_name(clubName);
        dto.setStudent_name(studentName);

        return ResponseEntity.ok(new ResourceR().resourceSuch(true, dto));
    }

    // ==================== 作品状态管理 ====================

    @RequireUserAndPermissionAnno({@RequireUserAndPermissionAnno.Check(identity = UserIdentityEnum.MANAGER, targetPermission = PermissionConst.SEC_MAINTAINER, isHasElseUpper = true, isSuchElseRequire = false)})
    @Transactional
    public ResponseEntity<J> updateWorkStatus(String uuid, String workId, WorkStatusEnum newStatus, String remark) {
        WorkModel work = workMapper.selectById(workId);
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

        work.setStatus(newStatus);
        if (!workMapper.update(work)) return ResponseEntity.ok(new ChangeR().udu(false, 3));

        recordLog(workId, uuid, operationType, Map.of("remark", remark != null ? remark : "", "old_status", oldStatus.name(), "new_status", newStatus.name()));
        notifyAuthor(work, operationType, remark);
        return ResponseEntity.ok(new ChangeR().udu(true, 3));
    }

    // ==================== 作品内容修改 ====================

    @RequireUserAndPermissionAnno({@RequireUserAndPermissionAnno.Check(identity = UserIdentityEnum.MANAGER, targetPermission = PermissionConst.SEC_MAINTAINER, isHasElseUpper = true, isSuchElseRequire = false)})
    @Transactional
    public ResponseEntity<J> updateWorkContent(String uuid, WorkModel requestModel) {
        if (workMapper.selectById(requestModel.getId()) == null) return ResponseEntity.ok(new ResourceR().resourceSuch(false, null));
        // 纯文字更新：不改封面/Banner/作者/收录者（图片在独立表中管理）
        if (!workMapper.updateText(requestModel)) return ResponseEntity.ok(new ChangeR().udu(false, 3));
        recordLog(requestModel.getId(), uuid, OP_EDIT, Map.of("remark", "管理员修改作品内容"));
        return ResponseEntity.ok(new ChangeR().udu(true, 3));
    }

    // ==================== 认领管理 ====================

    @RequireUserAndPermissionAnno({@RequireUserAndPermissionAnno.Check(identity = UserIdentityEnum.MANAGER, targetPermission = PermissionConst.SEC_MAINTAINER, isHasElseUpper = true, isSuchElseRequire = false)})
    public ResponseEntity<J> getClaimsList(String uuid) {
        List<WorkClaimModel> claims = workClaimMapper.selectAll();
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        List<WorkClaimListDto> dtos = new ArrayList<>();
        for (WorkClaimModel c : claims) {
            WorkClaimListDto dto = new WorkClaimListDto();
            dto.setId(c.getId());
            dto.setWork_id(c.getWork_id());
            dto.setApplicant_uuid(c.getApplicant_uuid());
            dto.setCreated_at(c.getCreated_at() != null ? c.getCreated_at().format(fmt) : "");

            WorkModel w = workMapper.selectById(c.getWork_id());
            dto.setWork_title(w != null ? w.getTitle() : "未知作品");

            UserArchiveModel ua = userArchiveMapper.selectByUuid(c.getApplicant_uuid());
            dto.setApplicant_nickname(ua != null ? ua.getNickname() : "");
            UserBasicModel ub = userBasicMapper.selectByUuid(c.getApplicant_uuid());
            dto.setApplicant_username(ub != null ? ub.getUsername() : "");

            dtos.add(dto);
        }
        return ResponseEntity.ok(new ResourceR().resourceSuch(true, dtos));
    }

    @RequireUserAndPermissionAnno({
        @RequireUserAndPermissionAnno.Check(identity = UserIdentityEnum.MANAGER, targetPermission = PermissionConst.DISCIPLINARY_COMMITTEE, isHasElseUpper = true, isSuchElseRequire = false),
        @RequireUserAndPermissionAnno.Check(identity = UserIdentityEnum.MANAGER, targetPermission = PermissionConst.SEC_MAINTAINER, isHasElseUpper = true, isSuchElseRequire = false)
    })
    @Transactional
    public ResponseEntity<J> approveClaim(String uuid, String claimId, boolean approved) {
        WorkClaimModel claim = workClaimMapper.selectById(claimId);
        if (claim == null) return ResponseEntity.ok(new ErrorR().normal("认领申请不存在"));
        WorkModel work = workMapper.selectById(claim.getWork_id());

        if (approved) {
            if (work == null) return ResponseEntity.ok(new ErrorR().normal("作品不存在"));
            if (work.getIs_claim()) return ResponseEntity.ok(new ErrorR().normal("作品已经被认领"));
            work.setIs_claim(true);
            work.setAuthor(claim.getApplicant_uuid());
            workMapper.update(work);
            workClaimMapper.deleteByWorkId(claim.getWork_id());
            recordLog(claim.getWork_id(), uuid, OP_CLAIM_APPROVE, Map.of("applicant_uuid", claim.getApplicant_uuid(), "result", "approved"));
            UserBasicModel applicant = userBasicMapper.selectByUuid(claim.getApplicant_uuid());
            if (applicant != null) sendEmailUtils.customEmail(applicant.getEmail(), "作品认领申请已通过", "您对作品《" + work.getTitle() + "》的认领申请已通过审核！");
        } else {
            workClaimMapper.deleteById(claimId);
            recordLog(claim.getWork_id(), uuid, OP_CLAIM_APPROVE, Map.of("applicant_uuid", claim.getApplicant_uuid(), "result", "rejected"));
            if (work != null) {
                UserBasicModel applicant = userBasicMapper.selectByUuid(claim.getApplicant_uuid());
                if (applicant != null) sendEmailUtils.customEmail(applicant.getEmail(), "作品认领申请已拒绝", "您对作品《" + work.getTitle() + "》的认领申请未通过审核。");
            }
        }
        return ResponseEntity.ok(new ChangeR().udu(true, 3));
    }

    @RequireUserAndPermissionAnno({@RequireUserAndPermissionAnno.Check(identity = UserIdentityEnum.MANAGER, targetPermission = PermissionConst.SEC_MAINTAINER, isHasElseUpper = true, isSuchElseRequire = false)})
    @Transactional
    public ResponseEntity<J> revokeClaim(String uuid, String workId) {
        WorkModel work = workMapper.selectById(workId);
        if (work == null) return ResponseEntity.ok(new ResourceR().resourceSuch(false, null));
        if (!work.getIs_claim()) return ResponseEntity.ok(new ErrorR().normal("该作品未被认领"));
        work.setIs_claim(false);
        work.setAuthor(WorkAttributionConst.COLLECTION_ASSISTANT_UUID);
        workMapper.update(work);
        recordLog(workId, uuid, OP_CLAIM_REVOKE, Map.of("remark", "管理员撤销认领"));
        return ResponseEntity.ok(new ChangeR().udu(true, 3));
    }

    @RequireUserAndPermissionAnno({@RequireUserAndPermissionAnno.Check(identity = UserIdentityEnum.MANAGER, targetPermission = PermissionConst.SEC_MAINTAINER, isHasElseUpper = true, isSuchElseRequire = false)})
    @Transactional
    public ResponseEntity<J> assignAuthor(String uuid, String workId, String authorUuid) {
        WorkModel work = workMapper.selectById(workId);
        if (work == null) return ResponseEntity.ok(new ResourceR().resourceSuch(false, null));
        if (userArchiveMapper.selectByUuid(authorUuid) == null) return ResponseEntity.ok(new UserR().noSuchUser());
        work.setIs_claim(true);
        work.setAuthor(authorUuid);
        workMapper.update(work);
        workClaimMapper.deleteByWorkId(workId);
        recordLog(workId, uuid, OP_CLAIM_ASSIGN, Map.of("assigned_author_uuid", authorUuid));
        return ResponseEntity.ok(new ChangeR().udu(true, 3));
    }

    @RequireUserAndPermissionAnno({@RequireUserAndPermissionAnno.Check(identity = UserIdentityEnum.MANAGER, targetPermission = PermissionConst.SEC_MAINTAINER, isHasElseUpper = true, isSuchElseRequire = false)})
    public ResponseEntity<J> getClaimHistory(String uuid, String workId) {
        return ResponseEntity.ok(new ResourceR().resourceSuch(true, workClaimMapper.selectByWorkId(workId)));
    }

    // ==================== 投诉处理 ====================

    @RequireUserAndPermissionAnno({@RequireUserAndPermissionAnno.Check(identity = UserIdentityEnum.MANAGER, targetPermission = PermissionConst.SEC_MAINTAINER, isHasElseUpper = true, isSuchElseRequire = false)})
    public ResponseEntity<J> getComplaintsList(String uuid) {
        return ResponseEntity.ok(new ResourceR().resourceSuch(true, workFeedbackMapper.selectAll()));
    }

    @RequireUserAndPermissionAnno({@RequireUserAndPermissionAnno.Check(identity = UserIdentityEnum.MANAGER, targetPermission = PermissionConst.SEC_MAINTAINER, isHasElseUpper = true, isSuchElseRequire = false)})
    @Transactional
    public ResponseEntity<J> processComplaint(String uuid, String complaintId, String action, String remark) {
        WorkFeedbackModel complaint = workFeedbackMapper.selectById(complaintId);
        if (complaint == null) return ResponseEntity.ok(new ErrorR().normal("投诉不存在"));
        if (Boolean.TRUE.equals(complaint.getStatus())) return ResponseEntity.ok(new ErrorR().normal("投诉已处理"));

        complaint.setStatus(true);
        workFeedbackMapper.update(complaintId, remark);

        if (!"IGNORE".equals(action)) {
            WorkStatusEnum newStatus = switch (action) { case "BAN" -> WorkStatusEnum.BAN; case "OFF" -> WorkStatusEnum.OFF; case "DELETE" -> WorkStatusEnum.DELETED; default -> null; };
            if (newStatus != null) {
                WorkModel work = workMapper.selectById(complaint.getWork_id());
                if (work != null) { work.setStatus(newStatus); workMapper.update(work); notifyAuthor(work, "BAN".equals(action) ? OP_BAN : "OFF".equals(action) ? OP_OFF : OP_DELETE, "因投诉处理：" + (remark != null ? remark : "")); }
            }
        }

        recordLog(complaint.getWork_id(), uuid, OP_COMPLAINT_PROCESS, Map.of("complaint_id", complaintId, "action", action, "remark", remark != null ? remark : ""));
        if (complaint.getEmail() != null && !complaint.getEmail().isEmpty()) sendEmailUtils.customEmail(complaint.getEmail(), "投诉反馈已处理完成", "您对作品的投诉已经完成处理。处理结果：" + action + "。" + (remark != null ? "处理备注：" + remark : ""));
        return ResponseEntity.ok(new ChangeR().udu(true, 3));
    }

    // ==================== 操作日志 ====================

    @RequireUserAndPermissionAnno({@RequireUserAndPermissionAnno.Check(identity = UserIdentityEnum.MANAGER, targetPermission = PermissionConst.SEC_MAINTAINER, isHasElseUpper = true, isSuchElseRequire = false)})
    public ResponseEntity<J> getOperationLogs(String uuid, PageRequestDto dto) {
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

    /** 搜索作品（供指派作者场景），返回最多10条 */
    public List<WorkModel> searchWorks(String keyword) {
        return workMapper.selectByPageForManage(null, keyword, 0, 10, "created_at", "DESC");
    }

    /** 搜索用户（按UUID/用户名/昵称），返回最多15条 */
    public List<UserInfoVo> searchUsers(String keyword) {
        return userArchiveMapper.searchByKeyword(keyword);
    }

    private void recordLog(String workId, String operatorUuid, String operationType, Map<String, String> detail) {
        try {
            WorkOperationLogModel logEntry = new WorkOperationLogModel();
            logEntry.setId(new GenerateUUID().getUuid36l());
            logEntry.setWork_id(workId);
            logEntry.setOperator_uuid(operatorUuid);
            logEntry.setOperation_type(operationType);
            logEntry.setDetail(objectMapper.writeValueAsString(detail));
            workOperationLogMapper.insert(logEntry);
        } catch (JsonProcessingException e) { log.error("操作日志JSON序列化失败", e); }
    }

    private void notifyAuthor(WorkModel work, String operationType, String remark) {
        try {
            UserBasicModel author = userBasicMapper.selectByUuid(work.getAuthor());
            if (author != null && author.getEmail() != null) {
                String opName = switch (operationType) { case OP_BAN -> "封禁"; case OP_OFF -> "下架"; case OP_DELETE -> "删除"; case OP_RESTORE -> "恢复"; default -> operationType; };
                sendEmailUtils.customEmail(author.getEmail(), "您的作品已被" + opName, "您在蔚蓝收录馆中收录的作品《" + work.getTitle() + "》已被" + opName + "。" + (remark != null ? "处理备注：" + remark : ""));
            }
        } catch (Exception e) { log.error("通知作者邮件发送失败: workId={}", work.getId(), e); }
    }
}
