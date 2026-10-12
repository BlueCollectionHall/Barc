package com.miaoyu.barc.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.miaoyu.barc.*;
import com.miaoyu.barc.annotation.IgnoreAuth;
import com.miaoyu.barc.interceptor.*;
import com.miaoyu.barc.utils.*;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.*;
import org.springframework.core.env.MapPropertySource;
import org.springframework.mock.web.MockServletContext;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** 使用真实 MVC 跨域处理器和认证链验证部署来源、预检和诊断路由隔离，不启动服务器。 */
class WebBoundaryContractTest {
    @Configuration @EnableWebMvc @Import({WebConfig.class, Fixture.class, HelloWorldController.class})
    static class ConfigurationForTest {
        @Bean AuthInterceptor auth() { return new AuthInterceptor(); }
        @Bean JwtService tokenParser() { return mock(JwtService.class); }
        @Bean com.miaoyu.barc.user.mapper.UserBasicMapper accounts() { return mock(com.miaoyu.barc.user.mapper.UserBasicMapper.class); }
        @Bean ObjectMapper json() { return new ObjectMapper(); }
        @Bean RequestLimits quotas() { return new RequestLimits(java.time.Clock.systemUTC()); }
        @Bean RequestLimitInterceptor limits() { return new RequestLimitInterceptor(); }
        @Bean HelloWorldService diagnostics() { return mock(HelloWorldService.class); }
    }
    @RestController static class Fixture {
        @IgnoreAuth @GetMapping("/public-fixture") J publicRead() { return new J(0, "公开", null); }
        @PostMapping("/protected-fixture") J protectedWrite() { return new J(0, "保护", null); }
    }

    private AnnotationConfigWebApplicationContext context(boolean local) {
        var context = new AnnotationConfigWebApplicationContext(); context.setServletContext(new MockServletContext());
        context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("test", Map.of("barc.web.allowed-origins", "https://user.example.test, https://admin.example.test")));
        if (local) context.getEnvironment().setActiveProfiles("local");
        context.register(ConfigurationForTest.class); context.refresh(); return context;
    }

    @Test void bothConfiguredOriginsCanPreflightProtectedJsonWritesWithoutAToken() throws Exception {
        try (var context = context(false)) {
            var mvc = MockMvcBuilders.webAppContextSetup(context).build();
            for (String origin : new String[]{"https://user.example.test", "https://admin.example.test"}) {
                mvc.perform(options("/protected-fixture").header("Origin", origin).header("Access-Control-Request-Method", "POST")
                                .header("Access-Control-Request-Headers", "authorization,content-type"))
                        .andExpect(status().isOk()).andExpect(header().string("Access-Control-Allow-Origin", origin))
                        .andExpect(header().doesNotExist("Access-Control-Allow-Credentials"));
            }
            mvc.perform(post("/protected-fixture").header("Origin", "https://user.example.test"))
                    .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value(1));
        }
    }

    @Test void unrelatedOriginsAreRejectedWhileAnonymousAndSameOriginReadsStillWork() throws Exception {
        try (var context = context(false)) {
            var mvc = MockMvcBuilders.webAppContextSetup(context).build();
            mvc.perform(get("/public-fixture").header("Origin", "https://untrusted.example.test")).andExpect(status().isForbidden());
            mvc.perform(options("/protected-fixture").header("Origin", "https://untrusted.example.test").header("Access-Control-Request-Method", "POST")).andExpect(status().isForbidden());
            mvc.perform(get("/public-fixture")).andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0));
            mvc.perform(get("/public-fixture").header("Origin", "https://user.example.test")).andExpect(status().isOk());
        }
    }

    @Test void diagnosticsRequireAnExplicitLocalProfile() {
        try (var deployed = context(false); var local = context(true)) {
            assertEquals(0, deployed.getBeanNamesForType(HelloWorldController.class).length);
            assertEquals(1, local.getBeanNamesForType(HelloWorldController.class).length);
        }
    }

    @Test void wildcardCredentialsAndPathOriginsCannotSilentlyWidenTheAllowlist() {
        for (String value : new String[]{"*", "https://*.example.test", "https://user:pass@example.test", "https://example.test/path", "https://example.test?q=x", "https://example.test#x", "ftp://example.test"}) {
            assertThrows(IllegalArgumentException.class, () -> new WebConfig(value));
        }
        assertDoesNotThrow(() -> new WebConfig(""));
        assertDoesNotThrow(() -> new WebConfig("http://localhost:5173, http://localhost:5173"));
    }
}
