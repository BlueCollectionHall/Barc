package com.miaoyu.barc.security;

import com.miaoyu.barc.exception.ApiException;
import com.miaoyu.barc.response.ErrorR;
import org.springframework.stereotype.Component;
import java.time.Clock;
import java.util.HashMap;
import java.util.Map;

/** 有界的应用实例内限流；邮件另有数据库签发间隔，防止多实例绕过发信配额。 */
@Component
public class RequestLimits {
    private final Clock clock;
    private final Map<String, Window> windows = new HashMap<>();
    private static final int MAX_KEYS = 20000;

    public RequestLimits(Clock clock) { this.clock = clock; }

    public synchronized boolean allow(String key, int maximum, long windowMillis) {
        long now = clock.millis();
        Window current = windows.get(key);
        if (current != null && current.until > now) {
            if (current.count >= maximum) return false;
            current.count++; return true;
        }
        if (windows.size() >= MAX_KEYS) windows.entrySet().removeIf(entry -> entry.getValue().until <= now);
        if (windows.size() >= MAX_KEYS && current == null) return false;
        windows.put(key, new Window(now + windowMillis));
        return true;
    }

    /** 业务服务入口也执行限流，不能靠绕过某个 Controller 规避。 */
    public void require(String key, int maximum, long windowMillis) {
        if (!allow(key, maximum, windowMillis)) throw new ApiException(new ErrorR().normal("操作过于频繁，请稍后重试"));
    }

    private static class Window {
        private final long until;
        private int count = 1;
        private Window(long until) { this.until = until; }
    }
}
