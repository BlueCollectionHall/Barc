package com.miaoyu.barc.api.work.model;

import lombok.Getter;
import lombok.Setter;

/**
 * 作品操作日志模型
 * 记录管理员对作品的所有管理操作（封禁/下架/删除/恢复/编辑/认领/投诉处理）
 */
@Setter
@Getter
public class WorkOperationLogModel {
    private String id;              // 主键UUID
    private String work_id;         // 关联作品ID
    private String operator_uuid;   // 操作人UUID
    private String operation_type;  // 操作类型：BAN/OFF/DELETE/RESTORE/EDIT/CLAIM_APPROVE等
    private String detail;          // 操作详情JSON
    private String created_at;      // 创建时间
}
