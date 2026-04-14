package com.miaoyu.barc.response;

import com.miaoyu.barc.utils.J;

public class UserR {
    public J noSuchUser() {
        J j = new J();
        j.setCode(1);
        j.setMsg("未找到用户信息：No such user basic information");
        j.setData(j.getMsg());
        return j;
    }
    public J insufficientAccountPermission() {
        J j = new J();
        j.setCode(1);
        j.setMsg("账号权限不足：Insufficient account permissions");
        j.setData(j.getMsg());
        return j;
    }
    public J uuidMismatch() {
        J j = new J();
        j.setCode(1);
        j.setMsg("用户UUID信息不匹配：User UUID information mismatch");
        j.setData(j.getMsg());
        return j;
    }
}
