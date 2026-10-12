package com.miaoyu.barc.security;

import com.miaoyu.barc.permission.PermissionConst;
import com.miaoyu.barc.user.enumeration.UserIdentityEnum;
import com.miaoyu.barc.user.mapper.UserArchiveMapper;
import com.miaoyu.barc.user.mapper.UserBasicMapper;
import com.miaoyu.barc.user.mapper.UserPermissionMapper;
import com.miaoyu.barc.user.model.UserArchiveModel;
import com.miaoyu.barc.user.service.UserPermissionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 覆盖负数掩码绕过、同级保护与合法降级授权。 */
class PermissionMutationRegressionTest {
    private final UserArchiveMapper users = mock(UserArchiveMapper.class);
    private final UserPermissionMapper permissions = mock(UserPermissionMapper.class);
    private final UserPermissionService service = new UserPermissionService();

    @BeforeEach void setup() {
        ReflectionTestUtils.setField(service, "userArchiveMapper", users);
        ReflectionTestUtils.setField(service, "userPermissionMapper", permissions);
        ReflectionTestUtils.setField(service, "userBasicMapper", mock(UserBasicMapper.class));
        ReflectionTestUtils.setField(service, "permissionConst", new PermissionConst());
    }

    @ParameterizedTest @NullSource @ValueSource(ints = {-1, -2147483648, 0, 64, 65})
    void rejectsUnknownAndNegativeManagerMasks(Integer mask) {
        assertEquals(1, service.changeUserPermissionService("operator", "target", UserIdentityEnum.MANAGER, mask).getBody().getCode());
        verifyNoInteractions(permissions);
    }

    @Test void rejectsUnknownUserMaskNullIdentitySelfAndMissingTarget() {
        assertEquals(1, service.changeUserPermissionService("operator", "target", UserIdentityEnum.USER, 8).getBody().getCode());
        assertEquals(1, service.changeUserPermissionService("operator", "target", null, 1).getBody().getCode());
        assertEquals(1, service.changeUserPermissionService("missing", "target", UserIdentityEnum.USER, 1).getBody().getCode());
        when(users.selectByUuid("operator")).thenReturn(PermissionBoundaryTest.user(UserIdentityEnum.MANAGER, 16));
        assertEquals(1, service.changeUserPermissionService("operator", "operator", UserIdentityEnum.USER, 1).getBody().getCode());
        assertEquals(1, service.changeUserPermissionService("operator", "missing", UserIdentityEnum.USER, 1).getBody().getCode());
        verifyNoInteractions(permissions);
    }

    @Test void lowerBitsCannotBeStackedToGrantOrModifySameRank() {
        when(users.selectByUuid("operator")).thenReturn(PermissionBoundaryTest.user(UserIdentityEnum.MANAGER, 15));
        when(users.selectByUuid("target")).thenReturn(PermissionBoundaryTest.user(UserIdentityEnum.USER, 1));
        assertEquals(1, service.changeUserPermissionService("operator", "target", UserIdentityEnum.MANAGER, 8).getBody().getCode());
        when(users.selectByUuid("target")).thenReturn(PermissionBoundaryTest.user(UserIdentityEnum.MANAGER, 8));
        assertEquals(1, service.changeUserPermissionService("operator", "target", UserIdentityEnum.USER, 1).getBody().getCode());
        verifyNoInteractions(permissions);
    }

    @Test void legitimateLowerRankAssignmentSucceeds() {
        when(users.selectByUuid("operator")).thenReturn(PermissionBoundaryTest.user(UserIdentityEnum.MANAGER, 16));
        UserArchiveModel target = PermissionBoundaryTest.user(UserIdentityEnum.USER, 1); target.setUuid("target");
        when(users.selectByUuid("target")).thenReturn(target);
        when(permissions.update(any())).thenReturn(true);
        assertEquals(0, service.changeUserPermissionService("operator", "target", UserIdentityEnum.MANAGER, 5).getBody().getCode());
        verify(permissions).update(argThat(user -> user.getIdentity() == UserIdentityEnum.MANAGER && user.getPermission() == 5));
    }
}
