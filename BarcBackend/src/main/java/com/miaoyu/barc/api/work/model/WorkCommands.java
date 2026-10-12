package com.miaoyu.barc.api.work.model;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.miaoyu.barc.api.work.enumeration.WorkStatusEnum;
import com.miaoyu.barc.utils.RequestInput;
import com.miaoyu.barc.utils.StrictBooleanDeserializer;

/** 写接口白名单：正文、状态、审核与归属互相隔离。 */
public final class WorkCommands {
    private WorkCommands() { }
    public record Content(String id, String title, String description, String content,
                          String author_nickname, String student, Long expected_revision) {
        public Content {
            id = RequestInput.required(id, "作品ID", 100);
            title = RequestInput.required(title, "标题", 255);
            RequestInput.optional(description, "简介", 5000);
            RequestInput.optional(content, "正文", 100000);
            RequestInput.optional(author_nickname, "作者署名", 255);
            if (expected_revision != null && expected_revision < 0) throw RequestInput.invalid("作品版本无效");
        }
        public WorkModel toModel() {
            WorkModel model = new WorkModel(); model.setId(id); model.setTitle(title);
            model.setDescription(description); model.setContent(content); model.setAuthor_nickname(author_nickname);
            model.setStudent(student); model.setExpected_revision(expected_revision); return model;
        }
    }
    public record Review(String work_id, @JsonDeserialize(using = StrictBooleanDeserializer.class) Boolean approved,
                         String reason, Long expected_revision) {
        public Review {
            work_id = RequestInput.required(work_id, "作品ID", 100);
            if (approved == null || expected_revision == null || expected_revision < 0) throw RequestInput.invalid("请提供审核结论和页面作品版本");
            RequestInput.optional(reason, "审核原因", 500);
        }
    }
    public record Status(String work_id, WorkStatusEnum status, String remark) {
        public Status {
            work_id = RequestInput.required(work_id, "作品ID", 100);
            if (status == null) throw RequestInput.invalid("请提供目标状态");
            RequestInput.optional(remark, "处理说明", 500);
        }
    }
    public record WorkId(String work_id) {
        public WorkId { work_id = RequestInput.required(work_id, "作品ID", 100); }
    }
    public record ClaimReview(String claim_id, @JsonDeserialize(using = StrictBooleanDeserializer.class) Boolean approved) {
        public ClaimReview {
            claim_id = RequestInput.required(claim_id, "申请ID", 36);
            if (approved == null) throw RequestInput.invalid("请提供认领审核结论");
        }
    }
    public record Assign(String work_id, String author_uuid) {
        public Assign {
            work_id = RequestInput.required(work_id, "作品ID", 100);
            author_uuid = RequestInput.required(author_uuid, "作者UUID", 36);
        }
    }
}
