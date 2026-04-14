package com.miaoyu.barc.feedback.model;

import com.miaoyu.barc.feedback.enumeration.FeedbackStatusEnum;
import com.miaoyu.barc.feedback.enumeration.FeedbackTypeEnum;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class FeedbackFormModel {
    private String id;
    private String target_id;
    private String ipv4;
    private String ipv6;
    private String author;
    private String email;
    private String content;
    private String echo;
    private FeedbackTypeEnum type;
    private FeedbackStatusEnum status;
    private LocalDateTime created_at;
    private LocalDateTime updated_at;
}
