package com.miaoyu.barc.feedback.model;

import com.miaoyu.barc.feedback.enumeration.FeedbackStatusEnum;

/** expected_status 可选以兼容旧客户端；数据库条件更新仍确保不会覆盖并发处理。 */
public record FeedbackUpdateRequest(String id, String echo, FeedbackStatusEnum status, FeedbackStatusEnum expected_status) { }
