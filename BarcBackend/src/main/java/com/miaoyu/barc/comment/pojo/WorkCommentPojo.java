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
    // 主评论随附有限条回复，真实总数用于继续加载，不以预览数量代替总数。
    private Long reply_count;
}
