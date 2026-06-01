package com.miaoyu.barc.api.work.model;

import lombok.Getter;
import lombok.Setter;

/**
 * 管理端认领列表项 DTO
 * 包含作品名、申请人昵称/用户名等展示信息
 */
@Setter
@Getter
public class WorkClaimListDto {
    private String id;               // 认领申请ID
    private String work_id;          // 作品ID
    private String work_title;       // 作品标题
    private String applicant_uuid;   // 申请人UUID
    private String applicant_nickname; // 申请人昵称
    private String applicant_username; // 申请人用户名
    private String created_at;       // 申请时间
}
