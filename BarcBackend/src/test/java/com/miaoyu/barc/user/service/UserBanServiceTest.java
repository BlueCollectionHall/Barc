package com.miaoyu.barc.user.service;

import com.miaoyu.barc.response.ErrorR;
import com.miaoyu.barc.response.ResourceR;
import com.miaoyu.barc.response.SuccessR;
import com.miaoyu.barc.user.mapper.UserBanRecordMapper;
import com.miaoyu.barc.user.mapper.UserBasicMapper;
import com.miaoyu.barc.user.model.UserBanRecordModel;
import com.miaoyu.barc.user.model.UserBasicModel;
import com.miaoyu.barc.utils.J;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 用户封号服务单元测试
 */
@ExtendWith(MockitoExtension.class)
class UserBanServiceTest {

    @Mock
    private UserBanRecordMapper userBanRecordMapper;

    @Mock
    private UserBasicMapper userBasicMapper;

    @InjectMocks
    private UserBanService userBanService;

    private UserBasicModel normalUser;
    private UserBasicModel bannedUser;

    @BeforeEach
    void setUp() {
        // 正常用户
        normalUser = new UserBasicModel();
        normalUser.setUuid("user001");
        normalUser.setUsername("testUser");
        normalUser.setSafe_level(5);
        normalUser.setSafe_level_before_ban(null);

        // 已封号用户
        bannedUser = new UserBasicModel();
        bannedUser.setUuid("user002");
        bannedUser.setUsername("bannedUser");
        bannedUser.setSafe_level(-1);
        bannedUser.setSafe_level_before_ban(5);
    }

    // ==================== banUser 测试 ====================

    @Test
    @DisplayName("测试临时封号 - 成功")
    void testBanUser_TemporaryBan_Success() {
        when(userBasicMapper.selectByUuid("user001")).thenReturn(normalUser);
        when(userBasicMapper.updateSafeLevelBeforeBan(anyString(), anyInt())).thenReturn(1);
        when(userBasicMapper.updateSafeLevel(anyString(), anyInt())).thenReturn(1);
        when(userBanRecordMapper.insert(any(UserBanRecordModel.class))).thenAnswer(invocation -> {
            UserBanRecordModel record = invocation.getArgument(0);
            record.setId(1L);
            return 1;
        });

        ResponseEntity<J> response = userBanService.banUser(
                "admin001", 1, "user001", 1, "违规发言", 7);

        J body = response.getBody();
        assertNotNull(body);
        assertEquals(0, body.getCode());
        assertEquals("封号成功", body.getData());

        verify(userBasicMapper).updateSafeLevelBeforeBan("user001", 5);
        verify(userBasicMapper).updateSafeLevel("user001", -1);
        verify(userBanRecordMapper).insert(any(UserBanRecordModel.class));
    }

    @Test
    @DisplayName("测试违规封号 - 成功")
    void testBanUser_ViolationBan_Success() {
        when(userBasicMapper.selectByUuid("user001")).thenReturn(normalUser);
        when(userBasicMapper.updateSafeLevelBeforeBan(anyString(), anyInt())).thenReturn(1);
        when(userBasicMapper.updateSafeLevel(anyString(), anyInt())).thenReturn(1);
        when(userBanRecordMapper.insert(any(UserBanRecordModel.class))).thenAnswer(invocation -> {
            UserBanRecordModel record = invocation.getArgument(0);
            record.setId(2L);
            return 1;
        });

        ResponseEntity<J> response = userBanService.banUser(
                "admin001", 1, "user001", 2, "严重违规", null);

        J body = response.getBody();
        assertNotNull(body);
        assertEquals(0, body.getCode());
        assertEquals("封号成功", body.getData());

        verify(userBasicMapper).updateSafeLevel("user001", -2);
    }

    @Test
    @DisplayName("测试软删除封号 - 成功")
    void testBanUser_SoftDelete_Success() {
        when(userBasicMapper.selectByUuid("user001")).thenReturn(normalUser);
        when(userBasicMapper.updateSafeLevelBeforeBan(anyString(), anyInt())).thenReturn(1);
        when(userBasicMapper.updateSafeLevel(anyString(), anyInt())).thenReturn(1);
        when(userBanRecordMapper.insert(any(UserBanRecordModel.class))).thenAnswer(invocation -> {
            UserBanRecordModel record = invocation.getArgument(0);
            record.setId(3L);
            return 1;
        });

        ResponseEntity<J> response = userBanService.banUser(
                "admin001", 1, "user001", 3, "用户请求删除", null);

        J body = response.getBody();
        assertNotNull(body);
        assertEquals(0, body.getCode());

        verify(userBasicMapper).updateSafeLevel("user001", -3);
    }

    @Test
    @DisplayName("测试封号 - 无效封号类型")
    void testBanUser_InvalidBanType() {
        ResponseEntity<J> response = userBanService.banUser(
                "admin001", 1, "user001", 99, "测试", null);

        J body = response.getBody();
        assertNotNull(body);
        assertEquals(1, body.getCode());
        assertEquals("无效的封号类型", body.getData());
    }

    @Test
    @DisplayName("测试临时封号 - 未提供天数")
    void testBanUser_TemporaryBan_MissingDuration() {
        ResponseEntity<J> response = userBanService.banUser(
                "admin001", 1, "user001", 1, "违规发言", null);

        J body = response.getBody();
        assertNotNull(body);
        assertEquals(1, body.getCode());
        assertEquals("临时封号必须提供有效的封号天数", body.getData());
    }

    @Test
    @DisplayName("测试临时封号 - 天数为0")
    void testBanUser_TemporaryBan_ZeroDuration() {
        ResponseEntity<J> response = userBanService.banUser(
                "admin001", 1, "user001", 1, "违规发言", 0);

        J body = response.getBody();
        assertNotNull(body);
        assertEquals(1, body.getCode());
        assertEquals("临时封号必须提供有效的封号天数", body.getData());
    }

    @Test
    @DisplayName("测试临时封号 - 天数为负数")
    void testBanUser_TemporaryBan_NegativeDuration() {
        ResponseEntity<J> response = userBanService.banUser(
                "admin001", 1, "user001", 1, "违规发言", -1);

        J body = response.getBody();
        assertNotNull(body);
        assertEquals(1, body.getCode());
        assertEquals("临时封号必须提供有效的封号天数", body.getData());
    }

    @Test
    @DisplayName("测试封号 - 用户不存在")
    void testBanUser_UserNotFound() {
        when(userBasicMapper.selectByUuid("nonexistent")).thenReturn(null);

        ResponseEntity<J> response = userBanService.banUser(
                "admin001", 1, "nonexistent", 1, "违规发言", 7);

        J body = response.getBody();
        assertNotNull(body);
        assertEquals(1, body.getCode());
        assertEquals("用户不存在", body.getData());
    }

    @Test
    @DisplayName("测试重复封号 - 用户已被封号")
    void testBanUser_AlreadyBanned() {
        when(userBasicMapper.selectByUuid("user002")).thenReturn(bannedUser);

        ResponseEntity<J> response = userBanService.banUser(
                "admin001", 1, "user002", 1, "再次违规", 7);

        J body = response.getBody();
        assertNotNull(body);
        assertEquals(1, body.getCode());
        assertEquals("用户已被封号，不能重复封号", body.getData());
    }

    @Test
    @DisplayName("测试封号 - safe_level为null时默认为0")
    void testBanUser_NullSafeLevel() {
        UserBasicModel userWithNullSafeLevel = new UserBasicModel();
        userWithNullSafeLevel.setUuid("user003");
        userWithNullSafeLevel.setSafe_level(null);

        when(userBasicMapper.selectByUuid("user003")).thenReturn(userWithNullSafeLevel);
        when(userBasicMapper.updateSafeLevelBeforeBan(anyString(), anyInt())).thenReturn(1);
        when(userBasicMapper.updateSafeLevel(anyString(), anyInt())).thenReturn(1);
        when(userBanRecordMapper.insert(any(UserBanRecordModel.class))).thenReturn(1);

        ResponseEntity<J> response = userBanService.banUser(
                "admin001", 1, "user003", 2, "违规", null);

        J body = response.getBody();
        assertNotNull(body);
        assertEquals(0, body.getCode());

        verify(userBasicMapper).updateSafeLevelBeforeBan("user003", 0);
    }

    // ==================== unbanUser 测试 ====================

    @Test
    @DisplayName("测试解封 - 成功")
    void testUnbanUser_Success() {
        when(userBasicMapper.selectByUuid("user002")).thenReturn(bannedUser);
        when(userBasicMapper.updateSafeLevel(anyString(), anyInt())).thenReturn(1);
        when(userBasicMapper.updateSafeLevelBeforeBan(anyString(), isNull())).thenReturn(1);

        UserBanRecordModel latestRecord = new UserBanRecordModel();
        latestRecord.setId(1L);
        when(userBanRecordMapper.selectLatestByUserId("user002")).thenReturn(latestRecord);
        when(userBanRecordMapper.updateUnbannedAt(eq(1L), any(LocalDateTime.class), anyString())).thenReturn(1);

        ResponseEntity<J> response = userBanService.unbanUser(
                "admin001", "user002", "申诉成功");

        J body = response.getBody();
        assertNotNull(body);
        assertEquals(0, body.getCode());
        assertEquals("解封成功", body.getData());

        verify(userBasicMapper).updateSafeLevel("user002", 5);
        verify(userBasicMapper).updateSafeLevelBeforeBan("user002", null);
        verify(userBanRecordMapper).updateUnbannedAt(eq(1L), any(LocalDateTime.class), eq("申诉成功"));
    }

    @Test
    @DisplayName("测试解封 - safe_level_before_ban为null时默认恢复到0")
    void testUnbanUser_NullSafeLevelBeforeBan() {
        UserBasicModel bannedUserNoHistory = new UserBasicModel();
        bannedUserNoHistory.setUuid("user004");
        bannedUserNoHistory.setSafe_level(-2);
        bannedUserNoHistory.setSafe_level_before_ban(null);

        when(userBasicMapper.selectByUuid("user004")).thenReturn(bannedUserNoHistory);
        when(userBasicMapper.updateSafeLevel(anyString(), anyInt())).thenReturn(1);
        when(userBasicMapper.updateSafeLevelBeforeBan(anyString(), isNull())).thenReturn(1);
        when(userBanRecordMapper.selectLatestByUserId("user004")).thenReturn(null);

        ResponseEntity<J> response = userBanService.unbanUser(
                "admin001", "user004", "解封");

        J body = response.getBody();
        assertNotNull(body);
        assertEquals(0, body.getCode());

        verify(userBasicMapper).updateSafeLevel("user004", 0);
    }

    @Test
    @DisplayName("测试解封 - 用户不存在")
    void testUnbanUser_UserNotFound() {
        when(userBasicMapper.selectByUuid("nonexistent")).thenReturn(null);

        ResponseEntity<J> response = userBanService.unbanUser(
                "admin001", "nonexistent", "解封");

        J body = response.getBody();
        assertNotNull(body);
        assertEquals(1, body.getCode());
        assertEquals("用户不存在", body.getData());
    }

    @Test
    @DisplayName("测试解封 - 用户未被封号")
    void testUnbanUser_NotBanned() {
        when(userBasicMapper.selectByUuid("user001")).thenReturn(normalUser);

        ResponseEntity<J> response = userBanService.unbanUser(
                "admin001", "user001", "解封");

        J body = response.getBody();
        assertNotNull(body);
        assertEquals(1, body.getCode());
        assertEquals("用户未被封号", body.getData());
    }

    @Test
    @DisplayName("测试解封 - safe_level为0时视为未封号")
    void testUnbanUser_SafeLevelZero() {
        UserBasicModel userSafeLevelZero = new UserBasicModel();
        userSafeLevelZero.setUuid("user005");
        userSafeLevelZero.setSafe_level(0);

        when(userBasicMapper.selectByUuid("user005")).thenReturn(userSafeLevelZero);

        ResponseEntity<J> response = userBanService.unbanUser(
                "admin001", "user005", "解封");

        J body = response.getBody();
        assertNotNull(body);
        assertEquals(1, body.getCode());
        assertEquals("用户未被封号", body.getData());
    }

    // ==================== getBanHistory 测试 ====================

    @Test
    @DisplayName("测试查询封号历史 - 有记录")
    void testGetBanHistory_WithRecords() {
        UserBanRecordModel record1 = new UserBanRecordModel();
        record1.setId(1L);
        record1.setUserId("user001");
        record1.setBanType(1);
        record1.setBanReason("违规发言");

        UserBanRecordModel record2 = new UserBanRecordModel();
        record2.setId(2L);
        record2.setUserId("user001");
        record2.setBanType(2);
        record2.setBanReason("严重违规");

        List<UserBanRecordModel> records = Arrays.asList(record1, record2);
        when(userBanRecordMapper.selectByUserId("user001", 0, 10)).thenReturn(records);
        when(userBanRecordMapper.countByUserId("user001")).thenReturn(2L);

        ResponseEntity<J> response = userBanService.getBanHistory("user001", 1, 10);

        J body = response.getBody();
        assertNotNull(body);
        assertEquals(0, body.getCode());

        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) body.getData();
        assertNotNull(data);
        assertEquals(2, ((List<?>) data.get("list")).size());
        assertEquals(2L, data.get("total"));
    }

    @Test
    @DisplayName("测试查询封号历史 - 无记录")
    void testGetBanHistory_NoRecords() {
        when(userBanRecordMapper.selectByUserId("user001", 0, 10)).thenReturn(Collections.emptyList());
        when(userBanRecordMapper.countByUserId("user001")).thenReturn(0L);

        ResponseEntity<J> response = userBanService.getBanHistory("user001", 1, 10);

        J body = response.getBody();
        assertNotNull(body);
        assertEquals(0, body.getCode());

        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) body.getData();
        assertNotNull(data);
        assertEquals(0, ((List<?>) data.get("list")).size());
        assertEquals(0L, data.get("total"));
    }

    @Test
    @DisplayName("测试查询封号历史 - 分页计算正确")
    void testGetBanHistory_Pagination() {
        when(userBanRecordMapper.selectByUserId("user001", 10, 10)).thenReturn(Collections.emptyList());
        when(userBanRecordMapper.countByUserId("user001")).thenReturn(15L);

        ResponseEntity<J> response = userBanService.getBanHistory("user001", 2, 10);

        verify(userBanRecordMapper).selectByUserId("user001", 10, 10);
    }

    // ==================== getUserStatus 测试 ====================

    @Test
    @DisplayName("测试查询用户状态 - 正常用户")
    void testGetUserStatus_NormalUser() {
        when(userBasicMapper.selectByUuid("user001")).thenReturn(normalUser);
        when(userBanRecordMapper.selectLatestByUserId("user001")).thenReturn(null);

        ResponseEntity<J> response = userBanService.getUserStatus("user001");

        J body = response.getBody();
        assertNotNull(body);
        assertEquals(0, body.getCode());

        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) body.getData();
        assertNotNull(data);
        assertEquals("user001", data.get("userId"));
        assertEquals(5, data.get("safeLevel"));
        assertEquals("正常", data.get("banStatus"));
    }

    @Test
    @DisplayName("测试查询用户状态 - 被封号用户")
    void testGetUserStatus_BannedUser() {
        UserBanRecordModel banRecord = new UserBanRecordModel();
        banRecord.setId(1L);
        banRecord.setBanReason("违规发言");
        banRecord.setBanDurationDays(7);
        banRecord.setBannedAt(LocalDateTime.of(2026, 5, 20, 10, 0, 0));

        when(userBasicMapper.selectByUuid("user002")).thenReturn(bannedUser);
        when(userBanRecordMapper.selectLatestByUserId("user002")).thenReturn(banRecord);

        ResponseEntity<J> response = userBanService.getUserStatus("user002");

        J body = response.getBody();
        assertNotNull(body);
        assertEquals(0, body.getCode());

        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) body.getData();
        assertNotNull(data);
        assertEquals("user002", data.get("userId"));
        assertEquals(-1, data.get("safeLevel"));
        assertEquals("临时封号", data.get("banStatus"));
        assertEquals("违规发言", data.get("banReason"));
        assertNotNull(data.get("unbanTime"));
    }

    @Test
    @DisplayName("测试查询用户状态 - 违规封号无解封时间")
    void testGetUserStatus_ViolationBanNoUnbanTime() {
        UserBasicModel violationBannedUser = new UserBasicModel();
        violationBannedUser.setUuid("user006");
        violationBannedUser.setSafe_level(-2);
        violationBannedUser.setSafe_level_before_ban(3);

        UserBanRecordModel banRecord = new UserBanRecordModel();
        banRecord.setId(2L);
        banRecord.setBanReason("严重违规");
        banRecord.setBanDurationDays(null); // 永久封号
        banRecord.setBannedAt(LocalDateTime.of(2026, 5, 20, 10, 0, 0));

        when(userBasicMapper.selectByUuid("user006")).thenReturn(violationBannedUser);
        when(userBanRecordMapper.selectLatestByUserId("user006")).thenReturn(banRecord);

        ResponseEntity<J> response = userBanService.getUserStatus("user006");

        J body = response.getBody();
        assertNotNull(body);
        assertEquals(0, body.getCode());

        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) body.getData();
        assertNotNull(data);
        assertEquals("违规封号", data.get("banStatus"));
        assertNull(data.get("unbanTime"));
    }

    @Test
    @DisplayName("测试查询用户状态 - 用户不存在")
    void testGetUserStatus_UserNotFound() {
        when(userBasicMapper.selectByUuid("nonexistent")).thenReturn(null);

        ResponseEntity<J> response = userBanService.getUserStatus("nonexistent");

        J body = response.getBody();
        assertNotNull(body);
        assertEquals(1, body.getCode());
        assertEquals("用户不存在", body.getData());
    }

    @Test
    @DisplayName("测试查询用户状态 - safe_level为null")
    void testGetUserStatus_NullSafeLevel() {
        UserBasicModel userNullSafeLevel = new UserBasicModel();
        userNullSafeLevel.setUuid("user007");
        userNullSafeLevel.setSafe_level(null);

        when(userBasicMapper.selectByUuid("user007")).thenReturn(userNullSafeLevel);
        when(userBanRecordMapper.selectLatestByUserId("user007")).thenReturn(null);

        ResponseEntity<J> response = userBanService.getUserStatus("user007");

        J body = response.getBody();
        assertNotNull(body);
        assertEquals(0, body.getCode());

        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) body.getData();
        assertNotNull(data);
        assertEquals("正常", data.get("banStatus"));
    }

    // ==================== unbanExpiredUsers 测试 ====================

    @Test
    @DisplayName("测试自动解封 - 有到期用户")
    void testUnbanExpiredUsers_WithExpiredUsers() {
        UserBanRecordModel record1 = new UserBanRecordModel();
        record1.setId(1L);
        record1.setUserId("user001");
        record1.setSafeLevelBeforeBan(5);

        UserBanRecordModel record2 = new UserBanRecordModel();
        record2.setId(2L);
        record2.setUserId("user002");
        record2.setSafeLevelBeforeBan(3);

        when(userBanRecordMapper.selectExpiredRecords()).thenReturn(Arrays.asList(record1, record2));
        when(userBasicMapper.updateSafeLevel(anyString(), anyInt())).thenReturn(1);
        when(userBasicMapper.updateSafeLevelBeforeBan(anyString(), isNull())).thenReturn(1);
        when(userBanRecordMapper.updateUnbannedAt(anyLong(), any(LocalDateTime.class), anyString())).thenReturn(1);

        int count = userBanService.unbanExpiredUsers();

        assertEquals(2, count);
        verify(userBasicMapper).updateSafeLevel("user001", 5);
        verify(userBasicMapper).updateSafeLevel("user002", 3);
        verify(userBanRecordMapper, times(2)).updateUnbannedAt(anyLong(), any(LocalDateTime.class), eq("系统自动解封"));
    }

    @Test
    @DisplayName("测试自动解封 - 无到期用户")
    void testUnbanExpiredUsers_NoExpiredUsers() {
        when(userBanRecordMapper.selectExpiredRecords()).thenReturn(Collections.emptyList());

        int count = userBanService.unbanExpiredUsers();

        assertEquals(0, count);
        verify(userBasicMapper, never()).updateSafeLevel(anyString(), anyInt());
    }
}
