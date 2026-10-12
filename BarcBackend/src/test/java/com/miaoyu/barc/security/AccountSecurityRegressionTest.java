package com.miaoyu.barc.security;

import com.miaoyu.barc.exception.ApiException;
import com.miaoyu.barc.user.mapper.BarcNaigosUuidMapper;
import com.miaoyu.barc.user.mapper.UserBasicMapper;
import com.miaoyu.barc.user.mapper.VerificationCodeMapper;
import com.miaoyu.barc.user.model.BarcNaigosUuidModel;
import com.miaoyu.barc.user.model.UserBasicModel;
import com.miaoyu.barc.user.model.dto.EmailChangeRequest;
import com.miaoyu.barc.user.model.dto.UserBasicUpdateRequest;
import com.miaoyu.barc.user.service.AccountSecurityService;
import com.miaoyu.barc.user.service.VerificationService;
import com.miaoyu.barc.utils.PasswordHash;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.time.Clock;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 改密和改绑的凭据归属、验证码一次性消费和安全字段白名单回归。 */
class AccountSecurityRegressionTest {
    private final UserBasicMapper users = mock(UserBasicMapper.class);
    private final BarcNaigosUuidMapper mappings = mock(BarcNaigosUuidMapper.class);
    private final PasswordHash hashes = mock(PasswordHash.class);
    private final VerificationCodeMapper codes = mock(VerificationCodeMapper.class);
    private final AccountSecurityService service = new AccountSecurityService();
    private final UserBasicModel user = new UserBasicModel();
    private static final String EMAIL = "owner@example.com";
    @BeforeEach void setup() {
        RequestLimits limits = new RequestLimits(Clock.systemUTC());
        VerificationService verification = new VerificationService();
        ReflectionTestUtils.setField(verification, "verificationCodeMapper", codes);
        ReflectionTestUtils.setField(verification, "limits", limits);
        ReflectionTestUtils.setField(service, "userBasicMapper", users);
        ReflectionTestUtils.setField(service, "barcNaigosUuidMapper", mappings);
        ReflectionTestUtils.setField(service, "passwordHash", hashes);
        ReflectionTestUtils.setField(service, "verificationService", verification);
        ReflectionTestUtils.setField(service, "limits", limits);
        user.setUuid("owner"); user.setUsername("name"); user.setEmail(EMAIL); user.setPassword("stored");
        when(users.selectByUuidForUpdate("owner")).thenReturn(user);
        when(users.selectByUuid("owner")).thenReturn(user);
    }

    @Test void resetCannotUseAnotherAccountsMailboxOrConsumedChallenge() {
        assertThrows(ApiException.class, () -> service.resetPassword("owner", "id", "123456", "other@example.com", "new-password"));
        verifyNoInteractions(codes);
        assertThrows(ApiException.class, () -> service.resetPassword("owner", "id", "123456", EMAIL, "new-password"));
        verify(users, never()).updatePassword(any(), any());
        when(codes.consume("valid-id", EMAIL, "ResetPassword", "123456")).thenReturn(1);
        when(hashes.encode("new-password")).thenReturn("modern"); when(users.updatePassword("owner", "modern")).thenReturn(true);
        assertEquals(0, service.resetPassword("owner", "valid-id", "123456", EMAIL, "new-password").getBody().getCode());
        verify(users).updatePassword("owner", "modern");
    }

    @Test void repeatedWrongCodesAndMalformedChallengesAreRejectedBeforePasswordWrite() {
        for (int attempt = 0; attempt < 7; attempt++) {
            assertThrows(ApiException.class, () -> service.resetPassword("owner", "id", "123456", EMAIL, "new-password"));
        }
        verify(codes, times(5)).consume("id", EMAIL, "ResetPassword", "123456");
        assertThrows(ApiException.class, () -> service.resetPassword("owner", null, "123456", EMAIL, "new-password"));
        assertThrows(ApiException.class, () -> service.resetPassword("owner", "id", "abc", EMAIL, "new-password"));
        assertThrows(ApiException.class, () -> service.resetPassword("missing", "id", "123456", EMAIL, "new-password"));
        user.setSafe_level(-1); assertThrows(ApiException.class, () -> service.resetPassword("owner", "id", "123456", EMAIL, "new-password"));
        verify(users, never()).updatePassword(any(), any());
    }

    @Test void passwordWriteFailureRaisesRollbackInsteadOfConsumingCodeWithSuccess() {
        when(codes.consume("id", EMAIL, "ResetPassword", "123456")).thenReturn(1);
        when(hashes.encode("new-password")).thenReturn("modern");
        assertThrows(ApiException.class, () -> service.resetPassword("owner", "id", "123456", EMAIL, "new-password"));
    }

    @Test void basicEditCannotChangeMailboxAndRenameRequiresPassword() {
        assertThrows(ApiException.class, () -> service.updateBasic("owner", new UserBasicUpdateRequest("owner", "name", "new@example.com", "", "")));
        assertThrows(ApiException.class, () -> service.updateBasic("owner", new UserBasicUpdateRequest("other", "name", EMAIL, "", "")));
        assertThrows(ApiException.class, () -> service.updateBasic("owner", new UserBasicUpdateRequest("owner", "new-name", EMAIL, "", "wrong")));
        verify(users, never()).update(any());
        when(users.update(any())).thenReturn(true);
        assertEquals(0, service.updateBasic("owner", new UserBasicUpdateRequest("owner", "name", EMAIL, "123", "")).getBody().getCode());
        verify(users).update(argThat(saved -> "name".equals(saved.getUsername()) && "123".equals(saved.getTelephone())
                && saved.getEmail() == null && saved.getPassword() == null && saved.getSafe_level() == null));
    }

    @Test void renamedUsernameCannotCollideAndValidRenameSucceeds() {
        when(hashes.matches("current", "stored")).thenReturn(true);
        when(users.selectByUsername("new-name")).thenReturn(new UserBasicModel());
        UserBasicUpdateRequest request = new UserBasicUpdateRequest("owner", "new-name", EMAIL, "123", "current");
        assertThrows(ApiException.class, () -> service.updateBasic("owner", request));
        when(users.selectByUsername("new-name")).thenReturn(null); when(users.update(any())).thenReturn(true);
        assertEquals(0, service.updateBasic("owner", request).getBody().getCode());
        assertThrows(ApiException.class, () -> service.updateBasic("owner", new UserBasicUpdateRequest("owner", "name", EMAIL, "1".repeat(21), "current")));
    }

    @Test void emailChangeNeedsPasswordAndNewEmailScenarioThenRevokesSession() {
        EmailChangeRequest request = new EmailChangeRequest("new@example.com", "current", "id", "123456");
        assertThrows(ApiException.class, () -> service.changeEmail("owner", request));
        verifyNoInteractions(codes);
        when(hashes.matches("current", "stored")).thenReturn(true);
        when(codes.consume("id", "new@example.com", "ChangeEmail", "123456")).thenReturn(1);
        when(users.updateVerifiedEmail("owner", "new@example.com")).thenReturn(true);
        assertEquals(0, service.changeEmail("owner", request).getBody().getCode());
        when(users.selectByEmail("new@example.com")).thenReturn(new UserBasicModel());
        assertThrows(ApiException.class, () -> service.changeEmail("owner", request));
    }

    @Test void externalDefaultOrDisabledPasswordCannotAuthorizeEmailChange() {
        user.setLocal_password_enabled(false);
        assertThrows(ApiException.class, () -> service.verifyCurrentPassword("owner", "123456"));
        user.setLocal_password_enabled(true);
        when(hashes.needsUpgrade("stored")).thenReturn(true); when(mappings.selectByUuid("owner")).thenReturn(new BarcNaigosUuidModel());
        when(hashes.matches("123456", "stored")).thenReturn(true);
        assertThrows(ApiException.class, () -> service.verifyCurrentPassword("owner", "123456"));
    }
}
