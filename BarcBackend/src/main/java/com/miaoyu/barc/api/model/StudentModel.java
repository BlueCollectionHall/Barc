package com.miaoyu.barc.api.model;

public class StudentModel {
    private String id;
    private String cn_name;
    private String jp_name;
    private String kr_name;
    private String en_name;
    private String introduce;
    private String avatar_square;
    private String avatar_rectangle;
    private String body_image;
    private String school;
    private String club;

    public String getClub() {
        return club;
    }

    public void setClub(String club) {
        this.club = club;
    }

    public String getSchool() {
        return school;
    }

    public void setSchool(String school) {
        this.school = school;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getCn_name() {
        return cn_name;
    }

    public void setCn_name(String cn_name) {
        this.cn_name = cn_name;
    }

    public String getJp_name() {
        return jp_name;
    }

    public void setJp_name(String jp_name) {
        this.jp_name = jp_name;
    }

    public String getKr_name() {
        return kr_name;
    }

    public void setKr_name(String kr_name) {
        this.kr_name = kr_name;
    }

    public String getEn_name() {
        return en_name;
    }

    public void setEn_name(String en_name) {
        this.en_name = en_name;
    }

    public String getIntroduce() {
        return introduce;
    }

    public void setIntroduce(String introduce) {
        this.introduce = introduce;
    }

    public String getAvatar_square() {
        return avatar_square;
    }

    public void setAvatar_square(String avatar_square) {
        this.avatar_square = avatar_square;
    }

    public String getAvatar_rectangle() {
        return avatar_rectangle;
    }

    public void setAvatar_rectangle(String avatar_rectangle) {
        this.avatar_rectangle = avatar_rectangle;
    }

    public String getBody_image() {
        return body_image;
    }

    public void setBody_image(String body_image) {
        this.body_image = body_image;
    }
}
