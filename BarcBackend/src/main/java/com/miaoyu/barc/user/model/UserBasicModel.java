package com.miaoyu.barc.user.model;

import java.time.LocalDateTime;

public class UserBasicModel {
    private String uuid;
    private String username;
    private String password;
    private Integer password_version;
    private String email;
    private Boolean email_verified;
    private String telephone;
    private Integer safe_level;
    private LocalDateTime created_at;
    private LocalDateTime updated_at;

    public String getUuid() {
        return uuid;
    }

    public void setUuid(String uuid) {
        this.uuid = uuid;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public Integer getPassword_version() {
        return password_version;
    }

    public void setPassword_version(Integer password_version) {
        this.password_version = password_version;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public Boolean getEmail_verified() {
        return email_verified;
    }

    public void setEmail_verified(Boolean email_verified) {
        this.email_verified = email_verified;
    }

    public String getTelephone() {
        return telephone;
    }

    public void setTelephone(String telephone) {
        this.telephone = telephone;
    }

    public Integer getSafe_level() {
        return safe_level;
    }

    public void setSafe_level(Integer safe_level) {
        this.safe_level = safe_level;
    }

    public LocalDateTime getCreated_at() {
        return created_at;
    }

    public void setCreated_at(LocalDateTime created_at) {
        this.created_at = created_at;
    }

    public LocalDateTime getUpdated_at() {
        return updated_at;
    }

    public void setUpdated_at(LocalDateTime updated_at) {
        this.updated_at = updated_at;
    }
}
