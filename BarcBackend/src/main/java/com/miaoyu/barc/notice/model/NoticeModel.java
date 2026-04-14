package com.miaoyu.barc.notice.model;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class NoticeModel {
    private String id;
    private String title;
    private String content;
    private String author;
    private LocalDateTime created_at;
    private LocalDateTime updated_at;
}
