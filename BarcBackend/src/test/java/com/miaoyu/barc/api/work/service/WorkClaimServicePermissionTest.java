package com.miaoyu.barc.api.work.service;

import com.miaoyu.barc.annotation.RequireUserAndPermissionAnno;
import com.miaoyu.barc.api.work.mapper.WorkClaimMapper;
import com.miaoyu.barc.permission.PermissionConst;
import com.miaoyu.barc.user.enumeration.UserIdentityEnum;
import com.miaoyu.barc.user.mapper.UserArchiveMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * 作品认领两个查询口的鉴权契约测试。
 *
 * <p>为什么需要它：这两个接口原先用的是手写判断
 * {@code if (!(identity == MANAGER) && !(permission == DISCIPLINARY_COMMITTEE))}。
 * 两边各带一个否定再用 {@code &&} 连接，语义退化成「或」——只要 permission 命中就放行。
 * 而 {@code PermissionConst.USER} 与 {@code PermissionConst.DISCIPLINARY_COMMITTEE} 同为 1，
 * 注册链路 {@code INSERT INTO user_archive (uuid, nickname, avatar)} 又不写 permission、
 * 表默认值正是 1，于是任意普通会员都能读到全部认领申请（含申请人 uuid）。</p>
 *
 * <p>修复方式是把鉴权交回项目统一的注解切面。因此这里断言三件事：
 * 1、注解存在且参数正确（MANAGER + 风纪委员位 + 1匹配 + 完整校验）；
 * 2、第 1 个形参仍是操作者 uuid —— 切面 {@code uuidIndex} 默认 0，形参一挪鉴权就静默失效；
 * 3、方法体内不再自行查档案表做鉴权，否则等于把手写判断又加了回来。</p>
 */
@ExtendWith(MockitoExtension.class)
class WorkClaimServicePermissionTest {

    @Mock
    private UserArchiveMapper userArchiveMapper;

    @Mock
    private WorkClaimMapper workClaimMapper;

    @InjectMocks
    private WorkClaimService workClaimService;

    @Test
    @DisplayName("两个认领查询口必须要求 MANAGER 身份 + 风纪委员权限位（1匹配）")
    void claimQueries_ShouldRequireManagerIdentityWithDisciplinaryBit() throws NoSuchMethodException {
        assertManagerDisciplinaryBit(
                WorkClaimService.class.getMethod("getAllClaimsControl", String.class));
        assertManagerDisciplinaryBit(
                WorkClaimService.class.getMethod("getClaimsByWorkControl", String.class, String.class));
    }

    @Test
    @DisplayName("被注解方法的第 1 个形参必须是操作者 uuid，否则切面取不到人")
    void claimQueries_ShouldTakeOperatorUuidAsFirstParameter() throws NoSuchMethodException {
        Method all = WorkClaimService.class.getMethod("getAllClaimsControl", String.class);
        assertEquals(String.class, all.getParameterTypes()[0],
                "getAllClaimsControl 的第 1 个形参必须是操作者 uuid（uuidIndex 默认 0）");

        Method byWork = WorkClaimService.class.getMethod(
                "getClaimsByWorkControl", String.class, String.class);
        assertEquals(String.class, byWork.getParameterTypes()[0],
                "getClaimsByWorkControl 的第 1 个形参必须是操作者 uuid（uuidIndex 默认 0）");
    }

    @Test
    @DisplayName("回归：方法体内不得再自行查档案表鉴权（越权判断已移除，鉴权交给切面）")
    void claimQueries_ShouldNotDoManualPermissionCheck() {
        workClaimService.getAllClaimsControl("someone-uuid");
        workClaimService.getClaimsByWorkControl("someone-uuid", "work-1");

        // 只查认领表、不查档案表 —— 说明权限判定已经完全交给 @RequireUserAndPermissionAnno
        verifyNoInteractions(userArchiveMapper);
    }

    private void assertManagerDisciplinaryBit(Method method) {
        RequireUserAndPermissionAnno anno = method.getAnnotation(RequireUserAndPermissionAnno.class);
        assertNotNull(anno, method.getName() + " 必须标注 @RequireUserAndPermissionAnno");
        assertTrue(anno.value().length > 0, method.getName() + " 的鉴权注解至少要有一个 Check");

        RequireUserAndPermissionAnno.Check check = anno.value()[0];
        assertEquals(UserIdentityEnum.MANAGER, check.identity(),
                method.getName() + " 必须先校验 MANAGER 身份，不能只比权限数字");
        assertEquals(PermissionConst.DISCIPLINARY_COMMITTEE, check.targetPermission(),
                method.getName() + " 的目标权限应为风纪委员位");
        assertTrue(check.isHasElseUpper(),
                method.getName() + " 必须走 1匹配（isHasElseUpper = true），不能用比大小");
        assertFalse(check.isSuchElseRequire(),
                method.getName() + " 必须执行完整身份+权限校验，不能退化成只校验用户存在性");
        assertEquals(0, check.uuidIndex(),
                method.getName() + " 的 uuidIndex 必须指向第 1 个形参");
    }
}
