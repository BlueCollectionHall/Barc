package com.miaoyu.barc;

import com.miaoyu.barc.interceptor.AuthInterceptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.lang.NonNullApi;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {
    @Autowired
    private AuthInterceptor authInterceptor;

    // 注册跨域
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**") // 允许所有路径
//                        .allowedOrigins("http://127.0.0.1:5173") // 允许访问的源
                .allowedOriginPatterns("*")
                .allowedMethods("*") // 允许的方法
                .allowedHeaders("*") // 允许的头
                .allowCredentials(true); // 允许发送Cookie
    }

    // 注册令牌拦截器
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        String[] stringArray = new String[]{
                "/hello/**",
                "/user/**",
                "/api/**",
                "/comment/**",
                "/feedback/**",
                "/trend/**",
                "/notice/**"
        };
        registry.addInterceptor(authInterceptor)
                .addPathPatterns(stringArray);
    }

    // 注册静态资源配置类
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/images/**")
                .addResourceLocations("classpath:/static/uploads/images/");
    }
}

//    @Bean
//    public WebMvcConfigurer configurer() {
//        return new WebMvcConfigurer() {
//            // 注册跨域
//            @Override
//            public void addCorsMappings(CorsRegistry registry) {
//                registry.addMapping("/**") // 允许所有路径
////                        .allowedOrigins("http://127.0.0.1:5173") // 允许访问的源
//                        .allowedOriginPatterns ("*")
//                        .allowedMethods("*") // 允许的方法
//                        .allowedHeaders("*") // 允许的头
//                        .allowCredentials(true); // 允许发送Cookie
//            }
//            // 注册令牌拦截器
//            @Override
//            public void addInterceptors(InterceptorRegistry registry) {
//                String [] stringArray = new String[]{
//                        "/hello/**",
//                        "/user/**",
//                        "/api/**",
//                        "/comment/**",
//                        "/feedback/**",
//                        "/trend/**",
//                };
//                registry.addInterceptor(authInterceptor)
//                        .addPathPatterns(stringArray);
//            }
            // 注册静态资源配置类
//            @Override
//            public void addResourceHandlers(ResourceHandlerRegistry registry) {
//                registry.addResourceHandler("/uploads/images/**")
//                        .addResourceLocations("classpath:/static/uploads/images/");
//            }
//        };
