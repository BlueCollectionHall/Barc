package com.miaoyu.barc.feedback.model;

import com.miaoyu.barc.feedback.enumeration.FeedbackTypeEnum;

/** 创建反馈仅接受业务内容和匿名联系地址；身份、IP、状态及回执不可由请求指定。 */
public record FeedbackCreateRequest(FeedbackTypeEnum type, String target_id, String content, String email) { }
