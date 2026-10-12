package com.miaoyu.barc.security;

import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

/** 使用真实 MyBatis 动态 SQL 构造验证排序注入不能进入最终语句，无数据库连接。 */
class WorkSortSqlRegressionTest {
    private static Configuration configuration;

    @BeforeAll static void loadMapper() throws Exception {
        configuration = new Configuration();
        try (InputStream stream = Resources.getResourceAsStream("mappers/work/WorkMapper.xml")) {
            new XMLMapperBuilder(stream, configuration, "mappers/work/WorkMapper.xml", configuration.getSqlFragments()).parse();
        }
    }

    @ParameterizedTest @ValueSource(strings = {"id", "title", "updated_at", "view_count", "like_count", "created_at"})
    void supportedSortsStayUsable(String field) {
        assertTrue(sql(field, "ASC").contains("ORDER BY w." + field + " ASC, w.id DESC"));
        assertTrue(sql(field, "desc").contains("ORDER BY w." + field + " DESC, w.id DESC"));
    }

    @Test void maliciousFieldsAndDirectionsFallBackToStaticDefaults() {
        String sql = sql("id,(SELECT SLEEP(9))", "DESC; DROP TABLE work");
        assertTrue(sql.contains("ORDER BY w.created_at DESC, w.id DESC"));
        assertFalse(sql.contains("SLEEP")); assertFalse(sql.contains("DROP"));
        assertTrue(sql(null, null).contains("ORDER BY w.created_at DESC, w.id DESC"));
        assertTrue(sql("created_at", "asc").contains("ORDER BY w.created_at ASC, w.id DESC"));
    }

    private String sql(String field, String order) {
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("sortField", field); parameters.put("sortOrder", order);
        parameters.put("status", null); parameters.put("keyword", null);
        parameters.put("offset", 0); parameters.put("limit", 10);
        return configuration.getMappedStatement("com.miaoyu.barc.api.work.mapper.WorkMapper.selectByPageForManage")
                .getBoundSql(parameters).getSql().replaceAll("\\s+", " ").replaceAll("\\s*,\\s*", ", ").trim();
    }
}
