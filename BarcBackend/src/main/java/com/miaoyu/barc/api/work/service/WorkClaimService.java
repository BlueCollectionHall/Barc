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

    public ResponseEntity<J> getAllClaimsControl(String uuid) {
        UserArchiveModel userArchive = userArchiveMapper.selectByUuid(uuid);
        if (userArchive == null) {
            return ResponseEntity.ok(new UserR().noSuchUser());
        }
        if (!(userArchive.getIdentity() == UserIdentityEnum.MANAGER) && !(userArchive.getPermission() == PermissionConst.DISCIPLINARY_COMMITTEE)) {
            return ResponseEntity.ok(new UserR().insufficientAccountPermission());
        }
        return ResponseEntity.ok(new ResourceR().resourceSuch(true, workClaimMapper.selectAll()));
    }

    public ResponseEntity<J> getClaimsByWorkControl(String uuid, String workId) {
        UserArchiveModel userArchive = userArchiveMapper.selectByUuid(uuid);
        if (userArchive == null) {
            return ResponseEntity.ok(new UserR().noSuchUser());
        }
        if (!(userArchive.getIdentity() == UserIdentityEnum.MANAGER) && !(userArchive.getPermission() == PermissionConst.DISCIPLINARY_COMMITTEE)) {
            return ResponseEntity.ok(new UserR().insufficientAccountPermission());
        }
        return ResponseEntity.ok(new ResourceR().resourceSuch(true, workClaimMapper.selectByWorkId(workId)));
    }

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
