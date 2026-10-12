package com.miaoyu.barc.api.work.model;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Setter
@Getter
public class WorkClaimModel {
    private String id;
    private String work_id;
    private String applicant_uuid;
    private LocalDateTime created_at;
    /** 申请结果持久保留，不能在审核完成后删除历史。 */
    private String status = "PENDING";
    private String statement;
    private String reviewer_uuid;
    private LocalDateTime processed_at;
    private String result_reason;
}
