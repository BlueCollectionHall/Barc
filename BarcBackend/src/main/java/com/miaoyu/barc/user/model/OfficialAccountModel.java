package com.miaoyu.barc.user.model;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 官方业务指定账号的绑定记录。
 *
 * 表结构约定（official_account）：
 * 1、business_key 为主键，一个业务只允许绑定一个账号；
 * 2、user_uuid 有唯一索引，一个账号只能绑定一个业务；
 * 3、本表不提供「解绑」，只能「更换」，因此 user_uuid 恒为 NOT NULL，「无记录」即「未绑定」。
 */
@Getter
@Setter
public class OfficialAccountModel {
    /** 业务键，取 OfficialAccountEnum.name() */
    private String business_key;
    /** 被指定的官方账号 UUID（user_archive.uuid） */
    private String user_uuid;
    /** 绑定备注 */
    private String remark;
    /** 最后操作的管理员 UUID */
    private String updated_by;
    private LocalDateTime created_at;
    private LocalDateTime updated_at;

    // ===== 以下字段不是 official_account 的列，仅在「联表查询」时填充，用于管理端列表展示 =====

    /** 绑定账号昵称；为 null 表示绑定行存在但账号已被删除（悬空绑定） */
    private String nickname;
    /** 绑定账号用户名 */
    private String username;
    /** 绑定账号头像 */
    private String avatar;
    /** 绑定行存在但账号已不存在时为 true */
    private Boolean dangling;
    /** 最后操作管理员的昵称（列表展示用） */
    private String updated_by_nickname;
}
