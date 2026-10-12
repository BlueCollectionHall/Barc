package com.miaoyu.barc.notice.model;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class NoticeModel {
    /** 公告入库和历史查询使用同一净化规则，首页摘要与详情均可安全展示。 */
    public void setContent(String content) {
        this.content = com.miaoyu.barc.security.RichText.clean(content);
    }

    private String id;
    private String title;
    private String content;
    private String author;
    private LocalDateTime created_at;
    private LocalDateTime updated_at;
}
