package com.miaoyu.barc.permission;

import org.springframework.stereotype.Component;

@Component
public class ComparePermission {
    /**比较basic权限值相对于target权限值高低
     * @param your 要比较的权限值
     * @param target 目标被比较的权限值
     * @return boolean类型，true: basic >= target，false: basic < target*/
    public boolean compare(int your, int target) {
        return your >= target;
    }
    /**判断basic权限值是否包含target权限值
     * @param your 要比较的权限值
     * @param target 目标权限值
     * @return boolean类型，true: basic中有target，反之*/
    public boolean has(int your, int target) {
        return (your & target) != 0;
    }
}
