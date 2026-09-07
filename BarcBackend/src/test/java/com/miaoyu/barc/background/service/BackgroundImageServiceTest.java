package com.miaoyu.barc.background.service;

import com.miaoyu.barc.api.service.CosGcService;
import com.miaoyu.barc.background.mapper.BackgroundImageMapper;
import com.miaoyu.barc.background.model.BackgroundImageModel;
import com.miaoyu.barc.background.enumeration.BackgroundModuleEnum;
import com.miaoyu.barc.utils.J;
import com.miaoyu.barc.utils.tencent.cos.CosBucketConfigEnum;
import com.miaoyu.barc.utils.tencent.cos.CosConfig;
import com.miaoyu.barc.utils.tencent.cos.CosService;
import com.miaoyu.barc.utils.tencent.cos.ImageUrlResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 背景图服务单元测试（纯校验与逻辑分支，不依赖真实 COS / DB）
 */
@ExtendWith(MockitoExtension.class)
class BackgroundImageServiceTest {

    @Mock
    private BackgroundImageMapper backgroundImageMapper;
    @Mock
    private CosService cosService;
    @Mock
    private CosConfig cosConfig;
    @Mock
    private CosGcService cosGcService;
    @Mock
    private ImageUrlResolver imageUrlResolver;

    @InjectMocks
    private BackgroundImageService backgroundImageService;

    private MockMultipartFile image;

    @BeforeEach
    void setUp() {
        // 轻量图片（< 1MB）
        image = new MockMultipartFile("file", "photo.jpg", "image/jpeg", new byte[]{1, 2, 3});
    }

    @Test
    @DisplayName("上传背景图 - 非法模块被拒")
    void upload_invalidModule() {
        ResponseEntity<J> response = backgroundImageService.uploadService("admin-uuid", image, "not-a-module", null, null);
        assertEquals(1, response.getBody().getCode());
        verify(cosService, never()).uploadFileWithKey(any(), anyString(), any());
    }

    @Test
    @DisplayName("上传背景图 - 空文件被拒")
    void upload_emptyFile() {
        MockMultipartFile empty = new MockMultipartFile("file", "a.jpg", "image/jpeg", new byte[0]);
        ResponseEntity<J> response = backgroundImageService.uploadService("admin-uuid", empty, "home", null, null);
        assertEquals(1, response.getBody().getCode());
    }

    @Test
    @DisplayName("上传背景图 - 超过 1MB 被拒")
    void upload_tooLarge() {
        byte[] big = new byte[1024 * 1024 + 1];
        MockMultipartFile large = new MockMultipartFile("file", "big.jpg", "image/jpeg", big);
        ResponseEntity<J> response = backgroundImageService.uploadService("admin-uuid", large, "home", null, null);
        assertEquals(1, response.getBody().getCode());
        verify(cosService, never()).uploadFileWithKey(any(), anyString(), any());
    }

    @Test
    @DisplayName("上传背景图 - 非图片类型被拒")
    void upload_notImage() {
        MockMultipartFile txt = new MockMultipartFile("file", "note.txt", "text/plain", "hello".getBytes());
        ResponseEntity<J> response = backgroundImageService.uploadService("admin-uuid", txt, "home", null, null);
        assertEquals(1, response.getBody().getCode());
    }

    @Test
    @DisplayName("上传背景图 - 成功，落 image 桶且命名符合规则")
    void upload_success() {
        when(cosService.uploadFileWithKey(eq(image), anyString(), eq(CosBucketConfigEnum.image)))
                .thenAnswer(inv -> new J(0, "文件上传成功", inv.getArgument(1)));
        when(backgroundImageMapper.insert(any(BackgroundImageModel.class))).thenReturn(1);

        ResponseEntity<J> response = backgroundImageService.uploadService("admin-uuid", image, "home", null, null);

        assertEquals(0, response.getBody().getCode());
        BackgroundImageModel model = (BackgroundImageModel) response.getBody().getData();
        assertNotNull(model);
        assertTrue(model.getObject_key().startsWith("client/bg/home/"));
        assertTrue(model.getEnabled());
        assertEquals("home", model.getModule());
        assertEquals("admin-uuid", model.getCreated_by());
        // 命名：UUID第一段-到秒时间戳.扩展名
        assertTrue(model.getObject_key().matches("client/bg/home/[0-9A-F]{8}-\\d{10}\\.jpg"));
    }

    @Test
    @DisplayName("上传背景图 - COS 上传失败返回错误")
    void upload_cosFail() {
        when(cosService.uploadFileWithKey(eq(image), anyString(), eq(CosBucketConfigEnum.image)))
                .thenReturn(new J(1, "文件上传失败", null));
        ResponseEntity<J> response = backgroundImageService.uploadService("admin-uuid", image, "home", null, null);
        assertEquals(1, response.getBody().getCode());
        verify(backgroundImageMapper, never()).insert(any());
    }

    @Test
    @DisplayName("背景模块下拉 - 返回全部模块")
    void modules_options() {
        ResponseEntity<J> response = backgroundImageService.modulesService("admin-uuid");
        assertEquals(0, response.getBody().getCode());
        @SuppressWarnings("unchecked")
        List<com.miaoyu.barc.utils.dto.ValueLabelDto> list =
                (List<com.miaoyu.barc.utils.dto.ValueLabelDto>) response.getBody().getData();
        assertEquals(BackgroundModuleEnum.values().length, list.size());
    }

    @Test
    @DisplayName("列表 - 非法模块被拒")
    void list_invalidModule() {
        ResponseEntity<J> response = backgroundImageService.listService("admin-uuid", "bad");
        assertEquals(1, response.getBody().getCode());
    }

    @Test
    @DisplayName("启用/停用 - 记录不存在返回失败")
    void setEnabled_notFound() {
        when(backgroundImageMapper.selectById("id1")).thenReturn(null);
        ResponseEntity<J> response = backgroundImageService.setEnabledService("admin-uuid", "id1", true);
        assertEquals(1, response.getBody().getCode());
    }

    @Test
    @DisplayName("启用/停用 - 更新成功")
    void setEnabled_success() {
        BackgroundImageModel model = new BackgroundImageModel();
        model.setId("id1");
        when(backgroundImageMapper.selectById("id1")).thenReturn(model);
        when(backgroundImageMapper.updateEnabled("id1", true)).thenReturn(1);

        ResponseEntity<J> response = backgroundImageService.setEnabledService("admin-uuid", "id1", true);
        assertEquals(0, response.getBody().getCode());
        verify(backgroundImageMapper).updateEnabled("id1", true);
    }

    @Test
    @DisplayName("删除 - 记录不存在返回失败")
    void delete_notFound() {
        when(backgroundImageMapper.selectById("id1")).thenReturn(null);
        ResponseEntity<J> response = backgroundImageService.deleteService("admin-uuid", "id1");
        assertEquals(1, response.getBody().getCode());
        verify(cosGcService, never()).enqueue(anyString(), anyString());
    }

    @Test
    @DisplayName("删除 - 成功并交给 GC 清理 COS 对象")
    void delete_success() {
        BackgroundImageModel model = new BackgroundImageModel();
        model.setId("id1");
        model.setObject_key("client/bg/home/ABC-123.jpg");
        when(backgroundImageMapper.selectById("id1")).thenReturn(model);
        when(backgroundImageMapper.softDelete("id1")).thenReturn(1);
        CosConfig.CosBucketPojo bucket = new CosConfig.CosBucketPojo();
        bucket.setBucketName("miaoyu-barc-image-1309572720");
        when(cosConfig.getImage()).thenReturn(bucket);

        ResponseEntity<J> response = backgroundImageService.deleteService("admin-uuid", "id1");
        assertEquals(0, response.getBody().getCode());
        verify(cosGcService).enqueue("miaoyu-barc-image-1309572720", "client/bg/home/ABC-123.jpg");
        verify(backgroundImageMapper).softDelete("id1");
    }

    @Test
    @DisplayName("公开接口 - 按模块分组并解析签名 URL")
    void publicAll_groupsByModule() {
        BackgroundImageModel home = new BackgroundImageModel();
        home.setModule("home");
        home.setObject_key("client/bg/home/A-1.jpg");
        BackgroundImageModel sign = new BackgroundImageModel();
        sign.setModule("sign");
        sign.setObject_key("client/bg/sign/B-2.jpg");
        when(backgroundImageMapper.selectAllEnabled()).thenReturn(List.of(home, sign));
        when(imageUrlResolver.resolve("client/bg/home/A-1.jpg")).thenReturn("https://signed/home");
        when(imageUrlResolver.resolve("client/bg/sign/B-2.jpg")).thenReturn("https://signed/sign");

        J result = backgroundImageService.publicAllService();

        assertEquals(0, result.getCode());
        @SuppressWarnings("unchecked")
        Map<String, List<BackgroundImageService.PublicItem>> grouped =
                (Map<String, List<BackgroundImageService.PublicItem>>) result.getData();
        assertEquals(1, grouped.get("home").size());
        assertEquals("https://signed/home", grouped.get("home").get(0).getUrl());
        assertEquals("https://signed/sign", grouped.get("sign").get(0).getUrl());
    }

    @Test
    @DisplayName("上传背景图 - 携带合法时段与节日标签并落库")
    void upload_withValidScene() {
        when(cosService.uploadFileWithKey(eq(image), anyString(), eq(CosBucketConfigEnum.image)))
                .thenAnswer(inv -> new J(0, "文件上传成功", inv.getArgument(1)));
        when(backgroundImageMapper.insert(any(BackgroundImageModel.class))).thenReturn(1);

        ResponseEntity<J> response = backgroundImageService.uploadService("admin-uuid", image, "home", "day", "newyear");

        assertEquals(0, response.getBody().getCode());
        BackgroundImageModel model = (BackgroundImageModel) response.getBody().getData();
        assertEquals("day", model.getTime_period());
        assertEquals("newyear", model.getFestival());
    }

    @Test
    @DisplayName("上传背景图 - 非法时段被拒且不上传 COS")
    void upload_invalidTimePeriod() {
        ResponseEntity<J> response = backgroundImageService.uploadService("admin-uuid", image, "home", "bad", null);
        assertEquals(1, response.getBody().getCode());
        verify(cosService, never()).uploadFileWithKey(any(), anyString(), any());
    }

    @Test
    @DisplayName("上传背景图 - 非法节日被拒且不上传 COS")
    void upload_invalidFestival() {
        ResponseEntity<J> response = backgroundImageService.uploadService("admin-uuid", image, "home", null, "bad");
        assertEquals(1, response.getBody().getCode());
        verify(cosService, never()).uploadFileWithKey(any(), anyString(), any());
    }

    @Test
    @DisplayName("更新场景 - 记录不存在返回失败")
    void updateScene_notFound() {
        when(backgroundImageMapper.selectById("id1")).thenReturn(null);
        ResponseEntity<J> response = backgroundImageService.updateSceneService("admin-uuid", "id1", "day", "newyear");
        assertEquals(1, response.getBody().getCode());
        verify(backgroundImageMapper, never()).updateScene(anyString(), any(), any());
    }

    @Test
    @DisplayName("更新场景 - 合法标签更新成功，空串归一化为空")
    void updateScene_success() {
        BackgroundImageModel model = new BackgroundImageModel();
        model.setId("id1");
        when(backgroundImageMapper.selectById("id1")).thenReturn(model);
        when(backgroundImageMapper.updateScene("id1", "night", null)).thenReturn(1);

        ResponseEntity<J> response = backgroundImageService.updateSceneService("admin-uuid", "id1", "night", "");

        assertEquals(0, response.getBody().getCode());
        verify(backgroundImageMapper).updateScene("id1", "night", null);
        BackgroundImageModel saved = (BackgroundImageModel) response.getBody().getData();
        assertEquals("night", saved.getTime_period());
        assertNull(saved.getFestival());
    }

    @Test
    @DisplayName("更新场景 - 非法时段被拒且不写库")
    void updateScene_invalidTimePeriod() {
        BackgroundImageModel model = new BackgroundImageModel();
        model.setId("id1");
        when(backgroundImageMapper.selectById("id1")).thenReturn(model);
        ResponseEntity<J> response = backgroundImageService.updateSceneService("admin-uuid", "id1", "bad", null);
        assertEquals(1, response.getBody().getCode());
        verify(backgroundImageMapper, never()).updateScene(anyString(), any(), any());
    }

    @Test
    @DisplayName("更新场景 - 非法节日被拒且不写库")
    void updateScene_invalidFestival() {
        BackgroundImageModel model = new BackgroundImageModel();
        model.setId("id1");
        when(backgroundImageMapper.selectById("id1")).thenReturn(model);
        ResponseEntity<J> response = backgroundImageService.updateSceneService("admin-uuid", "id1", null, "bad");
        assertEquals(1, response.getBody().getCode());
        verify(backgroundImageMapper, never()).updateScene(anyString(), any(), any());
    }

    @Test
    @DisplayName("公开接口 - 携带时段与节日标签")
    void publicAll_carriesScene() {
        BackgroundImageModel home = new BackgroundImageModel();
        home.setModule("home");
        home.setObject_key("client/bg/home/A-1.jpg");
        home.setTime_period("day");
        home.setFestival("newyear");
        when(backgroundImageMapper.selectAllEnabled()).thenReturn(List.of(home));
        when(imageUrlResolver.resolve("client/bg/home/A-1.jpg")).thenReturn("https://signed/home");

        J result = backgroundImageService.publicAllService();

        assertEquals(0, result.getCode());
        @SuppressWarnings("unchecked")
        Map<String, List<BackgroundImageService.PublicItem>> grouped =
                (Map<String, List<BackgroundImageService.PublicItem>>) result.getData();
        BackgroundImageService.PublicItem item = grouped.get("home").get(0);
        assertEquals("https://signed/home", item.getUrl());
        assertEquals("day", item.getTime_period());
        assertEquals("newyear", item.getFestival());
    }
}
