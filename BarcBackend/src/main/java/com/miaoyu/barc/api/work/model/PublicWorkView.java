package com.miaoyu.barc.api.work.model;

import com.miaoyu.barc.api.work.enumeration.WorkStatusEnum;
import com.miaoyu.barc.api.work.enumeration.WorkReviewStatusEnum;
import com.miaoyu.barc.api.work.model.entity.WorkEntity;
import lombok.Getter;

/** 公开作品白名单；审核人、内部原因及恢复状态不会随持久化模型新增字段而泄露。 */
@Getter
public class PublicWorkView {
    private String id, title, description, content, cover_image, author, author_nickname, author_display, uploader, student;
    private Integer view_count, like_count;
    private Boolean liked_by_current_user, is_claim;
    private WorkStatusEnum status;
    private WorkReviewStatusEnum review_status;
    private String created_at, updated_at, content_updated_at;

    public static PublicWorkView from(WorkModel work) {
        PublicWorkView view = new PublicWorkView();
        view.id = work.getId(); view.title = work.getTitle(); view.description = work.getDescription();
        view.content = work.getContent(); view.cover_image = work.getCover_image();
        view.author = work.getAuthor(); view.author_nickname = work.getAuthor_nickname(); view.author_display = work.getAuthor_display();
        view.uploader = work.getUploader(); view.student = work.getStudent(); view.is_claim = work.getIs_claim();
        view.view_count = work.getView_count(); view.like_count = work.getLike_count(); view.liked_by_current_user = work.getLiked_by_current_user();
        view.status = work.getStatus(); view.review_status = work.getReview_status();
        view.created_at = work.getCreated_at(); view.updated_at = work.getUpdated_at(); view.content_updated_at = work.getContent_updated_at();
        return view;
    }
    public static PublicWorkView from(WorkEntity work) {
        WorkModel model = new WorkModel(); model.setId(work.getId()); model.setTitle(work.getTitle()); model.setDescription(work.getDescription());
        model.setCover_image(work.getCover_image()); model.setAuthor(work.getAuthor()); model.setAuthor_nickname(work.getAuthor_nickname());
        model.setStudent(work.getStudent()); model.setIs_claim(work.getIs_claim()); model.setView_count(work.getView_count()); model.setLike_count(work.getLike_count());
        model.setStatus(work.getStatus()); model.setReview_status(work.getReview_status());
        model.setCreated_at(work.getCreated_at() == null ? null : work.getCreated_at().toString());
        model.setUpdated_at(work.getUpdated_at() == null ? null : work.getUpdated_at().toString());
        model.setContent_updated_at(work.getContent_updated_at() == null ? null : work.getContent_updated_at().toString());
        return from(model);
    }
}
