package com.miaoyu.barc.utils;

import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class J {
    private int code;
    private String msg;
    private Object data;

    public J() {}

    public J(int code, String msg, Object data) {
        this.code = code;
        this.msg = msg;
        this.data = data;
    }
}
