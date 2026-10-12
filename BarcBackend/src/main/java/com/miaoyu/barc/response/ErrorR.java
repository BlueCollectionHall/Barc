package com.miaoyu.barc.response;

import com.miaoyu.barc.utils.J;

public class ErrorR {
    public J normal(Object data) {
        J j = new J();
        j.setCode(1);
        // 双端有的读取 msg、有的读取 data；文本错误在两个字段保持同义，保留旧 data 契约。
        j.setMsg(data instanceof String message && !message.isBlank() ? message : "失败：Error");
        j.setData(data);
        return j;
    }
}
