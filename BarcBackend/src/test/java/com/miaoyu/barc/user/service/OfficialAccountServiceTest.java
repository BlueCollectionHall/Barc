package com.miaoyu.barc.user.service;

import com.miaoyu.barc.user.enumeration.OfficialAccountEnum;
import com.miaoyu.barc.user.mapper.OfficialAccountMapper;
import com.miaoyu.barc.user.mapper.UserArchiveMapper;
import com.miaoyu.barc.user.model.OfficialAccountModel;
import com.miaoyu.barc.user.model.UserArchiveModel;
import com.miaoyu.barc.user.model.vo.OfficialAccountItemVo;
import com.miaoyu.barc.user.model.vo.UserInfoVo;
import com.miaoyu.barc.utils.J;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 官方业务指定账号服务测试。
 *
 * 重点覆盖三类行为：
 * 1、列表必须是「枚举全量 × 绑定状态」，未绑定与悬空绑定都要如实暴露；
 * 2、绑定校验的每一条拒绝分支；
 * 3、换绑时必须精确迁移该业务的未认领作品，且首次绑定/换绑到同一账号时不得触发迁移。
 */
@ExtendWith(MockitoExtension.class)
class OfficialAccountServiceTest {

    private static final String OPERATOR_UUID = "operator-curator-uuid";
    private static final String BUSINESS = "COLLECTION_ASSISTANT";
    private static final String OLD_UUID = "old-official-uuid";
    private static final String NEW_UUID = "new-official-uuid";

    @Mock
    private OfficialAccountMapper officialAccountMapper;
    @Mock
    private UserArchiveMapper userArchiveMapper;

    @InjectMocks
    private OfficialAccountService officialAccountService;

    // ==================== 列表 ====================

    @Test
    @DisplayName("列表：无绑定记录时该业务必须以「未配置」出现，而不是被隐藏")
    void list_WhenNeverBound_ShouldExposeUnboundItem() {
        when(officialAccountMapper.selectAllWithAccount()).thenReturn(List.of());

        List<OfficialAccountItemVo> items = listItems();

        assertEquals(OfficialAccountEnum.values().length, items.size());
        OfficialAccountItemVo item = items.get(0);
        assertEquals(BUSINESS, item.getBusiness_key());
        assertEquals("未认领收录作品临时归属", item.getDisplay_name());
        assertFalse(item.getBound());
        assertFalse(item.getDangling());
        assertNull(item.getUser_uuid());
        assertEquals("", item.getUpdated_at());
    }

    @Test
    @DisplayName("列表：已绑定时必须带出账号昵称/用户名/头像与操作时间")
    void list_WhenBound_ShouldExposeAccountProfile() {
        OfficialAccountModel row = boundRow(NEW_UUID, false);
        row.setNickname("蔚蓝收录认领助手");
        row.setUsername("workslibrary");
        row.setAvatar("https://example.com/avatar.png");
        row.setRemark("官方业务账号");
        row.setUpdated_by_nickname("馆长");
        row.setUpdated_at(LocalDateTime.of(2026, 9, 19, 23, 30, 15));
        when(officialAccountMapper.selectAllWithAccount()).thenReturn(List.of(row));

        OfficialAccountItemVo item = listItems().get(0);

        assertTrue(item.getBound());
        assertFalse(item.getDangling());
        assertEquals(NEW_UUID, item.getUser_uuid());
        assertEquals("蔚蓝收录认领助手", item.getNickname());
        assertEquals("workslibrary", item.getUsername());
        assertEquals("官方业务账号", item.getRemark());
        assertEquals("馆长", item.getUpdated_by_nickname());
        assertEquals("2026-09-19 23:30:15", item.getUpdated_at());
    }

    @Test
    @DisplayName("列表：绑定账号已被删除时必须标记悬空且视为未绑定")
    void list_WhenBindingIsDangling_ShouldMarkDanglingAndUnbound() {
        OfficialAccountModel row = boundRow(OLD_UUID, true);
        when(officialAccountMapper.selectAllWithAccount()).thenReturn(List.of(row));

        OfficialAccountItemVo item = listItems().get(0);

        assertFalse(item.getBound());
        assertTrue(item.getDangling());
        // 仍带出旧 UUID，便于页面提示「绑定账号已不存在」
        assertEquals(OLD_UUID, item.getUser_uuid());
    }

    // ==================== 绑定读取（写链路入口） ====================

    @Test
    @DisplayName("读取绑定：正常已绑定时返回账号 UUID")
    void findBoundUuid_WhenBound_ShouldReturnUuid() {
        when(officialAccountMapper.selectBoundUuid(BUSINESS)).thenReturn(NEW_UUID);

        assertEquals(NEW_UUID, officialAccountService.findBoundUuid(OfficialAccountEnum.COLLECTION_ASSISTANT));
    }

    @Test
    @DisplayName("读取绑定：未绑定或账号已删除时返回 null（由调用方阻断写入）")
    void findBoundUuid_WhenUnboundOrDangling_ShouldReturnNull() {
        when(officialAccountMapper.selectBoundUuid(BUSINESS)).thenReturn(null);

        assertNull(officialAccountService.findBoundUuid(OfficialAccountEnum.COLLECTION_ASSISTANT));
        assertNull(officialAccountService.findBoundUuid(null));
        // 传 null 业务键时不应触碰数据库
        verify(officialAccountMapper, never()).selectBoundUuid(null);
    }

    // ==================== 绑定写入 ====================

    @Test
    @DisplayName("绑定：未知业务键必须被拒绝")
    void bind_WhenBusinessKeyUnknown_ShouldReject() {
        J body = bindBody("NOT_A_BUSINESS", NEW_UUID, null);

        assertEquals(1, body.getCode());
        assertTrue(body.getMsg().contains("未知的官方业务键"));
        verify(officialAccountMapper, never()).upsert(any());
    }

    @Test
    @DisplayName("绑定：未选择账号必须被拒绝")
    void bind_WhenUserUuidBlank_ShouldReject() {
        J body = bindBody(BUSINESS, "   ", null);

        assertEquals(1, body.getCode());
        assertTrue(body.getMsg().contains("请选择要绑定的账号"));
        verify(officialAccountMapper, never()).upsert(any());
    }

    @Test
    @DisplayName("绑定：目标账号不存在必须被拒绝")
    void bind_WhenTargetAccountMissing_ShouldReject() {
        when(userArchiveMapper.selectByUuid(NEW_UUID)).thenReturn(null);

        J body = bindBody(BUSINESS, NEW_UUID, null);

        assertEquals(1, body.getCode());
        assertTrue(body.getMsg().contains("未找到用户信息"));
        verify(officialAccountMapper, never()).upsert(any());
    }

    @Test
    @DisplayName("绑定：账号已被其它业务占用必须被拒绝")
    void bind_WhenAccountTakenByOtherBusiness_ShouldReject() {
        when(userArchiveMapper.selectByUuid(NEW_UUID)).thenReturn(new UserArchiveModel());
        when(officialAccountMapper.countByUserUuid(NEW_UUID, BUSINESS)).thenReturn(1);

        J body = bindBody(BUSINESS, NEW_UUID, null);

        assertEquals(1, body.getCode());
        assertTrue(body.getMsg().contains("已被其它官方业务绑定"));
        verify(officialAccountMapper, never()).upsert(any());
    }

    @Test
    @DisplayName("绑定：首次绑定成功，不得触发换绑迁移")
    void bind_WhenFirstBinding_ShouldNotMigrate() {
        when(userArchiveMapper.selectByUuid(NEW_UUID)).thenReturn(new UserArchiveModel());
        when(officialAccountMapper.countByUserUuid(NEW_UUID, BUSINESS)).thenReturn(0);
        when(officialAccountMapper.selectByBusinessKey(BUSINESS)).thenReturn(null);
        when(officialAccountMapper.upsert(any())).thenReturn(1);

        J body = bindBody(BUSINESS, NEW_UUID, "首次绑定");

        assertEquals(0, body.getCode());
        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) body.getData();
        assertEquals(NEW_UUID, result.get("user_uuid"));
        assertNull(result.get("replaced_uuid"));
        assertEquals(0, result.get("migrated_works"));

        ArgumentCaptor<OfficialAccountModel> captor = ArgumentCaptor.forClass(OfficialAccountModel.class);
        verify(officialAccountMapper).upsert(captor.capture());
        assertEquals(BUSINESS, captor.getValue().getBusiness_key());
        assertEquals(OPERATOR_UUID, captor.getValue().getUpdated_by());
        assertEquals("首次绑定", captor.getValue().getRemark());
        verify(officialAccountMapper, never()).migrateUnclaimedAuthor(anyString(), anyString());
    }

    @Test
    @DisplayName("绑定：换绑到不同账号时，必须迁移该业务的未认领作品并回传数量")
    void bind_WhenReplacingAccount_ShouldMigrateUnclaimedWorks() {
        when(userArchiveMapper.selectByUuid(NEW_UUID)).thenReturn(new UserArchiveModel());
        when(officialAccountMapper.countByUserUuid(NEW_UUID, BUSINESS)).thenReturn(0);
        when(officialAccountMapper.selectByBusinessKey(BUSINESS)).thenReturn(boundRow(OLD_UUID, false));
        when(officialAccountMapper.upsert(any())).thenReturn(1);
        when(officialAccountMapper.migrateUnclaimedAuthor(OLD_UUID, NEW_UUID)).thenReturn(3);

        J body = bindBody(BUSINESS, NEW_UUID, null);

        assertEquals(0, body.getCode());
        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) body.getData();
        assertEquals(OLD_UUID, result.get("replaced_uuid"));
        assertEquals(3, result.get("migrated_works"));
        verify(officialAccountMapper).migrateUnclaimedAuthor(OLD_UUID, NEW_UUID);
    }

    @Test
    @DisplayName("绑定：换绑到同一账号时必须幂等，不得触发迁移")
    void bind_WhenSameAccount_ShouldBeIdempotent() {
        when(userArchiveMapper.selectByUuid(NEW_UUID)).thenReturn(new UserArchiveModel());
        when(officialAccountMapper.countByUserUuid(NEW_UUID, BUSINESS)).thenReturn(0);
        when(officialAccountMapper.selectByBusinessKey(BUSINESS)).thenReturn(boundRow(NEW_UUID, false));
        when(officialAccountMapper.upsert(any())).thenReturn(1);

        J body = bindBody(BUSINESS, NEW_UUID, null);

        assertEquals(0, body.getCode());
        verify(officialAccountMapper, never()).migrateUnclaimedAuthor(anyString(), anyString());
    }

    @Test
    @DisplayName("绑定：写入失败必须报错，且不得触发迁移")
    void bind_WhenUpsertFails_ShouldReject() {
        when(userArchiveMapper.selectByUuid(NEW_UUID)).thenReturn(new UserArchiveModel());
        when(officialAccountMapper.countByUserUuid(NEW_UUID, BUSINESS)).thenReturn(0);
        when(officialAccountMapper.selectByBusinessKey(BUSINESS)).thenReturn(null);
        when(officialAccountMapper.upsert(any())).thenReturn(0);

        J body = bindBody(BUSINESS, NEW_UUID, null);

        assertEquals(1, body.getCode());
        assertTrue(body.getMsg().contains("绑定失败"));
        verify(officialAccountMapper, never()).migrateUnclaimedAuthor(anyString(), anyString());
    }

    // ==================== 账号搜索 ====================

    @Test
    @DisplayName("账号搜索：空关键词必须短路返回空列表，不得用 LIKE '%%' 拉全站账号")
    void searchUser_WhenKeywordBlank_ShouldReturnEmptyWithoutQuery() {
        assertEquals(List.of(), searchBody(null));
        assertEquals(List.of(), searchBody("   "));
        verify(userArchiveMapper, never()).searchByKeyword(anyString());
    }

    @Test
    @DisplayName("账号搜索：正常关键词必须透传给 Mapper")
    void searchUser_WhenKeywordPresent_ShouldDelegate() {
        UserInfoVo vo = new UserInfoVo();
        vo.setUuid(NEW_UUID);
        vo.setNickname("蔚蓝收录认领助手");
        when(userArchiveMapper.searchByKeyword("works")).thenReturn(List.of(vo));

        assertEquals(List.of(com.miaoyu.barc.user.model.vo.UserSearchView.from(vo)), searchBody("works"));
        verify(userArchiveMapper).searchByKeyword("works");
    }

    // ==================== 测试工具 ====================

    @SuppressWarnings("unchecked")
    private List<OfficialAccountItemVo> listItems() {
        ResponseEntity<J> response = officialAccountService.listService(OPERATOR_UUID);
        assertEquals(0, response.getBody().getCode());
        return (List<OfficialAccountItemVo>) response.getBody().getData();
    }

    private J bindBody(String businessKey, String userUuid, String remark) {
        return officialAccountService.bindService(OPERATOR_UUID, businessKey, userUuid, remark).getBody();
    }

    @SuppressWarnings("unchecked")
    private List<com.miaoyu.barc.user.model.vo.UserSearchView> searchBody(String keyword) {
        org.springframework.test.util.ReflectionTestUtils.setField(officialAccountService, "userSearchService", new UserSearchService(userArchiveMapper));
        ResponseEntity<J> response = officialAccountService.searchUserService(OPERATOR_UUID, keyword);
        assertEquals(0, response.getBody().getCode());
        return (List<com.miaoyu.barc.user.model.vo.UserSearchView>) response.getBody().getData();
    }

    private OfficialAccountModel boundRow(String userUuid, boolean dangling) {
        OfficialAccountModel row = new OfficialAccountModel();
        row.setBusiness_key(BUSINESS);
        row.setUser_uuid(userUuid);
        row.setDangling(dangling);
        row.setUpdated_at(LocalDateTime.of(2026, 9, 19, 23, 0, 0));
        return row;
    }
}
