package com.miaoyu.barc.friendlink.model;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 友情链接模型（管理端「系统 → 友情链接」维护，用户端首页展示）
 *
 * 存储约定：
 * 1、logo_key 存 COS 对象 key（可为空，非必填），不是完整 URL；
 *    对外返回时由服务层经 ImageUrlResolver.resolve() 签名后写入 logo_url 字段；
 * 2、sort_order 为全表连续序号（从 1 开始），上移 / 下移由服务端交换相邻两条的序号；
 * 3、软删除：deleted_at 非空即视为已删除，所有查询都带 deleted_at IS NULL。
 *
 * 字段命名说明：application.yaml 中 map-underscore-to-camel-case = false，
 * 因此模型属性名与数据库列名保持一致的下划线风格（与 BackgroundImageModel / FestivalModel 同款）。
 */
@Getter
@Setter
public class FriendLinkModel {
    private String id;
    /** 标题（必填，长度 ≤ 64） */
    private String title;
    /** 外部链接（必填，必须以 http:// 或 https:// 开头，长度 ≤ 512） */
    private String url;
    /** logo 的 COS 对象 key（可空） */
    private String logo_key;
    /** 简介（可空，长度 ≤ 255） */
    private String intro;
    /** 排序序号：全表连续序号，从 1 开始 */
    private Integer sort_order;
    /** 是否启用：本期无管理界面，公开接口也不按它过滤（预留列） */
    private Boolean enabled;
    /** 创建人 UUID */
    private String created_by;
    private LocalDateTime created_at;
    private LocalDateTime updated_at;
    private LocalDateTime deleted_at;
    /** 展示用签名 URL（非数据库列，logo_key 为空时为 null） */
    private String logo_url;
}
