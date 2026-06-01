package com.miaoyu.barc.user.model;

import java.time.LocalDateTime;

/**
 * 用户封号记录模型
 * 
 * 对应数据库表：user_ban_record
 * 记录所有封号/解封历史，用于审计追溯
 */
public class UserBanRecordModel {
    private Long id;
    private String userId;
    private Integer banType;
    private String banReason;
    private Integer banDurationDays;
    private LocalDateTime bannedAt;
    private LocalDateTime unbannedAt;
    private String unbanReason;
    private String operatorId;
    private Integer operatorType;
    private Integer safeLevelBeforeBan;
    private Integer safeLevelAfterBan;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public Integer getBanType() {
        return banType;
    }

    public void setBanType(Integer banType) {
        this.banType = banType;
    }

    public String getBanReason() {
        return banReason;
    }

    public void setBanReason(String banReason) {
        this.banReason = banReason;
    }

    public Integer getBanDurationDays() {
        return banDurationDays;
    }

    public void setBanDurationDays(Integer banDurationDays) {
        this.banDurationDays = banDurationDays;
    }

    public LocalDateTime getBannedAt() {
        return bannedAt;
    }

    public void setBannedAt(LocalDateTime bannedAt) {
        this.bannedAt = bannedAt;
    }

    public LocalDateTime getUnbannedAt() {
        return unbannedAt;
    }

    public void setUnbannedAt(LocalDateTime unbannedAt) {
        this.unbannedAt = unbannedAt;
    }

    public String getUnbanReason() {
        return unbanReason;
    }

    public void setUnbanReason(String unbanReason) {
        this.unbanReason = unbanReason;
    }

    public String getOperatorId() {
        return operatorId;
    }

    public void setOperatorId(String operatorId) {
        this.operatorId = operatorId;
    }

    public Integer getOperatorType() {
        return operatorType;
    }

    public void setOperatorType(Integer operatorType) {
        this.operatorType = operatorType;
    }

    public Integer getSafeLevelBeforeBan() {
        return safeLevelBeforeBan;
    }

    public void setSafeLevelBeforeBan(Integer safeLevelBeforeBan) {
        this.safeLevelBeforeBan = safeLevelBeforeBan;
    }

    public Integer getSafeLevelAfterBan() {
        return safeLevelAfterBan;
    }

    public void setSafeLevelAfterBan(Integer safeLevelAfterBan) {
        this.safeLevelAfterBan = safeLevelAfterBan;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
