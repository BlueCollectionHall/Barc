package com.miaoyu.barc.security;

import com.miaoyu.barc.annotation.RequireSelfOrPermissionAnno;
import com.miaoyu.barc.annotation.RequireUserAndPermissionAnno;
import com.miaoyu.barc.aspect.RequireSelfOrPermissionAspect;
import com.miaoyu.barc.aspect.RequireUserAndPermissionAspect;
import com.miaoyu.barc.exception.ApiException;
import com.miaoyu.barc.exception.ApiExceptionHandler;
import com.miaoyu.barc.permission.PermissionPolicy;
import com.miaoyu.barc.user.enumeration.UserIdentityEnum;
import com.miaoyu.barc.user.mapper.UserArchiveMapper;
import com.miaoyu.barc.user.model.UserArchiveModel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** 使用真实 Spring AOP 代理验证授权、角色组合、多用户条件与 HTTP 错误契约。 */
class PermissionBoundaryTest {
    private UserArchiveMapper mapper;
    private GuardedService service;

    @BeforeEach void setup() {
        mapper = mock(UserArchiveMapper.class);
        RequireUserAndPermissionAspect userAspect = new RequireUserAndPermissionAspect();
        RequireSelfOrPermissionAspect selfAspect = new RequireSelfOrPermissionAspect();
        ReflectionTestUtils.setField(userAspect, "userArchiveMapper", mapper);
        ReflectionTestUtils.setField(selfAspect, "userArchiveMapper", mapper);
        AspectJProxyFactory factory = new AspectJProxyFactory(new GuardedService());
        factory.addAspect(userAspect);
        factory.addAspect(selfAspect);
        service = factory.getProxy();
    }

    @ParameterizedTest @ValueSource(ints = {1, 4, 5, 7, 36, 63})
    void eitherClaimRolePasses(int mask) {
        when(mapper.selectByUuid("operator")).thenReturn(user(UserIdentityEnum.MANAGER, mask));
        assertEquals(List.of("ok"), service.eitherClaimRole("operator"));
    }

    @ParameterizedTest @ValueSource(ints = {-1, 0, 2, 8, 16, 32, 64, 65})
    void absentOrInvalidClaimRoleFails(int mask) {
        when(mapper.selectByUuid("operator")).thenReturn(user(UserIdentityEnum.MANAGER, mask));
        assertThrows(ApiException.class, () -> service.eitherClaimRole("operator"));
    }

    @Test void equalUserBitDoesNotGrantManagerRole() {
        when(mapper.selectByUuid("operator")).thenReturn(user(UserIdentityEnum.USER, 5));
        assertThrows(ApiException.class, () -> service.eitherClaimRole("operator"));
    }

    @Test void everyCheckRunsIncludingTargetExistence() {
        when(mapper.selectByUuid("operator")).thenReturn(user(UserIdentityEnum.MANAGER, 16));
        assertThrows(ApiException.class, () -> service.bothUsers("operator", "missing"));
        when(mapper.selectByUuid("target")).thenReturn(user(UserIdentityEnum.USER, 1));
        assertEquals("ok", service.bothUsers("operator", "target"));
    }

    @Test void existenceCheckDoesNotSkipLaterPermissionCheck() {
        when(mapper.selectByUuid("operator")).thenReturn(user(UserIdentityEnum.USER, 1));
        assertThrows(ApiException.class, () -> service.existenceThenManager("operator"));
    }

    @Test void selfAndManagerFallbackHandleNullOwner() {
        when(mapper.selectByUuid("operator")).thenReturn(user(UserIdentityEnum.USER, 1));
        assertEquals("ok", service.self("operator", "operator"));
        assertThrows(ApiException.class, () -> service.self("operator", null));
        when(mapper.selectByUuid("operator")).thenReturn(user(UserIdentityEnum.MANAGER, 32));
        assertEquals("ok", service.self("operator", null));
        assertEquals("ok", service.self("operator", "another"));
    }

    @Test void malformedArgumentsAndMissingUsersFailClosed() {
        assertThrows(ApiException.class, () -> service.eitherClaimRole(null));
        assertThrows(ApiException.class, () -> service.eitherClaimRole(" "));
        assertThrows(ApiException.class, () -> service.eitherClaimRole("missing"));
        assertThrows(ApiException.class, () -> service.invalidIndex("operator"));
        assertThrows(ApiException.class, () -> service.empty());
        assertThrows(ApiException.class, () -> service.self("missing", "missing"));
        assertThrows(ApiException.class, () -> service.self(null, "author"));
        assertThrows(ApiException.class, () -> service.sameIndex("operator"));
        assertThrows(ApiException.class, () -> service.modelOwner("operator", new Object()));
    }

    @Test void listReturnTypeStillProducesStructuredHttpRejection() throws Exception {
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new SearchController(service))
                .setControllerAdvice(new ApiExceptionHandler()).build();
        mvc.perform(get("/search")).andExpect(status().isOk()).andExpect(jsonPath("$.code").value(1));
    }

    @Test void validMasksFollowIdentityAndPreserveComparisonModes() {
        assertFalse(PermissionPolicy.isValid(null, 1));
        assertFalse(PermissionPolicy.isValid(UserIdentityEnum.USER, null));
        for (int mask = -1; mask <= 65; mask++) {
            assertEquals(mask >= 1 && mask <= 7, PermissionPolicy.isValid(UserIdentityEnum.USER, mask));
            assertEquals(mask >= 1 && mask <= 63, PermissionPolicy.isValid(UserIdentityEnum.MANAGER, mask));
        }
        assertFalse(PermissionPolicy.matches(null, UserIdentityEnum.MANAGER, 1, true));
        assertFalse(PermissionPolicy.matches(user(null, 1), UserIdentityEnum.MANAGER, 1, true));
        assertFalse(PermissionPolicy.matches(user(UserIdentityEnum.MANAGER, null), UserIdentityEnum.MANAGER, 1, true));
        assertFalse(PermissionPolicy.matches(user(UserIdentityEnum.MANAGER, 4), UserIdentityEnum.MANAGER, 16, false));
        assertTrue(PermissionPolicy.matches(user(UserIdentityEnum.MANAGER, 32), UserIdentityEnum.MANAGER, 16, false));
        assertFalse(PermissionPolicy.matches(user(UserIdentityEnum.MANAGER, 32), UserIdentityEnum.MANAGER, 16, true));
    }

    static UserArchiveModel user(UserIdentityEnum identity, Integer permission) {
        UserArchiveModel user = new UserArchiveModel(); user.setIdentity(identity); user.setPermission(permission); return user;
    }

    public static class GuardedService {
        @RequireUserAndPermissionAnno({@RequireUserAndPermissionAnno.Check(identity = UserIdentityEnum.MANAGER, targetPermission = 5, isSuchElseRequire = false, isHasElseUpper = true)})
        public List<String> eitherClaimRole(String uuid) { return List.of("ok"); }
        @RequireUserAndPermissionAnno({@RequireUserAndPermissionAnno.Check(identity = UserIdentityEnum.MANAGER, targetPermission = 8, isSuchElseRequire = false), @RequireUserAndPermissionAnno.Check(uuidIndex = 1)})
        public String bothUsers(String uuid, String target) { return "ok"; }
        @RequireUserAndPermissionAnno({@RequireUserAndPermissionAnno.Check(), @RequireUserAndPermissionAnno.Check(identity = UserIdentityEnum.MANAGER, isSuchElseRequire = false)})
        public String existenceThenManager(String uuid) { return "ok"; }
        @RequireUserAndPermissionAnno({@RequireUserAndPermissionAnno.Check(uuidIndex = 2)})
        public String invalidIndex(String uuid) { return "unexpected"; }
        @RequireUserAndPermissionAnno({}) public String empty() { return "unexpected"; }
        @RequireSelfOrPermissionAnno(identity = UserIdentityEnum.MANAGER, targetPermission = 16)
        public String self(String uuid, String author) { return "ok"; }
        @RequireSelfOrPermissionAnno(authorUuidIndex = 0)
        public String sameIndex(String uuid) { return "unexpected"; }
        @RequireSelfOrPermissionAnno public String modelOwner(String uuid, Object author) { return "unexpected"; }
    }

    @RestController static class SearchController {
        private final GuardedService service;
        SearchController(GuardedService service) { this.service = service; }
        @GetMapping("/search") public List<String> search() { return service.eitherClaimRole("missing"); }
    }
}
