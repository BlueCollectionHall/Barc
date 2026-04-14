package com.miaoyu.barc.feedback.pojo;

import com.miaoyu.barc.feedback.model.FeedbackImageModel;

import java.time.LocalDateTime;
import java.util.List;

public class WorkFeedbackPojo {
    private String id;
    private String work_id;
    private String ipv4;
    private String device_info;
    private String author;
    private String reason_option;
    private String content;
    private List<FeedbackImageModel> images;
    private String email;
    private Boolean status;
    private String note;
    private LocalDateTime created_at;
    private LocalDateTime updated_at;

    public Boolean getStatus() {
        return status;
    }

    public void setStatus(Boolean status) {
        this.status = status;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }

    public LocalDateTime getUpdated_at() {
        return updated_at;
    }

    public void setUpdated_at(LocalDateTime updated_at) {
        this.updated_at = updated_at;
    }

    public String getIpv4() {
        return ipv4;
    }

    public void setIpv4(String ipv4) {
        this.ipv4 = ipv4;
    }

    public String getDevice_info() {
        return device_info;
    }

    public void setDevice_info(String device_info) {
        this.device_info = device_info;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getWork_id() {
        return work_id;
    }

    public void setWork_id(String work_id) {
        this.work_id = work_id;
    }

    public String getAuthor() {
        return author;
    }

    public void setAuthor(String author) {
        this.author = author;
    }

    public String getReason_option() {
        return reason_option;
    }

    public void setReason_option(String reason_option) {
        this.reason_option = reason_option;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public List<FeedbackImageModel> getImages() {
        return images;
    }

    public void setImages(List<FeedbackImageModel> images) {
        this.images = images;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public LocalDateTime getCreated_at() {
        return created_at;
    }

    public void setCreated_at(LocalDateTime created_at) {
        this.created_at = created_at;
    }
}
