package com.miaoyu.barc.api.service;

import com.miaoyu.barc.api.mapper.CategoryMapper;
import com.miaoyu.barc.api.pojo.CategoryPojo;
import com.miaoyu.barc.response.ResourceR;
import com.miaoyu.barc.utils.J;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class CategoryService {
    @Autowired
    private CategoryMapper categoryMapper;

    public ResponseEntity<J> getCategoriesService(boolean isTop, String categoryId) {
        List<CategoryPojo> allCategories = categoryMapper.selectAll();
        // 创建ID到分类映射
        Map<String, CategoryPojo> categoryMap = new HashMap<>();
        // 找出所有顶级分类并映射
        List<CategoryPojo> rootCategories = new ArrayList<>();
        for (CategoryPojo cat : allCategories) {
            categoryMap.put(cat.getId(), cat);
            if (isTop) {
                if (cat.getLevel() == 1) {
                    rootCategories.add(cat);
                }
            } else {
                if (cat.getId().equals(categoryId)) {
                    rootCategories.add(cat);
                }
            }
        }
        // 建立父子关系
        for (CategoryPojo cat : allCategories) {
            if (cat.getParent_id() != null && !cat.getParent_id().isEmpty()) {
                CategoryPojo parent = categoryMap.get(cat.getParent_id());
                if (parent != null) {
                    parent.addChild(cat);
                }
            }
        }
        // 按sort字段排序
        sortCategories(rootCategories);

        return ResponseEntity.ok(new ResourceR().resourceSuch(true, rootCategories));
    }

    public ResponseEntity<J> getAllTopCategoriesService() {
        List<CategoryPojo> allTopCategories = categoryMapper.selectAllTop();
        sortCategories(allTopCategories);
        return ResponseEntity.ok(new ResourceR().resourceSuch(true, allTopCategories));
    }

    public ResponseEntity<J> getCategoriesAllNotGradedService() {
        return ResponseEntity.ok(new ResourceR().resourceSuch(true, categoryMapper.selectAll()));
    }

    private void sortCategories(List<CategoryPojo> categories) {
        if (categories == null) return;
        // 排序当前层级
        categories.sort(Comparator.comparingInt(CategoryPojo::getSort));
        // 递归排序子层级
        for (CategoryPojo cat : categories) {
            if (cat.getChildren() != null) {
                sortCategories(cat.getChildren());
            }
        }
    }
}
