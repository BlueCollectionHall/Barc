package com.miaoyu.barc.api.file.service;

import com.miaoyu.barc.exception.ApiException;
import com.miaoyu.barc.response.ErrorR;
import java.util.Objects;

/** 对象 key 与展示 URL 是不同契约，不从任意外链反推存储路径。 */
public final class ObjectKeyPolicy {
    private ObjectKeyPolicy() { }

    public static boolean isKey(String value) {
        return value != null && !value.isBlank() && value.length() <= 255
                && !value.contains(":") && !value.contains("?") && !value.contains("#")
                && !value.contains("\\") && !value.contains("..") && !value.contains("%")
                && !value.chars().anyMatch(Character::isISOControl);
    }

    public static String requireKey(String value) {
        if (!isKey(value)) throw new ApiException(new ErrorR().normal("图片只接受上传返回的对象 key，请重新上传图片"));
        return value;
    }

    /** 未提交图片字段视为保持原值；已污染的历史值只允许原样保留，待单独核查恢复。 */
    public static String replacement(String submitted, String existing) {
        if (submitted == null || Objects.equals(submitted, existing)) return existing;
        if (submitted.isBlank()) return null;
        return requireKey(submitted);
    }
}
