package com.miaoyu.barc.security;

import com.miaoyu.barc.exception.ApiException;
import com.miaoyu.barc.utils.AppService;
import com.miaoyu.barc.utils.GenerateCode;
import com.miaoyu.barc.utils.PasswordHash;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.time.Clock;
import java.util.concurrent.atomic.AtomicLong;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 验证实际慢哈希、旧哈希迁移、格式拒绝与限流窗口边界。 */
class PasswordAndLimitsTest {
    @Test void saltedPasswordHashMatchesAndSupportsLegacyWithoutRewritingSecrets() {
        PasswordHash hash = new PasswordHash(); AppService app = mock(AppService.class);
        when(app.getPwdKey()).thenReturn("test-only-pepper"); ReflectionTestUtils.setField(hash, "appService", app);
        String encoded = hash.encode("密码-abc12345"); String second = hash.encode("密码-abc12345");
        assertNotEquals(encoded, second); assertTrue(hash.matches("密码-abc12345", encoded)); assertFalse(hash.matches("wrong", encoded));
        String old = hash.passwordHash256("old"); assertTrue(hash.matches("old", old)); assertFalse(hash.matches("wrong", old));
        assertTrue(hash.needsUpgrade(old)); assertFalse(hash.needsUpgrade(encoded)); assertFalse(hash.needsUpgrade(null));
        assertNull(hash.passwordHash256(null)); assertFalse(hash.matches(null, old)); assertFalse(hash.matches("a", null));
        assertFalse(hash.matches("a".repeat(129), encoded)); assertThrows(IllegalArgumentException.class, () -> hash.encode(null));
        for (String bad : new String[]{"junk", "$pbkdf2-sha256$x", "$pbkdf2-sha256$a$a$a", "$pbkdf2-sha256$1$a$a", "$pbkdf2-sha256$999999999$a$a", "$pbkdf2-sha256$600000$bad$bad", "$pbkdf2-sha256$600000$?$?"}) {
            assertFalse(hash.matches("a", bad), bad);
        }
    }
    @Test void rateLimitResetsOnlyAfterWindowAndBoundsUniqueKeys() {
        Clock clock = mock(Clock.class); AtomicLong now = new AtomicLong(1000);
        when(clock.millis()).thenAnswer(call -> now.get()); RequestLimits limits = new RequestLimits(clock);
        assertTrue(limits.allow("one", 2, 1000)); assertTrue(limits.allow("one", 2, 1000)); assertFalse(limits.allow("one", 2, 1000));
        assertThrows(ApiException.class, () -> limits.require("one", 2, 1000));
        now.set(2000); limits.require("one", 2, 1000);
        for (int index = 0; index < 19999; index++) assertTrue(limits.allow("key" + index, 1, 1000));
        assertFalse(limits.allow("over-limit", 1, 1000)); now.set(3001);
        assertTrue(limits.allow("after-expiry", 1, 1000));
    }
    @Test void accountAndVerificationCodeInputHasExplicitBounds() {
        assertEquals("name@example.com", AccountInput.email(" NAME@example.com "));
        for (String invalid : new String[]{"bad", "a@b", "x\n@y.com"}) assertThrows(ApiException.class, () -> AccountInput.email(invalid));
        assertThrows(ApiException.class, () -> AccountInput.email(null)); assertThrows(ApiException.class, () -> AccountInput.email("a".repeat(256)));
        AccountInput.password("12345678"); AccountInput.username("有效用户名");
        assertThrows(ApiException.class, () -> AccountInput.password(null)); assertThrows(ApiException.class, () -> AccountInput.password("short"));
        assertThrows(ApiException.class, () -> AccountInput.password("a".repeat(129)));
        assertThrows(ApiException.class, () -> AccountInput.username(" user")); assertThrows(ApiException.class, () -> AccountInput.username(" "));
        assertThrows(ApiException.class, () -> AccountInput.username(null)); assertThrows(ApiException.class, () -> AccountInput.username("a".repeat(51)));
        GenerateCode code = new GenerateCode(); assertTrue(code.code(6).matches("[0-9]{6}"));
        assertThrows(IllegalArgumentException.class, () -> code.code(0)); assertThrows(IllegalArgumentException.class, () -> code.code(33));
    }
}
