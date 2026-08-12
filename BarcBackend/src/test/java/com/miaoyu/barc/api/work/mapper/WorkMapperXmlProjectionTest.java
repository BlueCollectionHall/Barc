package com.miaoyu.barc.api.work.mapper;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkMapperXmlProjectionTest {

    @Test
    @DisplayName("公开分页作品列表查询应返回浏览量和点赞数")
    void selectByPage_ShouldProjectViewCountAndLikeCount() throws IOException {
        String xml = readWorkMapperXml();
        String selectByPageBlock = slice(xml, "<select id=\"selectByPage\"", "</select>");

        assertTrue(selectByPageBlock.contains("w.view_count"), "selectByPage should project w.view_count");
        assertTrue(selectByPageBlock.contains("w.like_count"), "selectByPage should project w.like_count");
    }

    @Test
    @DisplayName("公开分页作品列表查询应返回内容更新时间")
    void selectByPage_ShouldProjectContentUpdatedAt() throws IOException {
        String xml = readWorkMapperXml();
        String selectByPageBlock = slice(xml, "<select id=\"selectByPage\"", "</select>");

        assertTrue(selectByPageBlock.contains("w.content_updated_at"), "selectByPage should project w.content_updated_at");
    }

    @Test
    @DisplayName("公开分类分页作品列表查询应返回浏览量和点赞数")
    void selectByPageOnCategory_ShouldProjectViewCountAndLikeCount() throws IOException {
        String xml = readWorkMapperXml();
        String selectByPageOnCategoryBlock = slice(xml, "<select id=\"selectByPageOnCategory\"", "</select>");

        assertTrue(selectByPageOnCategoryBlock.contains("w.view_count"), "selectByPageOnCategory should project w.view_count");
        assertTrue(selectByPageOnCategoryBlock.contains("w.like_count"), "selectByPageOnCategory should project w.like_count");
    }

    @Test
    @DisplayName("公开分类分页作品列表查询应返回内容更新时间")
    void selectByPageOnCategory_ShouldProjectContentUpdatedAt() throws IOException {
        String xml = readWorkMapperXml();
        String selectByPageOnCategoryBlock = slice(xml, "<select id=\"selectByPageOnCategory\"", "</select>");

        assertTrue(selectByPageOnCategoryBlock.contains("w.content_updated_at"), "selectByPageOnCategory should project w.content_updated_at");
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
}
