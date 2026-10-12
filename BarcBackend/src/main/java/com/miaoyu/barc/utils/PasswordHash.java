package com.miaoyu.barc.utils;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

@Service
public class PasswordHash {
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int ITERATIONS = 600000;
    private static final String PREFIX = "$pbkdf2-sha256$";
    @Autowired
    private AppService appService;

    public String passwordHash256(String password) {
        if (password == null) return null;
        String pwdKey = appService.getPwdKey();
        try{
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String input = password + pwdKey;
            byte[] encodedHash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b: encodedHash){
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e){
            return null;
        }
    }

    /** 新口令使用随机盐和 PBKDF2-HMAC-SHA256；旧 SHA-256 仅用于迁移时验证。 */
    public String encode(String password) {
        if (password == null || password.length() > 128) throw new IllegalArgumentException("密码长度无效");
        byte[] salt = new byte[16];
        RANDOM.nextBytes(salt);
        Base64.Encoder encoder = Base64.getEncoder().withoutPadding();
        return PREFIX + ITERATIONS + "$" + encoder.encodeToString(salt) + "$" + encoder.encodeToString(derive(password, salt, ITERATIONS));
    }

    /** 密码算法识别独立于 auth_version；错误或损坏的哈希必须拒绝。 */
    public boolean matches(String password, String stored) {
        if (password == null || password.length() > 128 || stored == null) return false;
        if (!stored.startsWith(PREFIX)) {
            String legacy = passwordHash256(password);
            return stored.matches("[0-9a-f]{64}") && MessageDigest.isEqual(stored.getBytes(StandardCharsets.UTF_8), legacy.getBytes(StandardCharsets.UTF_8));
        }
        try {
            String[] parts = stored.substring(PREFIX.length()).split("\\$", -1);
            if (parts.length != 3) return false;
            int iterations = Integer.parseInt(parts[0]);
            if (iterations < ITERATIONS || iterations > 1200000) return false;
            byte[] salt = Base64.getDecoder().decode(parts[1]);
            byte[] expected = Base64.getDecoder().decode(parts[2]);
            return salt.length == 16 && expected.length == 32
                    && MessageDigest.isEqual(expected, derive(password, salt, iterations));
        } catch (IllegalArgumentException failure) { return false; }
    }

    public boolean needsUpgrade(String stored) { return stored != null && !stored.startsWith(PREFIX); }

    private byte[] derive(String password, byte[] salt, int iterations) {
        PBEKeySpec specification = new PBEKeySpec(password.toCharArray(), salt, iterations, 256);
        try { return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(specification).getEncoded(); }
        catch (java.security.GeneralSecurityException failure) { throw new IllegalStateException("口令算法不可用", failure); }
        finally { specification.clearPassword(); }
    }
}
