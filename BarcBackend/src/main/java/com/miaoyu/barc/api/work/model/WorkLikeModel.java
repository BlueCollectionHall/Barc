package com.miaoyu.barc.api.work.model;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class WorkLikeModel {
    private String id;
    private String work_id;
    private String user_uuid;
    private String created_at;
}
