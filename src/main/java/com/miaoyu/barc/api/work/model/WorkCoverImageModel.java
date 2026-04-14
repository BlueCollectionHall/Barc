package com.miaoyu.barc.api.work.model;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class WorkCoverImageModel {
    private String id;
    private String work_id;
    private String object_key;
    private LocalDateTime created_at;
}
