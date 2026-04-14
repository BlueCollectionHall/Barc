package com.miaoyu.barc.response;

import com.miaoyu.barc.utils.J;

public class SuccessR {
    public J normal(Object data) {
        J j = new J();
        j.setCode(0);
        j.setMsg("成功：Success");
        j.setData(data);
        return j;
    }
}
