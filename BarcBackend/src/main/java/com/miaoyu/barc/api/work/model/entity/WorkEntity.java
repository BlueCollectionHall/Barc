package com.miaoyu.barc.api.work.model.entity;

import com.miaoyu.barc.api.work.enumeration.WorkStatusEnum;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class WorkEntity {
    private String id;
    private String title;
    private String description;
    private String cover_image;
    private Integer view_count;
    private Integer like_count;
    private String author;
    private String author_nickname;
    private Boolean is_claim;
    private WorkStatusEnum status;
    private String student;
    private LocalDateTime created_at;
    private LocalDateTime updated_at;
}
