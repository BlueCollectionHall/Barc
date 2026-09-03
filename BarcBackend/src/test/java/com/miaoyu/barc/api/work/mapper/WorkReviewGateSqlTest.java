package com.miaoyu.barc.api.work.mapper;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkReviewGateSqlTest {

    @Test
    @DisplayName("公开作品分页和分类分页必须在SQL层限制审核通过")
    void publicQueries_ShouldRequireApprovedReview() throws IOException {
        String xml = read("mappers/work/WorkMapper.xml");
        String publicWhere = slice(xml, "<sql id=\"whereConditions\">", "</sql>");
        String categoryWhere = slice(xml, "<sql id=\"whereConditionOnCategory\">", "</sql>");

        assertTrue(publicWhere.contains("wr.status = 'APPROVED'"));
        assertTrue(categoryWhere.contains("wr.status = 'APPROVED'"));
    }

    @Test
    @DisplayName("迁移应将存量作品标记通过并让新记录默认待审")
    void migration_ShouldBackfillLegacyAndDefaultNewWorksToPending() throws IOException {
        String sql = read("db/migration/V7__add_work_review.sql");

        assertTrue(sql.contains("DEFAULT 'PENDING'"));
        assertTrue(sql.contains("SELECT w.id, 'APPROVED'"));
    }

    @Test
    @DisplayName("审核列表应关联用户档案返回作者展示名并支持昵称搜索")
    void reviewQuery_ShouldResolveAndSearchAuthorNickname() throws IOException {
        String xml = read("mappers/work/WorkMapper.xml");
        String listQuery = slice(xml, "<select id=\"selectByPageForReview\"", "</select>");
        String countQuery = slice(xml, "<select id=\"countByPageForReview\"", "</select>");

        assertTrue(listQuery.contains("LEFT JOIN user_archive author_archive"));
        assertTrue(listQuery.contains("author_archive.nickname, ''), w.author) AS author_display"));
        assertTrue(listQuery.contains("w.is_claim = 1 AND author_archive.nickname LIKE"));
        assertTrue(listQuery.contains("w.is_claim = 0 AND w.author_nickname LIKE"));
        assertTrue(countQuery.contains("LEFT JOIN user_archive author_archive"));
        assertTrue(countQuery.contains("w.is_claim = 1 AND author_archive.nickname LIKE"));
        assertTrue(countQuery.contains("w.is_claim = 0 AND w.author_nickname LIKE"));
    }

    @Test
    @DisplayName("再次提审必须由SQL原子限制为驳回状态")
    void resubmitQuery_ShouldOnlyTransitionRejectedReviews() throws IOException {
        String mapper = readJavaSource("src/main/java/com/miaoyu/barc/api/work/mapper/WorkReviewMapper.java");

        assertTrue(mapper.contains("boolean resubmitRejected"));
        assertTrue(mapper.contains("WHERE work_id = #{work_id} AND status = 'REJECTED'"));
    }

    private String read(String resource) throws IOException {
        try (InputStream input = getClass().getClassLoader().getResourceAsStream(resource)) {
            if (input == null) throw new IOException("Unable to load " + resource);
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private String readJavaSource(String path) throws IOException {
        return java.nio.file.Files.readString(java.nio.file.Path.of(path), StandardCharsets.UTF_8);
    }

    private String slice(String source, String startMarker, String endMarker) {
        int start = source.indexOf(startMarker);
        int end = source.indexOf(endMarker, start);
        return start >= 0 && end >= 0 ? source.substring(start, end + endMarker.length()) : "";
    }
}
