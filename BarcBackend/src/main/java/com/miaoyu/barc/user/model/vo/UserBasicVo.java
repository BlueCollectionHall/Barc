package com.miaoyu.barc.user.model.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户基础信息对外视图（脱敏）。
 *
 * <p>为什么需要它：{@link com.miaoyu.barc.user.model.UserBasicModel} 是数据库行映射，
 * 里面带着 {@code password}（口令哈希）与 {@code password_version}。
 * 接口 {@code GET /user/basic_me} 原先直接把这个行对象返回给前端，
 * 等于把本人口令哈希下发给浏览器，属于不必要的敏感字段暴露。
 * 本视图是「本人基础信息」接口唯一允许的返回类型。</p>
 *
 * <p>字段取舍依据（2026-10 双端核对）：
 * 1、{@code uuid} / {@code username} / {@code email} / {@code telephone} 被 BarcFrontend 的
 *    UserPinia 消费（账号展示、反馈表单自动带邮箱、基础资料编辑表单直接 v-model 绑定），必须保留；
 * 2、{@code safe_level} 用于展示账号状态，保留；
 * 3、{@code created_at} / {@code updated_at} 用于展示与排查，保留；
 * 4、{@code password} / {@code password_version} / {@code safe_level_before_ban}
 *    双端均无任何消费，一律不返回。</p>
 *
 * <p>注意：字段名保持下划线写法。本项目 MyBatis 关闭了下划线转驼峰，
 * 且前端类型（UserBasicImpl / UserBasic）声明的是 {@code email_verified}、{@code safe_level}
 * 这类下划线字段名，改成驼峰会破坏双端解析。</p>
 */
@Data
public class UserBasicVo {
    /** 用户唯一标识 */
    private String uuid;
    /** 登录用户名（前端用于展示与「是否本人」判断） */
    private String username;
    /** 邮箱（前端反馈/申诉表单自动填充用） */
    private String email;
    /** 邮箱是否已验证 */
    private Boolean email_verified;
    /** 手机号（基础资料编辑表单直接绑定该字段） */
    private String telephone;
    /** 安全等级（账号状态展示） */
    private Integer safe_level;
    /** 创建时间 */
    private LocalDateTime created_at;
    /** 更新时间 */
    private LocalDateTime updated_at;
}
