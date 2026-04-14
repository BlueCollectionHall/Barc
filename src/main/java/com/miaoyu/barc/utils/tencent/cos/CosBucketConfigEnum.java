package com.miaoyu.barc.utils.tencent.cos;

import lombok.Getter;

/**
 * 腾讯云COS对象存储application.yaml配置参数
 * */

@Getter
public enum CosBucketConfigEnum {
    test("测试"),
    avatar("头像"),
    image("图片"),
    file("文件");

    private final String content;

    CosBucketConfigEnum(String content) {
        this.content = content;
    }
}
