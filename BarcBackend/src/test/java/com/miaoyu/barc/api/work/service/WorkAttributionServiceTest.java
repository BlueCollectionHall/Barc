package com.miaoyu.barc.api.work.service;

import com.miaoyu.barc.api.work.model.WorkModel;
import com.miaoyu.barc.user.mapper.UserArchiveMapper;
import com.miaoyu.barc.user.model.UserArchiveModel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WorkAttributionServiceTest {
    /**
     * 官方账号 UUID 已改为「管理端可配置」，不再是生产常量。
     * 测试使用自有字面量，避免与任何生产配置耦合。
     */
    private static final String OFFICIAL_ACCOUNT_UUID = "test-official-account-uuid";

    @Mock
    private UserArchiveMapper userArchiveMapper;

    @InjectMocks
    private WorkAttributionService workAttributionService;

    @Test
    @DisplayName("未认领作品的暂归属必须解析 author 账号而不是站外原作者署名")
    void unclaimedWork_ShouldResolveCollectionAssistantAsPlatformOwner() {
        WorkModel work = new WorkModel();
        work.setIs_claim(false);
        work.setAuthor(OFFICIAL_ACCOUNT_UUID);
        work.setAuthor_nickname("guochouchou");

        UserArchiveModel assistant = new UserArchiveModel();
        assistant.setUuid(OFFICIAL_ACCOUNT_UUID);
        assistant.setNickname("官方账号昵称");
        when(userArchiveMapper.selectByUuid(OFFICIAL_ACCOUNT_UUID)).thenReturn(assistant);

        assertEquals("官方账号昵称", workAttributionService.resolvePlatformOwnerNickname(work));
        verify(userArchiveMapper).selectByUuid(OFFICIAL_ACCOUNT_UUID);
    }

    @Test
    @DisplayName("收录者昵称必须通过 uploader 账号独立解析")
    void collectedWork_ShouldResolveUploaderIndependently() {
        WorkModel work = new WorkModel();
        work.setUploader("uploader-uuid");
        work.setAuthor_nickname("站外原作者");

        UserArchiveModel uploader = new UserArchiveModel();
        uploader.setUuid("uploader-uuid");
        uploader.setNickname("夜蛾笨蛋");
        when(userArchiveMapper.selectByUuid("uploader-uuid")).thenReturn(uploader);

        assertEquals("夜蛾笨蛋", workAttributionService.resolveUploaderNickname(work));
        verify(userArchiveMapper).selectByUuid("uploader-uuid");
    }
}
