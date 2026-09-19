package com.miaoyu.barc.api.controller;

import com.miaoyu.barc.api.service.CategoryManageService;
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
 * 管理端作品分类管理控制器 —— 路径前缀：/api/category/manage
 *
 * <p>权限检查在 {@link CategoryManageService} 层由 {@code @RequireUserAndPermissionAnno} 实现，
 * 统一为 {@code MANAGER} 身份 + {@code SEC_MAINTAINER} 的「1匹配」鉴权，与「作品管理」组其余接口一致。</p>
 *
 * <p>为什么单独开一个控制器，而不直接扩展公开的 {@code /api/category}：
 * 公开的 CategoryController 里每个接口都标了 {@code @IgnoreAuth}（用户端无需登录），
 * 把带写权限的管理接口混进去，极易在后续维护中被误加 {@code @IgnoreAuth} 而开放写操作；
 * 两条链路物理隔离后，公开接口继续保持只读，管理接口则强制走登录 + 权限切面。</p>
 */
@RestController
@RequestMapping("/api/category/manage")
public class CategoryManageController {

    @Autowired
    private CategoryManageService categoryManageService;

    /** 统一从鉴权拦截器写入的 request attribute 中取当前操作者 uuid */
    private String uuid(HttpServletRequest request) {
        return request.getAttribute("uuid").toString();
    }

    /**
     * 管理端：分类树列表（含每个分类的直接作品数与子分类数）
     */
    @GetMapping("/list")
    public ResponseEntity<J> listControl(HttpServletRequest request) {
        return categoryManageService.listService(uuid(request));
    }

    /**
     * 管理端：新增分类（id 手填，parent_id 为空表示顶级分类）
     */
    @PostMapping("/create")
    public ResponseEntity<J> createControl(
            HttpServletRequest request,
            @RequestBody CategoryManageService.CategoryForm form
    ) {
        return categoryManageService.createService(uuid(request), form);
    }

    /**
     * 管理端：修改分类（名称 / 排序 / 父分类；父分类变化即移动并级联修正子树层级）
     */
    @PutMapping("/{id}")
    public ResponseEntity<J> updateControl(
            HttpServletRequest request,
            @PathVariable String id,
            @RequestBody CategoryManageService.CategoryForm form
    ) {
        return categoryManageService.updateService(uuid(request), id, form);
    }

    /**
     * 管理端：同级上移 / 下移
     *
     * @param direction 仅接受 up / down
     */
    @PutMapping("/{id}/sort")
    public ResponseEntity<J> moveSortControl(
            HttpServletRequest request,
            @PathVariable String id,
            @RequestParam("direction") String direction
    ) {
        return categoryManageService.moveSortService(uuid(request), id, direction);
    }

    /**
     * 管理端：删除分类（有子分类或有作品关联时会被拒绝）
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<J> deleteControl(HttpServletRequest request, @PathVariable String id) {
        return categoryManageService.deleteService(uuid(request), id);
    }
}
