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

    @PostMapping("/upload")
    public ResponseEntity<J> uploadWorkClaimControl(
            HttpServletRequest request,
            @RequestBody WorkClaimModel model
    ) {
        return workClaimService.uploadWorkClaimService(request.getAttribute("uuid").toString(), model);
    }
}
