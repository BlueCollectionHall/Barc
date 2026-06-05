package com.miaoyu.barc.api.work.service;

import com.miaoyu.barc.api.work.enumeration.WorkStatusEnum;
import com.miaoyu.barc.api.work.mapper.WorkLikeMapper;
import com.miaoyu.barc.api.work.mapper.WorkMapper;
import com.miaoyu.barc.api.work.model.WorkModel;
import com.miaoyu.barc.utils.J;
import com.miaoyu.barc.utils.dto.PageRequestDto;
import com.miaoyu.barc.utils.dto.PageResultDto;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 个人中心喜欢作品列表服务单元测试
 */
@ExtendWith(MockitoExtension.class)
class WorkLikeListServiceTest {

    @Mock
    private WorkLikeMapper workLikeMapper;

    @Mock
    private WorkMapper workMapper;

    @Mock
    private WorkService workService;

    @InjectMocks
    private WorkLikeService workLikeService;

    @Test
    @DisplayName("用户存在时应分页返回公开喜欢作品并保持点赞时间倒序查询")
    void listLikedWorksByUsername_WhenUserExists_ShouldReturnPagedPublicWorksSortedByLikeTime() {
        PageRequestDto dto = pageRequest(2, 3);
        List<WorkModel> works = List.of(work("work-new"), work("work-old"));
        when(workLikeMapper.selectPublicLikedWorksByUsername("alice", WorkStatusEnum.PUBLIC, 3, 3)).thenReturn(works);
        when(workLikeMapper.countPublicLikedWorksByUsername("alice", WorkStatusEnum.PUBLIC)).thenReturn(5L);
        when(workService.loopSignatureWorkCover(works)).thenReturn(works);

        ResponseEntity<J> response = workLikeService.listLikedWorksByUsername("alice", dto);

        PageResultDto<WorkModel> page = successfulPage(response);
        assertEquals(5L, page.getTotal());
        assertEquals(works, page.getList());
        assertEquals(2, page.getPage_num());
        assertEquals(3, page.getPage_size());
        assertEquals(2, page.getTotal_page());
        assertEquals("work-new", page.getList().get(0).getId());
        assertEquals("work-old", page.getList().get(1).getId());
        verify(workLikeMapper).selectPublicLikedWorksByUsername("alice", WorkStatusEnum.PUBLIC, 3, 3);
        verify(workLikeMapper).countPublicLikedWorksByUsername("alice", WorkStatusEnum.PUBLIC);
    }

    @Test
    @DisplayName("用户名为空白时应返回普通错误")
    void listLikedWorksByUsername_WhenUsernameMissing_ShouldReturnFailure() {
        ResponseEntity<J> response = workLikeService.listLikedWorksByUsername("  ", pageRequest(1, 10));

        J body = response.getBody();
        assertNotNull(body);
        assertEquals(1, body.getCode());
        assertEquals("用户名不能为空", body.getData());
        verifyNoInteractions(workLikeMapper, workMapper, workService);
    }

    @Test
    @DisplayName("用户不存在时应返回空分页而不是失败")
    void listLikedWorksByUsername_WhenUserNotFound_ShouldReturnEmptyPage() {
        PageRequestDto dto = pageRequest(1, 10);
        when(workLikeMapper.selectPublicLikedWorksByUsername("missing", WorkStatusEnum.PUBLIC, 0, 10)).thenReturn(List.of());
        when(workLikeMapper.countPublicLikedWorksByUsername("missing", WorkStatusEnum.PUBLIC)).thenReturn(0L);

        ResponseEntity<J> response = workLikeService.listLikedWorksByUsername("missing", dto);

        PageResultDto<WorkModel> page = successfulPage(response);
        assertEquals(0L, page.getTotal());
        assertEquals(List.of(), page.getList());
        assertEquals(1, page.getPage_num());
        assertEquals(10, page.getPage_size());
        assertEquals(1, page.getTotal_page());
        verify(workService, never()).loopSignatureWorkCover(List.of());
    }

    @Test
    @DisplayName("返回前应复用作品服务批量签名封面图")
    void listLikedWorksByUsername_ShouldSignCoverImagesBeforeReturn() {
        PageRequestDto dto = pageRequest(1, 10);
        WorkModel unsignedWork = work("work-1");
        unsignedWork.setCover_image("covers/work-1.png");
        WorkModel signedWork = work("work-1");
        signedWork.setCover_image("https://signed.example/work-1.png");
        List<WorkModel> unsignedWorks = List.of(unsignedWork);
        List<WorkModel> signedWorks = List.of(signedWork);
        when(workLikeMapper.selectPublicLikedWorksByUsername("alice", WorkStatusEnum.PUBLIC, 0, 10)).thenReturn(unsignedWorks);
        when(workLikeMapper.countPublicLikedWorksByUsername("alice", WorkStatusEnum.PUBLIC)).thenReturn(1L);
        when(workService.loopSignatureWorkCover(unsignedWorks)).thenReturn(signedWorks);

        ResponseEntity<J> response = workLikeService.listLikedWorksByUsername("alice", dto);

        PageResultDto<WorkModel> page = successfulPage(response);
        assertEquals("https://signed.example/work-1.png", page.getList().get(0).getCover_image());
        verify(workService).loopSignatureWorkCover(unsignedWorks);
    }

    private PageRequestDto pageRequest(int pageNum, int pageSize) {
        PageRequestDto dto = new PageRequestDto();
        dto.setPage_num(pageNum);
        dto.setPage_size(pageSize);
        return dto;
    }

    private WorkModel work(String id) {
        WorkModel work = new WorkModel();
        work.setId(id);
        work.setTitle("作品" + id);
        work.setCover_image("covers/" + id + ".png");
        work.setView_count(10);
        work.setLike_count(3);
        return work;
    }

    @SuppressWarnings("unchecked")
    private PageResultDto<WorkModel> successfulPage(ResponseEntity<J> response) {
        J body = response.getBody();
        assertNotNull(body);
        assertEquals(0, body.getCode());
        return (PageResultDto<WorkModel>) body.getData();
    }
}
