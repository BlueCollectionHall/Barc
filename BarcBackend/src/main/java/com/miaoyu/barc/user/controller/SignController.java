package com.miaoyu.barc.user.controller;

import com.miaoyu.barc.annotation.IgnoreAuth;
import com.miaoyu.barc.annotation.UserPath;
import com.miaoyu.barc.user.model.UserBasicModel;
import com.miaoyu.barc.user.service.SignService;
import com.miaoyu.barc.utils.J;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/user/sign")
@IgnoreAuth
public class SignController {
    @Autowired
    private SignService signService;

    /**登录
     * @param type 登录方式 "username"/"email"
     * @param account 账号
     * @param password 明文密码*/
    @GetMapping("/in")
    public ResponseEntity<J> signInUserControl(
            @RequestParam("type") String type,
            @RequestParam("account") String account,
            @RequestParam("password") String password
    ) {
        return signService.signInUserService(type, account, password);
    }
    /**通过Naigos登录
     * @param type 登录方式 "uid"/"email"
     * @param account 账号
     * @param password 明文密码*/
    @GetMapping("/in_by_naigos")
    public ResponseEntity<J> signInUserByNaigosControl(
            @RequestParam("type") String type,
            @RequestParam("account") String account,
            @RequestParam("password") String password
    ) {
        return signService.signInByNaigosService(type, account, password);
    }
    /**注册
     * @param email 电子邮箱
     * @param uniqueId 邮箱验证唯一id
     * @param code 邮箱验证码
     * @param userBasic 用户基础信息实体*/
    @PostMapping("/up")
    public ResponseEntity<J> signUpUserControl(
            @RequestParam("email") String email,
            @RequestParam("unique_id") String uniqueId,
            @RequestParam("code") String code,
            @RequestBody UserBasicModel userBasic
    ) {
        J j = signService.checkSignupCode(uniqueId, code, email);
        if (j.getCode() == 1) {
            return ResponseEntity.ok(j);
        }
        return signService.signupUserService(userBasic, email);
    }
}
