package com.miaoyu.barc.exception;

import com.miaoyu.barc.utils.J;

/** 业务拒绝通过异常传递，避免切面向 List 等返回类型塞入 ResponseEntity。 */
public class ApiException extends RuntimeException {
    private final J response;

    public ApiException(J response) {
        super(response.getMsg());
        this.response = response;
    }

    public J getResponse() {
        return response;
    }
}
