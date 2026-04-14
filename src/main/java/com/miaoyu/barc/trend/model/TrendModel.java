package com.miaoyu.barc.trend.model;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class TrendModel {
    private String id;
    private String title;
    private String content;
    private String author;
    private LocalDateTime created_at;
    private LocalDateTime updated_at;
}
