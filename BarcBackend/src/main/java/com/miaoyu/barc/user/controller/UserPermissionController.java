package com.miaoyu.barc.user.controller;

import com.miaoyu.barc.annotation.IgnoreAuth;
import com.miaoyu.barc.response.ResourceR;
import com.miaoyu.barc.user.enumeration.UserIdentityEnum;
import com.miaoyu.barc.user.service.UserPermissionService;
import com.miaoyu.barc.utils.J;
import jakarta.servlet.http.HttpServletRequest;
import lombok.Getter;
import lombok.Setter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/user/permission")
public class UserPermissionController {
    @Autowired
    private UserPermissionService userPermissionService;

    @GetMapping("/all_identities")
    @IgnoreAuth
    public ResponseEntity<J> getAllUserIdentitiesControl() {
        return ResponseEntity.ok(new ResourceR().resourceSuch(true, UserIdentityEnum.getOptions()));
    }

    @GetMapping("/permissions_by_identity")
    @IgnoreAuth
    public ResponseEntity<J> getUserPermissionsByIdentityControl(@RequestParam("identity") UserIdentityEnum identity) {
        return userPermissionService.getUserPermissionsByIdentityService(identity);
    }

    @GetMapping("/permission_cn")
    @IgnoreAuth
    public ResponseEntity<J> getUserPermissionCnControl(
            @RequestParam("identity") UserIdentityEnum identity,
            @RequestParam("permission") Integer permission
    ) {
        return userPermissionService.getUserPermissionCnService(identity, permission);
    }

    // 说明（2026-10 清理）：原 GET /user/permission/permission_near_max?uuid= 已删除
    // —— 它与下面的 me_permission_near_max 调同一个 getUserPermissionNearMaxService，
    // 且它是 @IgnoreAuth，任何人可查任意 uuid 的权限文案；调用方一律用 token 版本，故移除公开变体。
    @GetMapping("/me_permission_near_max")
    public ResponseEntity<J> getMePermissionNearMaxControl(HttpServletRequest request) {
        return userPermissionService.getUserPermissionNearMaxService(request.getAttribute("uuid").toString());
    }

    @Getter
    @Setter
    public static class UserWithPermission{
        private String uuid;
        private Integer permission;
        private UserIdentityEnum identity;
    }
    @PostMapping("/change_permission")
    public ResponseEntity<J> changeUserPermissionControl(
            HttpServletRequest request,
            @RequestBody UserWithPermission model
    ) {
        return userPermissionService.changeUserPermissionService(
                request.getAttribute("uuid").toString(),
                model.getUuid(), model.getIdentity(), model.getPermission()
        );
    }
}
