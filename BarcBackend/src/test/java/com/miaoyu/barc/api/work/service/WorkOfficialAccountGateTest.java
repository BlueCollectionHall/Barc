package com.miaoyu.barc.api.work.service;

import com.miaoyu.barc.api.work.mapper.WorkMapper;
import com.miaoyu.barc.api.work.mapper.WorkOperationLogMapper;
import com.miaoyu.barc.api.work.model.WorkModel;
import com.miaoyu.barc.user.enumeration.OfficialAccountEnum;
import com.miaoyu.barc.user.service.OfficialAccountService;
import com.miaoyu.barc.utils.J;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 作品归属链路的「官方账号未配置即阻断」测试。
 *
 * 背景：未认领作品的 author 原先写死为编译期常量，现在改为读取管理端绑定。
 * 未绑定（或绑定账号已被删除）时必须**显式报错且不落库**，
 * 绝不允许回落到任何硬编码 UUID —— 否则生产会继续产生悬空归属。
 */
@ExtendWith(MockitoExtension.class)
class WorkOfficialAccountGateTest {

    private static final String OPERATOR_UUID = "operator-uuid";
    private static final String BOUND_UUID = "bound-official-uuid";

    @Mock
    private WorkMapper workMapper;
    @Mock
    private WorkOperationLogMapper workOperationLogMapper;
    @Mock
    private OfficialAccountService officialAccountService;
    @Mock
    private com.miaoyu.barc.api.mapper.StudentMapper studentMapper;

    @InjectMocks
    private WorkService workService;
    @InjectMocks
    private WorkManageService workManageService;

    // ==================== 搬运收录上传 ====================

    @Test
    @DisplayName("搬运收录上传：官方账号未配置时必须报错且不写库")
    void uploadWork_WhenOfficialUnbound_ShouldRejectWithoutInsert() {
        when(officialAccountService.findBoundUuid(OfficialAccountEnum.COLLECTION_ASSISTANT)).thenReturn(null);

        validStudent();
        // 文件和学生先验证；业务失败必须抛出异常以回滚完整上传事务。
        assertThrows(com.miaoyu.barc.exception.ApiException.class, () -> workService.uploadWorkService(
                OPERATOR_UUID, null, collectedWorkForm(), cover(), null));
        verify(workMapper, never()).insert(any());
    }

    @Test
    @DisplayName("搬运收录上传：官方账号已配置时 author 必须写入绑定账号 UUID")
    void uploadWork_WhenOfficialBound_ShouldUseBoundUuid() {
        when(officialAccountService.findBoundUuid(OfficialAccountEnum.COLLECTION_ASSISTANT)).thenReturn(BOUND_UUID);
        when(workMapper.insert(any())).thenReturn(false);

        validStudent();
        assertThrows(com.miaoyu.barc.exception.ApiException.class,
                () -> workService.uploadWorkService(OPERATOR_UUID, null, collectedWorkForm(), cover(), null));

        ArgumentCaptor<WorkModel> captor = ArgumentCaptor.forClass(WorkModel.class);
        verify(workMapper).insert(captor.capture());
        assertEquals(BOUND_UUID, captor.getValue().getAuthor());
        assertEquals(OPERATOR_UUID, captor.getValue().getUploader());
    }

    @Test
    @DisplayName("自创作品：author 仍是上传者本人，不得读取官方账号配置")
    void uploadWork_WhenOwnWork_ShouldKeepUploaderAsAuthor() {
        when(workMapper.insert(any())).thenReturn(false);

        WorkModel form = collectedWorkForm();
        form.setIs_claim(true);
        validStudent();
        assertThrows(com.miaoyu.barc.exception.ApiException.class,
                () -> workService.uploadWorkService(OPERATOR_UUID, null, form, cover(), null));

        ArgumentCaptor<WorkModel> captor = ArgumentCaptor.forClass(WorkModel.class);
        verify(workMapper).insert(captor.capture());
        assertEquals(OPERATOR_UUID, captor.getValue().getAuthor());
        verify(officialAccountService, never()).findBoundUuid(any());
    }

    // ==================== 撤销认领 ====================

    @Test
    @DisplayName("撤销认领：官方账号未配置时必须报错且不改动作品")
    void revokeClaim_WhenOfficialUnbound_ShouldRejectWithoutUpdate() {
        when(workMapper.selectByIdForUpdate("work-1")).thenReturn(claimedWork());
        when(officialAccountService.findBoundUuid(OfficialAccountEnum.COLLECTION_ASSISTANT)).thenReturn(null);

        ResponseEntity<J> response = workManageService.revokeClaim(OPERATOR_UUID, "work-1");

        assertEquals(1, response.getBody().getCode());
        assertTrue(String.valueOf(response.getBody().getData()).contains("官方账号未配置"));
        verify(workMapper, never()).update(any());
    }

    @Test
    @DisplayName("撤销认领：官方账号已配置时 author 必须回到绑定账号 UUID")
    void revokeClaim_WhenOfficialBound_ShouldResetToBoundUuid() {
        when(workMapper.selectByIdForUpdate("work-1")).thenReturn(claimedWork());
        when(officialAccountService.findBoundUuid(OfficialAccountEnum.COLLECTION_ASSISTANT)).thenReturn(BOUND_UUID);
        when(workMapper.updateAttribution("work-1", BOUND_UUID, false)).thenReturn(true);
        when(workOperationLogMapper.insert(any())).thenReturn(1);

        ResponseEntity<J> response = workManageService.revokeClaim(OPERATOR_UUID, "work-1");

        assertEquals(0, response.getBody().getCode());
        verify(workMapper).updateAttribution("work-1", BOUND_UUID, false);
        verify(workMapper, never()).update(any());
    }

    // ==================== 工具 ====================

    /** 一张「搬运收录」上传表单：is_claim=false 且带站外原作者署名 */
    private WorkModel collectedWorkForm() {
        WorkModel form = new WorkModel();
        form.setIs_claim(false);
        form.setAuthor_nickname("站外原作者");
        form.setTitle("搬运收录测试");
        form.setStudent("student");
        return form;
    }

    /** 使用真实图片头，确保测试走到官方账号门禁而非格式校验。 */
    private org.springframework.mock.web.MockMultipartFile cover() {
        return new org.springframework.mock.web.MockMultipartFile("cover_image", "a.png", "image/png", com.miaoyu.barc.security.TestImages.png());
    }

    private void validStudent() {
        when(studentMapper.selectById("student")).thenReturn(new com.miaoyu.barc.api.model.StudentModel());
    }

    /** 一篇已被认领的作品 */
    private WorkModel claimedWork() {
        WorkModel work = new WorkModel();
        work.setId("work-1");
        work.setTitle("已认领作品");
        work.setIs_claim(true);
        work.setAuthor("some-real-author-uuid");
        return work;
    }
}
