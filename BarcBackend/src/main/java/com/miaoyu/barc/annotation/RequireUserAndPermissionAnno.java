package com.miaoyu.barc.annotation;

import com.miaoyu.barc.user.enumeration.UserIdentityEnum;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RequireUserAndPermissionAnno {
    Check[] value();

    /**
     * 鉴权检索接口
     * 从组件方法中获取全部形参
     * uuidIndex：需要校验的uuid实参存在的索引位置
     * identity：身份
     * targetPermission：等待校验的目标权限
     * isHasElseUpper：true为二进制为1位，false为十进制>=位
     * ！！！只传输uuidIndex则只校验存在不校验身份权限*/
    @interface Check {
        int uuidIndex() default 0;
        UserIdentityEnum identity() default UserIdentityEnum.USER;
        int targetPermission() default 1;

        boolean isSuchElseRequire() default true;
        boolean isHasElseUpper() default false;
    }
}
