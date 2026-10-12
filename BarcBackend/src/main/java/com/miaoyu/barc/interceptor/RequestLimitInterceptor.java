package com.miaoyu.barc.interceptor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.miaoyu.barc.response.ErrorR;
import com.miaoyu.barc.security.RequestLimits;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/** 高成本写入按认证账号或实际连接地址限流；不信任客户端自带的转发地址头。 */
@Component
public class RequestLimitInterceptor implements HandlerInterceptor {
    @Autowired private RequestLimits limits;
    @Autowired private ObjectMapper objectMapper;

    @Override public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if (!(handler instanceof HandlerMethod) || "GET".equals(request.getMethod()) || "OPTIONS".equals(request.getMethod())) return true;
        String path = request.getRequestURI();
        boolean sensitive = path.startsWith("/user/sign/") || path.startsWith("/email/send/");
        boolean feedback = path.startsWith("/feedback/");
        int quota = sensitive ? 30 : feedback ? 20 : 300;
        Object uuid = request.getAttribute("uuid");
        String source = uuid == null ? "ip:" + request.getRemoteAddr() : "uuid:" + uuid;
        String group = sensitive ? "credentials" : feedback ? "feedback" : "writes";
        if (limits.allow("http:" + group + ":" + source, quota, 60 * 1000L)) return true;
        response.setStatus(429); response.setContentType("application/json;charset=UTF-8"); response.setHeader("Retry-After", "60");
        objectMapper.writeValue(response.getWriter(), new ErrorR().normal("操作过于频繁，请稍后重试"));
        return false;
    }
}
