package com.miaoyu.barc.user.service;

import com.miaoyu.barc.annotation.RequireUserAndPermissionAnno;
import com.miaoyu.barc.permission.PermissionConst;
import com.miaoyu.barc.permission.PermissionPolicy;
import com.miaoyu.barc.response.ErrorR;
import com.miaoyu.barc.response.ChangeR;
import com.miaoyu.barc.response.ResourceR;
import com.miaoyu.barc.response.UserR;
import com.miaoyu.barc.user.enumeration.UserIdentityEnum;
import com.miaoyu.barc.user.mapper.UserArchiveMapper;
import com.miaoyu.barc.user.mapper.UserBasicMapper;
import com.miaoyu.barc.user.mapper.UserPermissionMapper;
import com.miaoyu.barc.user.model.UserArchiveModel;
import com.miaoyu.barc.user.model.UserBasicModel;
import com.miaoyu.barc.utils.J;
import com.miaoyu.barc.utils.dto.ValueLabelDto;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Objects;

@Service
@Slf4j
public class UserPermissionService {
    @Autowired
    private PermissionConst permissionConst;
    @Autowired
    private UserArchiveMapper userArchiveMapper;
    @Autowired
    private UserPermissionMapper userPermissionMapper;
    @Autowired
    private com.miaoyu.barc.email.service.MailOutbox mailOutbox;
    @Autowired
    private UserBasicMapper userBasicMapper;

    public ResponseEntity<J> getUserPermissionsByIdentityService(UserIdentityEnum identity) {
        return ResponseEntity.ok(new ResourceR().resourceSuch(true, permissionConst.permissionsByIdentity(identity)));
    }

    public ResponseEntity<J> getUserPermissionNearMaxService(String uuid) {
        UserArchiveModel userArchive = userArchiveMapper.selectByUuid(uuid);
        if (userArchive == null) {
            return ResponseEntity.ok(new UserR().noSuchUser());
        }
        return ResponseEntity.ok(new ResourceR().resourceSuch(
                true,
                new ValueLabelDto(
                    Integer.highestOneBit(userArchive.getPermission()),
                    permissionConst.toString(
                            userArchive.getIdentity(), Integer.highestOneBit(userArchive.getPermission())
                    )
                )
            )
        );
    }

    public ResponseEntity<J> getUserPermissionCnService(UserIdentityEnum identity, Integer permission) {
        return ResponseEntity.ok(new ResourceR().resourceSuch(true, permissionConst.toString(identity, permission)));
    }

    @RequireUserAndPermissionAnno({
            @RequireUserAndPermissionAnno.Check(identity = UserIdentityEnum.MANAGER, targetPermission = PermissionConst.THI_MAINTAINER, isSuchElseRequire = false),
            @RequireUserAndPermissionAnno.Check(uuidIndex = 1)
    })
    @org.springframework.transaction.annotation.Transactional
    public ResponseEntity<J> changeUserPermissionService(
            String operatorUUID, String uuid, UserIdentityEnum identity, Integer permission
    ) {
        // 拒绝负数、空值和身份之外的权限位，避免位运算将 -1 当作全部角色。
        if (!PermissionPolicy.isValid(identity, permission)) {
            return ResponseEntity.ok(new ErrorR().normal("身份或权限值无效"));
        }
        UserArchiveModel opArchive = userArchiveMapper.selectByUuid(operatorUUID);
        if (!PermissionPolicy.matches(opArchive, UserIdentityEnum.MANAGER, PermissionConst.THI_MAINTAINER, false)
                || operatorUUID.equals(uuid)) {
            return ResponseEntity.ok(new UserR().insufficientAccountPermission());
        }
        UserArchiveModel userArchive = userArchiveMapper.selectByUuid(uuid);
        if (userArchive == null) return ResponseEntity.ok(new UserR().noSuchUser());
        // 授权级别看最高角色位，不能靠叠加低位越过同级账号保护。
        int operatorRank = Integer.highestOneBit(opArchive.getPermission());
        if ((identity == UserIdentityEnum.MANAGER && operatorRank <= Integer.highestOneBit(permission))
                || (userArchive.getIdentity() == UserIdentityEnum.MANAGER
                && (!PermissionPolicy.isValid(userArchive.getIdentity(), userArchive.getPermission())
                || operatorRank <= Integer.highestOneBit(userArchive.getPermission())))) {
            return ResponseEntity.ok(new UserR().insufficientAccountPermission());
        }
        UserIdentityEnum previousIdentity = userArchive.getIdentity();
        Integer previousPermission = userArchive.getPermission();
        userArchive.setPermission(permission);
        userArchive.setIdentity(identity);
        boolean update = userPermissionMapper.update(userArchive);
        if (update) {
            log.info("账号权限变更：operator={}, target={}, before={}/{}, after={}/{}", operatorUUID, uuid,
                    previousIdentity, previousPermission, identity, permission);
            UserBasicModel userBasic = userBasicMapper.selectByUuid(userArchive.getUuid());
            if (userBasic != null && userBasic.getEmail() != null) mailOutbox.enqueue(
                    "permission:" + java.util.UUID.randomUUID(),
                    userBasic.getEmail(),
                    "账号权限变更",
                    userArchive.getNickname() + "，您的账号已经变更为【" + identity.getDisplayName() + "：" + permissionConst.toString(identity, permission) + "】！"
            );
            return ResponseEntity.ok(new ChangeR().udu(true, 3));
        }
        return ResponseEntity.ok(new ChangeR().udu(false, 3));
    }
}
