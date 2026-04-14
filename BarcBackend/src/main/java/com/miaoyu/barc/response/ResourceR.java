package com.miaoyu.barc.response;

import com.miaoyu.barc.utils.J;

public class ResourceR {
    public J resourceSuch(boolean isSuch, Object data) {
        J j = new J();
        j.setCode(isSuch? 0: 1);
        j.setMsg("资源获取" + (isSuch? "成功": "失败") + ": Such resource " + (isSuch? "success": "fail"));
        j.setData(isSuch? data: j.getMsg());
        return j;
    }
}
