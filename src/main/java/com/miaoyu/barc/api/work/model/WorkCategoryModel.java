package com.miaoyu.barc.api.work.model;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class WorkCategoryModel {
    private String id;
    private String work_id;
    private String category_id;
    private LocalDateTime create_at;
}
