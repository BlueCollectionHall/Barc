package com.miaoyu.barc.festival.service;

import com.miaoyu.barc.background.mapper.BackgroundImageMapper;
import com.miaoyu.barc.festival.mapper.FestivalMapper;
import com.miaoyu.barc.festival.model.FestivalModel;
import com.miaoyu.barc.festival.utils.MonthDayRangeUtils;
import com.miaoyu.barc.utils.J;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 节日管理服务单元测试
 *
 * 覆盖：列表（含绑定数量）、新增（名称/日期/重名/重叠校验）、修改（排除自身）、
 * 关闭启用、删除（自动解绑背景图）、公开接口只返回启用中的节日。
 */
@ExtendWith(MockitoExtension.class)
class FestivalServiceTest {

    @Mock
    private FestivalMapper festivalMapper;
    @Mock
    private BackgroundImageMapper backgroundImageMapper;

    @InjectMocks
    private FestivalService festivalService;

    // ============ 列表 ============

    @Test
    @DisplayName("列表 - 返回全部节日并附带绑定背景图数量")
    void list_returnsBoundCount() {
        FestivalModel newYear = festival(MonthDayRangeUtils.toIndex(12, 1), MonthDayRangeUtils.toIndex(2, 28));
        newYear.setId("fest-ny");
        when(festivalMapper.selectAll()).thenReturn(List.of(newYear));
        when(backgroundImageMapper.countByFestival("fest-ny")).thenReturn(3);

        ResponseEntity<J> response = festivalService.listService("admin-uuid");

        assertEquals(0, response.getBody().getCode());
        @SuppressWarnings("unchecked")
        List<FestivalModel> list = (List<FestivalModel>) response.getBody().getData();
        assertEquals(1, list.size());
        assertEquals(3, list.get(0).getBound_backgrounds());
    }

    // ============ 新增 ============

    @Test
    @DisplayName("新增 - 成功落库并写入派生统一索引")
    void create_success() {
        when(festivalMapper.selectAll()).thenReturn(List.of());
        when(festivalMapper.countByName("新年", null)).thenReturn(0);
        when(festivalMapper.insert(any(FestivalModel.class))).thenReturn(1);

        ResponseEntity<J> response = festivalService.createService("admin-uuid", item("新年", 12, 1, 2, 28));

        assertEquals(0, response.getBody().getCode());
        FestivalModel saved = (FestivalModel) response.getBody().getData();
        assertEquals(MonthDayRangeUtils.toIndex(12, 1), saved.getStart_index());
        assertEquals(MonthDayRangeUtils.toIndex(2, 28), saved.getEnd_index());
        // 跨年区间：起始索引大于结束索引
        assertTrue(saved.getStart_index() > saved.getEnd_index());
        assertTrue(saved.getEnabled());
        assertEquals("admin-uuid", saved.getCreated_by());
        assertNotNull(saved.getId());
    }

    @Test
    @DisplayName("新增 - 名称为空被拒")
    void create_blankName() {
        ResponseEntity<J> response = festivalService.createService("admin-uuid", item("  ", 1, 1, 1, 31));
        assertEquals(1, response.getBody().getCode());
        verify(festivalMapper, never()).insert(any());
    }

    @Test
    @DisplayName("新增 - 请求体为空被拒")
    void create_nullItem() {
        ResponseEntity<J> response = festivalService.createService("admin-uuid", null);
        assertEquals(1, response.getBody().getCode());
        verify(festivalMapper, never()).insert(any());
    }

    @Test
    @DisplayName("新增 - 名称超长被拒")
    void create_tooLongName() {
        String longName = "节".repeat(65);
        ResponseEntity<J> response = festivalService.createService("admin-uuid", item(longName, 1, 1, 1, 31));
        assertEquals(1, response.getBody().getCode());
        verify(festivalMapper, never()).insert(any());
    }

    @Test
    @DisplayName("新增 - 月份非法被拒")
    void create_invalidMonth() {
        ResponseEntity<J> response = festivalService.createService("admin-uuid", item("测试", 0, 1, 1, 31));
        assertEquals(1, response.getBody().getCode());
        assertTrue(String.valueOf(response.getBody().getMsg()).contains("日期不合法"));
        verify(festivalMapper, never()).insert(any());
    }

    @Test
    @DisplayName("新增 - 日期超出该月天数被拒（4 月 31 日）")
    void create_invalidDay() {
        ResponseEntity<J> response = festivalService.createService("admin-uuid", item("测试", 4, 1, 4, 31));
        assertEquals(1, response.getBody().getCode());
        verify(festivalMapper, never()).insert(any());
    }

    @Test
    @DisplayName("新增 - 名称重复被拒")
    void create_duplicatedName() {
        when(festivalMapper.countByName("新年", null)).thenReturn(1);
        ResponseEntity<J> response = festivalService.createService("admin-uuid", item("新年", 12, 1, 2, 28));
        assertEquals(1, response.getBody().getCode());
        assertTrue(String.valueOf(response.getBody().getMsg()).contains("名称已存在"));
        verify(festivalMapper, never()).insert(any());
    }

    @Test
    @DisplayName("新增 - 与已有节日重叠（元旦落在新年区间内）被拒并提示冲突节日")
    void create_overlappedWithCrossYearFestival() {
        when(festivalMapper.countByName("元旦", null)).thenReturn(0);
        when(festivalMapper.selectAll()).thenReturn(List.of(namedFestival("新年", 12, 1, 2, 28)));

        ResponseEntity<J> response = festivalService.createService("admin-uuid", item("元旦", 1, 1, 1, 2));

        assertEquals(1, response.getBody().getCode());
        assertTrue(String.valueOf(response.getBody().getMsg()).contains("新年"));
        assertTrue(String.valueOf(response.getBody().getMsg()).contains("重叠"));
        verify(festivalMapper, never()).insert(any());
    }

    @Test
    @DisplayName("新增 - 已关闭的节日同样占用区间，不允许重叠")
    void create_overlappedWithDisabledFestival() {
        FestivalModel disabled = namedFestival("已关闭节日", 3, 1, 3, 31);
        disabled.setEnabled(false);
        when(festivalMapper.countByName("新节日", null)).thenReturn(0);
        when(festivalMapper.selectAll()).thenReturn(List.of(disabled));

        ResponseEntity<J> response = festivalService.createService("admin-uuid", item("新节日", 3, 10, 4, 10));

        assertEquals(1, response.getBody().getCode());
        assertTrue(String.valueOf(response.getBody().getMsg()).contains("已关闭节日"));
        verify(festivalMapper, never()).insert(any());
    }

    @Test
    @DisplayName("新增 - 不相邻区间可以共存（11 月整月 + 新年 12-01 起）")
    void create_notOverlapped() {
        when(festivalMapper.countByName("十一月", null)).thenReturn(0);
        when(festivalMapper.selectAll()).thenReturn(List.of(namedFestival("新年", 12, 1, 2, 28)));
        when(festivalMapper.insert(any(FestivalModel.class))).thenReturn(1);

        ResponseEntity<J> response = festivalService.createService("admin-uuid", item("十一月", 11, 1, 11, 30));

        assertEquals(0, response.getBody().getCode());
    }

    @Test
    @DisplayName("新增 - 落库失败返回错误")
    void create_insertFail() {
        when(festivalMapper.countByName("新年", null)).thenReturn(0);
        when(festivalMapper.selectAll()).thenReturn(List.of());
        when(festivalMapper.insert(any(FestivalModel.class))).thenReturn(0);

        ResponseEntity<J> response = festivalService.createService("admin-uuid", item("新年", 12, 1, 2, 28));

        assertEquals(1, response.getBody().getCode());
        assertTrue(String.valueOf(response.getBody().getMsg()).contains("新增失败"));
    }

    // ============ 修改 ============

    @Test
    @DisplayName("修改 - 成功更新名称与区间")
    void update_success() {
        FestivalModel existing = namedFestival("旧名", 3, 1, 3, 31);
        existing.setId("fest-1");
        when(festivalMapper.selectById("fest-1")).thenReturn(existing);
        when(festivalMapper.countByName("新名", "fest-1")).thenReturn(0);
        when(festivalMapper.selectAll()).thenReturn(List.of(existing));
        when(festivalMapper.update(any(FestivalModel.class))).thenReturn(1);

        ResponseEntity<J> response = festivalService.updateService("admin-uuid", "fest-1", item("新名", 4, 1, 4, 30));

        assertEquals(0, response.getBody().getCode());
        FestivalModel saved = (FestivalModel) response.getBody().getData();
        assertEquals("新名", saved.getName());
        assertEquals(MonthDayRangeUtils.toIndex(4, 1), saved.getStart_index());
        assertEquals(MonthDayRangeUtils.toIndex(4, 30), saved.getEnd_index());
    }

    @Test
    @DisplayName("修改 - 节日不存在返回失败")
    void update_notFound() {
        when(festivalMapper.selectById("missing")).thenReturn(null);
        ResponseEntity<J> response = festivalService.updateService("admin-uuid", "missing", item("新名", 4, 1, 4, 30));
        assertEquals(1, response.getBody().getCode());
        verify(festivalMapper, never()).update(any());
    }

    @Test
    @DisplayName("修改 - 名称与其他节日重复被拒")
    void update_duplicatedName() {
        FestivalModel existing = namedFestival("旧名", 3, 1, 3, 31);
        existing.setId("fest-1");
        when(festivalMapper.selectById("fest-1")).thenReturn(existing);
        when(festivalMapper.countByName("新年", "fest-1")).thenReturn(1);

        ResponseEntity<J> response = festivalService.updateService("admin-uuid", "fest-1", item("新年", 4, 1, 4, 30));

        assertEquals(1, response.getBody().getCode());
        verify(festivalMapper, never()).update(any());
    }

    @Test
    @DisplayName("修改 - 日期非法被拒")
    void update_invalidRange() {
        FestivalModel existing = namedFestival("旧名", 3, 1, 3, 31);
        existing.setId("fest-1");
        when(festivalMapper.selectById("fest-1")).thenReturn(existing);

        ResponseEntity<J> response = festivalService.updateService("admin-uuid", "fest-1", item("旧名", 13, 1, 4, 30));

        assertEquals(1, response.getBody().getCode());
        verify(festivalMapper, never()).update(any());
    }

    @Test
    @DisplayName("修改 - 与其他节日重叠被拒")
    void update_overlapped() {
        FestivalModel existing = namedFestival("旧名", 3, 1, 3, 31);
        existing.setId("fest-1");
        when(festivalMapper.selectById("fest-1")).thenReturn(existing);
        when(festivalMapper.countByName("旧名", "fest-1")).thenReturn(0);
        // 已存在的新年（12-01 → 02-28）
        when(festivalMapper.selectAll()).thenReturn(List.of(
                existing, namedFestivalWithId("fest-ny", "新年", 12, 1, 2, 28)));

        ResponseEntity<J> response = festivalService.updateService("admin-uuid", "fest-1", item("旧名", 1, 5, 1, 20));

        assertEquals(1, response.getBody().getCode());
        assertTrue(String.valueOf(response.getBody().getMsg()).contains("新年"));
        verify(festivalMapper, never()).update(any());
    }

    @Test
    @DisplayName("修改 - 保持自身区间不变时不判为与自己重叠")
    void update_selfNotCountedAsOverlap() {
        FestivalModel existing = namedFestival("新年", 12, 1, 2, 28);
        existing.setId("fest-ny");
        when(festivalMapper.selectById("fest-ny")).thenReturn(existing);
        when(festivalMapper.countByName("新年", "fest-ny")).thenReturn(0);
        when(festivalMapper.selectAll()).thenReturn(List.of(existing));
        when(festivalMapper.update(any(FestivalModel.class))).thenReturn(1);

        ResponseEntity<J> response = festivalService.updateService("admin-uuid", "fest-ny", item("新年", 12, 1, 2, 28));

        assertEquals(0, response.getBody().getCode());
    }

    @Test
    @DisplayName("修改 - 落库失败返回错误")
    void update_fail() {
        FestivalModel existing = namedFestival("旧名", 3, 1, 3, 31);
        existing.setId("fest-1");
        when(festivalMapper.selectById("fest-1")).thenReturn(existing);
        when(festivalMapper.countByName("新名", "fest-1")).thenReturn(0);
        when(festivalMapper.selectAll()).thenReturn(List.of(existing));
        when(festivalMapper.update(any(FestivalModel.class))).thenReturn(0);

        ResponseEntity<J> response = festivalService.updateService("admin-uuid", "fest-1", item("新名", 4, 1, 4, 30));

        assertEquals(1, response.getBody().getCode());
        assertTrue(String.valueOf(response.getBody().getMsg()).contains("修改失败"));
    }

    // ============ 关闭 / 启用 ============

    @Test
    @DisplayName("关闭 - 保留数据与背景图绑定，仅置 enabled=false")
    void setEnabled_closeKeepsBinding() {
        FestivalModel existing = namedFestival("新年", 12, 1, 2, 28);
        existing.setId("fest-ny");
        when(festivalMapper.selectById("fest-ny")).thenReturn(existing);
        when(festivalMapper.updateEnabled("fest-ny", false)).thenReturn(1);

        ResponseEntity<J> response = festivalService.setEnabledService("admin-uuid", "fest-ny", false);

        assertEquals(0, response.getBody().getCode());
        FestivalModel saved = (FestivalModel) response.getBody().getData();
        assertFalse(saved.getEnabled());
        // 关闭不应解绑背景图
        verify(backgroundImageMapper, never()).clearFestival(anyString());
    }

    @Test
    @DisplayName("关闭 - 节日不存在返回失败")
    void setEnabled_notFound() {
        when(festivalMapper.selectById("missing")).thenReturn(null);
        ResponseEntity<J> response = festivalService.setEnabledService("admin-uuid", "missing", false);
        assertEquals(1, response.getBody().getCode());
        verify(festivalMapper, never()).updateEnabled(anyString(), anyBoolean());
    }

    @Test
    @DisplayName("关闭 - 更新影响行数为 0 时返回失败")
    void setEnabled_updateFail() {
        FestivalModel existing = namedFestival("新年", 12, 1, 2, 28);
        existing.setId("fest-ny");
        when(festivalMapper.selectById("fest-ny")).thenReturn(existing);
        when(festivalMapper.updateEnabled("fest-ny", false)).thenReturn(0);
        ResponseEntity<J> response = festivalService.setEnabledService("admin-uuid", "fest-ny", false);
        assertEquals(1, response.getBody().getCode());
    }

    // ============ 删除 ============

    @Test
    @DisplayName("删除 - 自动把绑定背景图划归「无节日」并返回解绑数量")
    void delete_unbindsBackgrounds() {
        FestivalModel existing = namedFestival("新年", 12, 1, 2, 28);
        existing.setId("fest-ny");
        when(festivalMapper.selectById("fest-ny")).thenReturn(existing);
        when(backgroundImageMapper.clearFestival("fest-ny")).thenReturn(5);
        when(festivalMapper.softDelete("fest-ny")).thenReturn(1);

        ResponseEntity<J> response = festivalService.deleteService("admin-uuid", "fest-ny");

        assertEquals(0, response.getBody().getCode());
        FestivalService.DeleteResult result = (FestivalService.DeleteResult) response.getBody().getData();
        assertEquals("fest-ny", result.getId());
        assertEquals(5, result.getUnboundBackgrounds());
        // 关键链路：先解绑背景图，再删除节日
        verify(backgroundImageMapper).clearFestival("fest-ny");
        verify(festivalMapper).softDelete("fest-ny");
    }

    @Test
    @DisplayName("删除 - 节日不存在返回失败且不解绑背景图")
    void delete_notFound() {
        when(festivalMapper.selectById("missing")).thenReturn(null);
        ResponseEntity<J> response = festivalService.deleteService("admin-uuid", "missing");
        assertEquals(1, response.getBody().getCode());
        verify(backgroundImageMapper, never()).clearFestival(anyString());
        verify(festivalMapper, never()).softDelete(anyString());
    }

    @Test
    @DisplayName("删除 - 软删除未生效时抛出异常，触发事务回滚（不让解绑被单独提交）")
    void delete_softDeleteFailRollsBack() {
        FestivalModel existing = namedFestival("新年", 12, 1, 2, 28);
        existing.setId("fest-ny");
        when(festivalMapper.selectById("fest-ny")).thenReturn(existing);
        when(backgroundImageMapper.clearFestival("fest-ny")).thenReturn(3);
        when(festivalMapper.softDelete("fest-ny")).thenReturn(0);

        // 必须抛异常而不是返回错误码：@Transactional(rollbackFor = Exception.class) 只对异常回滚，
        // 若返回 ResponseEntity 则「已解绑背景图」会被单独提交，导致背景图丢绑定而节日还在
        assertThrows(IllegalStateException.class, () -> festivalService.deleteService("admin-uuid", "fest-ny"));
    }

    // ============ 公开接口 ============

    @Test
    @DisplayName("公开接口 - 只返回启用中的节日")
    void publicAll_onlyEnabled() {
        when(festivalMapper.selectAllEnabled()).thenReturn(List.of(namedFestival("新年", 12, 1, 2, 28)));

        J result = festivalService.publicAllService();

        assertEquals(0, result.getCode());
        @SuppressWarnings("unchecked")
        List<FestivalModel> list = (List<FestivalModel>) result.getData();
        assertEquals(1, list.size());
        assertEquals("新年", list.get(0).getName());
        verify(festivalMapper).selectAllEnabled();
    }

    @Test
    @DisplayName("选项接口 - 返回全部节日（含已关闭），供背景图下拉保留绑定")
    void options_returnsAllIncludingDisabled() {
        FestivalModel disabled = namedFestival("已关闭节日", 3, 1, 3, 31);
        disabled.setEnabled(false);
        when(festivalMapper.selectAll()).thenReturn(List.of(namedFestival("新年", 12, 1, 2, 28), disabled));

        ResponseEntity<J> response = festivalService.optionsService("admin-uuid");

        assertEquals(0, response.getBody().getCode());
        @SuppressWarnings("unchecked")
        List<FestivalModel> list = (List<FestivalModel>) response.getBody().getData();
        assertEquals(2, list.size());
        // 关闭的节日必须出现在候选中，否则背景图页面无法显示 / 保留它的绑定
        assertFalse(list.get(1).getEnabled());
    }

    // ============ 测试辅助 ============

    /** 构造一个启用的节日模型，统一索引由起止月日推导 */
    private FestivalModel festival(int startIndex, int endIndex) {
        FestivalModel model = new FestivalModel();
        model.setStart_index(startIndex);
        model.setEnd_index(endIndex);
        model.setEnabled(true);
        return model;
    }

    private FestivalModel namedFestival(String name, int sm, int sd, int em, int ed) {
        return namedFestivalWithId("fest-" + name, name, sm, sd, em, ed);
    }

    private FestivalModel namedFestivalWithId(String id, String name, int sm, int sd, int em, int ed) {
        FestivalModel model = new FestivalModel();
        model.setId(id);
        model.setName(name);
        model.setStart_month(sm);
        model.setStart_day(sd);
        model.setEnd_month(em);
        model.setEnd_day(ed);
        model.setStart_index(MonthDayRangeUtils.toIndex(sm, sd));
        model.setEnd_index(MonthDayRangeUtils.toIndex(em, ed));
        model.setEnabled(true);
        return model;
    }

    private FestivalService.FestivalItem item(String name, int sm, int sd, int em, int ed) {
        FestivalService.FestivalItem item = new FestivalService.FestivalItem();
        item.setName(name);
        item.setStart_month(sm);
        item.setStart_day(sd);
        item.setEnd_month(em);
        item.setEnd_day(ed);
        return item;
    }
}
