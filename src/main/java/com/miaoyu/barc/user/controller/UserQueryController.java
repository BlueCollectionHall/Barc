package com.miaoyu.barc.user.controller;

import com.miaoyu.barc.annotation.IgnoreAuth;
import com.miaoyu.barc.user.service.UserService;
import com.miaoyu.barc.utils.J;
import com.miaoyu.barc.utils.dto.PageRequestDto;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 查询用户信息专用控制器层
 * 包含：全部查询、分页查询、各权限查询
 * */

@RestController
@RequestMapping("/user/query")
public class UserQueryController {
    @Autowired
    private UserService userService;

    /**
     * 无条件获取全部用户信息（脱敏）
     * */
    @IgnoreAuth
    @PostMapping("/all_by_page")
    public ResponseEntity<J> getAllUsersByPage(@RequestBody PageRequestDto dto) {
        return userService.getUsersByPage(dto);
    }
}
