package com.miaoyu.barc.api.work.service;

import com.miaoyu.barc.annotation.RequireUserAndPermissionAnno;
import com.miaoyu.barc.api.work.mapper.WorkClaimMapper;
import com.miaoyu.barc.api.work.mapper.WorkMapper;
import com.miaoyu.barc.api.work.model.WorkClaimModel;
import com.miaoyu.barc.api.work.model.WorkModel;
import com.miaoyu.barc.email.utils.SendEmailUtils;
import com.miaoyu.barc.permission.PermissionConst;
import com.miaoyu.barc.response.ChangeR;
import com.miaoyu.barc.response.ErrorR;
import com.miaoyu.barc.response.ResourceR;
import com.miaoyu.barc.response.UserR;
import com.miaoyu.barc.user.enumeration.UserIdentityEnum;
import com.miaoyu.barc.user.mapper.UserArchiveMapper;
import com.miaoyu.barc.user.mapper.UserBasicMapper;
import com.miaoyu.barc.user.model.UserArchiveModel;
import com.miaoyu.barc.utils.GenerateUUID;
import com.miaoyu.barc.utils.J;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class WorkClaimService {
    @Autowired
    private UserArchiveMapper userArchiveMapper;
    @Autowired
    private WorkClaimMapper workClaimMapper;

    // 说明（2026-10 清理）：原 getAllClaimsControl(uuid) 与 getClaimsByWorkControl(uuid, workId) 已删除。
    // 二者分别与 WorkManageService.getClaimsList / getClaimHistory 调同一个 Mapper 方法
    // （workClaimMapper.selectAll / selectByWorkId），属重复入口；认领查看统一走管理端两条，
    // 其权限位已放宽为「二级管理员位 或 风纪委员位」，风纪委员入口不受影响。
    //
    // 这两条接口此前还存在越权缺陷（手写判断用 && 连接两个否定，且 USER 与 DISCIPLINARY_COMMITTEE 同为 1，
    // 导致任意会员可读全部认领申请），现已随方法一并移除，无需再修。
    //
    // 保留：下方 getClaimsByUuidControl（按申请人查自己的认领记录，暂无 Controller 入口，属待启用能力）、
    // checkWorkClaimService（认领审核）、uploadWorkClaimService（认领提交）。

    public ResponseEntity<J> getClaimsByUuidControl(String uuid) {
        UserArchiveModel userArchive = userArchiveMapper.selectByUuid(uuid);
        if (userArchive == null) {
            return ResponseEntity.ok(new UserR().noSuchUser());
        }
        return ResponseEntity.ok(new ResourceR().resourceSuch(true, workClaimMapper.selectByUuid(uuid)));
    }

    @Autowired
    private WorkMapper workMapper;
    @Autowired
    private SendEmailUtils sendEmailUtils;
    @Autowired
    private UserBasicMapper userBasicMapper;

    @RequireUserAndPermissionAnno({@RequireUserAndPermissionAnno.Check(isHasElseUpper = true, isSuchElseRequire = false, identity = UserIdentityEnum.MANAGER, targetPermission = PermissionConst.DISCIPLINARY_COMMITTEE)})
    public ResponseEntity<J> checkWorkClaimService(String uuid, String workClaimId) {
        WorkClaimModel workClaim = workClaimMapper.selectById(workClaimId);
        WorkModel work = workMapper.selectById(workClaim.getWork_id());
        if (work.getIs_claim()) {
            return ResponseEntity.ok(new ErrorR().normal("作品已经被认领！若有疑问，请反馈投诉该作品！"));
        }
        work.setIs_claim(true);
        work.setAuthor(workClaim.getApplicant_uuid());
        try {
            workMapper.update(work);
            workClaimMapper.deleteByWorkId(workClaim.getWork_id());
        } catch (Exception e) {
            return ResponseEntity.ok(new ChangeR().udu(false, 3));
        }
        return ResponseEntity.ok(new ChangeR().udu(true, 3));
    }

    @RequireUserAndPermissionAnno({@RequireUserAndPermissionAnno.Check()})
    public ResponseEntity<J> uploadWorkClaimService(String uuid, WorkClaimModel model) {
        if (model.getWork_id() != null && !model.getWork_id().isEmpty()) {
            model.setApplicant_uuid(uuid);
            model.setId(new GenerateUUID().getUuid36l());
            try {
                workClaimMapper.insert(model);
                return ResponseEntity.ok(new ChangeR().udu(true, 1));
            } catch (Exception e) {
                log.error(e.getMessage());
                return ResponseEntity.ok(new ChangeR().udu(false, 1));
            }
        } else {
            return ResponseEntity.ok(new ChangeR().udu(false, 1));
        }
    }
}
