package com.miaoyu.barc.api.controller;

import com.miaoyu.barc.annotation.ApiPath;
import com.miaoyu.barc.annotation.IgnoreAuth;
import com.miaoyu.barc.api.service.CategoryService;
import com.miaoyu.barc.utils.J;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/category")
public class CategoryController {
    @Autowired
    private CategoryService categoryService;

    /**获取全部分类及其后代分类
     * @return List类型中父级分类及其包含List类型中后代分类*/
    @IgnoreAuth
    @GetMapping("/all")
    public ResponseEntity<J> getCategoriesAllControl() {
        return categoryService.getCategoriesService(true, null);
    }

    /**
     * 获取全部顶级分类
     * 20260222.0150.Ver0.1.11（在当前版本中暂时不需要次级分类，因此该接口相对适用）
     * @return List类型中返回所有顶级分类信息
     * */
    @IgnoreAuth
    @GetMapping("/all_top_categories")
    public ResponseEntity<J> getAllTopCategoriesControl() {
        return categoryService.getAllTopCategoriesService();
    }

    /**获取全部分类不分级
     * @return List类型中包含全部分类但不分级*/
    @IgnoreAuth
    @GetMapping("/all_not_graded")
    public ResponseEntity<J> getCategoriesAllNotGradedControl() {
        return categoryService.getCategoriesAllNotGradedService();
    }

    /**根据任意层级分类id获取本层级及其后代层级分类
     * @return 本层级实体中List类型包含其后代层级分类*/
    @IgnoreAuth
    @GetMapping("/categories_by_category")
    public ResponseEntity<J> getCategoriesByCategoryControl(
            @RequestParam("category_id") String categoryId
    ) {
        return categoryService.getCategoriesService(false, categoryId);
    }
}
