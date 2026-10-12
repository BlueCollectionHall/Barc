package com.miaoyu.barc.trend.model;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class TrendImageModel {
    private String id;
    private String trend_id;
    private Integer sort;
    private String image_url;
    private String image_name;
    /** 新图片存储 COS key，image_url 仅作为读取时的签名展示或历史兼容字段。 */
    private String object_key;
    private LocalDateTime created_at;
}
