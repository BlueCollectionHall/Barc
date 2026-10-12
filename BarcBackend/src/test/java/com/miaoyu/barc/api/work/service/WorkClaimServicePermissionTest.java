package com.miaoyu.barc.api.work.service;

import com.miaoyu.barc.annotation.RequireUserAndPermissionAnno;
import com.miaoyu.barc.api.work.model.WorkClaimModel;
import com.miaoyu.barc.permission.PermissionConst;
import com.miaoyu.barc.user.enumeration.UserIdentityEnum;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 作品认领只读入口的鉴权契约测试。
 *
 * <p>历史沿革（2026-10）：{@code WorkClaimService} 原有两个只读查询口
 * {@code getAllClaimsControl} / {@code getClaimsByWorkControl}，它们分别与
 * {@code WorkManageService.getClaimsList} / {@code getClaimHistory} 调同一个 Mapper 方法，
 * 属重复入口。而且它们用手写判断鉴权：
 * {@code if (!(identity == MANAGER) && !(permission == DISCIPLINARY_COMMITTEE))} ——
 * 两边各带否定再用 {@code &&} 连接，语义退化成「或」，且
 * {@code PermissionConst.USER} 与 {@code DISCIPLINARY_COMMITTEE} 同为 1、
 * 注册时不写 permission（表默认值 1），于是任意普通会员都能读全部认领申请。</p>
 *
 * <p>处置：两条重复口连同其缺陷整体删除，认领查看统一走管理端两条；
 * 由于用户端原本允许风纪委员查看，管理端两条的权限位相应放宽为
 * {@code SEC_MAINTAINER | DISCIPLINARY_COMMITTEE}。本类锁住三件事：
 * ① 重复口不得被加回来；② 管理端两条的放宽后权限位正确；
 * ③ 认领的提交与审核入口（业务要求保留）仍在。</p>
 */
class WorkClaimServicePermissionTest {

    /** 放宽后的目标权限位：二级管理员 或 风纪委员 */
    private static final int EITHER_BIT =
            PermissionConst.SEC_MAINTAINER | PermissionConst.DISCIPLINARY_COMMITTEE;

    @Test
    @DisplayName("重复的认领只读口不得被加回来")
    void duplicateClaimQueries_ShouldStayRemoved() {
        assertThrows(NoSuchMethodException.class,
                () -> WorkClaimService.class.getMethod("getAllClaimsControl", String.class),
                "getAllClaimsControl 已与 /api/work/manage/claims 重复，不应重新出现");
        assertThrows(NoSuchMethodException.class,
                () -> WorkClaimService.class.getMethod("getClaimsByWorkControl", String.class, String.class),
                "getClaimsByWorkControl 已与 /api/work/manage/claim/history 重复，不应重新出现");
    }

    @Test
    @DisplayName("管理端两个认领查询口必须放宽到「二级管理员位 或 风纪委员位」")
    void manageClaimQueries_ShouldAllowEitherMaintainerOrDisciplinaryBit() throws NoSuchMethodException {
        assertEitherBit(WorkManageService.class.getMethod("getClaimsList", String.class));
        assertEitherBit(WorkManageService.class.getMethod(
                "getClaimHistory", String.class, String.class));
    }

    @Test
    @DisplayName("被注解方法的第 1 个形参必须是操作者 uuid，否则切面取不到人")
    void manageClaimQueries_ShouldTakeOperatorUuidAsFirstParameter() throws NoSuchMethodException {
        assertEquals(String.class,
                WorkManageService.class.getMethod("getClaimsList", String.class).getParameterTypes()[0],
                "getClaimsList 的第 1 个形参必须是操作者 uuid（uuidIndex 默认 0）");
        assertEquals(String.class,
                WorkManageService.class.getMethod("getClaimHistory", String.class, String.class)
                        .getParameterTypes()[0],
                "getClaimHistory 的第 1 个形参必须是操作者 uuid（uuidIndex 默认 0）");
    }

    @Test
    @DisplayName("认领的提交与审核入口必须保留（业务明确要求）")
    void claimWriteEntries_ShouldStayAvailable() {
        assertDoesNotThrow(
                () -> WorkClaimService.class.getMethod("checkWorkClaimService", String.class, String.class),
                "认领审核入口 checkWorkClaimService 不得被删除");
        assertDoesNotThrow(
                () -> WorkClaimService.class.getMethod(
                        "uploadWorkClaimService", String.class, WorkClaimModel.class),
                "认领提交入口 uploadWorkClaimService 不得被删除");
    }

    private void assertEitherBit(Method method) {
        RequireUserAndPermissionAnno anno = method.getAnnotation(RequireUserAndPermissionAnno.class);
        assertNotNull(anno, method.getName() + " 必须标注 @RequireUserAndPermissionAnno");

        RequireUserAndPermissionAnno.Check check = anno.value()[0];
        assertEquals(UserIdentityEnum.MANAGER, check.identity(),
                method.getName() + " 必须先校验 MANAGER 身份（风纪委员与管理员同属 MANAGER）");
        assertTrue(check.isHasElseUpper(),
                method.getName() + " 必须走 1匹配（按位与），否则多权限位的「或」语义不成立");
        assertFalse(check.isSuchElseRequire(),
                method.getName() + " 必须执行完整身份+权限校验，不能退化成只校验用户存在性");
        assertEquals(EITHER_BIT, check.targetPermission(),
                method.getName() + " 的目标权限位应为 SEC_MAINTAINER | DISCIPLINARY_COMMITTEE");

        // 语义验证：has(your, target) = (your & target) != 0
        assertTrue((PermissionConst.SEC_MAINTAINER & EITHER_BIT) != 0, "二级管理员必须能通过");
        assertTrue((PermissionConst.DISCIPLINARY_COMMITTEE & EITHER_BIT) != 0, "风纪委员必须能通过");
        assertFalse((PermissionConst.FIR_MAINTAINER & EITHER_BIT) != 0,
                "一级管理员位不在授权范围内（与原行为一致）");
        assertFalse((PermissionConst.ADMINISTRATOR & EITHER_BIT) != 0,
                "副馆长位不在授权范围内（与原行为一致，如需开放应显式调整 targetPermission）");
    }
}
