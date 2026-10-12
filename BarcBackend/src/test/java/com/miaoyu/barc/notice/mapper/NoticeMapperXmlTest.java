package com.miaoyu.barc.notice.mapper;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 公告 Mapper XML 的静态守护测试
 *
 * 背景：本次改动让 /notice/latest 在「day 天内查不到公告」时回退取全表最近一条公告，
 * 而 NoticeService 的单元测试把 Mapper 整体 Mock 掉了，XML 里即使写错 statement id、
 * 或误把天数过滤条件留在兜底语句里，单测也不会失败（前者运行期报 Invalid bound statement，
 * 后者会直接退回「超期即无公告」的原始缺陷）。
 * 因此这里照项目既有惯例（WorkMapperXmlProjectionTest）对 XML 做静态断言，防止回归。
 */
class NoticeMapperXmlTest {

    @Test
    @DisplayName("接口方法 latestWithoutDayLimit 必须有同名的 XML statement")
    void latestWithoutDayLimit_ShouldHaveMatchingStatementId() throws IOException {
        // 接口方法必须存在（被删除时 getMethod 直接抛异常使测试失败），且 XML 中存在同名 statement，
        // 避免运行期抛 Invalid bound statement (not found)
        assertDoesNotThrow(() -> NoticeMapper.class.getMethod("latestWithoutDayLimit"),
                "NoticeMapper.latestWithoutDayLimit 方法必须存在");

        String statementBlock = latestWithoutDayLimitBlock();
        assertFalse(statementBlock.isEmpty(),
                "NoticeMapper.xml 必须存在 id=\"latestWithoutDayLimit\" 的 select 语句");
    }

    @Test
    @DisplayName("兜底查询不得带任何时间过滤条件，否则会退化成「超期即无公告」")
    void latestWithoutDayLimit_ShouldNotFilterByTime() throws IOException {
        String statementBlock = latestWithoutDayLimitBlock();

        assertFalse(statementBlock.toUpperCase().contains("INTERVAL"),
                "兜底查询不允许出现 INTERVAL 时间过滤条件");
        assertFalse(statementBlock.toUpperCase().contains("DATE_SUB"),
                "兜底查询不允许出现 DATE_SUB 时间过滤条件");
        assertFalse(statementBlock.toUpperCase().contains("WHERE"),
                "兜底查询不应带 WHERE 过滤条件");
    }

    @Test
    @DisplayName("兜底查询应按 updated_at 倒序取全表最新一条公告")
    void latestWithoutDayLimit_ShouldOrderByUpdatedAtDescLimitOne() throws IOException {
        String statementBlock = latestWithoutDayLimitBlock();

        assertTrue(statementBlock.contains("FROM notice"), "兜底查询必须查询 notice 表");
        assertTrue(statementBlock.contains("ORDER BY updated_at DESC"),
                "兜底查询必须按 updated_at 倒序排序");
        assertTrue(statementBlock.contains("LIMIT 1"), "兜底查询必须只取一条公告");
    }

    @Test
    @DisplayName("最新公告合并为一次查询，移除无实际作用的天数过滤分支")
    void latest_UsesOnlyOneUnboundedByDayQuery() throws IOException {
        assertFalse(readNoticeMapperXml().contains("<select id=\"latest\""));
        assertFalse(Arrays.stream(NoticeMapper.class.getDeclaredMethods()).anyMatch(method -> method.getName().equals("latest")));
    }

    @Test
    @DisplayName("接口新增方法名与 XML statement id 必须逐一对应")
    void mapperMethods_ShouldAllBeDeclaredInXml() throws IOException {
        String xml = readNoticeMapperXml();

        boolean allDeclared = Arrays.stream(NoticeMapper.class.getDeclaredMethods())
                .map(Method::getName)
                .allMatch(name -> xml.contains("<select id=\"" + name + "\"")
                        || xml.contains("<insert id=\"" + name + "\"")
                        || xml.contains("<update id=\"" + name + "\"")
                        || xml.contains("<delete id=\"" + name + "\""));
        assertTrue(allDeclared, "NoticeMapper 的每个接口方法都必须在 XML 中有对应 statement");
    }

    // ============ 测试辅助 ============

    private String latestWithoutDayLimitBlock() throws IOException {
        return slice(readNoticeMapperXml(),
                "<select id=\"latestWithoutDayLimit\"", "</select>");
    }

    private String readNoticeMapperXml() throws IOException {
        try (InputStream inputStream = getClass().getClassLoader()
                .getResourceAsStream("mappers/Notice/NoticeMapper.xml")) {
            if (inputStream == null) {
                throw new IOException("Unable to load mappers/Notice/NoticeMapper.xml");
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
}
