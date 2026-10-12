package com.miaoyu.barc.comment.model;

import java.util.List;

/** 留言写请求白名单，避免 Map 强转错误和持久化对象承接用户字段。 */
public final class MessageCommands {
    private MessageCommands() { }
    public record Create(String content) { }
    public record Update(String message_id, String content, String reason) { }
    public record BatchDelete(List<String> message_ids, String reason) { }
}
