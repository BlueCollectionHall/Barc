package com.miaoyu.barc.response;

import com.miaoyu.barc.utils.J;

public class EmailR {
    public J email(boolean isSend) {
        J j = new J();
        j.setCode(isSend? 0: 1);
        j.setMsg("邮件发送" + (isSend? "成功": "失败"));
        j.setData(j.getMsg());
        return j;
    }
    public J rKeyEmail(String data) {
        J j = new J();
        j.setCode(0);
        j.setMsg("邮件发送成功");
        j.setData(data);
        return j;
    }
}
