package com.miaoyu.barc.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.miaoyu.barc.api.work.model.*;
import com.miaoyu.barc.exception.ApiException;
import com.miaoyu.barc.user.mapper.UserArchiveMapper;
import com.miaoyu.barc.user.model.vo.*;
import com.miaoyu.barc.user.service.UserSearchService;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 公开查询合同：匿名搜索、最小字段及分类/作者过滤始终受公开审核门禁约束。 */
class PublicQueryContractTest {
    @Test void emptySearchDoesNotEnumerateUsers() {
        var mapper = mock(UserArchiveMapper.class); var search = new UserSearchService(mapper);
        assertTrue(search.search(null).isEmpty()); assertTrue(search.search(" \t ").isEmpty());
        verifyNoInteractions(mapper);
    }
    @Test void searchNormalizesAndOnlyReturnsSelectorFields() {
        var mapper = mock(UserArchiveMapper.class); var user = new UserInfoVo();
        user.setUuid("u"); user.setNickname("小桃"); user.setSafe_level(9); user.setPermission(32);
        when(mapper.searchByKeyword("小桃")).thenReturn(List.of(user));
        var results = new UserSearchService(mapper).search("  小桃  ");
        var json = new ObjectMapper().valueToTree(results.get(0));
        assertEquals("小桃", json.get("nickname").asText()); assertEquals(4, json.size());
        assertFalse(json.has("permission")); assertFalse(json.has("safe_level"));
        verify(mapper).searchByKeyword("小桃");
    }
    @Test void oversizedSearchFailsBeforeSql() {
        var mapper = mock(UserArchiveMapper.class);
        assertThrows(ApiException.class, () -> new UserSearchService(mapper).search("x".repeat(101)));
        verifyNoInteractions(mapper);
    }
    @Test void publicUserDoesNotExposeBirthdayOrAccountState() {
        var user = new UserInfoVo(); user.setAge(20); user.setBirthday(java.time.LocalDate.of(2000,1,1)); user.setSafe_level(5);
        var json = new ObjectMapper().valueToTree(PublicUserView.from(user));
        assertEquals(20, json.get("age").asInt()); assertFalse(json.has("birthday")); assertFalse(json.has("safe_level"));
    }
    @Test void publicWorkDoesNotExposeInternalReviewOrRestoreData() {
        var work = new WorkModel(); work.setId("w"); work.setReview_reason("内部原因"); work.setReviewer_uuid("manager");
        work.setContent_revision(13L); work.setRestore_status(com.miaoyu.barc.api.work.enumeration.WorkStatusEnum.PRIVATE);
        var json = new ObjectMapper().valueToTree(PublicWorkView.from(work));
        assertEquals("w", json.get("id").asText());
        for (String key : List.of("review_reason", "reviewer_uuid", "reviewed_at", "restore_status", "content_revision", "expected_revision")) assertFalse(json.has(key), key);
    }
    @Test void combinedPublicFilterUsesBoundParametersAndMandatoryGate() throws Exception {
        Configuration configuration = new Configuration();
        try (var stream = getClass().getClassLoader().getResourceAsStream("mappers/work/WorkMapper.xml")) {
            new XMLMapperBuilder(stream, configuration, "mappers/work/WorkMapper.xml", configuration.getSqlFragments()).parse();
        }
        var params = Map.<String,Object>of("status", "PUBLIC", "condition", Map.of("category_id", "category-x", "author_uuid", "user-x", "keyword", "' OR 1=1 --"), "offset", 0, "page_size", 20);
        for (String method : List.of("selectByPage", "countByPage")) {
            var bound = configuration.getMappedStatement("com.miaoyu.barc.api.work.mapper.WorkMapper." + method).getBoundSql(params);
            String sql = bound.getSql();
            assertTrue(sql.contains("wr.status = 'APPROVED'")); assertTrue(sql.contains("w.author = ?")); assertTrue(sql.contains("wc.category_id = ?"));
            assertFalse(sql.contains("category-x")); assertFalse(sql.contains("OR 1=1"));
        }
    }
}
