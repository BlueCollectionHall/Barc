package com.miaoyu.barc.feedback.model;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Setter
@Getter
public class FeedbackImageModel {
    private String id;
    private String feedback_id;
    private String url;
    private LocalDateTime created_at;
}
