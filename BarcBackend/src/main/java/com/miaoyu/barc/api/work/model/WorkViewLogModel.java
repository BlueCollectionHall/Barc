package com.miaoyu.barc.api.work.model;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
public class WorkViewLogModel {
    private String id;
    private String work_id;
    private LocalDate view_date;
    private String viewer_type;
    private String viewer_user_uuid;
    private String viewer_ipv4;
    private String viewer_ipv6;
    private String viewer_ip_hash;
    private String dedupe_key;
    private String created_at;
}
