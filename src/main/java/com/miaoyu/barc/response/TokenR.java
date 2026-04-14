package com.miaoyu.barc.response;

import com.miaoyu.barc.utils.J;

public class TokenR {
    public J token(boolean isToken, String message, String data) {
        J j = new J();
        j.setCode(isToken ? 0: 1);
        j.setMsg(isToken? "Token成功": message);
        j.setData(isToken? data: j.getMsg());
        return j;
    }
}
