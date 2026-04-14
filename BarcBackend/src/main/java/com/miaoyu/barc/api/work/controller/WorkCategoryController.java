package com.miaoyu.barc.api.work.controller;

import com.miaoyu.barc.annotation.IgnoreAuth;
import com.miaoyu.barc.api.work.model.WorkCategoryModel;
import com.miaoyu.barc.api.work.service.WorkCategoryService;
import com.miaoyu.barc.utils.J;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/work/category")
public class WorkCategoryController {
    private final WorkCategoryService workCategoryService;

    public WorkCategoryController(WorkCategoryService workCategoryService) {
        this.workCategoryService = workCategoryService;
    }

    /**记录作品的分类信息
     * @param requestModel 记录分类实体
     * @return 记录是否成功*/
    @PostMapping("/record")
    public ResponseEntity<J> recordWorkAndCategoryControl(
            HttpServletRequest request, @RequestBody WorkCategoryModel requestModel
    ) {
        return workCategoryService.recordWorkAndCategoryService(request.getAttribute("uuid").toString(), requestModel);
    }

    /**根据作品ID获取所有其记录的分类信息
     * @param workId 作品ID
     * @return List类型中包含所有作品分类实体*/
    @IgnoreAuth
    @GetMapping("/categories_by_work")
    public ResponseEntity<J> getCategoriesByWorkIdControl(@RequestParam("work_id") String workId) {
        return workCategoryService.getCategoriesByWorkIdService(workId);
    }

    /**根据记录主键删除作品分类记录
     * @param id 作品分类记录主键
     * @return 删除是否成功*/
    @DeleteMapping("/delete")
    public ResponseEntity<J> deleteWorkCategoryControl(
            HttpServletRequest request,
            @RequestParam("id") String id
    ) {
        return workCategoryService.deleteWorkCategoryService(request.getAttribute("uuid").toString(), id);
    }
}
