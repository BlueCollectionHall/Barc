package com.miaoyu.barc.api.work.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkMapperContentUpdatedAtSqlTest {

    @Test
    @DisplayName("按天查询应使用内容更新时间并回退创建时间")
    void selectByDay_ShouldFilterByCoalescedContentUpdatedAt() throws NoSuchMethodException {
        String sql = normalizedSelectSql(WorkMapper.class.getMethod("selectByDay", Integer.class, com.miaoyu.barc.api.work.enumeration.WorkStatusEnum.class));

        assertTrue(sql.contains("COALESCE(content_updated_at, created_at) >= DATE_SUB(now(), INTERVAL #{day} DAY)"),
                "selectByDay should filter by COALESCE(content_updated_at, created_at)");
    }

    @Test
    @DisplayName("公开分页排序应使用内容更新时间并回退创建时间")
    void selectByPage_ShouldOrderByCoalescedContentUpdatedAt() throws IOException {
        String block = normalizedXmlBlock("<select id=\"selectByPage\"", "</select>");

        assertTrue(block.contains("ORDER BY COALESCE(w.content_updated_at, w.created_at) DESC"),
                "selectByPage should order by coalesced content freshness");
    }

    @Test
    @DisplayName("公开分类分页排序应使用内容更新时间并回退创建时间")
    void selectByPageOnCategory_ShouldOrderByCoalescedContentUpdatedAt() throws IOException {
        String block = normalizedXmlBlock("<select id=\"selectByPageOnCategory\"", "</select>");

        assertTrue(block.contains("ORDER BY COALESCE(w.content_updated_at, w.created_at) DESC"),
                "selectByPageOnCategory should order by coalesced content freshness");
    }

    @Test
    @DisplayName("作者作品筛选排序应使用内容更新时间并回退创建时间")
    void selectByUuidWithFilters_ShouldOrderByCoalescedContentUpdatedAt() throws IOException {
        String block = normalizedXmlBlock("<select id=\"selectByUuidWithFilters\"", "</select>");

        assertTrue(block.contains("ORDER BY COALESCE(w.content_updated_at, w.created_at) DESC"),
                "selectByUuidWithFilters should order by coalesced content freshness");
    }

    @Test
    @DisplayName("公开喜欢作品投影应包含内容更新时间")
    void likedWorksProjection_ShouldExposeContentUpdatedAt() throws NoSuchMethodException {
        String sql = normalizedSelectSql(WorkLikeMapper.class.getMethod(
                "selectPublicLikedWorksByUsername",
                String.class,
                com.miaoyu.barc.api.work.enumeration.WorkStatusEnum.class,
                Integer.class,
                Integer.class));

        assertTrue(sql.contains("w.content_updated_at"),
                "selectPublicLikedWorksByUsername should project w.content_updated_at");
    }

    @Test
    @DisplayName("纯文字更新应刷新内容更新时间")
    void updateText_ShouldSetContentUpdatedAt() throws NoSuchMethodException {
        String sql = normalizedUpdateSql(WorkMapper.class.getMethod("updateText", com.miaoyu.barc.api.work.model.WorkModel.class));

        assertTrue(sql.contains("content_updated_at = CURRENT_TIMESTAMP"),
                "updateText should explicitly set content_updated_at");
    }

    @Test
    @DisplayName("通用更新仅在内容字段变化时刷新内容更新时间")
    void update_ShouldRefreshContentUpdatedAtOnlyWhenContentLikeFieldsChange() throws NoSuchMethodException {
        String sql = normalizedUpdateSql(WorkMapper.class.getMethod("update", com.miaoyu.barc.api.work.model.WorkModel.class));

        assertTrue(sql.contains("content_updated_at = CASE WHEN NOT"),
                "generic update should guard content_updated_at with a CASE expression");
        assertTrue(sql.contains("title <=> #{title}"), "generic update should compare title before refreshing content freshness");
        assertTrue(sql.contains("description <=> #{description}"), "generic update should compare description before refreshing content freshness");
        assertTrue(sql.contains("content <=> #{content}"), "generic update should compare content before refreshing content freshness");
        assertTrue(sql.contains("author_nickname <=> #{author_nickname}"), "generic update should compare author_nickname before refreshing content freshness");
        assertTrue(sql.contains("student <=> #{student}"), "generic update should compare student before refreshing content freshness");
        assertTrue(sql.contains("THEN CURRENT_TIMESTAMP ELSE content_updated_at END"),
                "generic update should preserve content_updated_at when only status/claim/cover-like fields change");
        assertAssignmentBefore(sql, "content_updated_at = CASE", "title = #{title}");
        assertAssignmentBefore(sql, "content_updated_at = CASE", "description = #{description}");
        assertAssignmentBefore(sql, "content_updated_at = CASE", "content = #{content}");
        assertAssignmentBefore(sql, "content_updated_at = CASE", "author_nickname = #{author_nickname}");
        assertAssignmentBefore(sql, "content_updated_at = CASE", "student = #{student}");
    }

    @Test
    @DisplayName("作品创建应显式写入内容更新时间")
    void insert_ShouldSetContentUpdatedAt() throws NoSuchMethodException {
        String sql = normalizedInsertSql(WorkMapper.class.getMethod("insert", com.miaoyu.barc.api.work.model.WorkModel.class));

        assertTrue(sql.contains("content_updated_at"), "insert should include content_updated_at column");
        assertTrue(sql.contains("CURRENT_TIMESTAMP"), "insert should set content_updated_at explicitly");
    }

    @Test
    @DisplayName("浏览和点赞计数SQL不应触碰内容更新时间")
    void viewAndLikeCounterSql_ShouldNotMentionContentUpdatedAt() throws NoSuchMethodException {
        assertFalse(normalizedUpdateSql(WorkMapper.class.getMethod("incrementViewCount", String.class)).contains("content_updated_at"),
                "incrementViewCount should not touch content_updated_at");
        assertFalse(normalizedUpdateSql(WorkMapper.class.getMethod("incrementLikeCount", String.class)).contains("content_updated_at"),
                "incrementLikeCount should not touch content_updated_at");
        assertFalse(normalizedUpdateSql(WorkMapper.class.getMethod("decrementLikeCount", String.class)).contains("content_updated_at"),
                "decrementLikeCount should not touch content_updated_at");
    }

    private String normalizedSelectSql(Method method) {
        return normalize(String.join(" ", method.getAnnotation(Select.class).value()));
    }

    private String normalizedInsertSql(Method method) {
        return normalize(String.join(" ", method.getAnnotation(Insert.class).value()));
    }

    private String normalizedUpdateSql(Method method) {
        return normalize(String.join(" ", method.getAnnotation(Update.class).value()));
    }

    private String normalizedXmlBlock(String startMarker, String endMarker) throws IOException {
        return normalize(slice(readWorkMapperXml(), startMarker, endMarker));
    }

    private String readWorkMapperXml() throws IOException {
        try (InputStream inputStream = getClass().getClassLoader().getResourceAsStream("mappers/work/WorkMapper.xml")) {
            if (inputStream == null) {
                throw new IOException("Unable to load mappers/work/WorkMapper.xml");
            }
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private String slice(String xml, String startMarker, String endMarker) {
        int start = xml.indexOf(startMarker);
        int end = xml.indexOf(endMarker, start);
        if (start < 0 || end < 0) {
            return "";
        }
        return xml.substring(start, end + endMarker.length());
    }

    private String normalize(String sql) {
        return sql.replaceAll("\\s+", " ").trim();
    }

    private void assertAssignmentBefore(String sql, String earlierAssignment, String laterAssignment) {
        int earlierIndex = sql.indexOf(earlierAssignment);
        int laterIndex = sql.indexOf(laterAssignment);
        assertTrue(earlierIndex >= 0, "SQL should contain assignment: " + earlierAssignment);
        assertTrue(laterIndex >= 0, "SQL should contain assignment: " + laterAssignment);
        assertTrue(earlierIndex < laterIndex,
                earlierAssignment + " should appear before " + laterAssignment + " to avoid MySQL left-to-right UPDATE evaluation");
    }
}
