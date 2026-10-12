package com.miaoyu.barc.api.work.model;

import com.miaoyu.barc.api.work.enumeration.WorkStatusEnum;
import com.miaoyu.barc.api.work.enumeration.WorkReviewStatusEnum;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class WorkModel {
    /** Jackson 请求绑定与 MyBatis 历史记录装载均经此入口，阻断未审核内容在管理端执行脚本。 */
    public void setContent(String content) {
        this.content = com.miaoyu.barc.security.RichText.clean(content);
    }

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
    /** 内容版本由数据库递增；审核必须提交读到的版本，防止批准未查看过的改动。 */
    private Long content_revision = 0L;
    /** 仅用于兼容正文写入的乐观并发校验，不持久化。 */
    @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
    private Long expected_revision;
    /** 管理处置前的可见性，恢复时不得把原私有作品公开。 */
    private WorkStatusEnum restore_status;
    private WorkReviewStatusEnum review_status;
    private String review_reason;
    private String reviewer_uuid;
    private String review_submitted_at;
    private String reviewed_at;
}
