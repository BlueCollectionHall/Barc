package com.miaoyu.barc.api.work.service;

import com.miaoyu.barc.api.work.mapper.WorkMapper;
import com.miaoyu.barc.api.work.mapper.WorkViewLogMapper;
import com.miaoyu.barc.api.work.model.WorkModel;
import com.miaoyu.barc.api.work.model.WorkViewLogModel;
import com.miaoyu.barc.utils.J;
import com.miaoyu.barc.utils.JwtService;
import com.miaoyu.barc.utils.web.ClientIpInfo;
import com.miaoyu.barc.utils.web.ClientIpResolver;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WorkViewServiceTest {

    @Mock
    private WorkViewLogMapper workViewLogMapper;
    @Mock
    private WorkMapper workMapper;
    @Mock
    private JwtService jwtService;
    @Mock
    private ClientIpResolver clientIpResolver;
    @Mock
    private Clock clock;
    @Mock
    private TransactionTemplate transactionTemplate;
    @Mock
    private HttpServletRequest request;

    @InjectMocks
    private WorkViewService workViewService;

    private WorkModel work;

    @BeforeEach
    void setUp() {
        work = new WorkModel();
        work.setId("work-1");
        work.setAuthor("author-1");

        lenient().when(clock.instant()).thenReturn(Instant.parse("2026-06-20T12:00:00Z"));
        lenient().when(clock.getZone()).thenReturn(ZoneOffset.UTC);
        lenient().doAnswer(invocation -> {
            java.util.function.Consumer<org.springframework.transaction.TransactionStatus> callback = invocation.getArgument(0);
            callback.accept(null);
            return null;
        }).when(transactionTemplate).executeWithoutResult(any());
    }

    @Test
    @DisplayName("匿名首次访问应写入明细并增加浏览量")
    void recordViewIfNeeded_WhenAnonymousFirstVisit_ShouldInsertLogAndIncrementCount() {
        when(request.getHeader("Authorization")).thenReturn(null);
        when(clientIpResolver.resolve(request)).thenReturn(new ClientIpInfo("IPV4", "198.51.100.8", hashA()));
        when(workViewLogMapper.insert(any(WorkViewLogModel.class))).thenReturn(1);
        when(workMapper.incrementViewCount("work-1")).thenReturn(1);

        workViewService.recordViewIfNeeded(request, work);

        ArgumentCaptor<WorkViewLogModel> captor = ArgumentCaptor.forClass(WorkViewLogModel.class);
        verify(workViewLogMapper).insert(captor.capture());
        WorkViewLogModel model = captor.getValue();
        assertEquals("work-1", model.getWork_id());
        assertEquals(LocalDate.of(2026, 6, 20), model.getView_date());
        assertEquals("IPV4", model.getViewer_type());
        assertEquals("198.51.100.8", model.getViewer_ipv4());
        assertEquals(hashA(), model.getViewer_ip_hash());
        assertEquals("IPV4:" + hashA(), model.getDedupe_key());
        verify(workMapper).incrementViewCount("work-1");
    }

    @Test
    @DisplayName("匿名重复访问命中唯一键时不应增加浏览量")
    void recordViewIfNeeded_WhenDuplicateAnonymousVisit_ShouldSkipIncrement() {
        when(request.getHeader("Authorization")).thenReturn(null);
        when(clientIpResolver.resolve(request)).thenReturn(new ClientIpInfo("IPV4", "198.51.100.8", hashA()));
        when(workViewLogMapper.insert(any(WorkViewLogModel.class))).thenThrow(new DuplicateKeyException("duplicate"));

        workViewService.recordViewIfNeeded(request, work);

        verify(workMapper, never()).incrementViewCount("work-1");
    }

    @Test
    @DisplayName("登录首次访问应按 UUID 入账")
    void recordViewIfNeeded_WhenLoggedInFirstVisit_ShouldInsertUserViewLog() {
        when(request.getHeader("Authorization")).thenReturn("valid-jwt");
        when(jwtService.jwtParser("valid-jwt")).thenReturn(new J(0, "ok", "user-1"));
        when(workViewLogMapper.insert(any(WorkViewLogModel.class))).thenReturn(1);
        when(workMapper.incrementViewCount("work-1")).thenReturn(1);

        workViewService.recordViewIfNeeded(request, work);

        ArgumentCaptor<WorkViewLogModel> captor = ArgumentCaptor.forClass(WorkViewLogModel.class);
        verify(workViewLogMapper).insert(captor.capture());
        WorkViewLogModel model = captor.getValue();
        assertEquals("USER", model.getViewer_type());
        assertEquals("user-1", model.getViewer_user_uuid());
        assertEquals("USER:user-1", model.getDedupe_key());
        verifyNoInteractions(clientIpResolver);
        verify(workMapper).incrementViewCount("work-1");
    }

    @Test
    @DisplayName("登录重复访问命中唯一键时不应增加浏览量")
    void recordViewIfNeeded_WhenLoggedInDuplicateVisit_ShouldSkipIncrement() {
        when(request.getHeader("Authorization")).thenReturn("valid-jwt");
        when(jwtService.jwtParser("valid-jwt")).thenReturn(new J(0, "ok", "user-1"));
        when(workViewLogMapper.insert(any(WorkViewLogModel.class))).thenThrow(new DuplicateKeyException("duplicate"));

        workViewService.recordViewIfNeeded(request, work);

        verify(workMapper, never()).incrementViewCount("work-1");
    }

    @Test
    @DisplayName("同一匿名 IP 跨日访问应重新入账")
    void recordViewIfNeeded_WhenAnonymousVisitsOnNextDay_ShouldUseNewViewDate() {
        when(request.getHeader("Authorization")).thenReturn(null);
        when(clientIpResolver.resolve(request)).thenReturn(new ClientIpInfo("IPV4", "198.51.100.8", hashA()));
        when(workViewLogMapper.insert(any(WorkViewLogModel.class))).thenReturn(1);
        when(workMapper.incrementViewCount("work-1")).thenReturn(1);
        when(clock.instant()).thenReturn(Instant.parse("2026-06-20T15:59:59Z"), Instant.parse("2026-06-20T16:00:01Z"));

        workViewService.recordViewIfNeeded(request, work);
        workViewService.recordViewIfNeeded(request, work);

        verify(workViewLogMapper).insert(argThatDate(LocalDate.of(2026, 6, 20)));
        verify(workViewLogMapper).insert(argThatDate(LocalDate.of(2026, 6, 21)));
        verify(workMapper, org.mockito.Mockito.times(2)).incrementViewCount("work-1");
    }

    @Test
    @DisplayName("匿名后登录同日访问应允许再次入账")
    void recordViewIfNeeded_WhenAnonymousThenLoginSameDay_ShouldCountTwice() {
        HttpServletRequest anonymousRequest = request;
        HttpServletRequest loggedInRequest = org.mockito.Mockito.mock(HttpServletRequest.class);
        when(anonymousRequest.getHeader("Authorization")).thenReturn(null);
        when(loggedInRequest.getHeader("Authorization")).thenReturn("valid-jwt");
        when(clientIpResolver.resolve(anonymousRequest)).thenReturn(new ClientIpInfo("IPV4", "198.51.100.8", hashA()));
        when(jwtService.jwtParser("valid-jwt")).thenReturn(new J(0, "ok", "user-1"));
        when(workViewLogMapper.insert(any(WorkViewLogModel.class))).thenReturn(1);
        when(workMapper.incrementViewCount("work-1")).thenReturn(1);

        workViewService.recordViewIfNeeded(anonymousRequest, work);
        workViewService.recordViewIfNeeded(loggedInRequest, work);

        verify(workMapper, org.mockito.Mockito.times(2)).incrementViewCount("work-1");
    }

    @Test
    @DisplayName("作者本人访问自己的作品不应计数")
    void recordViewIfNeeded_WhenAuthorViewsOwnWork_ShouldSkip() {
        when(request.getHeader("Authorization")).thenReturn("author-jwt");
        when(jwtService.jwtParser("author-jwt")).thenReturn(new J(0, "ok", "author-1"));

        workViewService.recordViewIfNeeded(request, work);

        verifyNoInteractions(workViewLogMapper, clientIpResolver);
        verify(workMapper, never()).incrementViewCount(anyString());
    }

    @Test
    @DisplayName("匿名无合法 IP 时应跳过")
    void recordViewIfNeeded_WhenAnonymousIpMissing_ShouldSkip() {
        when(request.getHeader("Authorization")).thenReturn(null);
        when(clientIpResolver.resolve(request)).thenReturn(null);

        workViewService.recordViewIfNeeded(request, work);

        verifyNoInteractions(workViewLogMapper);
        verify(workMapper, never()).incrementViewCount(anyString());
    }

    @Test
    @DisplayName("浏览量写入异常不应向外抛出")
    void recordViewIfNeeded_WhenInsertFails_ShouldSwallowFailure() {
        when(request.getHeader("Authorization")).thenReturn(null);
        when(clientIpResolver.resolve(request)).thenReturn(new ClientIpInfo("IPV4", "198.51.100.8", hashA()));
        when(workViewLogMapper.insert(any(WorkViewLogModel.class))).thenThrow(new IllegalStateException("db down"));

        assertDoesNotThrow(() -> workViewService.recordViewIfNeeded(request, work));
        verify(workMapper, never()).incrementViewCount("work-1");
    }

    private String hashA() {
        return "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
    }

    private WorkViewLogModel argThatDate(LocalDate expectedDate) {
        return org.mockito.ArgumentMatchers.argThat(model -> expectedDate.equals(model.getView_date()));
    }
}
