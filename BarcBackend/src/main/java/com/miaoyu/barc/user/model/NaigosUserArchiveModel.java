/*
 * 用户的个人信息档案模型*/
package com.miaoyu.barc.user.model;

public class NaigosUserArchiveModel {
    private Integer id;
    private Long qq_id;
    private String nickname;
    private String group_real_user_id;
    private String register_real_group_id;
    private String city;
    private Integer score;
    private String avatar;
    private Integer favorite;
    private String email;
    private Integer safe_level;
    private boolean is_bot_memory;

    public boolean isIs_bot_memory() {
        return is_bot_memory;
    }

    public void setIs_bot_memory(boolean is_bot_memory) {
        this.is_bot_memory = is_bot_memory;
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public Long getQq_id() {
        return qq_id;
    }

    public void setQq_id(Long qq_id) {
        this.qq_id = qq_id;
    }

    public String getNickname() {
        return nickname;
    }

    public void setNickname(String nickname) {
        this.nickname = nickname;
    }

    public String getGroup_real_user_id() {
        return group_real_user_id;
    }

    public void setGroup_real_user_id(String group_real_user_id) {
        this.group_real_user_id = group_real_user_id;
    }

    public String getRegister_real_group_id() {
        return register_real_group_id;
    }

    public void setRegister_real_group_id(String register_real_group_id) {
        this.register_real_group_id = register_real_group_id;
    }

    public String getCity() {
        return city;
    }

    public void setCity(String city) {
        this.city = city;
    }

    public String getAvatar() {
        return avatar;
    }

    public void setAvatar(String avatar) {
        this.avatar = avatar;
    }

    public Integer getScore() {
        return score;
    }

    public void setScore(Integer score) {
        this.score = score;
    }

    public Integer getFavorite() {
        return favorite;
    }

    public void setFavorite(Integer favorite) {
        this.favorite = favorite;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public Integer getSafe_level() {
        return safe_level;
    }

    public void setSafe_level(Integer safe_level) {
        this.safe_level = safe_level;
    }
}
