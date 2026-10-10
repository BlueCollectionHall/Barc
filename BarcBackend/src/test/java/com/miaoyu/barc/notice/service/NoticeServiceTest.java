package com.miaoyu.barc.notice.service;

import com.miaoyu.barc.notice.mapper.NoticeMapper;
import com.miaoyu.barc.notice.model.NoticeModel;
import com.miaoyu.barc.utils.J;
import com.miaoyu.barc.utils.dto.PageRequestDto;
import com.miaoyu.barc.utils.dto.PageResultDto;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 公告服务单元测试
 *
 * 覆盖重点：
 * 1、/notice/latest 的两级查询与回退兜底（改动核心：公告超期后前端不再显示“暂无公告”）；
 * 2、公告分页查询的总页数计算；
 * 3、公告详情查询、新增、修改、删除的成败分支。
 */
@ExtendWith(MockitoExtension.class)
class NoticeServiceTest {

    @Mock
    private NoticeMapper noticeMapper;

    @InjectMocks
    private NoticeService noticeService;

    // ============ 最新公告（改动核心） ============

    @Test
    @DisplayName("最新公告 - day 天内有公告时返回范围内最新一条，且不触发回退查询")
    void latestNotice_WhenInRange_ShouldReturnRangeLatestWithoutFallback() {
        NoticeModel inRange = notice("n-in-range", "近 30 天的公告");
        when(noticeMapper.latest(30)).thenReturn(inRange);

        ResponseEntity<J> response = noticeService.latestNoticeService(30);

        assertNotNull(response.getBody());
        assertEquals(0, response.getBody().getCode());
        List<NoticeModel> list = castList(response.getBody().getData());
        assertEquals(1, list.size());
        assertEquals("n-in-range", list.get(0).getId());
        // 命中天数范围时属于第一级查询，绝不能再去查全表最新（保持原语义、避免多余查询）
        verify(noticeMapper, never()).latestWithoutDayLimit();
    }

    @Test
    @DisplayName("最新公告 - day 天内无公告时回退返回全表最近一条，前端不再显示暂无公告")
    void latestNotice_WhenOutOfRange_ShouldFallbackToLatestOverall() {
        NoticeModel fallback = notice("n-old", "超过 30 天的历史公告");
        when(noticeMapper.latest(30)).thenReturn(null);
        when(noticeMapper.latestWithoutDayLimit()).thenReturn(fallback);

        ResponseEntity<J> response = noticeService.latestNoticeService(30);

        assertNotNull(response.getBody());
        assertEquals(0, response.getBody().getCode());
        List<NoticeModel> list = castList(response.getBody().getData());
        assertEquals(1, list.size());
        assertEquals("n-old", list.get(0).getId());
        // 回退时两个查询都应真实发生
        verify(noticeMapper).latest(30);
        verify(noticeMapper).latestWithoutDayLimit();
    }

    @Test
    @DisplayName("最新公告 - 表中完全没有公告时返回空列表，前端展示暂无数据")
    void latestNotice_WhenNoNoticeAtAll_ShouldReturnEmptyList() {
        when(noticeMapper.latest(30)).thenReturn(null);
        when(noticeMapper.latestWithoutDayLimit()).thenReturn(null);

        ResponseEntity<J> response = noticeService.latestNoticeService(30);

        assertNotNull(response.getBody());
        assertEquals(0, response.getBody().getCode());
        List<NoticeModel> list = castList(response.getBody().getData());
        assertTrue(list.isEmpty());
    }

    @Test
    @DisplayName("最新公告 - day 参数为 null 时同样走第一级查询并可按结果回退")
    void latestNotice_WhenDayIsNull_ShouldStillFallback() {
        NoticeModel fallback = notice("n-null-day", "未传天数范围时的兜底公告");
        // 不桩 latest(isNull())：Mockito 默认返回 null，正好模拟「未传天数范围时查不到公告」
        when(noticeMapper.latestWithoutDayLimit()).thenReturn(fallback);

        ResponseEntity<J> response = noticeService.latestNoticeService(null);

        assertNotNull(response.getBody());
        assertEquals(0, response.getBody().getCode());
        List<NoticeModel> list = castList(response.getBody().getData());
        assertEquals(1, list.size());
        assertEquals("n-null-day", list.get(0).getId());
    }

    // ============ 公告分页 ============

    @Test
    @DisplayName("公告分页 - 按总数与每页条数计算总页数")
    void noticesByPage_ShouldComputeTotalPage() {
        PageRequestDto dto = new PageRequestDto();
        dto.setPage_num(2);
        dto.setPage_size(10);
        when(noticeMapper.selectByPage(anyInt(), anyInt(), anyMap()))
                .thenReturn(List.of(notice("n-page", "分页公告")));
        when(noticeMapper.countByPage(anyMap())).thenReturn(25L);

        ResponseEntity<J> response = noticeService.noticesByPageService(dto);

        assertNotNull(response.getBody());
        assertEquals(0, response.getBody().getCode());
        PageResultDto<?> result = (PageResultDto<?>) response.getBody().getData();
        assertEquals(25L, result.getTotal());
        assertEquals(3, result.getTotal_page());
        assertEquals(2, result.getPage_num());
        assertEquals(10, result.getPage_size());
        assertEquals(1, result.getList().size());
    }

    @Test
    @DisplayName("公告分页 - 无数据时总页数兜底为 1，避免前端出现第 0 页")
    void noticesByPage_WhenEmpty_ShouldReturnTotalPageOne() {
        PageRequestDto dto = new PageRequestDto();
        when(noticeMapper.selectByPage(anyInt(), anyInt(), anyMap())).thenReturn(new ArrayList<>());
        when(noticeMapper.countByPage(anyMap())).thenReturn(0L);

        ResponseEntity<J> response = noticeService.noticesByPageService(dto);

        assertNotNull(response.getBody());
        PageResultDto<?> result = (PageResultDto<?>) response.getBody().getData();
        assertEquals(0L, result.getTotal());
        assertEquals(1, result.getTotal_page());
        assertTrue(result.getList().isEmpty());
    }

    // ============ 公告详情 ============

    @Test
    @DisplayName("公告详情 - 公告存在时返回成功与公告内容")
    void getNoticeOnly_WhenExists_ShouldReturnNotice() {
        when(noticeMapper.selectById("n-1")).thenReturn(notice("n-1", "详情公告"));

        ResponseEntity<J> response = noticeService.getNoticeOnlyService("n-1");

        assertNotNull(response.getBody());
        assertEquals(0, response.getBody().getCode());
        NoticeModel model = (NoticeModel) response.getBody().getData();
        assertEquals("n-1", model.getId());
    }

    @Test
    @DisplayName("公告详情 - 公告不存在时返回失败标记")
    void getNoticeOnly_WhenMissing_ShouldReturnFail() {
        when(noticeMapper.selectById("n-missing")).thenReturn(null);

        ResponseEntity<J> response = noticeService.getNoticeOnlyService("n-missing");

        assertNotNull(response.getBody());
        // ResourceR 在失败分支会把 data 置为失败提示文案，这里只校验失败标记与文案
        assertEquals(1, response.getBody().getCode());
        assertTrue(response.getBody().getMsg().contains("fail"));
        assertEquals(response.getBody().getMsg(), response.getBody().getData());
    }

    // ============ 公告新增 ============

    @Test
    @DisplayName("公告新增 - 落库成功并回填 id 与作者")
    void uploadNotice_WhenInsertSuccess_ShouldFillIdAndAuthor() {
        when(noticeMapper.insert(any(NoticeModel.class))).thenReturn(true);

        ResponseEntity<J> response = noticeService.uploadNoticeService("author-uuid", notice(null, "新公告"));

        assertNotNull(response.getBody());
        assertEquals(0, response.getBody().getCode());
        ArgumentCaptor<NoticeModel> captor = ArgumentCaptor.forClass(NoticeModel.class);
        verify(noticeMapper).insert(captor.capture());
        assertNotNull(captor.getValue().getId());
        assertEquals("author-uuid", captor.getValue().getAuthor());
    }

    @Test
    @DisplayName("公告新增 - 落库失败时返回失败标记")
    void uploadNotice_WhenInsertFail_ShouldReturnFail() {
        when(noticeMapper.insert(any(NoticeModel.class))).thenReturn(false);

        ResponseEntity<J> response = noticeService.uploadNoticeService("author-uuid", notice(null, "新公告"));

        assertNotNull(response.getBody());
        assertEquals(1, response.getBody().getCode());
    }

    // ============ 公告修改 ============

    @Test
    @DisplayName("公告修改 - 更新成功时返回成功标记")
    void updateNotice_WhenSuccess_ShouldReturnSuccess() {
        when(noticeMapper.update(any(NoticeModel.class))).thenReturn(true);

        ResponseEntity<J> response = noticeService.updateNoticeService("uuid", "author-uuid", notice("n-1", "改名公告"));

        assertNotNull(response.getBody());
        assertEquals(0, response.getBody().getCode());
    }

    @Test
    @DisplayName("公告修改 - 更新抛异常时捕获并返回失败标记")
    void updateNotice_WhenException_ShouldReturnFail() {
        when(noticeMapper.update(any(NoticeModel.class))).thenThrow(new RuntimeException("更新失败"));

        ResponseEntity<J> response = noticeService.updateNoticeService("uuid", "author-uuid", notice("n-1", "改名公告"));

        assertNotNull(response.getBody());
        assertEquals(1, response.getBody().getCode());
    }

    // ============ 公告删除 ============

    @Test
    @DisplayName("公告删除 - 删除成功时返回成功标记")
    void deleteNotice_WhenSuccess_ShouldReturnSuccess() {
        when(noticeMapper.deleteById("n-1")).thenReturn(true);

        ResponseEntity<J> response = noticeService.deleteNoticeService("uuid", "n-1");

        assertNotNull(response.getBody());
        assertEquals(0, response.getBody().getCode());
    }

    @Test
    @DisplayName("公告删除 - 删除抛异常时捕获并返回失败标记")
    void deleteNotice_WhenException_ShouldReturnFail() {
        when(noticeMapper.deleteById("n-1")).thenThrow(new RuntimeException("删除失败"));

        ResponseEntity<J> response = noticeService.deleteNoticeService("uuid", "n-1");

        assertNotNull(response.getBody());
        assertEquals(1, response.getBody().getCode());
    }

    // ============ 测试辅助 ============

    /**
     * 构造一条公告实体，减少各用例的重复代码
     */
    private NoticeModel notice(String id, String title) {
        NoticeModel model = new NoticeModel();
        model.setId(id);
        model.setTitle(title);
        model.setContent("<p>" + title + "</p>");
        model.setAuthor("author-uuid");
        model.setCreated_at(LocalDateTime.now());
        model.setUpdated_at(LocalDateTime.now());
        return model;
    }

    @SuppressWarnings("unchecked")
    private List<NoticeModel> castList(Object data) {
        return (List<NoticeModel>) data;
    }
}
