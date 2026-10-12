package com.miaoyu.barc.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.miaoyu.barc.email.service.MailOutbox;
import com.miaoyu.barc.interceptor.RequestLimitInterceptor;
import com.miaoyu.barc.permission.PermissionConst;
import com.miaoyu.barc.user.enumeration.UserIdentityEnum;
import com.miaoyu.barc.user.mapper.*;
import com.miaoyu.barc.user.model.*;
import com.miaoyu.barc.user.scheduler.UserBanScheduler;
import com.miaoyu.barc.user.service.*;
import com.miaoyu.barc.utils.VerifyCodeCleanupService;
import com.miaoyu.barc.utils.dto.ValueLabelDto;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.*;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.method.HandlerMethod;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 运行时限流、角色展示与定时清理边界；全部持久化依赖使用替身，绝不清理现有数据。 */
@ExtendWith(MockitoExtension.class)
class RuntimePolicyTest {
    @Mock RequestLimits limits;
    @Mock UserArchiveMapper archives;
    @Mock UserBasicMapper basics;
    @Mock UserPermissionMapper permissions;
    @Mock MailOutbox outbox;
    @Mock VerificationCodeMapper codes;
    @Mock UserBanService bans;
    @Spy PermissionConst dictionary = new PermissionConst();
    @InjectMocks UserPermissionService permissionService;
    @InjectMocks VerifyCodeCleanupService cleanup;
    @InjectMocks UserBanScheduler scheduler;

    @Test void rateLimitGroupsUseConnectionAddressAndAuthenticatedIdentity() throws Exception {
        var interceptor = interceptor(); var handler = new HandlerMethod(new Object(), Object.class.getMethod("toString"));
        var request = new MockHttpServletRequest("POST", "/user/sign/in"); request.setRemoteAddr("127.0.0.2"); request.addHeader("X-Forwarded-For", "forged");
        when(limits.allow("http:credentials:ip:127.0.0.2", 30, 60000L)).thenReturn(true); assertTrue(interceptor.preHandle(request, new MockHttpServletResponse(), handler));
        request.setRequestURI("/feedback/upload"); request.setAttribute("uuid", "actor"); var response = new MockHttpServletResponse();
        assertFalse(interceptor.preHandle(request, response, handler)); verify(limits).allow("http:feedback:uuid:actor", 20, 60000L);
        assertEquals(429, response.getStatus()); assertEquals("60", response.getHeader("Retry-After")); assertEquals(1, new ObjectMapper().readTree(response.getContentAsString()).path("code").asInt());
        request.setRequestURI("/api/work/update"); when(limits.allow("http:writes:uuid:actor", 300, 60000L)).thenReturn(true); assertTrue(interceptor.preHandle(request, new MockHttpServletResponse(), handler));
    }
    @Test void readsPreflightAndStaticResourcesDoNotConsumeWriteQuota() throws Exception {
        var interceptor = interceptor(); var handler = new HandlerMethod(new Object(), Object.class.getMethod("toString"));
        for (String method : List.of("GET", "OPTIONS")) assertTrue(interceptor.preHandle(new MockHttpServletRequest(method, "/api/work/all"), new MockHttpServletResponse(), handler));
        assertTrue(interceptor.preHandle(new MockHttpServletRequest("POST", "/asset"), new MockHttpServletResponse(), new Object())); verifyNoInteractions(limits);
    }
    @Test void identityOptionsRemainSingleBitsAndCombinationLabelsUseHighestRole() {
        var users = dictionary.permissionsByIdentity(UserIdentityEnum.USER); var managers = dictionary.permissionsByIdentity(UserIdentityEnum.MANAGER);
        assertEquals(List.of(1, 2, 4), users.stream().map(ValueLabelDto::getValue).toList());
        assertEquals(List.of("会员", "收录员", "创作者"), users.stream().map(ValueLabelDto::getLabel).toList());
        assertEquals(List.of(1, 2, 4, 8, 16, 32), managers.stream().map(ValueLabelDto::getValue).toList());
        assertEquals(List.of("风纪委员", "一级管理员", "二级管理员", "三级管理员", "副馆长", "馆长"), managers.stream().map(ValueLabelDto::getLabel).toList());
        assertNull(dictionary.toString(UserIdentityEnum.USER, 8)); assertNull(dictionary.toString(UserIdentityEnum.MANAGER, 64));
        assertEquals("收录员", permissionService.getUserPermissionCnService(UserIdentityEnum.USER, 2).getBody().getData());
        assertEquals(6, ((List<?>) permissionService.getUserPermissionsByIdentityService(UserIdentityEnum.MANAGER).getBody().getData()).size());
        assertNotEquals(0, permissionService.getUserPermissionNearMaxService("missing").getBody().getCode());
        var combined = archive("u", 5); when(archives.selectByUuid("u")).thenReturn(combined);
        var label = (ValueLabelDto) permissionService.getUserPermissionNearMaxService("u").getBody().getData(); assertEquals(4, label.getValue()); assertEquals("二级管理员", label.getLabel());
    }
    @Test void permissionSuccessEnqueuesRecipientFromDatabaseAndFailureDoesNotNotify() {
        when(archives.selectByUuid("admin")).thenReturn(archive("admin", 32)); var target = archive("target", 1); when(archives.selectByUuid("target")).thenReturn(target);
        var basic = new UserBasicModel(); basic.setEmail("target@example.test"); when(basics.selectByUuid("target")).thenReturn(basic); when(permissions.update(target)).thenReturn(true, false);
        assertEquals(0, permissionService.changeUserPermissionService("admin", "target", UserIdentityEnum.MANAGER, 4).getBody().getCode());
        verify(outbox).enqueue(startsWith("permission:"), eq("target@example.test"), eq("账号权限变更"), contains("二级管理员"));
        assertNotEquals(0, permissionService.changeUserPermissionService("admin", "target", UserIdentityEnum.USER, 1).getBody().getCode()); verifyNoMoreInteractions(outbox);
    }
    @Test void cleanupDeletesOnlyExpiredOrConsumedChallenges() {
        var unused = code("unused", false, LocalDateTime.now().plusDays(1)); var used = code("used", true, LocalDateTime.now().plusDays(1)); var expired = code("expired", false, LocalDateTime.now().minusDays(1));
        when(codes.selectAll()).thenReturn(List.of(unused, used, expired)); cleanup.verifyCodeCleanup();
        verify(codes).deleteByUniqueId("used"); verify(codes).deleteByUniqueId("expired"); verify(codes, never()).deleteByUniqueId("unused");
    }
    @Test void scheduledUnbanDelegatesAndContainsSchedulerFailures() {
        when(bans.unbanExpiredUsers()).thenReturn(2).thenThrow(new IllegalStateException("模拟异常"));
        assertDoesNotThrow(scheduler::unbanExpiredUsers); assertDoesNotThrow(scheduler::unbanExpiredUsers); verify(bans, times(2)).unbanExpiredUsers();
    }
    private RequestLimitInterceptor interceptor() { var value = new RequestLimitInterceptor(); ReflectionTestUtils.setField(value, "limits", limits); ReflectionTestUtils.setField(value, "objectMapper", new ObjectMapper()); return value; }
    private UserArchiveModel archive(String uuid, int permission) { var value = new UserArchiveModel(); value.setUuid(uuid); value.setNickname("名称"); value.setIdentity(UserIdentityEnum.MANAGER); value.setPermission(permission); return value; }
    private VerificationCodeModel code(String id, boolean used, LocalDateTime expiration) { var value = new VerificationCodeModel(); value.setUnique_id(id); value.setUsed(used); value.setExpiration_at(expiration); return value; }
}
