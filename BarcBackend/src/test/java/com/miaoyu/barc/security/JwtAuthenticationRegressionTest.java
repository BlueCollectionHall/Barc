package com.miaoyu.barc.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.miaoyu.barc.JwtConfig;
import com.miaoyu.barc.annotation.IgnoreAuth;
import com.miaoyu.barc.exception.ApiException;
import com.miaoyu.barc.interceptor.AuthInterceptor;
import com.miaoyu.barc.user.mapper.UserBasicMapper;
import com.miaoyu.barc.user.model.UserBasicModel;
import com.miaoyu.barc.utils.J;
import com.miaoyu.barc.utils.JwtService;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.method.HandlerMethod;
import javax.crypto.SecretKey;
import java.util.Base64;
import java.util.Date;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 使用临时随机测试密钥验证签名、来源、会话撤销与可选登录，不读取应用密钥。 */
class JwtAuthenticationRegressionTest {
    private SecretKey key;
    private JwtService service;
    private UserBasicMapper users;
    private UserBasicModel user;
    @BeforeEach void setup() {
        key = Jwts.SIG.HS256.key().build();
        JwtConfig config = new JwtConfig(); config.setKey(Base64.getEncoder().encodeToString(key.getEncoded()));
        service = new JwtService(config); users = mock(UserBasicMapper.class);
        ReflectionTestUtils.setField(service, "userBasicMapper", users);
        user = new UserBasicModel(); user.setUuid("user"); user.setSafe_level(100); user.setAuth_version(0L);
        when(users.selectByUuid("user")).thenReturn(user);
    }

    @Test void bareAndPrefixedTokensBothResolveCurrentActiveUser() {
        String token = service.jwtSigned("user");
        assertEquals("user", service.jwtParser(token).getData());
        assertEquals("user", service.jwtParser("Token " + token).getData());
        assertEquals("user", service.jwtParser(" token " + token + " ").getData());
        user.setAuth_version(null);
        assertEquals(0, service.jwtParser(token).getCode());
    }
    @Test void banAndPasswordVersionChangeInvalidateExistingToken() {
        String token = service.jwtSigned("user");
        user.setSafe_level(-1); assertEquals(1, service.jwtParser(token).getCode());
        assertThrows(ApiException.class, () -> service.jwtSigned("user"));
        user.setSafe_level(100); user.setAuth_version(1L); assertEquals(1, service.jwtParser(token).getCode());
        assertEquals(0, service.jwtParser(service.jwtSigned("user")).getCode());
        when(users.selectByUuid("user")).thenReturn(null); assertEquals(1, service.jwtParser(token).getCode());
        assertThrows(ApiException.class, () -> service.jwtSigned("user"));
    }
    @ParameterizedTest @NullAndEmptySource @ValueSource(strings = {" ", "Token ", "not-a-jwt", "a.b.c", "Bearer wrong"})
    void malformedTokensReturnFailureWithoutThrowing(String token) { assertEquals(1, service.jwtParser(token).getCode()); }

    @Test void issuerServiceUuidVersionSignatureAndExpiryMustMatch() {
        assertEquals(1, service.jwtParser(token("com.miaoyu", "barc", "user", 0L, true, key)).getCode());
        assertEquals(1, service.jwtParser(token("com.miaoyu.naigos", "naigos", "user", 0L, true, key)).getCode());
        assertEquals(1, service.jwtParser(token("com.miaoyu.barc", "other", "user", 0L, true, key)).getCode());
        assertEquals(1, service.jwtParser(token("com.miaoyu.barc", "barc", " ", 0L, true, key)).getCode());
        assertEquals(1, service.jwtParser(token("com.miaoyu.barc", "barc", "user", null, true, key)).getCode());
        assertEquals(1, service.jwtParser(token("com.miaoyu.barc", "barc", "user", 0L, false, key)).getCode());
        assertEquals(1, service.jwtParser(token("com.miaoyu.barc", "barc", "user", 0L, true, Jwts.SIG.HS256.key().build())).getCode());
        String expired = Jwts.builder().issuer("com.miaoyu.barc").claim("service", "barc").claim("uuid", "user")
                .claim("auth_version", 0).expiration(new Date(0)).signWith(key).compact();
        assertEquals(1, service.jwtParser(expired).getCode());
    }

    @Test void interceptorUsesJsonAndOnlySetsVerifiedIdentity() throws Exception {
        AuthInterceptor interceptor = new AuthInterceptor();
        ReflectionTestUtils.setField(interceptor, "jwtService", service);
        ReflectionTestUtils.setField(interceptor, "objectMapper", new ObjectMapper());
        HandlerMethod privateHandler = new HandlerMethod(new Endpoints(), Endpoints.class.getMethod("privateApi"));
        HandlerMethod publicHandler = new HandlerMethod(new Endpoints(), Endpoints.class.getMethod("publicApi"));
        MockHttpServletRequest request = new MockHttpServletRequest(); MockHttpServletResponse response = new MockHttpServletResponse();
        assertFalse(interceptor.preHandle(request, response, privateHandler));
        assertEquals(401, response.getStatus()); assertEquals(1, new ObjectMapper().readTree(response.getContentAsString()).get("code").asInt());
        assertTrue(interceptor.preHandle(request, new MockHttpServletResponse(), publicHandler));
        request.addHeader("Authorization", service.jwtSigned("user")); response = new MockHttpServletResponse();
        assertTrue(interceptor.preHandle(request, response, privateHandler));
        assertEquals(200, response.getStatus()); assertEquals("user", request.getAttribute("uuid"));
        request = new MockHttpServletRequest(); request.addHeader("Authorization", "bad");
        assertTrue(interceptor.preHandle(request, new MockHttpServletResponse(), publicHandler)); assertNull(request.getAttribute("uuid"));
        assertTrue(interceptor.preHandle(request, new MockHttpServletResponse(), new Object()));
    }

    private String token(String issuer, String origin, String uuid, Long version, boolean expiry, SecretKey signingKey) {
        var builder = Jwts.builder().issuer(issuer).claim("service", origin).claim("uuid", uuid).claim("auth_version", version);
        if (expiry) builder.expiration(new Date(System.currentTimeMillis() + 60000));
        return builder.signWith(signingKey).compact();
    }
    public static class Endpoints {
        public void privateApi() { }
        @IgnoreAuth public void publicApi() { }
    }
}
