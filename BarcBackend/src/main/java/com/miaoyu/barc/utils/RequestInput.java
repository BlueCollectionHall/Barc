package com.miaoyu.barc.utils;

import com.miaoyu.barc.exception.ApiException;
import com.miaoyu.barc.response.ErrorR;

/** 业务请求的集中边界校验；无效输入必须在任何写入之前失败。 */
public final class RequestInput {
    private RequestInput() { }
    public static String required(String value, String name, int max) {
        if (value == null || value.isBlank() || value.length() > max) throw invalid(name + "不能为空或超过" + max + "字");
        return value.trim();
    }
    public static String optional(String value, String name, int max) {
        if (value != null && value.length() > max) throw invalid(name + "不能超过" + max + "字");
        return value;
    }
    /** 搜索输入单独去除边缘空白；正文仍由 optional 保留原始排版。 */
    public static String search(String value, int max) {
        return search(value, "搜索词", max);
    }
    /** JSON 筛选映射只接受文本，拒绝数组或对象被隐式转换成搜索表达式。 */
    public static String search(Object value, String name, int max) {
        if (value == null) return null;
        if (!(value instanceof String text)) throw invalid(name + "必须是文本");
        optional(text, name, max);
        return text.isBlank() ? null : text.trim();
    }
    public static ApiException invalid(String message) { return new ApiException(new ErrorR().normal(message)); }
}
