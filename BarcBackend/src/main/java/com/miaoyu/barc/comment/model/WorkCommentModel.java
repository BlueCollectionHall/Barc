package com.miaoyu.barc.comment.model;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Setter
@Getter
public class WorkCommentModel {
    private String id;
    private String work_id;
    private String author;
    private String content;
    private LocalDateTime created_at;

}
