package com.miaoyu.barc.api.work.controller;

import com.miaoyu.barc.annotation.ApiPath;
import com.miaoyu.barc.annotation.SuchWorkClaimAnno;
import com.miaoyu.barc.api.work.model.WorkClaimModel;
import com.miaoyu.barc.api.work.service.WorkClaimService;
import com.miaoyu.barc.utils.J;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/work/claim")
public class WorkClaimController {
    @Autowired
    private WorkClaimService workClaimService;

    // 说明（2026-10 清理）：原 GET /all_claims 与 GET /claims_by_work 已删除。
    // 它们分别与 /api/work/manage/claims、/api/work/manage/claim/history 调同一个 Mapper 方法
    // （workClaimMapper.selectAll / selectByWorkId），属重复入口。
    // 认领查看统一走管理端两条，其权限位已放宽为「二级管理员位 或 风纪委员位」，风纪委员入口不受影响。
    // 下方 /claim_check 与 /upload 是认领的审核与提交入口，按业务要求保留。

    @PostMapping("/claim_check")
    @SuchWorkClaimAnno(index = 1)
    public ResponseEntity<J> checkWorkClaimControl(HttpServletRequest request, @RequestParam("work_claim_id") String workClaimId) {
        return workClaimService.checkWorkClaimService(request.getAttribute("uuid").toString(), workClaimId);
    }

    /** 当前用户自己的申请记录，身份只取认证上下文。 */
    @GetMapping("/mine")
    public ResponseEntity<J> mine(HttpServletRequest request,
            @RequestParam(value = "work_id", required = false) String workId,
            @RequestParam(value = "page", defaultValue = "1") Integer page,
            @RequestParam(value = "size", defaultValue = "20") Integer size) {
        return workClaimService.getMyClaims(request.getAttribute("uuid").toString(), workId, page, size);
    }

    /** 客户端只能提供作品及说明，申请人/状态/处理信息由服务端产生。 */
    public record ClaimRequest(String work_id, String statement) { }

    @PostMapping("/upload")
    public ResponseEntity<J> uploadWorkClaimControl(HttpServletRequest request, @RequestBody ClaimRequest body) {
        WorkClaimModel model = new WorkClaimModel(); model.setWork_id(body.work_id()); model.setStatement(body.statement());
        return workClaimService.uploadWorkClaimService(request.getAttribute("uuid").toString(), model);
    }
}
