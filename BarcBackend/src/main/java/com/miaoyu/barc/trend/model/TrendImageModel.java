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
    private LocalDateTime created_at;
}
