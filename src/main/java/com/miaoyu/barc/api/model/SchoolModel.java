package com.miaoyu.barc.api.model;

public class SchoolModel {
    private String id;
    private String cn_name;
    private String jp_name;
    private String kr_name;
    private String en_name;
    private String introduce;
    private String logo;
    private String beautify_logo;
    private String bg;

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

    public String getLogo() {
        return logo;
    }

    public void setLogo(String logo) {
        this.logo = logo;
    }

    public String getBeautify_logo() {
        return beautify_logo;
    }

    public void setBeautify_logo(String beautify_logo) {
        this.beautify_logo = beautify_logo;
    }

    public String getBg() {
        return bg;
    }

    public void setBg(String bg) {
        this.bg = bg;
    }
}
