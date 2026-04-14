package com.miaoyu.barc.user.controller;

import com.miaoyu.barc.annotation.IgnoreAuth;
import com.miaoyu.barc.annotation.UserPath;
import com.miaoyu.barc.response.SuccessR;
import com.miaoyu.barc.user.model.UserArchiveModel;
import com.miaoyu.barc.user.model.UserBasicModel;
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
    public ResponseEntity<J> editArchiveControl(HttpServletRequest request, @RequestBody UserArchiveModel requestModel) {
        return userService.editArchiveService(request.getAttribute("uuid").toString(), requestModel);
    }
    /**更新个人基础信息
     * @param requestModel 用户个人基础信息实体
     * @return 更新是否成功*/
    @PostMapping("/edit_basic")
    public ResponseEntity<J> editBasicControl(HttpServletRequest request, @RequestBody UserBasicModel requestModel) {
        return userService.editBasicService(request.getAttribute("uuid").toString(), requestModel);
    }
    /**确定重置密码
     * @param email 电子邮箱
     * @param uniqueId 验证码唯一ID
     * @param code 验证码
     * @param password 新密码的明文
     * @return 重置是否成功*/
    @GetMapping("/reset_password")
    public ResponseEntity<J> resetPasswordControl(
            HttpServletRequest request,
            @RequestParam("email") String email,
            @RequestParam("unique_id") String uniqueId,
            @RequestParam("code") String code,
            @RequestParam("password") String password
    ) {
        return userService.resetPasswordService(request.getAttribute("uuid").toString(), uniqueId, code, email, password);
    }

}
