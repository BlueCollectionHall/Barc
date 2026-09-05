package com.miaoyu.barc.background.model;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class BackgroundImageModel {
    private String id;
    private String module;
    private String object_key;
    private String filename;
    private Integer sort_order;
    private Boolean enabled;
    private String created_by;
    private LocalDateTime created_at;
    private LocalDateTime updated_at;
    private LocalDateTime deleted_at;
    /** 展示用签名 URL（非数据库列） */
    private String url;
}
