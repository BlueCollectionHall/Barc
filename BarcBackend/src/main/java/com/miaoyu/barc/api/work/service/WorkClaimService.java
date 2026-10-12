package com.miaoyu.barc.api.work.service;

import com.miaoyu.barc.annotation.RequireUserAndPermissionAnno;
import com.miaoyu.barc.api.work.enumeration.*;
import com.miaoyu.barc.api.work.mapper.*;
import com.miaoyu.barc.api.work.model.*;
import com.miaoyu.barc.permission.PermissionConst;
import com.miaoyu.barc.response.*;
import com.miaoyu.barc.user.enumeration.UserIdentityEnum;
import com.miaoyu.barc.utils.*;
import com.miaoyu.barc.utils.dto.PageResultDto;
import com.miaoyu.barc.utils.pojo.PageInitPojo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;

/** 认领提交与本人进度；旧审核入口委托管理端的同一事务命令。 */
@Service
public class WorkClaimService {
    // 等待业务行锁后读取最新提交数据，避免鉴权读在可重复读隔离下固定旧快照。
    @Autowired private WorkClaimMapper workClaimMapper;
    @Autowired private WorkMapper workMapper;
    @Autowired private WorkManageService workManageService;

    @RequireUserAndPermissionAnno({@RequireUserAndPermissionAnno.Check()})
    public ResponseEntity<J> getClaimsByUuidControl(String uuid) { return getMyClaims(uuid, null, 1, 100); }

    @RequireUserAndPermissionAnno({@RequireUserAndPermissionAnno.Check()})
    public ResponseEntity<J> getMyClaims(String uuid, String workId, Integer page, Integer size) {
        PageInitPojo p = new PageInitPojo(page, size);
        long total = workClaimMapper.countPage(null, workId, uuid);
        return ResponseEntity.ok(new ResourceR().resourceSuch(true, new PageResultDto<>(total,
            workClaimMapper.selectPage(null, workId, uuid, p.getOffset(), p.getPageSize()),
            p.getPageNum(), p.getPageSize(), Math.max(1, (int) Math.ceil((double) total / p.getPageSize())))));
    }

    @RequireUserAndPermissionAnno({@RequireUserAndPermissionAnno.Check(isHasElseUpper = true, isSuchElseRequire = false,
        identity = UserIdentityEnum.MANAGER, targetPermission = PermissionConst.DISCIPLINARY_COMMITTEE | PermissionConst.SEC_MAINTAINER)})
    public ResponseEntity<J> checkWorkClaimService(String uuid, String workClaimId) {
        return workManageService.approveClaim(uuid, workClaimId, true);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    @RequireUserAndPermissionAnno({@RequireUserAndPermissionAnno.Check()})
    public ResponseEntity<J> uploadWorkClaimService(String uuid, WorkClaimModel request) {
        String workId = RequestInput.required(request.getWork_id(), "作品ID", 100);
        RequestInput.optional(request.getStatement(), "认领说明", 2000);
        // 先锁作品，再访问申请；审核/指派同样按这个顺序，避免并发重复认领。
        WorkModel work = workMapper.selectByIdForUpdate(workId);
        if (work == null || work.getStatus() != WorkStatusEnum.PUBLIC || work.getReview_status() != WorkReviewStatusEnum.APPROVED) {
            throw RequestInput.invalid("作品不存在或不可认领");
        }
        if (Boolean.TRUE.equals(work.getIs_claim())) throw RequestInput.invalid("作品已经被认领");
        WorkClaimModel existing = workClaimMapper.selectPending(workId, uuid);
        if (existing != null) return ResponseEntity.ok(new ResourceR().resourceSuch(true, existing));
        WorkClaimModel claim = new WorkClaimModel(); claim.setId(new GenerateUUID().getUuid36l());
        claim.setWork_id(workId); claim.setApplicant_uuid(uuid); claim.setStatement(request.getStatement());
        if (!workClaimMapper.insert(claim)) throw RequestInput.invalid("认领申请保存失败");
        return ResponseEntity.ok(new ResourceR().resourceSuch(true, claim));
    }
}
