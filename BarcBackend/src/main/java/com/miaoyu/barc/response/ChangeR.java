package com.miaoyu.barc.response;

import com.miaoyu.barc.utils.J;

public class ChangeR {
    public J udu(boolean is, int type) {
        J j = new J();
        j.setCode(is? 0: 1);
        switch (type) {
            case 1: j.setMsg("上传"); break;
            case 2: j.setMsg("删除"); break;
            case 3: j.setMsg("修改"); break;
            default: return null;
        }
        j.setMsg(j.getMsg() + (is? "成功": "失败"));
        j.setData(j.getMsg());
        return j;
    }
}
