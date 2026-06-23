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
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;

@Component
public class ClientIpResolver {
    private static final String HEADER_X_REAL_IP = "X-Real-IP";
    private static final String HEADER_X_FORWARDED_FOR = "X-Forwarded-For";

    public ClientIpInfo resolve(HttpServletRequest request) {
        if (request == null) {
            return null;
        }

        for (String candidate : candidates(request)) {
            InetAddress address = parseIp(candidate);
            if (address != null) {
                String normalizedIp = normalize(address);
                return new ClientIpInfo(viewerType(address), normalizedIp, sha256Hex(normalizedIp));
            }
        }

        return null;
    }

    private List<String> candidates(HttpServletRequest request) {
        return Arrays.asList(
                request.getHeader(HEADER_X_REAL_IP),
                firstForwardedFor(request.getHeader(HEADER_X_FORWARDED_FOR)),
                request.getRemoteAddr()
        );
    }

    private String firstForwardedFor(String headerValue) {
        if (headerValue == null) {
            return null;
        }
        return headerValue.split(",", 2)[0];
    }

    private InetAddress parseIp(String value) {
        if (value == null) {
            return null;
        }

        String candidate = value.trim();
        if (candidate.isBlank() || "unknown".equalsIgnoreCase(candidate)) {
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
