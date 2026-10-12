package com.miaoyu.barc.feedback.service;

import com.miaoyu.barc.feedback.enumeration.*;
import com.miaoyu.barc.feedback.mapper.FeedbackMapper;
import com.miaoyu.barc.user.mapper.UserArchiveMapper;
import com.miaoyu.barc.user.model.UserArchiveModel;
import com.miaoyu.barc.user.enumeration.UserIdentityEnum;
import com.miaoyu.barc.utils.dto.PageResultDto;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 检验分页查询保持分类权限、本人隔离与容量边界。 */
@ExtendWith(MockitoExtension.class)
class FeedbackPaginationTest {
    @Mock FeedbackMapper feedbackMapper;
    @Mock UserArchiveMapper userArchiveMapper;
    @InjectMocks FeedbackService service;
    @Test void personalPageFixesAuthorToAuthenticatedIdentity() {
        when(feedbackMapper.countPage(FeedbackTypeEnum.BUG, FeedbackStatusEnum.PENDING, "u")).thenReturn(145L);
        var page = (PageResultDto<?>) service.getPage("u", FeedbackTypeEnum.BUG, FeedbackStatusEnum.PENDING, false, 2, 20).getBody().getData();
        assertEquals(145L,page.getTotal()); assertEquals(8,page.getTotal_page());
        verify(feedbackMapper).selectPage(FeedbackTypeEnum.BUG,FeedbackStatusEnum.PENDING,"u",20,20);
    }
    @Test void managerCannotUsePaginationToEscapeCategoryPermission() {
        var manager = new UserArchiveModel(); manager.setIdentity(UserIdentityEnum.MANAGER); manager.setPermission(4);
        when(userArchiveMapper.selectByUuid("m")).thenReturn(manager);
        assertNotEquals(0,service.getPage("m",FeedbackTypeEnum.USER,null,true,1,20).getBody().getCode());
        verifyNoInteractions(feedbackMapper);
    }
    @Test void legacyCountAndPageSizeAreBounded() {
        service.getLegacyList("u",FeedbackTypeEnum.WORK,false,3);
        service.getLegacyList("u",FeedbackTypeEnum.WORK,false,0);
        verify(feedbackMapper).selectPage(FeedbackTypeEnum.WORK,null,"u",0,3);
        verify(feedbackMapper).selectPage(FeedbackTypeEnum.WORK,null,"u",0,100);
    }
}
