package com.miaoyu.barc.exception;

import com.miaoyu.barc.utils.J;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** 保留两端使用的 J 业务错误契约，并让鉴权失败独立于被拦截方法的返回类型。 */
@RestControllerAdvice
public class ApiExceptionHandler {
    /** MVC 绑定错误同样返回稳定业务提示，不泄露解析器堆栈或把缺字段当默认拒绝。 */
    @ExceptionHandler({org.springframework.http.converter.HttpMessageNotReadableException.class,
            org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class,
            org.springframework.web.bind.MissingServletRequestParameterException.class,
            org.springframework.web.multipart.support.MissingServletRequestPartException.class})
    public ResponseEntity<J> handleInvalidRequest(Exception exception) {
        return ResponseEntity.badRequest().body(new com.miaoyu.barc.response.ErrorR().normal("请求字段缺失或格式不正确"));
    }

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<J> handleBusinessError(ApiException exception) {
        return ResponseEntity.ok(exception.getResponse());
    }

    /** 唯一约束与外键冲突可能在并发预检之后发生；事务已回滚，响应不暴露 SQL 或账号数据。 */
    @ExceptionHandler(org.springframework.dao.DataIntegrityViolationException.class)
    public ResponseEntity<J> handleDataConflict(org.springframework.dao.DataIntegrityViolationException exception) {
        return ResponseEntity.status(409).body(new com.miaoyu.barc.response.ErrorR().normal("记录已存在或关联数据已变化，请刷新后重试"));
    }
}
