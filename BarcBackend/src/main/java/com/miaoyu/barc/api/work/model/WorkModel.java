package com.miaoyu.barc.api.work.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.miaoyu.barc.api.work.enumeration.WorkStatusEnum;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Setter
@Getter
public class WorkModel {
    private String id;
    private String title;
    private String description;
    private String content;
    private String banner_image;
    private String cover_image;
    private Integer view_count;
    private Integer like_count;
    private String author;
    private String author_nickname;
    private String uploader;
    private Boolean is_claim;
    private WorkStatusEnum status;
    private String student;
    private String created_at;
    private String updated_at;
}
