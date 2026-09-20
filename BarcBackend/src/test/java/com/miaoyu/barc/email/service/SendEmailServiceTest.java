package com.miaoyu.barc.email.service;

import com.miaoyu.barc.user.mapper.UserBasicMapper;
import com.miaoyu.barc.user.mapper.VerificationCodeMapper;
import com.miaoyu.barc.user.model.UserBasicModel;
import com.miaoyu.barc.user.model.VerificationCodeModel;
import com.miaoyu.barc.utils.J;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 发送验证码服务测试。
 *
 * 重点守护注册链路的核心修复：
 * 1、发码接口只做「落库并返回 unique_id」，SMTP 发信必须交给异步发送器，
 *    不能在同步链路上阻塞（同步 SMTP 实测 12~13 秒，超过前端 10 秒超时）；
 * 2、返回的 unique_id 必须是本次落库记录的主键，前端才可能注册成功；
 * 3、同一邮箱重复发码要作废旧记录，避免新旧验证码错配。
 */
@ExtendWith(MockitoExtension.class)
class SendEmailServiceTest {

    private static final String EMAIL = "signup-test@example.com";

    @Mock
    private VerificationCodeMapper verificationCodeMapper;
    @Mock
    private MailDispatcher mailDispatcher;
    @Mock
    private UserBasicMapper userBasicMapper;

    @InjectMocks
    private SendEmailService sendEmailService;

    // ==================== 注册验证码 ====================

    @Test
    @DisplayName("注册发码：落库成功后立即返回 unique_id，且发信交给异步发送器")
    void sendSignupEmail_WhenInsertSucceeds_ShouldReturnUniqueIdAndDispatchAsync() {
        when(verificationCodeMapper.insert(any(VerificationCodeModel.class), eq(30))).thenReturn(true);
        // 本次插入的就是最新一条，因此不应触发旧码作废
        when(verificationCodeMapper.selectLatestByUsernameAndScenario(EMAIL, "Signup")).thenReturn(null);

        ResponseEntity<J> response = sendEmailService.sendSignupEmailService(EMAIL);

        assertNotNull(response.getBody());
        assertEquals(0, response.getBody().getCode());

        ArgumentCaptor<VerificationCodeModel> inserted = ArgumentCaptor.forClass(VerificationCodeModel.class);
        verify(verificationCodeMapper).insert(inserted.capture(), eq(30));
        VerificationCodeModel vc = inserted.getValue();
        assertEquals(EMAIL, vc.getUsername());
        assertEquals("Signup", vc.getScenario());
        assertEquals(6, vc.getCode().length(), "验证码应为 6 位");
        assertNotNull(vc.getUnique_id());

        // 返回给前端的 unique_id 必须与落库记录一致，否则注册必然报「验证码会话不存在」
        assertEquals(vc.getUnique_id(), response.getBody().getData());

        // 关键断言：发信走异步发送器，而不是在同步链路上调用 SMTP
        verify(mailDispatcher).dispatchSignupEmail(eq(EMAIL), eq(vc.getCode()), eq(30));
    }

    @Test
    @DisplayName("注册发码：落库失败时返回失败，且不得发送邮件")
    void sendSignupEmail_WhenInsertFails_ShouldReturnErrorWithoutSendingMail() {
        when(verificationCodeMapper.insert(any(VerificationCodeModel.class), anyInt())).thenReturn(false);

        ResponseEntity<J> response = sendEmailService.sendSignupEmailService(EMAIL);

        assertNotNull(response.getBody());
        assertEquals(1, response.getBody().getCode());
        verify(mailDispatcher, never()).dispatchSignupEmail(anyString(), anyString(), anyInt());
    }

    @Test
    @DisplayName("注册发码：同一邮箱已有旧记录时必须把旧记录标记为已使用，避免新旧验证码错配")
    void sendSignupEmail_WhenPreviousCodeExists_ShouldInvalidatePrevious() {
        when(verificationCodeMapper.insert(any(VerificationCodeModel.class), eq(30))).thenReturn(true);
        VerificationCodeModel previous = new VerificationCodeModel();
        previous.setUnique_id("previous-unique-id");
        previous.setCode("000000");
        previous.setScenario("Signup");
        previous.setUsername(EMAIL);
        when(verificationCodeMapper.selectLatestByUsernameAndScenario(EMAIL, "Signup")).thenReturn(previous);

        ResponseEntity<J> response = sendEmailService.sendSignupEmailService(EMAIL);

        assertNotNull(response.getBody());
        assertEquals(0, response.getBody().getCode());
        // 旧记录必须被作废，否则用户拿旧邮件的验证码配新 unique_id 会必然失败
        verify(verificationCodeMapper).updateUsed("previous-unique-id");
        assertNotEquals("previous-unique-id", response.getBody().getData());
    }

    @Test
    @DisplayName("注册发码：库中最新记录就是本次插入的那条时，不得把自己作废")
    void sendSignupEmail_WhenLatestRecordIsCurrent_ShouldNotInvalidateIt() {
        ArgumentCaptor<VerificationCodeModel> inserted = ArgumentCaptor.forClass(VerificationCodeModel.class);
        when(verificationCodeMapper.insert(inserted.capture(), eq(30))).thenReturn(true);
        when(verificationCodeMapper.selectLatestByUsernameAndScenario(eq(EMAIL), eq("Signup")))
                .thenAnswer(invocation -> {
                    VerificationCodeModel latest = new VerificationCodeModel();
                    latest.setUnique_id(inserted.getValue().getUnique_id());
                    return latest;
                });

        sendEmailService.sendSignupEmailService(EMAIL);

        verify(verificationCodeMapper, never()).updateUsed(anyString());
    }

    // ==================== 重置密码验证码 ====================

    @Test
    @DisplayName("重置密码发码：用户存在时返回 unique_id 并异步发信")
    void sendResetPasswordEmail_WhenUserExists_ShouldReturnUniqueIdAndDispatchAsync() {
        UserBasicModel user = new UserBasicModel();
        user.setUuid("user-uuid");
        user.setEmail(EMAIL);
        when(userBasicMapper.selectByEmail(EMAIL)).thenReturn(user);
        when(verificationCodeMapper.insert(any(VerificationCodeModel.class), eq(5))).thenReturn(true);

        ResponseEntity<J> response = sendEmailService.sendResetPasswordEmailService(EMAIL);

        assertNotNull(response.getBody());
        assertEquals(0, response.getBody().getCode());
        ArgumentCaptor<VerificationCodeModel> inserted = ArgumentCaptor.forClass(VerificationCodeModel.class);
        verify(verificationCodeMapper).insert(inserted.capture(), eq(5));
        assertEquals("ResetPassword", inserted.getValue().getScenario());
        assertEquals(inserted.getValue().getUnique_id(), response.getBody().getData());
        verify(mailDispatcher).dispatchResetPasswordEmail(eq(EMAIL), eq(inserted.getValue().getCode()), eq(5));
    }

    @Test
    @DisplayName("重置密码发码：用户不存在时返回未找到用户，且不落库不发信")
    void sendResetPasswordEmail_WhenUserMissing_ShouldReturnNoSuchUser() {
        when(userBasicMapper.selectByEmail(EMAIL)).thenReturn(null);

        ResponseEntity<J> response = sendEmailService.sendResetPasswordEmailService(EMAIL);

        assertNotNull(response.getBody());
        assertEquals(1, response.getBody().getCode());
        verify(verificationCodeMapper, never()).insert(any(VerificationCodeModel.class), anyInt());
        verify(mailDispatcher, never()).dispatchResetPasswordEmail(anyString(), anyString(), anyInt());
    }

    @Test
    @DisplayName("重置密码发码：落库失败时返回失败，且不发信")
    void sendResetPasswordEmail_WhenInsertFails_ShouldReturnError() {
        UserBasicModel user = new UserBasicModel();
        user.setUuid("user-uuid");
        when(userBasicMapper.selectByEmail(EMAIL)).thenReturn(user);
        when(verificationCodeMapper.insert(any(VerificationCodeModel.class), anyInt())).thenReturn(false);

        ResponseEntity<J> response = sendEmailService.sendResetPasswordEmailService(EMAIL);

        assertNotNull(response.getBody());
        assertEquals(1, response.getBody().getCode());
        verify(mailDispatcher, never()).dispatchResetPasswordEmail(anyString(), anyString(), anyInt());
    }
}
