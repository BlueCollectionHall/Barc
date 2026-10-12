package com.miaoyu.barc.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.miaoyu.barc.info.controller.InfoController;
import com.miaoyu.barc.interceptor.AuthInterceptor;
import com.miaoyu.barc.utils.*;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** 经过真实认证拦截器验证匿名页脚，防止全路径认证误伤既有公共页面。 */
class PublicFooterAuthenticationTest {
    @RestController static class ProtectedController { @GetMapping("/protected-fixture") J fixture() { return new J(0, "ok", null); } }
    @Test void footerIsAnonymousWhileUndeclaredRoutesRemainAuthenticated() throws Exception {
        var info = new InfoController(); var application = mock(AppService.class); when(application.getVersion()).thenReturn("test"); ReflectionTestUtils.setField(info, "appService", application);
        var interceptor = new AuthInterceptor(); var jwt = mock(JwtService.class); ReflectionTestUtils.setField(interceptor, "jwtService", jwt); ReflectionTestUtils.setField(interceptor, "objectMapper", new ObjectMapper());
        var mvc = MockMvcBuilders.standaloneSetup(info, new ProtectedController()).addInterceptors(interceptor).build();
        mvc.perform(get("/info/icp_num")).andExpect(status().isOk()).andExpect(jsonPath("$.data").value("黑ICP备2025036071号-2"));
        mvc.perform(get("/info/copyright")).andExpect(status().isOk()).andExpect(jsonPath("$.data").value(org.hamcrest.Matchers.containsString("B.A.R.C")));
        mvc.perform(get("/protected-fixture")).andExpect(status().isUnauthorized()); verifyNoInteractions(jwt);
    }
}
