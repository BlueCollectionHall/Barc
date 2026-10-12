package com.miaoyu.barc.user.controller;

import com.miaoyu.barc.annotation.IgnoreAuth;
import com.miaoyu.barc.user.model.dto.SignInRequest;
import com.miaoyu.barc.user.model.dto.SignupRequest;
import com.miaoyu.barc.user.service.SignService;
import com.miaoyu.barc.utils.J;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/** 登录与注册只接受 JSON 请求体；旧 GET 凭据入口不再执行写入或登录。 */
@RestController
@RequestMapping("/user/sign")
@IgnoreAuth
public class SignController {
    @Autowired private SignService signService;

    @PostMapping("/in")
    public ResponseEntity<J> signInUserControl(@RequestBody SignInRequest request) {
        return signService.signInUserService(request.type(), request.account(), request.password());
    }
    @PostMapping("/in_by_naigos")
    public ResponseEntity<J> signInUserByNaigosControl(@RequestBody SignInRequest request) {
        return signService.signInByNaigosService(request.type(), request.account(), request.password());
    }
    @PostMapping("/up")
    public ResponseEntity<J> signUpUserControl(@RequestBody SignupRequest request) {
        return signService.register(request);
    }
}
