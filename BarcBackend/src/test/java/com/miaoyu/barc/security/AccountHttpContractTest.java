package com.miaoyu.barc.security;

import com.miaoyu.barc.user.controller.*;
import com.miaoyu.barc.user.enumeration.UserIdentityEnum;
import com.miaoyu.barc.user.service.*;
import org.junit.jupiter.api.*;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.*;
import static com.miaoyu.barc.security.HttpContractSupport.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** 活跃双端的账号 HTTP 合同：凭据不再通过 GET，个人资料不接受权限和安全状态。 */
class AccountHttpContractTest {
    UserService users = apiMock(UserService.class);
    AccountSecurityService security = apiMock(AccountSecurityService.class);
    SignService sign = apiMock(SignService.class);
    UserPermissionService permissions = apiMock(UserPermissionService.class);
    OfficialAccountService official = apiMock(OfficialAccountService.class);
    UserBanService bans = apiMock(UserBanService.class);
    MockMvc mvc;
    @BeforeEach void setup() {
        var user = new UserController(); var login = new SignController(); var permission = new UserPermissionController(); var account = new OfficialAccountController(); var ban = new UserBanController();
        ReflectionTestUtils.setField(user, "userService", users); ReflectionTestUtils.setField(user, "accountSecurityService", security); ReflectionTestUtils.setField(login, "signService", sign);
        ReflectionTestUtils.setField(permission, "userPermissionService", permissions); ReflectionTestUtils.setField(account, "officialAccountService", official); ReflectionTestUtils.setField(ban, "userBanService", bans); mvc = mvc(user, login, permission, account, ban);
    }
    @Test void loginAndSignupUseJsonAndGetNoLongerExecutes() throws Exception {
        String login = "{\"type\":\"email\",\"account\":\"a@example.test\",\"password\":\"test-password\"}";
        ok(post("/user/sign/in").contentType(MediaType.APPLICATION_JSON).content(login)); verify(sign).signInUserService("email", "a@example.test", "test-password");
        ok(post("/user/sign/in_by_naigos").contentType(MediaType.APPLICATION_JSON).content(login)); verify(sign).signInByNaigosService("email", "a@example.test", "test-password");
        ok(post("/user/sign/up").contentType(MediaType.APPLICATION_JSON).content("{\"username\":\"new-user\",\"password\":\"test-password\",\"email\":\"a@example.test\",\"unique_id\":\"challenge\",\"code\":\"123456\",\"safe_level\":0}"));
        verify(sign).register(argThat(request -> request.username().equals("new-user") && request.unique_id().equals("challenge") && request.code().equals("123456")));
        for (String route : new String[]{"in", "in_by_naigos", "up"}) mvc.perform(get("/user/sign/" + route).param("password", "query-password")).andExpect(status().isMethodNotAllowed());
    }
    @Test void publicProfilesAndAuthenticatedReadsRemainSeparate() throws Exception {
        ok(get("/user/current_by_uuid").param("uuid", "target")); verify(users).getCurrentByUuidService("target");
        ok(get("/user/current_by_username").param("username", "public-name")); verify(users).getCurrentByUsernameService("public-name");
        ok(get("/user/current_me").requestAttr("uuid", "actor").param("uuid", "forged")); verify(users).getMeCurrentService("actor");
        ok(get("/user/basic_me").requestAttr("uuid", "actor")); verify(users).getMeBasicService("actor");
    }
    @Test void profileAndBasicUpdatesUseDistinctWhitelistedCommands() throws Exception {
        ok(post("/user/edit_archive").requestAttr("uuid", "actor").contentType(MediaType.APPLICATION_JSON).content("{\"nickname\":\"名称\",\"identity\":\"MANAGER\",\"permission\":32,\"uuid\":\"forged\"}")); verify(users).editArchiveService(eq("actor"), argThat(request -> request.getNickname().equals("名称")));
        ok(post("/user/edit_basic").requestAttr("uuid", "actor").contentType(MediaType.APPLICATION_JSON).content("{\"username\":\"new-name\",\"telephone\":\"123\",\"email\":\"forged@example.test\"}")); verify(security).updateBasic(eq("actor"), argThat(request -> request.username().equals("new-name")));
        ok(post("/user/reset_password").requestAttr("uuid", "actor").contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"a@example.test\",\"unique_id\":\"challenge\",\"code\":\"123456\",\"password\":\"new-password\"}")); verify(security).resetPassword("actor", "challenge", "123456", "a@example.test", "new-password");
        ok(post("/user/change_email").requestAttr("uuid", "actor").contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"new@example.test\",\"current_password\":\"current-password\",\"unique_id\":\"challenge\",\"code\":\"123456\"}")); verify(security).changeEmail(eq("actor"), argThat(request -> request.email().equals("new@example.test") && request.current_password().equals("current-password")));
        mvc.perform(get("/user/reset_password").requestAttr("uuid", "actor")).andExpect(status().isMethodNotAllowed());
    }
    @Test void permissionManagementBindsTargetWithoutOverwritingOperator() throws Exception {
        ok(get("/user/permission/all_identities"));
        ok(get("/user/permission/permissions_by_identity").param("identity", "MANAGER")); verify(permissions).getUserPermissionsByIdentityService(UserIdentityEnum.MANAGER);
        ok(get("/user/permission/permission_cn").param("identity", "USER").param("permission", "2")); verify(permissions).getUserPermissionCnService(UserIdentityEnum.USER, 2);
        ok(get("/user/permission/me_permission_near_max").requestAttr("uuid", "actor")); verify(permissions).getUserPermissionNearMaxService("actor");
        ok(post("/user/permission/change_permission").requestAttr("uuid", "actor").contentType(MediaType.APPLICATION_JSON).content("{\"uuid\":\"target\",\"identity\":\"MANAGER\",\"permission\":5}")); verify(permissions).changeUserPermissionService("actor", "target", UserIdentityEnum.MANAGER, 5);
    }
    @Test void officialBindingAndSearchPassExplicitBusinessKey() throws Exception {
        ok(get("/user/official/list").requestAttr("uuid", "actor")); verify(official).listService("actor");
        ok(post("/user/official/bind").requestAttr("uuid", "actor").contentType(MediaType.APPLICATION_JSON).content("{\"business_key\":\"COLLECTION_ASSISTANT\",\"user_uuid\":\"target\",\"remark\":\"说明\"}")); verify(official).bindService("actor", "COLLECTION_ASSISTANT", "target", "说明");
        ok(get("/user/official/search_user").requestAttr("uuid", "actor").param("keyword", "名称")); verify(official).searchUserService("actor", "名称");
    }
    @Test void banRequestsBindTargetAndKeepOperatorInRequestAttribute() throws Exception {
        ok(post("/user/ban").requestAttr("uuid", "actor").contentType(MediaType.APPLICATION_JSON).content("{\"userId\":\"target\",\"banType\":2,\"reason\":\"原因\",\"durationDays\":3}")); verify(bans).banUser("actor", 1, "target", 2, "原因", 3);
        ok(post("/user/ban/unban").requestAttr("uuid", "actor").contentType(MediaType.APPLICATION_JSON).content("{\"userId\":\"target\",\"reason\":\"申诉通过\"}")); verify(bans).unbanUser("actor", "target", "申诉通过");
        ok(get("/user/ban/history").requestAttr("uuid", "actor").param("userId", "target")); verify(bans).getBanHistory("target", 1, 10);
        ok(get("/user/ban/status").requestAttr("uuid", "actor").param("userId", "target")); verify(bans).getUserStatus("target");
    }
    private void ok(RequestBuilder request) throws Exception { mvc.perform(request).andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0)); }
}
