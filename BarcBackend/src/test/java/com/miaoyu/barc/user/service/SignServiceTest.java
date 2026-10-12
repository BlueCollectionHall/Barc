package com.miaoyu.barc.user.service;

import com.miaoyu.barc.exception.ApiException;
import com.miaoyu.barc.security.RequestLimits;
import com.miaoyu.barc.user.mapper.*;
import com.miaoyu.barc.user.model.*;
import com.miaoyu.barc.user.model.dto.SignupRequest;
import com.miaoyu.barc.utils.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import java.io.IOException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 账号创建、历史口令升级、外部账号隔离及注册事务边界回归。 */
@ExtendWith(MockitoExtension.class)
class SignServiceTest {
    @Mock private UserBasicMapper basics;
    @Mock private UserArchiveMapper archives;
    @Mock private BarcNaigosUuidMapper mappings;
    @Mock private PasswordHash passwordHash;
    @Mock private JwtService jwt;
    @Mock private NaigosService naigos;
    @Mock private VerificationService verification;
    @Mock private RequestLimits limits;
    @InjectMocks private SignService service;
    private static final String EMAIL = "signup-test@example.com";
    private static final String PASSWORD = "correct-password";

    private UserBasicModel input() {
        UserBasicModel user = new UserBasicModel(); user.setUuid("user"); user.setUsername("newbie");
        user.setEmail(EMAIL); user.setPassword(PASSWORD); return user;
    }
    private SignupRequest registration() { return new SignupRequest("newbie", PASSWORD, EMAIL, null, "code-id", "123456"); }

    @Test void signupConsumesCorrectScenarioBeforeWritingAccount() {
        when(passwordHash.encode(PASSWORD)).thenReturn("encoded");
        when(basics.insert(any())).thenReturn(true); when(archives.insert(any())).thenReturn(true);
        assertEquals(0, service.register(registration()).getBody().getCode());
        InOrder order = inOrder(verification, basics, archives);
        order.verify(verification).consume("code-id", EMAIL, "Signup", "123456");
        order.verify(basics).insert(argThat(user -> user.getEmail_verified() && user.getLocal_password_enabled()
                && user.getPassword_version() == 2 && user.getPassword().equals("encoded")));
        order.verify(archives).insert(argThat(user -> user.getUuid() != null && user.getPermission() == 1));
    }

    @Test void badVerificationOrDuplicatesNeverCreateUser() {
        doThrow(new ApiException(new J(1, "invalid", null))).when(verification).consume(any(), any(), any(), any());
        assertThrows(ApiException.class, () -> service.register(registration()));
        verify(basics, never()).insert(any());
        when(basics.selectByUsername("newbie")).thenReturn(input());
        assertThrows(ApiException.class, () -> service.register(registration()));
    }

    @Test void duplicateEmailAndInvalidInputAreRejected() {
        when(basics.selectByEmail(EMAIL)).thenReturn(input());
        assertThrows(ApiException.class, () -> service.register(registration()));
        assertThrows(ApiException.class, () -> service.register(new SignupRequest("newbie", "short", EMAIL, null, "id", "123456")));
        verifyNoInteractions(verification); verify(basics, never()).insert(any());
    }

    @Test void everyPartialRegistrationFailureThrowsForRollback() {
        when(passwordHash.encode(PASSWORD)).thenReturn("encoded");
        assertThrows(ApiException.class, () -> service.register(registration()));
        when(basics.insert(any())).thenReturn(true);
        assertThrows(ApiException.class, () -> service.register(registration()));
        verify(archives).insert(any());
    }

    @Test void adminCreatedAccountDoesNotInventVerifiedEmailOrTrustSecurityFields() {
        UserBasicModel input = input(); input.setSafe_level(-1); input.setEmail_verified(true); input.setPassword_version(-1);
        when(passwordHash.encode(PASSWORD)).thenReturn("encoded");
        when(basics.insert(any())).thenReturn(true); when(archives.insert(any())).thenReturn(true);
        assertEquals(0, service.signupUserService(input, EMAIL).getBody().getCode());
        verify(basics).insert(argThat(user -> !user.getEmail_verified() && user.getSafe_level() == null
                && !"user".equals(user.getUuid()) && user.getPassword_version() == 2));
        assertEquals(1, service.signupUserService(input, "wrong@example.com").getBody().getCode());
        assertEquals(1, service.signupUserService(null, EMAIL).getBody().getCode());
    }

    @Test void localLoginUpgradesLegacyHashWithoutOverwritingConcurrentChange() {
        UserBasicModel user = input(); user.setPassword("old-hash");
        when(basics.selectByUsername("newbie")).thenReturn(user);
        when(passwordHash.matches(PASSWORD, "old-hash")).thenReturn(true);
        when(passwordHash.needsUpgrade("old-hash")).thenReturn(true);
        when(passwordHash.encode(PASSWORD)).thenReturn("modern-hash");
        assertEquals(1, service.signInUserService("username", "newbie", PASSWORD).getBody().getCode());
        verify(jwt, never()).jwtSigned(any());
        when(basics.upgradePassword("user", "old-hash", "modern-hash")).thenReturn(true);
        when(jwt.jwtSigned("user")).thenReturn("token");
        assertEquals("token", service.signInUserService("username", "newbie", PASSWORD).getBody().getData());
    }

    @Test void externalLegacyDefaultPasswordCannotUseLocalLogin() {
        UserBasicModel user = input(); user.setPassword("default-hash");
        when(basics.selectByUsername("newbie")).thenReturn(user);
        when(passwordHash.needsUpgrade("default-hash")).thenReturn(true);
        when(mappings.selectByUuid("user")).thenReturn(new BarcNaigosUuidModel());
        when(passwordHash.matches("123456", "default-hash")).thenReturn(true);
        assertEquals(1, service.signInUserService("username", "newbie", "123456").getBody().getCode());
        verifyNoInteractions(jwt);
    }

    @Test void bannedDisabledMissingAndWrongPasswordAccountsCannotLogin() {
        assertEquals(1, service.signInUserService("unknown", "account", PASSWORD).getBody().getCode());
        assertEquals(1, service.signInUserService("username", null, PASSWORD).getBody().getCode());
        assertEquals(1, service.signInUserService("username", "", PASSWORD).getBody().getCode());
        assertEquals(1, service.signInUserService("username", "newbie", null).getBody().getCode());
        UserBasicModel user = input(); user.setSafe_level(-1);
        when(basics.selectByEmail(EMAIL)).thenReturn(user);
        assertEquals(1, service.signInUserService("email", EMAIL, PASSWORD).getBody().getCode());
        user.setSafe_level(100); user.setLocal_password_enabled(false);
        assertEquals(1, service.signInUserService("email", EMAIL, PASSWORD).getBody().getCode());
        user.setLocal_password_enabled(true);
        assertEquals(1, service.signInUserService("email", EMAIL, PASSWORD).getBody().getCode());
        when(passwordHash.matches(PASSWORD, PASSWORD)).thenReturn(true);
        when(jwt.jwtSigned("user")).thenReturn("token");
        assertEquals(0, service.signInUserService("email", EMAIL, PASSWORD).getBody().getCode());
    }

    @Test void existingExternalAccountUsesMappingAndChecksBan() throws Exception {
        NaigosUserArchiveModel remote = remote();
        when(naigos.getNaigosToken("uid", "account", PASSWORD)).thenReturn("remote-token");
        when(naigos.getNaigosArchive("remote-token")).thenReturn(remote);
        BarcNaigosUuidModel mapping = new BarcNaigosUuidModel(); mapping.setUuid("user");
        when(mappings.selectByNaigosUuid(remote.getGroup_real_user_id())).thenReturn(mapping);
        assertEquals(1, service.signInByNaigosService("uid", "account", PASSWORD).getBody().getCode());
        when(basics.selectByUuid("user")).thenReturn(input()); when(jwt.jwtSigned("user")).thenReturn("token");
        assertEquals("token", service.signInByNaigosService("uid", "account", PASSWORD).getBody().getData());
        verify(basics, never()).insert(any());
    }

    @Test void newExternalAccountGetsRandomDisabledLocalPasswordAndUnverifiedEmail() throws Exception {
        NaigosUserArchiveModel remote = remote(); remote.setEmail("未知邮箱");
        when(naigos.getNaigosToken("uid", "account", PASSWORD)).thenReturn("remote-token");
        when(naigos.getNaigosArchive("remote-token")).thenReturn(remote);
        when(passwordHash.encode(anyString())).thenReturn("random-hash");
        when(basics.insert(any())).thenReturn(true); when(archives.insert(any())).thenReturn(true); when(mappings.insert(any())).thenReturn(true);
        when(jwt.jwtSigned(any())).thenReturn("token");
        assertEquals(0, service.signInByNaigosService("uid", "account", PASSWORD).getBody().getCode());
        verify(basics).insert(argThat(user -> !user.getLocal_password_enabled() && !user.getEmail_verified() && "random-hash".equals(user.getPassword())));
        verify(passwordHash, never()).encode("123456");
    }

    @Test void externalFailuresDoNotCreateHalfAccounts() throws Exception {
        assertEquals(1, service.signInByNaigosService("bad", "account", PASSWORD).getBody().getCode());
        when(naigos.getNaigosToken("uid", "account", PASSWORD)).thenReturn(null).thenThrow(new IOException("offline"));
        assertEquals(1, service.signInByNaigosService("uid", "account", PASSWORD).getBody().getCode());
        assertThrows(ApiException.class, () -> service.signInByNaigosService("uid", "account", PASSWORD));
        verify(basics, never()).insert(any());
    }

    private NaigosUserArchiveModel remote() {
        NaigosUserArchiveModel remote = new NaigosUserArchiveModel();
        remote.setGroup_real_user_id("remote-user"); remote.setQq_id(12345L); remote.setEmail(EMAIL);
        remote.setNickname("外部用户"); return remote;
    }
}
