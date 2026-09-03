package com.miaoyu.barc.api.work.controller;

import com.miaoyu.barc.api.work.enumeration.WorkStatusEnum;
import com.miaoyu.barc.api.work.enumeration.WorkReviewStatusEnum;
import com.miaoyu.barc.api.work.model.WorkModel;
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
    public ResponseEntity<J> reviewWork(HttpServletRequest r, @RequestBody Map<String, Object> body) {
        return workManageService.reviewWork(
                uuid(r),
                body.get("work_id").toString(),
                Boolean.parseBoolean(body.get("approved").toString()),
                body.get("reason") != null ? body.get("reason").toString() : null);
    }

    /** 获取作品编辑详情（含封面/内容图签名URL、作者/收录者信息、学园/部团/学生名） */
    @GetMapping("/edit-detail")
    public ResponseEntity<J> getWorkEditDetail(HttpServletRequest r, @RequestParam("work_id") String workId) {
        return workManageService.getWorkEditDetail(uuid(r), workId);
    }

    // ==================== 作品状态管理 ====================
    @PutMapping("/status")
    public ResponseEntity<J> updateWorkStatusManage(HttpServletRequest r, @RequestBody Map<String, Object> body) {
        return workManageService.updateWorkStatus(uuid(r),
                body.get("work_id").toString(),
                WorkStatusEnum.valueOf(body.get("status").toString()),
                body.get("remark") != null ? body.get("remark").toString() : null);
    }

    // ==================== 作品内容修改 ====================
    @PutMapping("/update")
    public ResponseEntity<J> updateWorkContentManage(HttpServletRequest r, @RequestBody WorkModel workModel) {
        return workManageService.updateWorkContent(uuid(r), workModel);
    }

    // ==================== 认领管理 ====================
    @GetMapping("/claims")
    public ResponseEntity<J> getClaimsManage(HttpServletRequest r) {
        return workManageService.getClaimsList(uuid(r));
    }

    @PostMapping("/claim/approve")
    public ResponseEntity<J> approveClaimManage(HttpServletRequest r, @RequestBody Map<String, Object> body) {
        return workManageService.approveClaim(uuid(r), body.get("claim_id").toString(), Boolean.parseBoolean(body.get("approved").toString()));
    }

    @PostMapping("/claim/revoke")
    public ResponseEntity<J> revokeClaimManage(HttpServletRequest r, @RequestBody Map<String, String> body) {
        return workManageService.revokeClaim(uuid(r), body.get("work_id"));
    }

    @PostMapping("/claim/assign")
    public ResponseEntity<J> assignAuthorManage(HttpServletRequest r, @RequestBody Map<String, String> body) {
        return workManageService.assignAuthor(uuid(r), body.get("work_id"), body.get("author_uuid"));
    }

    @GetMapping("/claim/history")
    public ResponseEntity<J> getClaimHistoryManage(HttpServletRequest r, @RequestParam("work_id") String workId) {
        return workManageService.getClaimHistory(uuid(r), workId);
    }

    // ==================== 投诉处理 ====================
    @GetMapping("/complaints")
    public ResponseEntity<J> getComplaintsManage(HttpServletRequest r) {
        return workManageService.getComplaintsList(uuid(r));
    }

    @PostMapping("/complaint/process")
    public ResponseEntity<J> processComplaintManage(HttpServletRequest r, @RequestBody Map<String, Object> body) {
        return workManageService.processComplaint(uuid(r),
                body.get("complaint_id").toString(),
                body.get("action").toString(),
                body.get("remark") != null ? body.get("remark").toString() : null);
    }

    // ==================== 操作日志 ====================
    @PostMapping("/logs")
    public ResponseEntity<J> getOperationLogsManage(HttpServletRequest r, @RequestBody PageRequestDto dto) {
        return workManageService.getOperationLogs(uuid(r), dto);
    }

    // ==================== 辅助搜索 ====================

    /** 搜索作品（按ID/标题），供指派作者等下拉选择 */
    @GetMapping("/search-work")
    public ResponseEntity<J> searchWork(@RequestParam("keyword") String keyword) {
        List<WorkModel> works = workManageService.searchWorks(keyword);
        return ResponseEntity.ok(new ResourceR().resourceSuch(true, works));
    }

    /** 搜索用户（按UUID/用户名/昵称），供指派作者等下拉选择 */
    @GetMapping("/search-user")
    public ResponseEntity<J> searchUser(@RequestParam("keyword") String keyword) {
        List<UserInfoVo> users = workManageService.searchUsers(keyword);
        return ResponseEntity.ok(new ResourceR().resourceSuch(true, users));
    }
}
