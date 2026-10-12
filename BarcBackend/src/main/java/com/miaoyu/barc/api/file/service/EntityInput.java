package com.miaoyu.barc.api.file.service;

import com.miaoyu.barc.exception.ApiException;
import com.miaoyu.barc.response.ErrorR;

/** 基础资料写入先校验字段边界，避免数据库约束错误或界面修改未生效。 */
public final class EntityInput {
    private EntityInput() { }
    public static void names(String chinese, String japanese, String korean, String introduction) {
        if (chinese == null || chinese.isBlank() || chinese.length() > 255
                || (japanese != null && japanese.length() > 255) || (korean != null && korean.length() > 255)
                || (introduction != null && introduction.length() > 10000)) {
            throw new ApiException(new ErrorR().normal("中文名必填，名称最多 255 字，介绍最多 10000 字"));
        }
    }
    public static void immutableEnglish(String submitted, String existing) {
        if (submitted != null && !submitted.equals(existing)) throw new ApiException(new ErrorR().normal("英文名用于生成 ID，创建后不可修改"));
    }
}
