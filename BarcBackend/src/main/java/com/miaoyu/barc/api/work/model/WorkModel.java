package com.miaoyu.barc.api.work.model;

import com.miaoyu.barc.api.work.enumeration.WorkStatusEnum;
import com.miaoyu.barc.api.work.enumeration.WorkReviewStatusEnum;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class WorkModel {
    private String id;
    private String title;
    private String description;
    private String content;
    private String banner_image;
    private String cover_image;
    private Integer view_count;
    private Integer like_count;
    private Boolean liked_by_current_user;
    private String author;
    private String author_nickname;
    /** author 对应的平台当前归属账号昵称；author_nickname 仍只表示站外原作者。 */
    private String author_display;
    private String uploader;
    private Boolean is_claim;
    private WorkStatusEnum status;
    private String student;
    private String created_at;
    private String updated_at;
    private String content_updated_at;
    private WorkReviewStatusEnum review_status;
    private String review_reason;
    private String reviewer_uuid;
    private String review_submitted_at;
    private String reviewed_at;
}
