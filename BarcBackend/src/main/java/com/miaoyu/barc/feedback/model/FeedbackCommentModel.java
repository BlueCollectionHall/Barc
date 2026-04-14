package com.miaoyu.barc.feedback.model;

import com.miaoyu.barc.feedback.pojo.FeedbackFormPojo;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class FeedbackCommentModel extends FeedbackFormPojo {
    private String comment_id;
}
