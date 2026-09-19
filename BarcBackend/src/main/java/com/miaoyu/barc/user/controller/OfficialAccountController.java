package com.miaoyu.barc.user.controller;

import com.miaoyu.barc.user.service.OfficialAccountService;
import com.miaoyu.barc.utils.J;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 官方业务指定账号控制器（管理端「用户 → 官方账号」）。
 *
 * 路径挂在 /user 下：/user/** 已被 AuthInterceptor 覆盖，因此无需 @IgnoreAuth；
 * 具体的「馆长 1匹配」鉴权由 OfficialAccountService 各方法上的
 * {@code @RequireUserAndPermissionAnno} 承担（与项目其它管理接口一致）。
 */
@RestController
@RequestMapping("/user/official")
public class OfficialAccountController {

    @Autowired
    private OfficialAccountService officialAccountService;

    /** 管理端：全部官方业务项与绑定状态（含未绑定项） */
    @GetMapping("/list")
    public ResponseEntity<J> listControl(HttpServletRequest request) {
        return officialAccountService.listService(uuid(request));
    }

    /** 管理端：绑定 / 更换某业务的官方账号；更换时后端会一并迁移该业务的未认领作品 */
    @PostMapping("/bind")
    public ResponseEntity<J> bindControl(HttpServletRequest request, @RequestBody Map<String, Object> body) {
        return officialAccountService.bindService(
                uuid(request),
                body.get("business_key") != null ? body.get("business_key").toString() : null,
                body.get("user_uuid") != null ? body.get("user_uuid").toString() : null,
                body.get("remark") != null ? body.get("remark").toString() : null);
    }

    /** 管理端：账号选择器搜索（UUID / 用户名 / 昵称） */
    @GetMapping("/search_user")
    public ResponseEntity<J> searchUserControl(HttpServletRequest request,
                                               @RequestParam("keyword") String keyword) {
        return officialAccountService.searchUserService(uuid(request), keyword);
    }

    /** 从拦截器写入的请求属性中取当前登录账号 UUID */
    private String uuid(HttpServletRequest request) {
        return request.getAttribute("uuid").toString();
    }
}
