package com.miaoyu.barc.user.service;

import com.miaoyu.barc.user.mapper.UserArchiveMapper;
import com.miaoyu.barc.utils.J;
import com.miaoyu.barc.utils.dto.PageRequestDto;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@code POST /user/query/all_by_page} 服务层契约测试。
 *
 * <p>两点修复的回归保护（2026-10）：
 * 1、{@code PageRequestDto.params} 默认是非 null 的空 Map，但请求体里显式写
 *    {@code "params": null} 时 Jackson 会用 null 覆盖它，旧代码直接 {@code .isEmpty()} 会 NPE，
 *    而这个接口是免登录的，任何人都能触发；
 * 2、原先按「params 是否为空」分流到两条 SQL，两条投影字段不一致。
 *    现合并为一条，取数与计数共用同一份条件。</p>
 */
@ExtendWith(MockitoExtension.class)
class UserServiceGetUsersByPageTest {

    @Mock
    private UserArchiveMapper userArchiveMapper;

    @InjectMocks
    private UserService userService;

    private PageRequestDto pageRequest(Map<String, Object> params) {
        PageRequestDto dto = new PageRequestDto();
        dto.setPage_num(1);
        dto.setPage_size(10);
        dto.setParams(params);
        return dto;
    }

    private void stubEmptyPage() {
        when(userArchiveMapper.selectUserInfoByPage(anyInt(), anyInt(), anyMap())).thenReturn(List.of());
        when(userArchiveMapper.countUserInfo(anyMap())).thenReturn(0L);
    }

    @Test
    @DisplayName("params 为 null 时不得抛 NPE，应正常返回分页结果")
    void getUsersByPage_ShouldTolerateNullParams() {
        stubEmptyPage();

        ResponseEntity<J> response = userService.getUsersByPage(pageRequest(null));

        assertNotNull(response.getBody(), "必须返回响应体");
        assertEquals(0, response.getBody().getCode(), "params 为 null 时应正常返回，而不是抛空指针");
    }

    @Test
    @DisplayName("空条件与带条件走同一条分页 SQL（旧的无条件分支已合并）")
    void getUsersByPage_ShouldAlwaysUseSingleQuery() {
        stubEmptyPage();

        // 空条件（params 为 null）也必须落到 selectUserInfoByPage 上
        userService.getUsersByPage(pageRequest(null));

        verify(userArchiveMapper).selectUserInfoByPage(anyInt(), anyInt(), anyMap());
        verify(userArchiveMapper).countUserInfo(anyMap());
    }

    @Test
    @DisplayName("性别/年龄筛选原样透传给分页 SQL")
    void getUsersByPage_ShouldPassGenderAndAge() {
        stubEmptyPage();

        Map<String, Object> params = new HashMap<>();
        params.put("gender", 1);
        params.put("age", 20);
        userService.getUsersByPage(pageRequest(params));

        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(userArchiveMapper).selectUserInfoByPage(anyInt(), anyInt(), captor.capture());
        assertEquals(1, captor.getValue().get("gender"), "性别筛选必须原样透传");
        assertEquals(20, captor.getValue().get("age"), "年龄筛选必须原样透传");
    }

    @Test
    @DisplayName("取数与计数必须共用同一份条件对象，否则分页总数与列表对不上")
    void getUsersByPage_ShouldShareConditionBetweenListAndCount() {
        stubEmptyPage();

        Map<String, Object> params = new HashMap<>();
        params.put("identity", "USER");
        userService.getUsersByPage(pageRequest(params));

        ArgumentCaptor<Map<String, Object>> listCaptor = ArgumentCaptor.forClass(Map.class);
        ArgumentCaptor<Map<String, Object>> countCaptor = ArgumentCaptor.forClass(Map.class);
        verify(userArchiveMapper).selectUserInfoByPage(anyInt(), anyInt(), listCaptor.capture());
        verify(userArchiveMapper).countUserInfo(countCaptor.capture());

        assertSame(listCaptor.getValue(), countCaptor.getValue(),
                "列表与计数必须使用同一份条件对象");
    }
}
