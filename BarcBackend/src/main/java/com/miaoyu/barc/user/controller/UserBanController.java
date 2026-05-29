package com.miaoyu.barc.user.controller;

import com.miaoyu.barc.annotation.RequireUserAndPermissionAnno;
import com.miaoyu.barc.permission.PermissionConst;
import com.miaoyu.barc.user.enumeration.UserIdentityEnum;
import com.miaoyu.barc.user.service.UserBanService;
import com.miaoyu.barc.utils.J;
import jakarta.servlet.http.HttpServletRequest;
import lombok.Getter;
import lombok.Setter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * 用户封号控制器
 * 所有接口需要副馆长(ADMINISTRATOR=16)及以上权限
 */
@RestController
@RequestMapping("/user/ban")
public class UserBanController {

    @Autowired
    private UserBanService userBanService;

    @Getter
    @Setter
    public static class BanRequest {
        private String userId;
        private Integer banType;
        private String reason;
        private Integer durationDays;
    }

    /**
     * 封号用户
     *
     * @param request    HTTP请求（含操作人uuid）
     * @param banRequest 封号请求体
     * @return 封号结果
     */
    @RequireUserAndPermissionAnno({
            @RequireUserAndPermissionAnno.Check(
                    identity = UserIdentityEnum.MANAGER,
                    targetPermission = PermissionConst.ADMINISTRATOR,
                    isSuchElseRequire = false,
                    isHasElseUpper = false
            )
    })
    @PostMapping
    public ResponseEntity<J> banUserControl(
            HttpServletRequest request,
            @RequestBody BanRequest banRequest
    ) {
        String operatorId = request.getAttribute("uuid").toString();
        return userBanService.banUser(
                operatorId, 1, banRequest.getUserId(),
                banRequest.getBanType(), banRequest.getReason(), banRequest.getDurationDays()
        );
    }

    /**
     * 解封用户
     *
     * @param request    HTTP请求（含操作人uuid）
     * @param banRequest 解封请求体（userId, reason）
     * @return 解封结果
     */
    @RequireUserAndPermissionAnno({
            @RequireUserAndPermissionAnno.Check(
                    identity = UserIdentityEnum.MANAGER,
                    targetPermission = PermissionConst.ADMINISTRATOR,
                    isSuchElseRequire = false,
                    isHasElseUpper = false
            )
    })
    @PostMapping("/unban")
    public ResponseEntity<J> unbanUserControl(
            HttpServletRequest request,
            @RequestBody BanRequest banRequest
    ) {
        String operatorId = request.getAttribute("uuid").toString();
        return userBanService.unbanUser(operatorId, banRequest.getUserId(), banRequest.getReason());
    }

    /**
     * 查询封号历史
     *
     * @param request HTTP请求（含操作人uuid）
     * @param userId  被查询用户ID
     * @param page    页码
     * @param size    每页大小
     * @return 封号历史列表
     */
    @RequireUserAndPermissionAnno({
            @RequireUserAndPermissionAnno.Check(
                    identity = UserIdentityEnum.MANAGER,
                    targetPermission = PermissionConst.ADMINISTRATOR,
                    isSuchElseRequire = false,
                    isHasElseUpper = false
            )
    })
    @GetMapping("/history")
    public ResponseEntity<J> getBanHistoryControl(
            HttpServletRequest request,
            @RequestParam("user_id") String userId,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "10") int size
    ) {
        return userBanService.getBanHistory(userId, page, size);
    }

    /**
     * 查询用户状态
     *
     * @param request HTTP请求（含操作人uuid）
     * @param userId  被查询用户ID
     * @return 用户状态信息
     */
    @RequireUserAndPermissionAnno({
            @RequireUserAndPermissionAnno.Check(
                    identity = UserIdentityEnum.MANAGER,
                    targetPermission = PermissionConst.ADMINISTRATOR,
                    isSuchElseRequire = false,
                    isHasElseUpper = false
            )
    })
    @GetMapping("/status")
    public ResponseEntity<J> getUserStatusControl(
            HttpServletRequest request,
            @RequestParam("user_id") String userId
    ) {
        return userBanService.getUserStatus(userId);
    }
}
