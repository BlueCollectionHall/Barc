package com.miaoyu.barc.comment.pojo;

import com.miaoyu.barc.comment.model.WorkCommentModel;
import com.miaoyu.barc.comment.model.WorkCommentReplyModel;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;

@Setter
@Getter
public class WorkCommentPojo extends WorkCommentModel {
    private List<WorkCommentReplyModel> replies;
}
