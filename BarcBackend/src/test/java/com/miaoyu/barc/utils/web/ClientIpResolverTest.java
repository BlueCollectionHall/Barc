package com.miaoyu.barc.utils.web;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClientIpResolverTest {

    private final ClientIpResolver resolver = new ClientIpResolver();

    @Test
    @DisplayName("X-Real-IP 应优先于 X-Forwarded-For")
    void resolve_WhenXRealIpPresent_ShouldPreferItOverXForwardedFor() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Real-IP", "203.0.113.9");
        request.addHeader("X-Forwarded-For", "198.51.100.10");
        request.setRemoteAddr("192.0.2.1");

        ClientIpInfo info = resolver.resolve(request);

        assertNotNull(info);
        assertEquals("IPV4", info.viewerType());
        assertEquals("203.0.113.9", info.normalizedIp());
    }

    @Test
    @DisplayName("IPv4-mapped IPv6 应折叠为 IPv4")
    void resolve_WhenIpv4MappedIpv6_ShouldCollapseToIpv4() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("::ffff:127.0.0.1");

        ClientIpInfo info = resolver.resolve(request);

        assertNotNull(info);
        assertEquals("IPV4", info.viewerType());
        assertEquals("127.0.0.1", info.normalizedIp());
    }

    @Test
    @DisplayName("非法 IP 应返回空结果")
    void resolve_WhenInvalidIp_ShouldReturnNull() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Real-IP", "unknown");
        request.addHeader("X-Forwarded-For", "not-an-ip");
        request.setRemoteAddr("also-not-an-ip");

        assertNull(resolver.resolve(request));
    }

    @Test
    @DisplayName("IP 哈希应为稳定的 SHA-256 十六进制串")
    void resolve_WhenSameIpResolvedTwice_ShouldReturnStableSha256HashShape() {
        MockHttpServletRequest firstRequest = new MockHttpServletRequest();
        firstRequest.setRemoteAddr("198.51.100.8");
        MockHttpServletRequest secondRequest = new MockHttpServletRequest();
        secondRequest.setRemoteAddr("198.51.100.8");

        ClientIpInfo firstInfo = resolver.resolve(firstRequest);
        ClientIpInfo secondInfo = resolver.resolve(secondRequest);

        assertNotNull(firstInfo);
        assertNotNull(secondInfo);
        assertEquals(firstInfo.ipHash(), secondInfo.ipHash());
        assertTrue(firstInfo.ipHash().matches("[0-9a-f]{64}"));
    }
}
