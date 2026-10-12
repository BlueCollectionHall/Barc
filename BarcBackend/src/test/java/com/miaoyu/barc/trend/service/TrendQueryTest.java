package com.miaoyu.barc.trend.service;

import com.miaoyu.barc.trend.mapper.*;
import com.miaoyu.barc.trend.model.TrendModel;
import com.miaoyu.barc.exception.ApiException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 动态空库、搜索参数与各历史入口的容量边界。 */
@ExtendWith(MockitoExtension.class)
class TrendQueryTest {
    @Mock TrendSelectMapper trendSelectMapper;
    @Mock TrendImageMapper trendImageMapper;
    @InjectMocks TrendService service;
    @Test void legacyListsShareBoundedQuery() {
        service.getAllTrendService(); service.getNewTrendService(Integer.MAX_VALUE); service.getTrendsByUuidService("u");
        verify(trendSelectMapper, times(2)).selectPage(null, null, 0, 100); verify(trendSelectMapper).selectPage("u", null, 0, 100);
    }
    @Test void filtersAreNormalizedBeforeSql() {
        service.getPage(" u ", " words ", 2, 20);
        verify(trendSelectMapper).selectPage("u", "words", 20, 20); verify(trendSelectMapper).countPage("u", "words");
    }
    @Test void largeKeywordCannotReachMapper() {
        assertThrows(ApiException.class, () -> service.getList(null, "x".repeat(101), 1, 10)); verifyNoInteractions(trendSelectMapper);
    }
    @Test void latestEmptyUsesResourceResponse() {
        when(trendSelectMapper.selectPage(null,null,0,1)).thenReturn(List.of());
        assertNotNull(service.getLatest().getBody()); assertNotEquals(0, service.getLatest().getBody().getCode());
    }
    @Test void latestResolvesExistingDetail() {
        var model = new TrendModel(); model.setId("t"); model.setTitle("最新");
        when(trendSelectMapper.selectPage(null,null,0,1)).thenReturn(List.of(model)); when(trendSelectMapper.selectById("t")).thenReturn(model);
        when(trendImageMapper.selectByTrendId("t")).thenReturn(List.of());
        assertEquals(0, service.getLatest().getBody().getCode()); verify(trendImageMapper).selectByTrendId("t");
    }
}
