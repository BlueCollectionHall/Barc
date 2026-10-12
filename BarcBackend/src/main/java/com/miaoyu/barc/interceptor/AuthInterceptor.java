package com.miaoyu.barc.interceptor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.miaoyu.barc.annotation.IgnoreAuth;
import com.miaoyu.barc.utils.J;
import com.miaoyu.barc.utils.JwtService;
import com.miaoyu.barc.response.TokenR;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/** 全局认证同时处理可选登录，公开入口仍匿名可读，失效身份不会被写入上下文。 */
@Component
public class AuthInterceptor implements HandlerInterceptor {
    @Autowired private JwtService jwtService;
    @Autowired private ObjectMapper objectMapper;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if (!(handler instanceof HandlerMethod method)) return true;
        boolean optional = method.hasMethodAnnotation(IgnoreAuth.class) || method.getBeanType().isAnnotationPresent(IgnoreAuth.class);
        String token = request.getHeader("Authorization");
        J result = token == null ? new TokenR().token(false, "请先登录", null) : jwtService.jwtParser(token);
        if (result.getCode() == 0) {
            request.setAttribute("uuid", result.getData());
            return true;
        }
        if (optional) return true;
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json;charset=UTF-8");
        objectMapper.writeValue(response.getWriter(), result);
        return false;
    }
}
