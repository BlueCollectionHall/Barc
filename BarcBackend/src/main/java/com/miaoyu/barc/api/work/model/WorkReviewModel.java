package com.miaoyu.barc.api.work.model;

import com.miaoyu.barc.api.work.enumeration.WorkReviewStatusEnum;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/** 作品当前审核记录；每个作品在 work_review 中只保留一条当前状态。 */
@Getter
@Setter
public class WorkReviewModel {
    private String work_id;
    private WorkReviewStatusEnum status;
    private String rejection_reason;
    private String reviewer_uuid;
    private LocalDateTime submitted_at;
    private LocalDateTime reviewed_at;
    private LocalDateTime updated_at;
}
