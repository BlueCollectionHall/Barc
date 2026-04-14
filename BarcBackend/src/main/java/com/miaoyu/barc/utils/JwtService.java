package com.miaoyu.barc.utils;

import com.miaoyu.barc.JwtConfig;
import com.miaoyu.barc.response.TokenR;
import com.miaoyu.barc.user.mapper.BarcNaigosUuidMapper;
import com.miaoyu.barc.user.model.BarcNaigosUuidModel;
import io.jsonwebtoken.*;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SignatureException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.util.*;

@Service
public class JwtService {
    private final JwtConfig jwtConfig;
    @Autowired
    public JwtService(JwtConfig jwtConfig) {
        this.jwtConfig = jwtConfig;
    }
    @Autowired
    private BarcNaigosUuidMapper barcNaigosUuidMapper;

    private String getKey() {
        return jwtConfig.getKey();
    }
    private String getNaigosKey() {
        return jwtConfig.getNaigos_key();
    }

    public String jwtSigned(String uuid) {
        String issuer = "com.miaoyu.barc";
        SecretKey key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(this.getKey()));
        Map<String, Object> claims = new HashMap<>();
        claims.put("uuid", uuid);
        claims.put("service", "barc");
        return Jwts.builder()
                .signWith(key, Jwts.SIG.HS256)
                .issuer(issuer)
                .expiration(new Date(System.currentTimeMillis() + 1000 * 60 * 60 * 24 * 15))
                .id(UUID.randomUUID().toString())
                .issuedAt(new Date())
                .claims(claims)
                .compact();
    }

    public J jwtParser(String token) {
        Claims payload;
        SecretKey key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(this.getKey()));
        try{
            payload = Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (ExpiredJwtException e){
            System.out.println("ExpiredJwtException" + e.getMessage());
            return new TokenR().token(false, "令牌已过期！", null);
        } catch (SignatureException e) {
            System.out.println("SignatureException" + e.getMessage());
            return new TokenR().token(false, "令牌鉴签失效！", null);
        } catch (MalformedJwtException e) {
            System.out.println("MalformedJwtException" + e.getMessage());
            return new TokenR().token(false, "令牌错误！", null);
        } catch (UnsupportedJwtException e) {
            System.out.println("UnsupportedJwtException" + e.getMessage());
            return new TokenR().token(false, "令牌解算失效！", null);
        } catch (JwtException e){
            System.out.println("JwtException" + e.getMessage());
            return new TokenR().token(false, "令牌无效！", null);
        }
        String[] issuers = payload.getIssuer().split("\\.");
        if (issuers.length < 2) {
            return new TokenR().token(false, "未知令牌来源！", null);
        }
        if (issuers[2].equals("naigos")) {
            BarcNaigosUuidModel barcNaigosUuid = barcNaigosUuidMapper.selectByNaigosUuid(payload.get("uuid").toString());
            if (Objects.isNull(barcNaigosUuid)) {
                return new TokenR().token(false, "Naigos服务Token令牌无法互认或记录不存在！", null);
            }
            return new TokenR().token(true, null, barcNaigosUuid.getUuid());
        }
        return new TokenR().token(true, null, payload.get("uuid").toString());
    }
}
