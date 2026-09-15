package com.miaoyu.barc.festival.controller;

import com.miaoyu.barc.annotation.IgnoreAuth;
import com.miaoyu.barc.festival.service.FestivalService;
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

/**
 * 节日管理接口
 *
 * 管理端接口统一走 FestivalService 上的「1匹配」馆长鉴权；
 * 公开接口仅返回「启用中」的节日（含日期区间），供用户端背景图判定当前节日。
 */
@RestController
@RequestMapping("/api/festival")
public class FestivalController {

    @Autowired
    private FestivalService festivalService;

    /** 管理端：节日列表（含已关闭，1匹配鉴权） */
    @GetMapping("/admin/list")
    public ResponseEntity<J> listControl(HttpServletRequest request) {
        return festivalService.listService(request.getAttribute("uuid").toString());
    }

    /** 管理端：新增节日（1匹配鉴权） */
    @PostMapping("/admin/create")
    public ResponseEntity<J> createControl(
            HttpServletRequest request,
            @RequestBody FestivalService.FestivalItem item
    ) {
        return festivalService.createService(request.getAttribute("uuid").toString(), item);
    }

    /** 管理端：修改节日（1匹配鉴权） */
    @PutMapping("/admin/{id}")
    public ResponseEntity<J> updateControl(
            HttpServletRequest request,
            @PathVariable String id,
            @RequestBody FestivalService.FestivalItem item
    ) {
        return festivalService.updateService(request.getAttribute("uuid").toString(), id, item);
    }

    /** 管理端：关闭 / 启用节日（1匹配鉴权） */
    @PutMapping("/admin/{id}/enabled")
    public ResponseEntity<J> enabledControl(
            HttpServletRequest request,
            @PathVariable String id,
            @RequestParam("enabled") Boolean enabled
    ) {
        return festivalService.setEnabledService(
                request.getAttribute("uuid").toString(), id, Boolean.TRUE.equals(enabled));
    }

    /** 管理端：删除节日（1匹配鉴权，自动把绑定背景图划归「无节日」） */
    @DeleteMapping("/admin/{id}")
    public ResponseEntity<J> deleteControl(HttpServletRequest request, @PathVariable String id) {
        return festivalService.deleteService(request.getAttribute("uuid").toString(), id);
    }

    /** 管理端：全部节日（含已关闭，供背景图「节日」下拉展示与筛选，1匹配鉴权） */
    @GetMapping("/options")
    public ResponseEntity<J> optionsControl(HttpServletRequest request) {
        // 走一次鉴权切面：传入 uuid 以校验馆长权限
        return festivalService.optionsService(request.getAttribute("uuid").toString());
    }

    /** 用户端：启用中的节日列表（含日期区间，无需登录） */
    @GetMapping("/all")
    @IgnoreAuth
    public ResponseEntity<J> allControl() {
        return ResponseEntity.ok(festivalService.publicAllService());
    }
}
