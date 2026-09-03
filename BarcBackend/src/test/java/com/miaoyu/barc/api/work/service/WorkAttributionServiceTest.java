package com.miaoyu.barc.api.work.service;

import com.miaoyu.barc.api.work.constant.WorkAttributionConst;
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
    @Mock
    private UserArchiveMapper userArchiveMapper;

    @InjectMocks
    private WorkAttributionService workAttributionService;

    @Test
    @DisplayName("未认领作品的暂归属必须解析 author 账号而不是站外原作者署名")
    void unclaimedWork_ShouldResolveCollectionAssistantAsPlatformOwner() {
        WorkModel work = new WorkModel();
        work.setIs_claim(false);
        work.setAuthor(WorkAttributionConst.COLLECTION_ASSISTANT_UUID);
        work.setAuthor_nickname("guochouchou");

        UserArchiveModel assistant = new UserArchiveModel();
        assistant.setUuid(WorkAttributionConst.COLLECTION_ASSISTANT_UUID);
        assistant.setNickname("蔚蓝收录助手");
        when(userArchiveMapper.selectByUuid(WorkAttributionConst.COLLECTION_ASSISTANT_UUID)).thenReturn(assistant);

        assertEquals("蔚蓝收录助手", workAttributionService.resolvePlatformOwnerNickname(work));
        verify(userArchiveMapper).selectByUuid(WorkAttributionConst.COLLECTION_ASSISTANT_UUID);
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
