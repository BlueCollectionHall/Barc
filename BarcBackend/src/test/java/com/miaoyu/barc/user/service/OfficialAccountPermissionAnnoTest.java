package com.miaoyu.barc.user.service;

import com.miaoyu.barc.annotation.RequireUserAndPermissionAnno;
import com.miaoyu.barc.api.work.service.WorkManageService;
import com.miaoyu.barc.permission.PermissionConst;
import com.miaoyu.barc.user.enumeration.UserIdentityEnum;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 官方账号模块与两个管理搜索口的鉴权契约测试。
 *
 * 为什么需要它：
 * 1、这两个搜索口（search-user / search-work）原先**完全没有权限注解**，
 *    只有 AuthInterceptor 的登录门槛，任意登录用户都能搜全站用户；
 * 2、而注解又挂不到 Controller —— AOP 切面按 {@code uuidIndex}（默认 0）
 *    从形参取被校验的 uuid（见 RequireUserAndPermissionAspect#requireSuchAndPermissionAspect），
 *    所以校验必须落在「uuid 为第 1 个形参」的 Service 方法上。
 *    一旦有人把形参顺序改回去（第 1 个变成 keyword），注解就会静默失效，
 *    因此这里把「注解存在」与「第 1 形参是 String」一起断言。
 */
class OfficialAccountPermissionAnnoTest {

    @Test
    @DisplayName("官方账号三个接口必须要求馆长 1匹配（ADVANCED_ADMINISTRATOR + isHasElseUpper）")
    void officialAccountService_ShouldRequireCuratorBitPermission() throws NoSuchMethodException {
        assertCuratorBit(OfficialAccountService.class.getMethod("listService", String.class));
        assertCuratorBit(OfficialAccountService.class.getMethod(
                "bindService", String.class, String.class, String.class, String.class));
        assertCuratorBit(OfficialAccountService.class.getMethod(
                "searchUserService", String.class, String.class));
    }

    @Test
    @DisplayName("管理端两个搜索口必须要求 MANAGER + 比大小（permission >= 风纪委员）")
    void workManageSearch_ShouldRequireManagerIdentityWithCompareMode() throws NoSuchMethodException {
        assertManagerCompare(WorkManageService.class.getMethod("searchWorks", String.class, String.class));
        assertManagerCompare(WorkManageService.class.getMethod("searchUsers", String.class, String.class));
    }

    /**
     * AOP 契约：uuidIndex 默认 0，因此被注解方法的第 1 个形参必须是操作者 uuid（String），
     * 否则切面会拿 keyword 之类的东西去查用户档案，鉴权形同失效。
     */
    @Test
    @DisplayName("被注解方法的第 1 个形参必须是操作者 uuid 字符串")
    void annotatedMethods_ShouldTakeOperatorUuidAsFirstParameter() throws NoSuchMethodException {
        assertFirstParameterIsString(OfficialAccountService.class.getMethod("listService", String.class));

        Method bind = OfficialAccountService.class.getMethod(
                "bindService", String.class, String.class, String.class, String.class);
        assertFirstParameterIsString(bind);

        assertFirstParameterIsString(WorkManageService.class.getMethod("searchWorks", String.class, String.class));
        assertFirstParameterIsString(WorkManageService.class.getMethod("searchUsers", String.class, String.class));
    }

    private void assertCuratorBit(Method method) {
        RequireUserAndPermissionAnno.Check check = firstCheck(method);
        assertEquals(UserIdentityEnum.MANAGER, check.identity());
        assertEquals(PermissionConst.ADVANCED_ADMINISTRATOR, check.targetPermission());
        assertTrue(check.isHasElseUpper(), "馆长权限位必须走 1匹配（isHasElseUpper = true）");
        assertTrue(!check.isSuchElseRequire(), "必须执行完整身份+权限校验，不能只校验用户存在性");
    }

    private void assertManagerCompare(Method method) {
        RequireUserAndPermissionAnno.Check check = firstCheck(method);
        assertEquals(UserIdentityEnum.MANAGER, check.identity());
        assertEquals(PermissionConst.DISCIPLINARY_COMMITTEE, check.targetPermission());
        assertTrue(!check.isHasElseUpper(), "管理者门槛必须走比大小（isHasElseUpper = false）");
        assertTrue(!check.isSuchElseRequire(), "必须执行完整身份+权限校验，不能只校验用户存在性");
    }

    private void assertFirstParameterIsString(Method method) {
        assertTrue(method.getParameterCount() >= 1, method.getName() + " 至少需要一个形参承载操作者 uuid");
        assertEquals(String.class, method.getParameterTypes()[0],
                method.getName() + " 的第 1 个形参必须是 String 类型的操作者 uuid（uuidIndex 默认 0）");
    }

    private RequireUserAndPermissionAnno.Check firstCheck(Method method) {
        RequireUserAndPermissionAnno anno = method.getAnnotation(RequireUserAndPermissionAnno.class);
        assertNotNull(anno, method.getName() + " 必须标注 @RequireUserAndPermissionAnno");
        assertTrue(anno.value().length > 0, method.getName() + " 的鉴权注解至少要有一个 Check");
        return anno.value()[0];
    }
}
