package com.miaoyu.barc;

import com.miaoyu.barc.interceptor.AuthInterceptor;
import com.miaoyu.barc.interceptor.RequestLimitInterceptor;
import java.net.URI;
import java.util.Arrays;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** 默认认证全部业务路径；公开路由显式声明，跨域来源由部署配置列举。 */
@Configuration
public class WebConfig implements WebMvcConfigurer {
    @Autowired private AuthInterceptor authInterceptor;
    @Autowired private RequestLimitInterceptor requestLimitInterceptor;
    private final String[] allowedOrigins;

    /** 开发默认只允许双端本地 Vite 来源；部署通过 BARC_WEB_ALLOWED_ORIGINS 设置实际域名。 */
    public WebConfig(@Value("${barc.web.allowed-origins:http://localhost:5173,http://127.0.0.1:5173,http://localhost:5174,http://127.0.0.1:5174}") String origins) {
        allowedOrigins = Arrays.stream(origins.split(",")).map(String::trim).filter(origin -> !origin.isEmpty()).distinct().toArray(String[]::new);
        for (String origin : allowedOrigins) {
            URI uri = URI.create(origin);
            if (!("http".equals(uri.getScheme()) || "https".equals(uri.getScheme())) || uri.getHost() == null
                    || uri.getRawUserInfo() != null || (uri.getRawPath() != null && !uri.getRawPath().isEmpty())
                    || uri.getRawQuery() != null || uri.getRawFragment() != null) {
                throw new IllegalArgumentException("跨域来源必须是明确的 HTTP(S) 协议、主机和可选端口，不能含通配符或路径");
            }
        }
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**").allowedOrigins(allowedOrigins)
                .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                .allowedHeaders("Authorization", "Content-Type", "Accept")
                .allowCredentials(false).maxAge(3600);
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 默认保护所有业务路由；公开 API 必须显式标记 IgnoreAuth，避免新增前缀遗漏。
        registry.addInterceptor(authInterceptor).addPathPatterns("/**")
                .excludePathPatterns("/error", "/images/**", "/favicon.ico");
        // 先解析身份，再执行限流；匿名入口按连接地址分组。
        registry.addInterceptor(requestLimitInterceptor).addPathPatterns("/**");
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/images/**").addResourceLocations("classpath:/static/uploads/images/");
    }
}
