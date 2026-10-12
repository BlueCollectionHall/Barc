package com.miaoyu.barc.user.controller;

import com.miaoyu.barc.annotation.IgnoreAuth;
import com.miaoyu.barc.annotation.UserPath;
import com.miaoyu.barc.response.SuccessR;
import com.miaoyu.barc.user.model.UserArchiveModel;
import com.miaoyu.barc.user.model.UserBasicModel;
import com.miaoyu.barc.user.model.dto.UserProfileUpdateRequest;
import com.miaoyu.barc.user.model.dto.UserBasicUpdateRequest;
import com.miaoyu.barc.user.model.dto.PasswordResetRequest;
import com.miaoyu.barc.user.model.dto.EmailChangeRequest;
import com.miaoyu.barc.user.service.AccountSecurityService;
import com.miaoyu.barc.user.service.UserService;
import com.miaoyu.barc.utils.J;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * 用户信息查询（单个）
 * 用户信息修改
 * */

@RestController
@RequestMapping("/user")
public class UserController {
    @Autowired
    private UserService userService;
    @Autowired private AccountSecurityService accountSecurityService;

    /**通过UUID获取账号档案的信息
     * @param uuid UUID
     * @return 用户的档案信息*/
    @IgnoreAuth
    @GetMapping("/current_by_uuid")
    public ResponseEntity<J> getCurrentByUuidControl(@RequestParam("uuid") String uuid) {
        return userService.getCurrentByUuidService(uuid);
    }
    /**通过用户名获取账号档案的信息
     * @param username UUID
     * @return 用户的档案信息*/
    @IgnoreAuth
    @GetMapping("/current_by_username")
    public ResponseEntity<J> getCurrentByUsernameControl(@RequestParam("username") String username) {
        return userService.getCurrentByUsernameService(username);
    }

    /**通过请求头中Token令牌获取本账号个人信息
     * @return 本账号个人信息*/
    @GetMapping("/current_me")
    public ResponseEntity<J> getMeCurrentControl(HttpServletRequest request) {
        return userService.getMeCurrentService(request.getAttribute("uuid").toString());
    }
    /**通过请求头中Token令牌获取本账号个人基础信息
     * @return 本账号个人基础信息*/
    @GetMapping("/basic_me")
    public ResponseEntity<J> getMeBasicControl(HttpServletRequest request) {
        return userService.getMeBasicService(request.getAttribute("uuid").toString());
    }
    /**更新个人信息
     * @param requestModel 用户个人信息实体
     * @return 更新是否成功*/
    @PostMapping("/edit_archive")
    public ResponseEntity<J> editArchiveControl(HttpServletRequest request, @RequestBody UserProfileUpdateRequest requestModel) {
        return userService.editArchiveService(request.getAttribute("uuid").toString(), requestModel);
    }
    /**更新个人基础信息
     * @param requestModel 用户个人基础信息实体
     * @return 更新是否成功*/
    @PostMapping("/edit_basic")
    public ResponseEntity<J> editBasicControl(HttpServletRequest request, @RequestBody UserBasicUpdateRequest requestModel) {
        return accountSecurityService.updateBasic(request.getAttribute("uuid").toString(), requestModel);
    }
    /** 改密只从 JSON 读取凭据，成功后旧令牌立即失效。 */
    @PostMapping("/reset_password")
    public ResponseEntity<J> resetPasswordControl(HttpServletRequest request, @RequestBody PasswordResetRequest body) {
        return accountSecurityService.resetPassword(request.getAttribute("uuid").toString(), body.unique_id(), body.code(), body.email(), body.password());
    }

    /** 新邮箱验证通过后改绑，并要求重新登录以清理旧会话。 */
    @PostMapping("/change_email")
    public ResponseEntity<J> changeEmailControl(HttpServletRequest request, @RequestBody EmailChangeRequest body) {
        return accountSecurityService.changeEmail(request.getAttribute("uuid").toString(), body);
    }
}
