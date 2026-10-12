package com.miaoyu.barc.user.mapper;

import org.apache.ibatis.annotations.Select;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 用户分页 SQL 契约测试。
 *
 * <p>背景（2026-10 修复）：性别与年龄的筛选条件原先写成 {@code b.gender} / {@code b.age}，
 * 而 {@code b} 是 {@code user_basic} —— 这两列实际属于 {@code user_archive}（{@code a}）。
 * 同一句 SQL 里「返回取 a、筛选取 b」，一旦传参就是 Unknown column 报错；
 * 即便 user_basic 真的有这两列，也会因条件与投影不同源而筛出错行。</p>
 *
 * <p>这类错误不会被编译器和常规单测发现（SQL 是字符串），所以用静态断言把它钉死：
 * 1、筛选与投影必须同源（都挂 {@code a.}）；
 * 2、取数与计数必须用同一套条件，否则分页总数与列表对不上；
 * 3、不可达的旧分支不得被加回来。</p>
 */
class UserArchiveMapperUserPageSqlTest {

    /** 匹配 {@code condition.xxx != null} 里的筛选键名。 */
    private static final Pattern CONDITION_KEY =
            Pattern.compile("condition\\.(\\w+)\\s*!=");

    @Test
    @DisplayName("性别/年龄筛选必须挂在 user_archive（a.）上，不得挂到 user_basic（b.）")
    void genderAndAgeFilters_ShouldBeQualifiedWithArchiveAlias() {
        for (String sql : new String[] {listSql(), countSql()}) {
            assertTrue(sql.contains("AND a.gender = #{condition.gender}"),
                    "性别筛选必须写成 a.gender（user_archive 的列）：" + sql);
            assertTrue(sql.contains("AND a.age = #{condition.age}"),
                    "年龄筛选必须写成 a.age（user_archive 的列）：" + sql);
            assertFalse(sql.contains("b.gender"),
                    "不得再出现 b.gender —— user_basic 没有该列，传参即 Unknown column：" + sql);
            assertFalse(sql.contains("b.age"),
                    "不得再出现 b.age —— user_basic 没有该列，传参即 Unknown column：" + sql);
        }
    }

    @Test
    @DisplayName("取数与计数必须共用同一套筛选条件")
    void listAndCount_ShouldShareIdenticalFilterConditions() {
        Set<String> listKeys = conditionKeys(listSql());
        Set<String> countKeys = conditionKeys(countSql());

        assertFalse(listKeys.isEmpty(), "未从列表 SQL 中解析到任何筛选条件，断言失效");
        assertEqualsAsMessage(listKeys, countKeys);
    }

    @Test
    @DisplayName("列表投影必须保留前端列表页依赖的字段")
    void listProjection_ShouldKeepFieldsConsumedByManageFrontend() {
        String sql = listSql();

        // UsersListView / UsersPermissionsView 的表格与状态判定依赖这些字段
        assertTrue(sql.contains("a.age"), "列表页「年龄」列依赖 a.age");
        assertTrue(sql.contains("a.birthday"), "列表页依赖 a.birthday");
        assertTrue(sql.contains("a.gender"), "列表页依赖 a.gender");
        assertTrue(sql.contains("b.safe_level"), "列表页「状态」列与解封按钮依赖 b.safe_level");
        assertTrue(sql.contains("b.username"), "列表页「用户名」列依赖 b.username");
        assertTrue(sql.contains("a.identity"), "列表页「身份」列依赖 a.identity");
    }

    @Test
    @DisplayName("不可达的无条件分页分支不得被加回来")
    void selectByPage_ShouldStayRemoved() {
        // 管理端两个页面恒定携带 identity 筛选，空条件分支不可达；
        // 且它的投影缺少 age/birthday/gender/safe_level，会让同一接口的返回结构随参数变化。
        assertThrows(NoSuchMethodException.class,
                () -> UserArchiveMapper.class.getMethod("selectByPage", int.class, int.class),
                "selectByPage 已被合并进 selectUserInfoByPage，不应重新出现");
    }

    private String listSql() {
        return sqlOf("selectUserInfoByPage", int.class, int.class, java.util.Map.class);
    }

    private String countSql() {
        return sqlOf("countUserInfo", java.util.Map.class);
    }

    private String sqlOf(String name, Class<?>... parameterTypes) {
        try {
            Method method = UserArchiveMapper.class.getMethod(name, parameterTypes);
            Select select = method.getAnnotation(Select.class);
            assertTrue(select != null, name + " 必须仍是 @Select 注解 SQL");
            return String.join(" ", select.value()).replaceAll("\\s+", " ");
        } catch (NoSuchMethodException e) {
            throw new AssertionError(name + " 不存在，用户分页链路已被整体改动", e);
        }
    }

    private Set<String> conditionKeys(String sql) {
        Set<String> keys = new TreeSet<>();
        Matcher matcher = CONDITION_KEY.matcher(sql);
        while (matcher.find()) {
            keys.add(matcher.group(1));
        }
        return keys;
    }

    private void assertEqualsAsMessage(Set<String> expected, Set<String> actual) {
        assertTrue(expected.equals(actual),
                "列表与计数的筛选条件不一致，会导致分页总数与列表对不上。列表=" + expected + "，计数=" + actual);
    }
}
