package com.miaoyu.barc.response;

import com.miaoyu.barc.utils.J;

public class ErrorR {
    public J normal(Object data) {
        J j = new J();
        j.setCode(1);
        j.setMsg("失败：Error");
        j.setData(data);
        return j;
    }
}
