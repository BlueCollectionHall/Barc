package com.miaoyu.barc.user.model;

import java.time.LocalDateTime;

public class VerificationCodeModel {
    private String unique_id;
    private String code;
    private String username;
    private String scenario;
    private LocalDateTime create_at;
    private LocalDateTime expiration_at;
    private Boolean used;

    public String getUnique_id() {
        return unique_id;
    }

    public void setUnique_id(String unique_id) {
        this.unique_id = unique_id;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getScenario() {
        return scenario;
    }

    public void setScenario(String scenario) {
        this.scenario = scenario;
    }

    public LocalDateTime getCreate_at() {
        return create_at;
    }

    public void setCreate_at(LocalDateTime create_at) {
        this.create_at = create_at;
    }

    public LocalDateTime getExpiration_at() {
        return expiration_at;
    }

    public void setExpiration_at(LocalDateTime expiration_at) {
        this.expiration_at = expiration_at;
    }

    public Boolean getUsed() {
        return used;
    }

    public void setUsed(Boolean used) {
        this.used = used;
    }
}
