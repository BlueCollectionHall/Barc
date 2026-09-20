package com.miaoyu.barc.user.service;

import com.miaoyu.barc.user.mapper.BarcNaigosUuidMapper;
import com.miaoyu.barc.user.mapper.UserArchiveMapper;
import com.miaoyu.barc.user.mapper.UserBanRecordMapper;
import com.miaoyu.barc.user.mapper.UserBasicMapper;
import com.miaoyu.barc.user.mapper.VerificationCodeMapper;
import com.miaoyu.barc.user.model.UserArchiveModel;
import com.miaoyu.barc.user.model.UserBasicModel;
import com.miaoyu.barc.user.model.VerificationCodeModel;
import com.miaoyu.barc.utils.J;
import com.miaoyu.barc.utils.PasswordHash;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 注册服务测试。
 *
 * 覆盖本次注册链路修复的关键行为：
 * 1、验证码记录不存在时必须给出「验证码会话不存在或已过期」的可读文案，
 *    而不是内部实现用语「资源获取失败: Such resource fail」——那会让用户误判为网络不通；
 * 2、校验通过必须把验证码标记为已使用，防止同一验证码重复注册；
 * 3、基础信息写入成功但个人信息写入失败时必须抛异常触发事务回滚，不能留下孤儿账号。
 */
@ExtendWith(MockitoExtension.class)
class SignServiceTest {

    private static final String EMAIL = "signup-test@example.com";
    private static final String UNIQUE_ID = "unique-id-0001";
    private static final String CODE = "123456";

    @Mock
    private VerificationCodeMapper verificationCodeMapper;
    @Mock
    private UserBasicMapper userBasicMapper;
    @Mock
    private BarcNaigosUuidMapper barcNaigosUuidMapper;
    @Mock
    private UserArchiveMapper userArchiveMapper;
    @Mock
    private UserBanRecordMapper userBanRecordMapper;
    @Mock
    private PasswordHash passwordHash;

    @InjectMocks
    private SignService signService;

    /** 构造一条可用的验证码记录 */
    private VerificationCodeModel validCodeRecord() {
        VerificationCodeModel vc = new VerificationCodeModel();
        vc.setUnique_id(UNIQUE_ID);
        vc.setCode(CODE);
        vc.setUsername(EMAIL);
        vc.setScenario("Signup");
        vc.setUsed(false);
        vc.setExpiration_at(LocalDateTime.now().plusMinutes(30));
        return vc;
    }

    // ==================== 验证码校验 ====================

    @Test
    @DisplayName("校验：验证码记录不存在时返回「验证码会话不存在或已过期」，不再回传资源获取失败")
    void checkSignupCode_WhenRecordMissing_ShouldReturnActionableMessage() {
        when(verificationCodeMapper.selectByUniqueId(UNIQUE_ID)).thenReturn(null);

        ResponseEntity<J> result = signService.checkSignupCode(UNIQUE_ID, CODE, EMAIL);

        assertNotNull(result, "记录不存在时必须返回失败响应");
        assertNotNull(result.getBody());
        assertEquals(1, result.getBody().getCode());
        String message = String.valueOf(result.getBody().getData());
        assertTrue(message.contains("验证码会话不存在或已过期"), "实际文案：" + message);
        assertFalse(message.contains("Such resource fail"), "不得再暴露内部实现用语");
        verify(verificationCodeMapper, never()).updateUsed(anyString());
    }

    @Test
    @DisplayName("校验：邮箱不匹配时返回验证信息不匹配")
    void checkSignupCode_WhenEmailMismatch_ShouldReject() {
        when(verificationCodeMapper.selectByUniqueId(UNIQUE_ID)).thenReturn(validCodeRecord());

        ResponseEntity<J> result = signService.checkSignupCode(UNIQUE_ID, CODE, "other@example.com");

        assertNotNull(result);
        assertEquals("验证信息不匹配", result.getBody().getData());
        verify(verificationCodeMapper, never()).updateUsed(anyString());
    }

    @Test
    @DisplayName("校验：场景不是 Signup 时返回验证信息不匹配")
    void checkSignupCode_WhenScenarioMismatch_ShouldReject() {
        VerificationCodeModel vc = validCodeRecord();
        vc.setScenario("ResetPassword");
        when(verificationCodeMapper.selectByUniqueId(UNIQUE_ID)).thenReturn(vc);

        ResponseEntity<J> result = signService.checkSignupCode(UNIQUE_ID, CODE, EMAIL);

        assertNotNull(result);
        assertEquals("验证信息不匹配", result.getBody().getData());
    }

    @Test
    @DisplayName("校验：验证码已被使用过时返回验证信息不匹配（防止重复注册）")
    void checkSignupCode_WhenAlreadyUsed_ShouldReject() {
        VerificationCodeModel vc = validCodeRecord();
        vc.setUsed(true);
        when(verificationCodeMapper.selectByUniqueId(UNIQUE_ID)).thenReturn(vc);

        ResponseEntity<J> result = signService.checkSignupCode(UNIQUE_ID, CODE, EMAIL);

        assertNotNull(result);
        assertEquals("验证信息不匹配", result.getBody().getData());
    }

    @Test
    @DisplayName("校验：used 为 null 时按未使用处理，不抛空指针")
    void checkSignupCode_WhenUsedIsNull_ShouldNotThrow() {
        VerificationCodeModel vc = validCodeRecord();
        vc.setUsed(null);
        when(verificationCodeMapper.selectByUniqueId(UNIQUE_ID)).thenReturn(vc);

        ResponseEntity<J> result = signService.checkSignupCode(UNIQUE_ID, CODE, EMAIL);

        assertNull(result, "used 为 null 应视为未使用并校验通过");
    }

    @Test
    @DisplayName("校验：验证码不匹配时返回验证码不匹配")
    void checkSignupCode_WhenCodeWrong_ShouldReject() {
        when(verificationCodeMapper.selectByUniqueId(UNIQUE_ID)).thenReturn(validCodeRecord());

        ResponseEntity<J> result = signService.checkSignupCode(UNIQUE_ID, "000000", EMAIL);

        assertNotNull(result);
        assertEquals("验证码不匹配", result.getBody().getData());
        verify(verificationCodeMapper, never()).updateUsed(anyString());
    }

    @Test
    @DisplayName("校验：验证码已过期时返回验证码已过期")
    void checkSignupCode_WhenExpired_ShouldReject() {
        VerificationCodeModel vc = validCodeRecord();
        vc.setExpiration_at(LocalDateTime.now().minusSeconds(1));
        when(verificationCodeMapper.selectByUniqueId(UNIQUE_ID)).thenReturn(vc);

        ResponseEntity<J> result = signService.checkSignupCode(UNIQUE_ID, CODE, EMAIL);

        assertNotNull(result);
        assertEquals("验证码已过期", result.getBody().getData());
    }

    @Test
    @DisplayName("校验：过期时间为空时按已过期处理")
    void checkSignupCode_WhenExpirationNull_ShouldReject() {
        VerificationCodeModel vc = validCodeRecord();
        vc.setExpiration_at(null);
        when(verificationCodeMapper.selectByUniqueId(UNIQUE_ID)).thenReturn(vc);

        ResponseEntity<J> result = signService.checkSignupCode(UNIQUE_ID, CODE, EMAIL);

        assertNotNull(result);
        assertEquals("验证码已过期", result.getBody().getData());
    }

    @Test
    @DisplayName("校验：全部通过时标记验证码已使用并返回 null 表示放行")
    void checkSignupCode_WhenValid_ShouldMarkUsedAndReturnNull() {
        when(verificationCodeMapper.selectByUniqueId(UNIQUE_ID)).thenReturn(validCodeRecord());

        ResponseEntity<J> result = signService.checkSignupCode(UNIQUE_ID, CODE, EMAIL);

        assertNull(result, "校验通过必须返回 null，调用方才继续注册");
        verify(verificationCodeMapper).updateUsed(UNIQUE_ID);
    }

    // ==================== 注册写入 ====================

    @Test
    @DisplayName("注册：请求体邮箱与参数邮箱不一致时直接失败")
    void signupUser_WhenEmailMismatch_ShouldReject() {
        UserBasicModel userBasic = new UserBasicModel();
        userBasic.setEmail("another@example.com");
        userBasic.setUsername("newbie");

        ResponseEntity<J> result = signService.signupUserService(userBasic, EMAIL);

        assertEquals(1, result.getBody().getCode());
        verify(userBasicMapper, never()).insert(any(UserBasicModel.class));
    }

    @Test
    @DisplayName("注册：用户名已存在时返回用户名重复")
    void signupUser_WhenUsernameTaken_ShouldReject() {
        UserBasicModel existing = new UserBasicModel();
        existing.setUuid("existing-uuid");
        when(userBasicMapper.selectByUsername("newbie")).thenReturn(existing);
        UserBasicModel userBasic = new UserBasicModel();
        userBasic.setEmail(EMAIL);
        userBasic.setUsername("newbie");

        ResponseEntity<J> result = signService.signupUserService(userBasic, EMAIL);

        assertEquals(1, result.getBody().getCode());
        assertEquals("用户名已经存在", result.getBody().getData());
        verify(userBasicMapper, never()).insert(any(UserBasicModel.class));
    }

    @Test
    @DisplayName("注册：密码加密失败时不写库")
    void signupUser_WhenPasswordHashFails_ShouldReject() {
        when(userBasicMapper.selectByUsername("newbie")).thenReturn(null);
        when(passwordHash.passwordHash256("plain")).thenReturn(null);
        UserBasicModel userBasic = new UserBasicModel();
        userBasic.setEmail(EMAIL);
        userBasic.setUsername("newbie");
        userBasic.setPassword("plain");

        ResponseEntity<J> result = signService.signupUserService(userBasic, EMAIL);

        assertEquals(1, result.getBody().getCode());
        verify(userBasicMapper, never()).insert(any(UserBasicModel.class));
    }

    @Test
    @DisplayName("注册：基础信息写入失败时返回注册失败")
    void signupUser_WhenBasicInsertFails_ShouldReject() {
        when(userBasicMapper.selectByUsername("newbie")).thenReturn(null);
        when(passwordHash.passwordHash256("plain")).thenReturn("hashed");
        when(userBasicMapper.insert(any(UserBasicModel.class))).thenReturn(false);
        UserBasicModel userBasic = new UserBasicModel();
        userBasic.setEmail(EMAIL);
        userBasic.setUsername("newbie");
        userBasic.setPassword("plain");

        ResponseEntity<J> result = signService.signupUserService(userBasic, EMAIL);

        assertEquals(1, result.getBody().getCode());
        verify(userArchiveMapper, never()).insert(any(UserArchiveModel.class));
    }

    @Test
    @DisplayName("注册：个人信息写入失败时必须抛异常以回滚事务，不得留下孤儿账号")
    void signupUser_WhenArchiveInsertFails_ShouldThrowForRollback() {
        when(userBasicMapper.selectByUsername("newbie")).thenReturn(null);
        when(passwordHash.passwordHash256("plain")).thenReturn("hashed");
        when(userBasicMapper.insert(any(UserBasicModel.class))).thenReturn(true);
        when(userArchiveMapper.insert(any(UserArchiveModel.class))).thenReturn(false);
        UserBasicModel userBasic = new UserBasicModel();
        userBasic.setEmail(EMAIL);
        userBasic.setUsername("newbie");
        userBasic.setPassword("plain");

        assertThrows(IllegalStateException.class, () -> signService.signupUserService(userBasic, EMAIL));
    }

    @Test
    @DisplayName("注册：全部成功时返回注册成功，并写入加密密码与已校验邮箱")
    void signupUser_WhenAllSucceed_ShouldReturnSuccess() {
        when(userBasicMapper.selectByUsername("newbie")).thenReturn(null);
        when(passwordHash.passwordHash256("plain")).thenReturn("hashed");
        ArgumentCaptor<UserBasicModel> insertedBasic = ArgumentCaptor.forClass(UserBasicModel.class);
        when(userBasicMapper.insert(insertedBasic.capture())).thenReturn(true);
        ArgumentCaptor<UserArchiveModel> insertedArchive = ArgumentCaptor.forClass(UserArchiveModel.class);
        when(userArchiveMapper.insert(insertedArchive.capture())).thenReturn(true);
        UserBasicModel userBasic = new UserBasicModel();
        userBasic.setEmail(EMAIL);
        userBasic.setUsername("newbie");
        userBasic.setPassword("plain");

        ResponseEntity<J> result = signService.signupUserService(userBasic, EMAIL);

        assertNotNull(result.getBody());
        assertEquals(0, result.getBody().getCode());
        UserBasicModel saved = insertedBasic.getValue();
        assertNotNull(saved.getUuid());
        assertEquals("hashed", saved.getPassword());
        assertTrue(saved.getEmail_verified());
        // 个人信息必须与账号使用同一个 uuid，否则两边对不上
        UserArchiveModel archive = insertedArchive.getValue();
        assertEquals(saved.getUuid(), archive.getUuid());
        assertNotNull(archive.getNickname());
        assertNotNull(archive.getAvatar());
    }
}
