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
}
