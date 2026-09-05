package com.miaoyu.barc.background.controller;

import com.miaoyu.barc.annotation.IgnoreAuth;
import com.miaoyu.barc.background.service.BackgroundImageService;
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

import java.util.List;

@RestController
@RequestMapping("/api/background")
public class BackgroundController {
    @Autowired
    private BackgroundImageService backgroundImageService;

    /** 管理端：背景模块下拉选项（1匹配鉴权） */
    @GetMapping("/modules")
    public ResponseEntity<J> modulesControl(HttpServletRequest request) {
        return backgroundImageService.modulesService(request.getAttribute("uuid").toString());
    }

    /** 管理端：上传背景图（1匹配鉴权，1MB 限制） */
    @PostMapping("/upload")
    public ResponseEntity<J> uploadControl(
            HttpServletRequest request,
            @RequestParam("file") MultipartFile file,
            @RequestParam("module") String module
    ) {
        return backgroundImageService.uploadService(request.getAttribute("uuid").toString(), file, module);
    }

    /** 管理端：某模块背景图列表（1匹配鉴权） */
    @GetMapping("/admin/list")
    public ResponseEntity<J> listControl(
            HttpServletRequest request,
            @RequestParam("module") String module
    ) {
        return backgroundImageService.listService(request.getAttribute("uuid").toString(), module);
    }

    /** 管理端：批量排序（1匹配鉴权） */
    @PutMapping("/reorder")
    public ResponseEntity<J> reorderControl(
            HttpServletRequest request,
            @RequestBody List<BackgroundImageService.OrderItem> items
    ) {
        return backgroundImageService.reorderService(request.getAttribute("uuid").toString(), items);
    }

    /** 管理端：启用 / 停用（1匹配鉴权） */
    @PutMapping("/{id}/enabled")
    public ResponseEntity<J> enabledControl(
            HttpServletRequest request,
            @PathVariable String id,
            @RequestParam("enabled") Boolean enabled
    ) {
        return backgroundImageService.setEnabledService(
                request.getAttribute("uuid").toString(), id, Boolean.TRUE.equals(enabled));
    }

    /** 管理端：删除背景图（1匹配鉴权） */
    @DeleteMapping("/{id}")
    public ResponseEntity<J> deleteControl(HttpServletRequest request, @PathVariable String id) {
        return backgroundImageService.deleteService(request.getAttribute("uuid").toString(), id);
    }

    /** 用户端：全部启用背景图，按模块分组（无需登录） */
    @GetMapping("/all")
    @IgnoreAuth
    public ResponseEntity<J> allControl() {
        return ResponseEntity.ok(backgroundImageService.publicAllService());
    }
}
