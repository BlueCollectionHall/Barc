package com.miaoyu.barc.response;

import com.miaoyu.barc.utils.J;

public class FeedbackR {
    public J notFoundOrCompleted() {
        J j = new J();
        j.setCode(1);
        j.setMsg("该反馈单未找到或已经处理结束");
        j.setData("The feedback form was not found or has already been processed!");
        return j;
    }
}
