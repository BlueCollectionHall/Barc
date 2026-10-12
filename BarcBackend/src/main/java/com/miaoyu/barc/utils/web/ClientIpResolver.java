package com.miaoyu.barc.utils.web;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;

@Component
public class ClientIpResolver {
    /** 只采用服务器连接来源；可信代理转换应在部署容器中配置，不采信任意客户端头。 */
    public ClientIpInfo resolve(HttpServletRequest request) {
        if (request == null) {
            return null;
        }

        InetAddress address = parseIp(request.getRemoteAddr());
        if (address == null) return null;
        String normalizedIp = normalize(address);
        return new ClientIpInfo(viewerType(address), normalizedIp, sha256Hex(normalizedIp));
    }

    private InetAddress parseIp(String value) {
        if (value == null) {
            return null;
        }

        String candidate = value.trim();
        if (candidate.isBlank() || candidate.length() > 45 || "unknown".equalsIgnoreCase(candidate)) {
            return null;
        }

        try {
            if (candidate.contains(":")) {
                InetAddress address = InetAddress.getByName(candidate);
                return address instanceof Inet6Address || address instanceof Inet4Address ? address : null;
            }
            if (isIpv4Literal(candidate)) {
                return InetAddress.getByName(candidate);
            }
            return null;
        } catch (UnknownHostException ex) {
            return null;
        }
    }

    private boolean isIpv4Literal(String value) {
        String[] parts = value.split("\\.", -1);
        if (parts.length != 4) {
            return false;
        }

        for (String part : parts) {
            if (part.isBlank() || part.length() > 3) {
                return false;
            }
            for (int i = 0; i < part.length(); i++) {
                if (!Character.isDigit(part.charAt(i))) {
                    return false;
                }
            }
            int octet = Integer.parseInt(part);
            if (octet < 0 || octet > 255) {
                return false;
            }
        }

        return true;
    }

    private String normalize(InetAddress address) {
        return address.getHostAddress().toLowerCase(Locale.ROOT);
    }

    private String viewerType(InetAddress address) {
        if (address instanceof Inet4Address) {
            return "IPV4";
        }
        return "IPV6";
    }

    private String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is not available", ex);
        }
    }
}
