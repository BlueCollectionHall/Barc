package com.miaoyu.barc.security;

import com.miaoyu.barc.api.mapper.CatalogMutationMapper;
import com.miaoyu.barc.background.mapper.BackgroundImageMapper;
import com.miaoyu.barc.background.model.BackgroundImageModel;
import com.miaoyu.barc.background.service.BackgroundImageService;
import com.miaoyu.barc.friendlink.mapper.FriendLinkMapper;
import com.miaoyu.barc.friendlink.model.FriendLinkModel;
import com.miaoyu.barc.friendlink.service.FriendLinkService;
import com.miaoyu.barc.exception.ApiException;
import com.miaoyu.barc.utils.tencent.cos.ImageUrlResolver;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 排序容量、集合完整性及失败传播；真实事务回滚另由 MySQL 集成测试覆盖。 */
@ExtendWith(MockitoExtension.class)
class CatalogMutationTest {
    @Mock CatalogMutationMapper catalogMutationMapper;
    @Mock BackgroundImageMapper backgroundImageMapper;
    @Mock FriendLinkMapper friendLinkMapper;
    @Mock ImageUrlResolver imageUrlResolver;
    @InjectMocks BackgroundImageService backgrounds;
    @InjectMocks FriendLinkService links;

    @Test void repeatedIdsAndOrdersFailBeforeAnyWrite() {
        assertThrows(ApiException.class, () -> backgrounds.reorderService("m", List.of(order("a",1),order("a",2))));
        assertThrows(ApiException.class, () -> backgrounds.reorderService("m", List.of(order("a",1),order("b",1))));
        assertThrows(ApiException.class, () -> backgrounds.reorderService("m", List.of()));
        verifyNoInteractions(backgroundImageMapper, catalogMutationMapper);
    }
    @Test void omittedOrForeignItemsCannotPartiallyReorderModule() {
        when(backgroundImageMapper.selectById("a")).thenReturn(background("a","home"));
        when(backgroundImageMapper.selectByModule("home")).thenReturn(List.of(background("a","home"), background("b","home")));
        assertThrows(ApiException.class, () -> backgrounds.reorderService("m", List.of(order("a",1))));
        verify(backgroundImageMapper, never()).updateSortOrder(anyString(), anyInt());
    }
    @Test void secondBackgroundWriteFailureEscapesTransaction() {
        when(backgroundImageMapper.selectById("a")).thenReturn(background("a","home")); when(backgroundImageMapper.selectById("b")).thenReturn(background("b","home"));
        when(backgroundImageMapper.selectByModule("home")).thenReturn(List.of(background("a","home"), background("b","home")));
        when(backgroundImageMapper.updateSortOrder("a",2)).thenReturn(1);
        assertThrows(ApiException.class, () -> backgrounds.reorderService("m",List.of(order("a",2),order("b",1))));
        var sequence = inOrder(catalogMutationMapper, backgroundImageMapper);
        sequence.verify(catalogMutationMapper).lock("presentation"); sequence.verify(backgroundImageMapper).selectById("a");
    }
    @Test void legacyEqualOrdersAreRepairedWhenMoving() {
        var a=link("a",1); var b=link("b",1); var c=link("c",1);
        when(friendLinkMapper.selectAll()).thenReturn(List.of(a,b,c)); when(friendLinkMapper.updateSortOrder(anyString(),anyInt())).thenReturn(1);
        assertEquals(0,links.moveService("m","b","down").getBody().getCode());
        verify(friendLinkMapper).updateSortOrder("a",1); verify(friendLinkMapper).updateSortOrder("c",2); verify(friendLinkMapper).updateSortOrder("b",3);
    }
    @Test void partialFriendMoveIsNeverReportedAsSuccess() {
        when(friendLinkMapper.selectAll()).thenReturn(List.of(link("a",1),link("b",2)));
        when(friendLinkMapper.updateSortOrder("b",1)).thenReturn(1);
        assertThrows(ApiException.class, () -> links.moveService("m","a","down"));
    }
    private BackgroundImageService.OrderItem order(String id,int position) { var item=new BackgroundImageService.OrderItem(); item.setId(id); item.setSort_order(position); return item; }
    private BackgroundImageModel background(String id,String module) { var model=new BackgroundImageModel(); model.setId(id); model.setModule(module); return model; }
    private FriendLinkModel link(String id,int position) { var model=new FriendLinkModel(); model.setId(id); model.setSort_order(position); return model; }
}
