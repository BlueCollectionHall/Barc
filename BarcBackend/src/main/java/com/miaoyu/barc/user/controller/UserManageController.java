package com.miaoyu.barc.user.controller;

import com.miaoyu.barc.user.model.UserBasicModel;
import com.miaoyu.barc.user.service.UserManageService;
import com.miaoyu.barc.utils.J;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/user/manage")
public class UserManageController {
    @Autowired
    private UserManageService userManageService;

    @PostMapping("/upload_new_user")
    public ResponseEntity<J> uploadNewUserControl(
            HttpServletRequest request,
            @RequestBody UserBasicModel model
    ) {
        return userManageService.uploadNewUserService(request.getAttribute("uuid").toString(), model);
    }
}
