package com.miaoyu.barc.api.work.service;

import com.miaoyu.barc.api.work.enumeration.WorkStatusEnum;
import com.miaoyu.barc.api.work.mapper.WorkMapper;
import com.miaoyu.barc.api.work.mapper.WorkOperationLogMapper;
import com.miaoyu.barc.api.work.model.WorkModel;
import com.miaoyu.barc.api.work.model.WorkOperationLogModel;
import com.miaoyu.barc.permission.PermissionConst;
import com.miaoyu.barc.permission.PermissionPolicy;
import com.miaoyu.barc.response.ChangeR;
import com.miaoyu.barc.response.ResourceR;
import com.miaoyu.barc.response.UserR;
import com.miaoyu.barc.user.enumeration.UserIdentityEnum;
import com.miaoyu.barc.user.mapper.UserArchiveMapper;
import com.miaoyu.barc.user.model.UserArchiveModel;
import com.miaoyu.barc.utils.GenerateUUID;
import com.miaoyu.barc.utils.J;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 作者与兼容删除入口统一执行软删除，保留审核、评论和文件以支持管理端恢复。 */
@Service
public class WorkDeletionService {
    @Autowired private WorkMapper workMapper;
    @Autowired private UserArchiveMapper userArchiveMapper;
    @Autowired private WorkOperationLogMapper operationLogMapper;

    @Transactional
    public ResponseEntity<J> delete(String operatorUuid, String workId) {
        WorkModel work = workMapper.selectByIdForUpdate(workId);
        if (work == null) return ResponseEntity.ok(new ResourceR().resourceSuch(false, null));
        UserArchiveModel operator = userArchiveMapper.selectByUuid(operatorUuid);
        boolean owner = operatorUuid != null && (operatorUuid.equals(work.getAuthor()) || operatorUuid.equals(work.getUploader()));
        if (operator == null || (!owner && !PermissionPolicy.matches(operator, UserIdentityEnum.MANAGER, PermissionConst.SEC_MAINTAINER, true))) {
            return ResponseEntity.ok(new UserR().insufficientAccountPermission());
        }
        if (work.getStatus() == WorkStatusEnum.DELETED) return ResponseEntity.ok(new ChangeR().udu(true, 2));
        if (!workMapper.softDelete(workId)) return ResponseEntity.ok(new ChangeR().udu(false, 2));
        WorkOperationLogModel operation = new WorkOperationLogModel();
        operation.setId(new GenerateUUID().getUuid36l());
        operation.setWork_id(workId);
        operation.setOperator_uuid(operatorUuid);
        operation.setOperation_type(WorkManageService.OP_DELETE);
        operation.setDetail("{\"remark\":\"兼容入口授权软删除\"}");
        // 审计失败必须回滚删除，不能返回成功但没有可追踪记录。
        if (operationLogMapper.insert(operation) != 1) throw com.miaoyu.barc.utils.RequestInput.invalid("删除审计保存失败");
        return ResponseEntity.ok(new ChangeR().udu(true, 2));
    }
}
