package com.miaoyu.barc.feedback.model;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Setter
@Getter
public class WorkFeedbackModel {
    private String id;
    private String work_id;
    private String ipv4;
    private String device_info;
    private String author;
    private String reason_option;
    private String content;
    private String email;
    private Boolean status;
    private LocalDateTime created_at;
    private LocalDateTime updated_at;
}


