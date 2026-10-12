package com.miaoyu.barc.utils;

import com.miaoyu.barc.JwtConfig;
import com.miaoyu.barc.exception.ApiException;
import com.miaoyu.barc.response.TokenR;
import com.miaoyu.barc.response.UserR;
import com.miaoyu.barc.user.mapper.UserBasicMapper;
import com.miaoyu.barc.user.model.UserBasicModel;
import io.jsonwebtoken.*;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import javax.crypto.SecretKey;
import java.util.Date;
import java.util.UUID;

/** Barc 令牌必须具有完整来源、用户与会话版本；外部账号通过登录交换签发本服务令牌。 */
@Service
public class JwtService {
    private final JwtConfig jwtConfig;
    @Autowired private UserBasicMapper userBasicMapper;

    public JwtService(JwtConfig jwtConfig) { this.jwtConfig = jwtConfig; }

    private SecretKey key() { return Keys.hmacShaKeyFor(Decoders.BASE64.decode(jwtConfig.getKey())); }

    public String jwtSigned(String uuid) {
        UserBasicModel user = userBasicMapper.selectByUuid(uuid);
        if (!active(user)) throw new ApiException(new UserR().insufficientAccountPermission());
        return Jwts.builder().signWith(key(), Jwts.SIG.HS256).issuer("com.miaoyu.barc")
                .expiration(new Date(System.currentTimeMillis() + 1000L * 60 * 60 * 24 * 15))
                .id(UUID.randomUUID().toString()).issuedAt(new Date())
                .claim("uuid", uuid).claim("service", "barc")
                .claim("auth_version", version(user)).compact();
    }

    /** 同时接受宪法格式 Token {jwt} 和旧客户端裸 JWT，解析异常永远转为认证失败。 */
    public J jwtParser(String token) {
        if (token == null || token.isBlank()) return invalid();
        String normalized = token.trim();
        if (normalized.regionMatches(true, 0, "Token ", 0, 6)) normalized = normalized.substring(6).trim();
        try {
            Claims claims = Jwts.parser().verifyWith(key()).requireIssuer("com.miaoyu.barc")
                    .require("service", "barc").build().parseSignedClaims(normalized).getPayload();
            String uuid = claims.get("uuid", String.class);
            Number claimVersion = claims.get("auth_version", Number.class);
            if (uuid == null || uuid.isBlank() || claimVersion == null || claims.getExpiration() == null) return invalid();
            UserBasicModel user = userBasicMapper.selectByUuid(uuid);
            if (!active(user) || claimVersion.longValue() != version(user)) return invalid();
            return new TokenR().token(true, null, uuid);
        } catch (JwtException | IllegalArgumentException failure) { return invalid(); }
    }

    private boolean active(UserBasicModel user) {
        return user != null && (user.getSafe_level() == null || user.getSafe_level() >= 0);
    }
    private long version(UserBasicModel user) { return user.getAuth_version() == null ? 0L : user.getAuth_version(); }
    private J invalid() { return new TokenR().token(false, "登录状态已失效，请重新登录", null); }
}
