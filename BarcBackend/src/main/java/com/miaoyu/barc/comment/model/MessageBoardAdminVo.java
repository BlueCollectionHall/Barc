package com.miaoyu.barc.comment.model;

import java.time.LocalDateTime;

public class MessageBoardAdminVo {
    private String id;
    private String author;
    private String author_name;
    private String author_nickname;
    private String content;
    private LocalDateTime created_at;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getAuthor() { return author; }
    public void setAuthor(String author) { this.author = author; }

    public String getAuthor_name() { return author_name; }
    public void setAuthor_name(String author_name) { this.author_name = author_name; }

    public String getAuthor_nickname() { return author_nickname; }
    public void setAuthor_nickname(String author_nickname) { this.author_nickname = author_nickname; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public LocalDateTime getCreated_at() { return created_at; }
    public void setCreated_at(LocalDateTime created_at) { this.created_at = created_at; }
}
