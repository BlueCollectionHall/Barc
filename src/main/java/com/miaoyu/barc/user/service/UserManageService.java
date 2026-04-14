package com.miaoyu.barc.user.service;

import com.miaoyu.barc.annotation.RequireUserAndPermissionAnno;
import com.miaoyu.barc.permission.PermissionConst;
import com.miaoyu.barc.user.enumeration.UserIdentityEnum;
import com.miaoyu.barc.user.model.UserBasicModel;
import com.miaoyu.barc.utils.J;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.util.Objects;

@Service
public class UserManageService {
    @Autowired
    private UserService userService;
    @Autowired
    private SignService signService;

    @RequireUserAndPermissionAnno({@RequireUserAndPermissionAnno.Check(identity = UserIdentityEnum.MANAGER, targetPermission = PermissionConst.THI_MAINTAINER, isSuchElseRequire = false, isHasElseUpper = true)})
    public ResponseEntity<J> uploadNewUserService(String uuid, UserBasicModel model) {
        return signService.signupUserService(model, model.getEmail());
    }
}
