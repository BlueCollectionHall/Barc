package com.miaoyu.barc.user.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.miaoyu.barc.user.mapper.UserBasicMapper;
import com.miaoyu.barc.user.model.UserBasicModel;
import com.miaoyu.barc.user.model.vo.UserBasicVo;
import com.miaoyu.barc.utils.J;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

/**
 * {@code GET /user/basic_me} 脱敏回归测试。
 *
 * <p>为什么需要它：该接口原先直接把 {@link UserBasicModel}（数据库行对象）返回给前端，
 * 报文里带着 {@code password} 口令哈希与 {@code password_version}。
 * 这类回归非常隐蔽——前端不读该字段，功能测试全绿，只有抓包才看得出来。
 * 因此这里用「真实序列化链路」断言报文里不能出现哈希，
 * 而不是只断言某个 getter 返回 null。</p>
 *
 * <p>同时锁住双端已消费字段：BarcFrontend 的 UserPinia 会拿 uuid / username / email /
 * telephone 去渲染账号信息与反馈表单，一旦有人「顺手」把这些也一起脱敏掉，两端会静默白屏。</p>
 */
@ExtendWith(MockitoExtension.class)
class UserBasicMeDesensitizeTest {

    @Mock
    private UserBasicMapper userBasicMapper;

    @InjectMocks
    private UserService userService;

    /** 口令哈希样例：与真实库 user_basic.password 列同形态（32 位十六进制） */
    private static final String PASSWORD_HASH = "5f4dcc3b5aa765d61d8327deb882cf99";

    private static final String UUID = "u-0001";

    /** 构造一行「带全部敏感列」的用户基础信息，模拟数据库真实返回 */
    private UserBasicModel rowWithSecret() {
        UserBasicModel model = new UserBasicModel();
        model.setUuid(UUID);
        model.setUsername("barc_tester");
        model.setPassword(PASSWORD_HASH);
        model.setPassword_version(3);
        model.setEmail("tester@barc.work");
        model.setEmail_verified(true);
        model.setTelephone("13800000000");
        model.setSafe_level(100);
        model.setSafe_level_before_ban(80);
        model.setCreated_at(LocalDateTime.of(2026, 1, 2, 3, 4, 5));
        model.setUpdated_at(LocalDateTime.of(2026, 9, 10, 11, 12, 13));
        return model;
    }

    @Test
    @DisplayName("basic_me 的返回报文里不得出现口令哈希")
    void getMeBasicService_ShouldNotSerializePasswordHash() throws Exception {
        when(userBasicMapper.selectByUuid(UUID)).thenReturn(rowWithSecret());

        ResponseEntity<J> response = userService.getMeBasicService(UUID);
        J body = response.getBody();
        assertNotNull(body, "接口必须返回响应体");
        assertEquals(0, body.getCode(), "正常查询必须返回 code=0");

        // 走真实序列化链路：只要哈希或密码字段出现在报文里，这条断言就会红
        ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
        String json = mapper.writeValueAsString(body);
        assertFalse(json.contains(PASSWORD_HASH), "返回报文里出现了口令哈希：" + json);
        assertFalse(json.contains("password"), "返回报文里出现了 password 相关字段：" + json);
        assertFalse(json.contains("safe_level_before_ban"), "返回报文里出现了封号前等级：" + json);
    }

    @Test
    @DisplayName("UserBasicVo 不得声明 password / password_version / safe_level_before_ban 字段")
    void userBasicVo_ShouldNotDeclareSecretFields() {
        List<String> names = Arrays.stream(UserBasicVo.class.getDeclaredFields())
                .map(Field::getName)
                .collect(Collectors.toList());

        assertFalse(names.contains("password"), "对外视图不得携带口令哈希字段");
        assertFalse(names.contains("password_version"), "对外视图不得携带口令版本字段");
        assertFalse(names.contains("safe_level_before_ban"), "对外视图不需要封号前等级");
        assertTrue(names.contains("username"), "username 是双端消费字段，必须保留");
        assertTrue(names.contains("email"), "email 是反馈表单自动填充字段，必须保留");
        assertTrue(names.contains("telephone"), "telephone 被基础资料编辑表单直接绑定，必须保留");
    }

    @Test
    @DisplayName("双端已消费的公开字段必须原样透传")
    void getMeBasicService_ShouldKeepFieldsConsumedByBothFrontends() {
        when(userBasicMapper.selectByUuid(UUID)).thenReturn(rowWithSecret());

        J body = userService.getMeBasicService(UUID).getBody();
        assertNotNull(body);

        Object data = body.getData();
        assertTrue(data instanceof UserBasicVo, "返回体必须是脱敏视图，而不是数据库行对象");
        UserBasicVo vo = (UserBasicVo) data;

        assertEquals(UUID, vo.getUuid());
        assertEquals("barc_tester", vo.getUsername());
        assertEquals("tester@barc.work", vo.getEmail());
        assertEquals("13800000000", vo.getTelephone());
        assertEquals(Integer.valueOf(100), vo.getSafe_level());
        assertEquals(Boolean.TRUE, vo.getEmail_verified());
        assertEquals(LocalDateTime.of(2026, 1, 2, 3, 4, 5), vo.getCreated_at());
        assertEquals(LocalDateTime.of(2026, 9, 10, 11, 12, 13), vo.getUpdated_at());
    }

    @Test
    @DisplayName("user_basic 缺行时返回「用户不存在」，不再返回 null data")
    void getMeBasicService_ShouldReportMissingRow() {
        when(userBasicMapper.selectByUuid("ghost-uuid")).thenReturn(null);

        J body = userService.getMeBasicService("ghost-uuid").getBody();
        assertNotNull(body, "缺行时也必须返回响应体");
        assertEquals(1, body.getCode(), "缺行时必须返回非 0 code，而不是成功但 data 为 null");
    }
}
