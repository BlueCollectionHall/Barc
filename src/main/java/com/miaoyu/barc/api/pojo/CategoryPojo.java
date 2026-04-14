package com.miaoyu.barc.api.pojo;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Setter
@Getter
public class CategoryPojo {
    private String id;
    private String parent_id;
    private String name;
    private Integer level;
    private Integer sort;
    private Boolean is_enabled;
    private String icon;
    private LocalDateTime create_at;
    private LocalDateTime update_at;
    private List<CategoryPojo> children; // 子分类列表

    public void addChild(CategoryPojo child) {
        if (children == null) {
            children = new ArrayList<>();
        }
        children.add(child);
    }

}