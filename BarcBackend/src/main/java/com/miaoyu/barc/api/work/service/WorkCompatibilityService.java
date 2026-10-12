package com.miaoyu.barc.api.work.service;

import com.miaoyu.barc.api.work.model.WorkModel;
import com.miaoyu.barc.permission.PermissionConst;
import com.miaoyu.barc.permission.PermissionPolicy;
import com.miaoyu.barc.user.enumeration.UserIdentityEnum;
import com.miaoyu.barc.user.mapper.UserArchiveMapper;
import com.miaoyu.barc.utils.J;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

/** 旧通用更新仅保留兼容分流，不再全字段保存；提审和审计复用正式业务入口。 */
@Service
public class WorkCompatibilityService {
    @Autowired private WorkService workService;
    @Autowired private WorkManageService workManageService;
    @Autowired private UserArchiveMapper userArchiveMapper;

    public ResponseEntity<J> update(String uuid, WorkModel request) {
        if (PermissionPolicy.matches(userArchiveMapper.selectByUuid(uuid), UserIdentityEnum.MANAGER, PermissionConst.SEC_MAINTAINER, true)) {
            return workManageService.updateWorkContent(uuid, request);
        }
        return workService.updateOwnerWorkContent(uuid, request);
    }
}
