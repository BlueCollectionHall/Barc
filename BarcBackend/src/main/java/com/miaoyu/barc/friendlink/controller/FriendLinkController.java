package com.miaoyu.barc.friendlink.controller;

import com.miaoyu.barc.annotation.IgnoreAuth;
import com.miaoyu.barc.friendlink.service.FriendLinkService;
import com.miaoyu.barc.utils.J;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 友情链接接口（管理端「系统 → 友情链接」+ 用户端首页展示）
 *
 * 管理端接口统一走 FriendLinkService 上的「1匹配」馆长鉴权；
 * 公开接口 /api/friend-link/all 带 @IgnoreAuth，无需登录。
 *
 * 注意：/api/** 已被 AuthInterceptor 覆盖（WebConfig#addInterceptors），
 * 「没有权限注解」并不等于「免登录」，只有 @IgnoreAuth 才真正免登录。
 */
@RestController
@RequestMapping("/api/friend-link")
public class FriendLinkController {

    @Autowired
    private FriendLinkService friendLinkService;

    /** 管理端：上传 logo（1匹配鉴权，≤1MB，仅图片；只上传不落库，返回 objectKey） */
    @PostMapping("/upload")
    public ResponseEntity<J> uploadControl(
            HttpServletRequest request,
            @RequestParam("file") MultipartFile file
    ) {
        return friendLinkService.uploadService(request.getAttribute("uuid").toString(), file);
    }

    /** 管理端：友链列表（1匹配鉴权，含签名后的 logo_url） */
    @GetMapping("/admin/list")
    public ResponseEntity<J> listControl(HttpServletRequest request) {
        return friendLinkService.listService(request.getAttribute("uuid").toString());
    }

    /** 管理端：新增友链（1匹配鉴权） */
    @PostMapping("/admin/create")
    public ResponseEntity<J> createControl(
            HttpServletRequest request,
            @RequestBody FriendLinkService.FriendLinkItem item
    ) {
        return friendLinkService.createService(request.getAttribute("uuid").toString(), item);
    }

    /** 管理端：修改友链（1匹配鉴权，logo 变更时旧对象交 GC 清理） */
    @PutMapping("/admin/{id}")
    public ResponseEntity<J> updateControl(
            HttpServletRequest request,
            @PathVariable String id,
            @RequestBody FriendLinkService.FriendLinkItem item
    ) {
        return friendLinkService.updateService(request.getAttribute("uuid").toString(), id, item);
    }

    /** 管理端：上移 / 下移（1匹配鉴权，direction 只能是 up / down） */
    @PutMapping("/admin/{id}/move")
    public ResponseEntity<J> moveControl(
            HttpServletRequest request,
            @PathVariable String id,
            @RequestParam("direction") String direction
    ) {
        return friendLinkService.moveService(request.getAttribute("uuid").toString(), id, direction);
    }

    /** 管理端：删除友链（1匹配鉴权，软删记录 + logo 对象交 GC 清理） */
    @DeleteMapping("/admin/{id}")
    public ResponseEntity<J> deleteControl(HttpServletRequest request, @PathVariable String id) {
        return friendLinkService.deleteService(request.getAttribute("uuid").toString(), id);
    }

    /** 用户端：全部友链（无需登录），每项仅含 title / url / intro / logo_url */
    @GetMapping("/all")
    @IgnoreAuth
    public ResponseEntity<J> allControl() {
        return ResponseEntity.ok(friendLinkService.publicAllService());
    }
}
