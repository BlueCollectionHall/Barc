package com.miaoyu.barc.email.service;

import com.miaoyu.barc.exception.ApiException;
import com.miaoyu.barc.user.mapper.UserBasicMapper;
import com.miaoyu.barc.user.mapper.VerificationCodeMapper;
import com.miaoyu.barc.user.model.UserBasicModel;
import com.miaoyu.barc.user.model.VerificationCodeModel;
import com.miaoyu.barc.user.service.AccountSecurityService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 验证码签发按数据库锁串行化，提交前不发信，重复或失败请求不产生额外邮件。 */
@ExtendWith(MockitoExtension.class)
class SendEmailServiceTest {
    private static final String EMAIL = "signup-test@example.com";
    @Mock private VerificationCodeMapper codes;
    @Mock private UserBasicMapper users;
    @Mock private MailOutbox mail;
    @Mock private AccountSecurityService security;
    @InjectMocks private SendEmailService service;

    @Test void signupInvalidatesOldCodesBeforeInsertingAndDispatchesAfterCommit() {
        when(codes.tryMarkIssued(EMAIL, "Signup")).thenReturn(1);
        when(codes.insert(any(), eq(30))).thenReturn(true);
        TransactionSynchronizationManager.initSynchronization();
        try {
            Object id = service.sendSignupEmailService(EMAIL).getBody().getData();
            InOrder order = inOrder(codes);
            order.verify(codes).ensureIssueGuard(EMAIL, "Signup"); order.verify(codes).tryMarkIssued(EMAIL, "Signup");
            order.verify(codes).invalidatePrevious(EMAIL, "Signup");
            ArgumentCaptor<VerificationCodeModel> captured = ArgumentCaptor.forClass(VerificationCodeModel.class);
            order.verify(codes).insert(captured.capture(), eq(30));
            assertEquals(id, captured.getValue().getUnique_id()); assertTrue(captured.getValue().getCode().matches("[0-9]{6}"));
            for (TransactionSynchronization callback : TransactionSynchronizationManager.getSynchronizations()) callback.afterCommit();
            verify(mail).enqueue(eq("verification:" + id), eq(EMAIL), anyString(), contains(captured.getValue().getCode()), any());
        } finally { TransactionSynchronizationManager.clearSynchronization(); }
    }

    @Test void outboxFailurePropagatesToRollbackCodeAndGuard() {
        when(codes.tryMarkIssued(EMAIL, "Signup")).thenReturn(1);
        when(codes.insert(any(), eq(30))).thenReturn(true);
        TransactionSynchronizationManager.initSynchronization();
        try {
            doThrow(new IllegalStateException("队列写入失败")).when(mail).enqueue(anyString(), anyString(), anyString(), anyString(), any());
            assertThrows(IllegalStateException.class, () -> service.sendSignupEmailService(EMAIL));
        } finally { TransactionSynchronizationManager.clearSynchronization(); }
    }

    @Test void issuanceWithinCooldownDoesNotInvalidateCurrentCode() {
        assertThrows(ApiException.class, () -> service.sendSignupEmailService(EMAIL));
        verify(codes, never()).invalidatePrevious(any(), any()); verify(codes, never()).insert(any(), anyInt()); verifyNoInteractions(mail);
    }

    @Test void insertFailureThrowsSoPreviousCodeInvalidationRollsBack() {
        when(codes.tryMarkIssued(EMAIL, "Signup")).thenReturn(1);
        assertThrows(ApiException.class, () -> service.sendSignupEmailService(EMAIL)); verifyNoInteractions(mail);
    }

    @Test void resetUsesSpecificScenarioAndRegisteredEmail() {
        assertEquals(1, service.sendResetPasswordEmailService(EMAIL).getBody().getCode());
        when(users.selectByEmail(EMAIL)).thenReturn(new UserBasicModel());
        when(codes.tryMarkIssued(EMAIL, "ResetPassword")).thenReturn(1);
        when(codes.insert(any(), eq(5))).thenReturn(true);
        assertEquals(0, service.sendResetPasswordEmailService(EMAIL).getBody().getCode());
        verify(mail).enqueue(anyString(), eq(EMAIL), anyString(), contains("ResetPassword"), any());
        verify(codes).invalidatePrevious(EMAIL, "ResetPassword");
    }

    @Test void emailChangeRequiresCurrentPasswordAndUnusedDestination() {
        when(codes.tryMarkIssued(EMAIL, "ChangeEmail")).thenReturn(1);
        when(codes.insert(any(), eq(5))).thenReturn(true);
        assertEquals(0, service.sendChangeEmail("user", EMAIL, "password").getBody().getCode());
        verify(security).verifyCurrentPassword("user", "password");
        verify(mail).enqueue(anyString(), eq(EMAIL), anyString(), contains("ChangeEmail"), any());
        when(users.selectByEmail(EMAIL)).thenReturn(new UserBasicModel());
        assertEquals(1, service.sendChangeEmail("user", EMAIL, "password").getBody().getCode());
        assertEquals(1, service.sendSignupEmailService(EMAIL).getBody().getCode());
    }
}
