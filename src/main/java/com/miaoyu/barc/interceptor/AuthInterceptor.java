package com.miaoyu.barc.interceptor;

import com.miaoyu.barc.annotation.IgnoreAuth;
import com.miaoyu.barc.utils.J;
import com.miaoyu.barc.utils.JwtService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.lang.reflect.Method;
import java.util.Objects;

// 全局路径Authorization令牌实现拦截器
@Component
public class AuthInterceptor implements HandlerInterceptor {
    @Autowired
    private JwtService jwtService;

    @Override
    public boolean preHandle(
            HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        // 放行静态合法请求
        if (!(handler instanceof HandlerMethod)) {
            return true;
        }
        HandlerMethod handlerMethod = (HandlerMethod) handler;
        Method method = handlerMethod.getMethod();
        // 放行阻挠注解
        if (method.isAnnotationPresent(IgnoreAuth.class) ||
                handlerMethod.getBeanType().isAnnotationPresent(IgnoreAuth.class)) {
            return true;
        }
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json;charset=UTF-8");
        String token = request.getHeader("Authorization");
        if (Objects.isNull(token)) {
            response.getWriter().write("账号未登录！Accept without Authorization!");
            return false;
        }
        J jwt = jwtService.jwtParser(token);
        if (jwt.getCode() == 0) {
            request.setAttribute("uuid", jwt.getData());
            return true;
        } else {
            response.getWriter().write(jwt.getMsg());
            return false;
        }
    }
}
