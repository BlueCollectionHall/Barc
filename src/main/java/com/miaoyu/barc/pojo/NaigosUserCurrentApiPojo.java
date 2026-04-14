package com.miaoyu.barc.pojo;

import com.miaoyu.barc.user.model.NaigosUserArchiveModel;

public class NaigosUserCurrentApiPojo {
    private Integer code;
    private String message;
    private NaigosUserArchiveModel data;

    public Integer getCode() {
        return code;
    }

    public void setCode(Integer code) {
        this.code = code;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public NaigosUserArchiveModel getData() {
        return data;
    }

    public void setData(NaigosUserArchiveModel data) {
        this.data = data;
    }
}
