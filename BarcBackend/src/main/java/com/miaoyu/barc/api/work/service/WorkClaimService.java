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

    /**
     * 获取全部作品认领申请。
     *
     * <p>安全修复（2026-10）：原实现是手写判断
     * {@code if (!(identity == MANAGER) && !(permission == DISCIPLINARY_COMMITTEE))}。
     * 每边各带一个否定再用 {@code &&} 连接，语义退化成「或」——只要 permission 命中就放行，
     * 与 identity 毫无关系。而 {@code PermissionConst.USER} 与
     * {@code PermissionConst.DISCIPLINARY_COMMITTEE} 同为 1，注册时
     * {@code INSERT INTO user_archive (uuid, nickname, avatar)} 并不写 permission、
     * 表默认值又正是 1，于是**任意普通会员**都能读到全部认领申请（含申请人 uuid）。</p>
     *
     * <p>现改回项目统一下发的注解鉴权：身份必须是 MANAGER，且权限位命中风纪委员（1匹配），
     * 与同类的 {@link #checkWorkClaimService(String, String)} 保持同一范式。</p>
     *
     * <p>⚠️ {@code uuid} 形参在方法体内已不再使用，但**绝不能删**：
     * AOP 切面按 {@code uuidIndex}（默认 0）从第 1 个形参取操作者 uuid，
     * 删掉或调换形参顺序会让鉴权静默失效（见 RequireUserAndPermissionAspect）。</p>
     */
    @RequireUserAndPermissionAnno({@RequireUserAndPermissionAnno.Check(
            isHasElseUpper = true,
            isSuchElseRequire = false,
            identity = UserIdentityEnum.MANAGER,
            targetPermission = PermissionConst.DISCIPLINARY_COMMITTEE)})
    public ResponseEntity<J> getAllClaimsControl(String uuid) {
        return ResponseEntity.ok(new ResourceR().resourceSuch(true, workClaimMapper.selectAll()));
    }

    /**
     * 按作品查询认领申请。
     *
     * <p>与 {@link #getAllClaimsControl(String)} 属同一处安全修复：原手写判断的
     * {@code &&} 写法同样会放过任意普通会员，现统一交给注解鉴权（MANAGER + 风纪委员位）。</p>
     *
     * <p>⚠️ {@code uuid} 必须是第 1 个形参，原因同上。</p>
     */
    @RequireUserAndPermissionAnno({@RequireUserAndPermissionAnno.Check(
            isHasElseUpper = true,
            isSuchElseRequire = false,
            identity = UserIdentityEnum.MANAGER,
            targetPermission = PermissionConst.DISCIPLINARY_COMMITTEE)})
    public ResponseEntity<J> getClaimsByWorkControl(String uuid, String workId) {
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
