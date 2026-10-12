package com.miaoyu.barc.api.work.controller;

import com.miaoyu.barc.api.work.enumeration.WorkStatusEnum;
import com.miaoyu.barc.api.work.enumeration.WorkReviewStatusEnum;
import com.miaoyu.barc.api.work.model.WorkModel;
import com.miaoyu.barc.api.work.model.WorkCommands;
import com.miaoyu.barc.api.work.model.WorkSearchView;
import com.miaoyu.barc.api.work.service.WorkManageService;
import com.miaoyu.barc.response.ResourceR;
import com.miaoyu.barc.user.model.vo.UserInfoVo;
import com.miaoyu.barc.utils.J;
import com.miaoyu.barc.utils.dto.PageRequestDto;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 管理端作品管理控制器 - 路径前缀：/api/work/manage
 * 权限检查在 WorkManageService 层由 @RequireUserAndPermissionAnno 实现
 */
@RestController
@RequestMapping("/api/work/manage")
public class WorkManageController {

    @Autowired
    private WorkManageService workManageService;

    private String uuid(HttpServletRequest r) { return r.getAttribute("uuid").toString(); }

    // ==================== 作品列表与详情 ====================
    @PostMapping("/list")
    public ResponseEntity<J> getWorkListManage(HttpServletRequest r, @RequestBody PageRequestDto dto) {
        return workManageService.getWorkListForManage(uuid(r), dto);
    }

    @GetMapping("/detail")
    public ResponseEntity<J> getWorkDetailManage(HttpServletRequest r, @RequestParam("work_id") String workId) {
        return workManageService.getWorkDetailForManage(uuid(r), workId);
    }

    /** 上传审核列表，可在待审/通过/驳回之间切换回看。 */
    @PostMapping("/reviews")
    public ResponseEntity<J> getReviewList(
            HttpServletRequest r,
            @RequestParam(value = "review_status", defaultValue = "PENDING") WorkReviewStatusEnum reviewStatus,
            @RequestBody PageRequestDto dto) {
        return workManageService.getReviewList(uuid(r), reviewStatus, dto);
    }

    /** 静默审核作品；拒绝时 reason 必填，处理结果不会触发邮件。 */
    @PostMapping("/review")
    public ResponseEntity<J> reviewWork(HttpServletRequest r, @RequestBody WorkCommands.Review body) {
        return workManageService.reviewWork(uuid(r), body.work_id(), body.approved(), body.reason(), body.expected_revision());
    }

    /** 获取作品编辑详情（含封面/内容图签名URL、作者/收录者信息、学园/部团/学生名） */
    @GetMapping("/edit-detail")
    public ResponseEntity<J> getWorkEditDetail(HttpServletRequest r, @RequestParam("work_id") String workId) {
        return workManageService.getWorkEditDetail(uuid(r), workId);
    }

    // ==================== 作品状态管理 ====================
    @PutMapping("/status")
    public ResponseEntity<J> updateWorkStatusManage(HttpServletRequest r, @RequestBody WorkCommands.Status body) {
        return workManageService.updateWorkStatus(uuid(r), body.work_id(), body.status(), body.remark());
    }

    // ==================== 作品内容修改 ====================
    @PutMapping("/update")
    public ResponseEntity<J> updateWorkContentManage(HttpServletRequest r, @RequestBody WorkCommands.Content workModel) {
        return workManageService.updateWorkContent(uuid(r), workModel.toModel());
    }

    // ==================== 认领管理 ====================
    @GetMapping("/claims")
    public ResponseEntity<J> getClaimsManage(HttpServletRequest r) {
        return workManageService.getClaimsList(uuid(r));
    }

    /** 有界认领队列/历史；旧 claims 数组入口留作兼容。 */
    @GetMapping("/claims/page")
    public ResponseEntity<J> claimsPage(HttpServletRequest r,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "work_id", required = false) String workId,
            @RequestParam(value = "page", defaultValue = "1") Integer page,
            @RequestParam(value = "size", defaultValue = "20") Integer size) {
        return workManageService.getClaimsPage(uuid(r), status, workId, page, size);
    }

    /** 风纪委员查看认领证据，不因此获得作品编辑或管理状态写入能力。 */
    @GetMapping("/claim/detail")
    public ResponseEntity<J> claimDetail(HttpServletRequest r, @RequestParam("work_id") String workId) {
        return workManageService.getClaimWorkDetail(uuid(r), workId);
    }

    @PostMapping("/claim/approve")
    public ResponseEntity<J> approveClaimManage(HttpServletRequest r, @RequestBody WorkCommands.ClaimReview body) {
        return workManageService.approveClaim(uuid(r), body.claim_id(), body.approved());
    }

    @PostMapping("/claim/revoke")
    public ResponseEntity<J> revokeClaimManage(HttpServletRequest r, @RequestBody WorkCommands.WorkId body) {
        return workManageService.revokeClaim(uuid(r), body.work_id());
    }

    @PostMapping("/claim/assign")
    public ResponseEntity<J> assignAuthorManage(HttpServletRequest r, @RequestBody WorkCommands.Assign body) {
        return workManageService.assignAuthor(uuid(r), body.work_id(), body.author_uuid());
    }

    @GetMapping("/claim/history")
    public ResponseEntity<J> getClaimHistoryManage(HttpServletRequest r, @RequestParam("work_id") String workId) {
        return workManageService.getClaimHistory(uuid(r), workId);
    }

    // ==================== 操作日志 ====================
    @PostMapping("/logs")
    public ResponseEntity<J> getOperationLogsManage(HttpServletRequest r, @RequestBody PageRequestDto dto) {
        return workManageService.getOperationLogs(uuid(r), dto);
    }

    // ==================== 辅助搜索 ====================

    /** 搜索作品（按ID/标题），供指派作者等下拉选择（MANAGER + 比大小鉴权，见 WorkManageService#searchWorks） */
    @GetMapping("/search-work")
    public ResponseEntity<J> searchWork(HttpServletRequest r, @RequestParam("keyword") String keyword) {
        List<WorkSearchView> works = workManageService.searchWorks(uuid(r), keyword);
        return ResponseEntity.ok(new ResourceR().resourceSuch(true, works));
    }

    /** 搜索用户（按UUID/用户名/昵称），供指派作者等下拉选择（MANAGER + 比大小鉴权，见 WorkManageService#searchUsers） */
    @GetMapping("/search-user")
    public ResponseEntity<J> searchUser(HttpServletRequest r, @RequestParam("keyword") String keyword) {
        List<com.miaoyu.barc.user.model.vo.UserSearchView> users = workManageService.searchUsers(uuid(r), keyword);
        return ResponseEntity.ok(new ResourceR().resourceSuch(true, users));
    }
}
