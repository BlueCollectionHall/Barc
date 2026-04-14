package com.miaoyu.barc.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 检索作品是否存在
 * selectType：id唯一ID，model模型
 * index：参数索引位置*/
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface SuchWorkAnno {
    String selectType();
    int index() default 1;
}
