package com.miaoyu.barc.comment.service;

import com.miaoyu.barc.comment.mapper.MessageBoardMapper;
import com.miaoyu.barc.comment.model.MessageBoardModel;
import com.miaoyu.barc.user.mapper.UserArchiveMapper;
import com.miaoyu.barc.user.model.UserArchiveModel;
import com.miaoyu.barc.user.enumeration.UserIdentityEnum;
import com.miaoyu.barc.exception.ApiException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 留言写入字段、批量边界及评论/留言共享治理权限回归。 */
@ExtendWith(MockitoExtension.class)
class MessageBoardBoundaryTest {
    @Mock MessageBoardMapper messageBoardMapper;
    @Mock UserArchiveMapper userArchiveMapper;
    @Mock MessageNotificationService notificationService;
    @InjectMocks MessageBoardService service;
    @Test void blankAndLongMessageDoNotWrite() {
        assertThrows(ApiException.class, () -> service.uploadBoardMessageService("u","  "));
        assertThrows(ApiException.class, () -> service.uploadBoardMessageService("u","x".repeat(2001)));
        verifyNoInteractions(messageBoardMapper);
    }
    @Test void newMessageUsesOperatorAndTrimmedContent() {
        when(messageBoardMapper.insert(any())).thenReturn(true);
        service.uploadBoardMessageService("u"," 内容 ");
        verify(messageBoardMapper).insert(argThat(item -> "u".equals(item.getAuthor()) && "内容".equals(item.getContent()) && item.getId() != null));
    }
    @Test void publicCapacityIsBounded() {
        service.getNewBoardMessagesService(Integer.MAX_VALUE); service.getPage(2,20);
        verify(messageBoardMapper).selectByLimit(100); verify(messageBoardMapper).selectPage(20,20);
    }
    @Test void unknownRoleCannotUseBatchCommands() {
        manager(4);
        assertThrows(ApiException.class, () -> service.adminBatchDeleteBoardMessagesService("m",List.of("a"),"原因"));
        verifyNoInteractions(messageBoardMapper, notificationService);
    }
    @Test void emptyDuplicateOrLargeBatchCannotWrite() {
        manager(2);
        assertThrows(ApiException.class, () -> service.adminBatchDeleteBoardMessagesService("m",List.of(),""));
        assertThrows(ApiException.class, () -> service.adminBatchDeleteBoardMessagesService("m",List.of("a","a"),""));
        assertThrows(ApiException.class, () -> service.adminBatchDeleteBoardMessagesService("m",Collections.nCopies(101,"a"),""));
        verifyNoInteractions(messageBoardMapper, notificationService);
    }
    @Test void partialDeleteThrowsBeforeAnyNotification() {
        manager(16); when(messageBoardMapper.selectByIdForUpdate("a")).thenReturn(message("a")); when(messageBoardMapper.selectByIdForUpdate("b")).thenReturn(message("b"));
        when(messageBoardMapper.batchDelete(List.of("a","b"))).thenReturn(1);
        assertThrows(ApiException.class, () -> service.adminBatchDeleteBoardMessagesService("m",List.of("b","a"),""));
        verifyNoInteractions(notificationService);
    }
    @Test void fullDeleteLocksInStableOrderAndNotifiesAfterSuccess() {
        manager(32); var a=message("a"); var b=message("b");
        when(messageBoardMapper.selectByIdForUpdate("a")).thenReturn(a); when(messageBoardMapper.selectByIdForUpdate("b")).thenReturn(b);
        when(messageBoardMapper.batchDelete(List.of("a","b"))).thenReturn(2);
        assertEquals(0,service.adminBatchDeleteBoardMessagesService("m",List.of("b","a"),"").getBody().getCode());
        var sequence=inOrder(messageBoardMapper,notificationService);
        sequence.verify(messageBoardMapper).selectByIdForUpdate("a"); sequence.verify(messageBoardMapper).selectByIdForUpdate("b");
        sequence.verify(messageBoardMapper).batchDelete(List.of("a","b")); sequence.verify(notificationService).notifyMessageDeleted(a,"m");
    }
    private void manager(int permission) { var user=new UserArchiveModel(); user.setIdentity(UserIdentityEnum.MANAGER); user.setPermission(permission); when(userArchiveMapper.selectByUuid("m")).thenReturn(user); }
    private MessageBoardModel message(String id) { var m=new MessageBoardModel(); m.setId(id); m.setAuthor("u"); return m; }
}
