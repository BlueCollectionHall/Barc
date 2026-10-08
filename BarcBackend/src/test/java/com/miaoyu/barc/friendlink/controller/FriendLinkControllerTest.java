package com.miaoyu.barc.friendlink.controller;

import com.miaoyu.barc.annotation.IgnoreAuth;
import com.miaoyu.barc.annotation.RequireUserAndPermissionAnno;
import com.miaoyu.barc.friendlink.service.FriendLinkService;
import com.miaoyu.barc.permission.PermissionConst;
import com.miaoyu.barc.user.enumeration.UserIdentityEnum;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.multipart.MultipartFile;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 友情链接接口契约测试。
 *
 * 为什么需要它：
 * 1、管理端接口的权限必须与前端 meta（minManagerPermissionBit + 1匹配）成对；
 * 2、AOP 切面按 uuidIndex（默认 0）从 **Service 方法** 第 1 个形参取操作者 uuid
 *    （RestController 上注解不生效，鉴权统一写在 Service 上），
 *    一旦有人把形参顺序改回去，注解会静默失效，因此这里把「注解存在」与「第 1 形参是 String」一起断言；
 * 3、公开接口只有在标注 @IgnoreAuth 时才真正免登录（/api/** 被 AuthInterceptor 覆盖），
 *    这条也必须在测试里钉住；
 * 4、顺带断言 Controller 上的 HTTP 契约（路径 + 方法），防止路径漂移导致前端 404。
 */
class FriendLinkControllerTest {

    @Test
    @DisplayName("管理端六个接口都必须要求馆长 1匹配（ADVANCED_ADMINISTRATOR + isHasElseUpper）")
    void serviceMethods_ShouldRequireCuratorBitPermission() throws NoSuchMethodException {
        assertCuratorBit(FriendLinkService.class.getMethod("uploadService", String.class, MultipartFile.class));
        assertCuratorBit(FriendLinkService.class.getMethod("listService", String.class));
        assertCuratorBit(FriendLinkService.class.getMethod(
                "createService", String.class, FriendLinkService.FriendLinkItem.class));
        assertCuratorBit(FriendLinkService.class.getMethod(
                "updateService", String.class, String.class, FriendLinkService.FriendLinkItem.class));
        assertCuratorBit(FriendLinkService.class.getMethod("moveService", String.class, String.class, String.class));
        assertCuratorBit(FriendLinkService.class.getMethod("deleteService", String.class, String.class));
    }

    @Test
    @DisplayName("被注解方法的第 1 个形参必须是操作者 uuid 字符串（uuidIndex 默认 0）")
    void annotatedMethods_ShouldTakeOperatorUuidAsFirstParameter() throws NoSuchMethodException {
        assertFirstParameterIsString(
                FriendLinkService.class.getMethod("uploadService", String.class, MultipartFile.class));
        assertFirstParameterIsString(FriendLinkService.class.getMethod("listService", String.class));
        assertFirstParameterIsString(FriendLinkService.class.getMethod(
                "createService", String.class, FriendLinkService.FriendLinkItem.class));
        assertFirstParameterIsString(FriendLinkService.class.getMethod(
                "updateService", String.class, String.class, FriendLinkService.FriendLinkItem.class));
        assertFirstParameterIsString(
                FriendLinkService.class.getMethod("moveService", String.class, String.class, String.class));
        assertFirstParameterIsString(
                FriendLinkService.class.getMethod("deleteService", String.class, String.class));
    }

    @Test
    @DisplayName("公开接口必须是 @IgnoreAuth 且不带任何权限注解")
    void publicMethod_ShouldBeIgnoreAuthAndNotAnnotated() throws NoSuchMethodException {
        // /api/** 被 AuthInterceptor 覆盖，只有 @IgnoreAuth 才真正免登录
        Method publicControl = FriendLinkController.class.getMethod("allControl");
        assertNotNull(publicControl.getAnnotation(IgnoreAuth.class),
                "公开接口必须在 Controller 方法上标注 @IgnoreAuth");
        // Service 侧的公开方法不应带权限注解（它本来就要对未登录用户开放）
        Method publicService = FriendLinkService.class.getMethod("publicAllService");
        assertNull(publicService.getAnnotation(RequireUserAndPermissionAnno.class),
                "公开接口的 Service 方法不应带 @RequireUserAndPermissionAnno");
    }

    @Test
    @DisplayName("Controller 路径契约：/api/friend-link + admin/* + 公开 /all")
    void controllerPaths_ShouldMatchFrontendContract() throws NoSuchMethodException {
        RequestMapping base = FriendLinkController.class.getAnnotation(RequestMapping.class);
        assertNotNull(base);
        assertEquals("/api/friend-link", base.value()[0]);

        assertPath(FriendLinkController.class.getMethod(
                "uploadControl", HttpServletRequest.class, MultipartFile.class), PostMapping.class, "/upload");
        assertPath(FriendLinkController.class.getMethod("listControl", HttpServletRequest.class),
                GetMapping.class, "/admin/list");
        assertPath(FriendLinkController.class.getMethod(
                        "createControl", HttpServletRequest.class, FriendLinkService.FriendLinkItem.class),
                PostMapping.class, "/admin/create");
        assertPath(FriendLinkController.class.getMethod(
                        "updateControl", HttpServletRequest.class, String.class, FriendLinkService.FriendLinkItem.class),
                PutMapping.class, "/admin/{id}");
        assertPath(FriendLinkController.class.getMethod(
                        "moveControl", HttpServletRequest.class, String.class, String.class),
                PutMapping.class, "/admin/{id}/move");
        assertPath(FriendLinkController.class.getMethod("deleteControl", HttpServletRequest.class, String.class),
                DeleteMapping.class, "/admin/{id}");
        assertPath(FriendLinkController.class.getMethod("allControl"), GetMapping.class, "/all");
    }

    // ============ 断言辅助 ============

    private void assertCuratorBit(Method method) {
        RequireUserAndPermissionAnno.Check check = firstCheck(method);
        assertEquals(UserIdentityEnum.MANAGER, check.identity());
        assertEquals(PermissionConst.ADVANCED_ADMINISTRATOR, check.targetPermission());
        assertTrue(check.isHasElseUpper(), "馆长权限位必须走 1匹配（isHasElseUpper = true）");
        assertTrue(!check.isSuchElseRequire(), "必须执行完整身份+权限校验，不能只校验用户存在性");
    }

    private void assertFirstParameterIsString(Method method) {
        assertTrue(method.getParameterCount() >= 1, method.getName() + " 至少需要一个形参承载操作者 uuid");
        assertEquals(String.class, method.getParameterTypes()[0],
                method.getName() + " 的第 1 个形参必须是 String 类型的操作者 uuid（uuidIndex 默认 0）");
    }

    private <A extends java.lang.annotation.Annotation> void assertPath(
            Method method, Class<A> mappingType, String expectedPath) {
        A mapping = method.getAnnotation(mappingType);
        assertNotNull(mapping, method.getName() + " 缺少 " + mappingType.getSimpleName() + " 注解");
        // 项目为 Java 17，这里显式分支取值（不用 switch 模式匹配，那是 Java 21 才有的语法）
        String actual;
        if (mapping instanceof GetMapping m) {
            actual = m.value()[0];
        } else if (mapping instanceof PostMapping m) {
            actual = m.value()[0];
        } else if (mapping instanceof PutMapping m) {
            actual = m.value()[0];
        } else if (mapping instanceof DeleteMapping m) {
            actual = m.value()[0];
        } else {
            throw new IllegalStateException("不支持的映射类型: " + mappingType);
        }
        assertEquals(expectedPath, actual, method.getName() + " 的路径与前端契约不一致");
    }

    private RequireUserAndPermissionAnno.Check firstCheck(Method method) {
        RequireUserAndPermissionAnno anno = method.getAnnotation(RequireUserAndPermissionAnno.class);
        assertNotNull(anno, method.getName() + " 必须标注 @RequireUserAndPermissionAnno");
        assertTrue(anno.value().length > 0, method.getName() + " 的鉴权注解至少要有一个 Check");
        return anno.value()[0];
    }
}
