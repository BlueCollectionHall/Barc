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
    /**
     * 获取全部作品认领信息
     * @return ListL类型中包含全部作品认领信息*/
    @GetMapping("/all_claims")
    public ResponseEntity<J> getAllWorkClaimsControl(HttpServletRequest request) {
        return workClaimService.getAllClaimsControl(request.getAttribute("uuid").toString());
    }
    @GetMapping("/claims_by_work")
    public ResponseEntity<J> getClaimsByWorkControl(HttpServletRequest request, @RequestParam("work_id") String workId) {
        return workClaimService.getClaimsByWorkControl(request.getAttribute("uuid").toString(), workId);
    }

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
