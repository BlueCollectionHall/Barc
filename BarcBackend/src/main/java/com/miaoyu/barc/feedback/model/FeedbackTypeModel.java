package com.miaoyu.barc.feedback.model;

import lombok.Getter;
import lombok.Setter;

/**
 * 【已废弃】对应已废弃的 {@code feedback_type} 表，仅服务于 FeedbackMapper 中同样已废弃的历史查询方法。
 *
 * @deprecated 反馈分类改由 FeedbackTypeEnum 与前端本地映射驱动，请勿在新功能中使用
 */
@Deprecated
@Setter
@Getter
public class FeedbackTypeModel {
    private String id;
    private String name;

}
