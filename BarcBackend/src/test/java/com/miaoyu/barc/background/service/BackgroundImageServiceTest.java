package com.miaoyu.barc.background.service;

import com.miaoyu.barc.api.service.CosGcService;
import com.miaoyu.barc.api.file.service.ObjectStorageService;
import com.miaoyu.barc.exception.ApiException;
import com.miaoyu.barc.security.TestImages;
import com.miaoyu.barc.background.mapper.BackgroundImageMapper;
import com.miaoyu.barc.background.model.BackgroundImageModel;
import com.miaoyu.barc.background.model.BackgroundModuleOption;
import com.miaoyu.barc.background.enumeration.BackgroundModuleEnum;
import com.miaoyu.barc.festival.mapper.FestivalMapper;
import com.miaoyu.barc.festival.model.FestivalModel;
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
    @Mock private ObjectStorageService objectStorage;

    @Mock
    private BackgroundImageMapper backgroundImageMapper;
    @Mock
    private FestivalMapper festivalMapper;
    @Mock
    private CosService cosService;
    @Mock
    private CosConfig cosConfig;
    @Mock
    private CosGcService cosGcService;
    @Mock
    private ImageUrlResolver imageUrlResolver;

    @Mock private com.miaoyu.barc.api.mapper.CatalogMutationMapper catalogMutationMapper;
    @InjectMocks
    private BackgroundImageService backgroundImageService;

    private MockMultipartFile image;

    @BeforeEach
    void setUp() {
        // 轻量图片（< 1MB）
        image = new MockMultipartFile("file", "photo.jpg", "image/jpeg", TestImages.jpeg());
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
        when(objectStorage.uploadWithKey(eq("admin-uuid"), eq(image), anyString(), eq(1024L * 1024)))
                .thenAnswer(inv -> inv.getArgument(2));
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
        when(objectStorage.uploadWithKey(eq("admin-uuid"), eq(image), anyString(), eq(1024L * 1024)))
                .thenThrow(new ApiException(new J(1, "文件上传失败", null)));
        assertThrows(ApiException.class, () -> backgroundImageService.uploadService("admin-uuid", image, "home", null, null));
        verify(backgroundImageMapper, never()).insert(any());
    }

    @Test
    @DisplayName("背景模块下拉 - 返回全部模块且携带 sceneEnabled 标志")
    void modules_options() {
        ResponseEntity<J> response = backgroundImageService.modulesService("admin-uuid");
        assertEquals(0, response.getBody().getCode());
        @SuppressWarnings("unchecked")
        List<BackgroundModuleOption> list =
                (List<BackgroundModuleOption>) response.getBody().getData();
        assertEquals(BackgroundModuleEnum.values().length, list.size());
        // 首页为唯一启用场景化的模块
        BackgroundModuleOption home = list.stream()
                .filter(opt -> "home".equals(opt.getValue()))
                .findFirst().orElse(null);
        assertNotNull(home);
        assertTrue(home.isSceneEnabled());
        // 登录/注册模块不应启用场景化（纯随机）
        BackgroundModuleOption sign = list.stream()
                .filter(opt -> "sign".equals(opt.getValue()))
                .findFirst().orElse(null);
        assertNotNull(sign);
        assertFalse(sign.isSceneEnabled());
        // 不应出现已废弃的 student 独立模块
        assertTrue(list.stream().noneMatch(opt -> "student".equals(opt.getValue())));
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
        when(objectStorage.uploadWithKey(eq("admin-uuid"), eq(image), anyString(), eq(1024L * 1024)))
                .thenAnswer(inv -> inv.getArgument(2));
        when(backgroundImageMapper.insert(any(BackgroundImageModel.class))).thenReturn(1);
        // 节日改为「节日管理」中的动态数据：节日存在即可绑定
        when(festivalMapper.selectById("fest-1")).thenReturn(enabledFestival("fest-1"));

        ResponseEntity<J> response = backgroundImageService.uploadService("admin-uuid", image, "home", "day", "fest-1");

        assertEquals(0, response.getBody().getCode());
        BackgroundImageModel model = (BackgroundImageModel) response.getBody().getData();
        assertEquals("day", model.getTime_period());
        assertEquals("fest-1", model.getFestival());
    }

    @Test
    @DisplayName("上传背景图 - 非法时段被拒且不上传 COS")
    void upload_invalidTimePeriod() {
        ResponseEntity<J> response = backgroundImageService.uploadService("admin-uuid", image, "home", "bad", null);
        assertEquals(1, response.getBody().getCode());
        verify(cosService, never()).uploadFileWithKey(any(), anyString(), any());
    }

    @Test
    @DisplayName("上传背景图 - 不存在的节日被拒且不上传 COS")
    void upload_invalidFestival() {
        ResponseEntity<J> response = backgroundImageService.uploadService("admin-uuid", image, "home", null, "bad");
        assertEquals(1, response.getBody().getCode());
        verify(cosService, never()).uploadFileWithKey(any(), anyString(), any());
    }

    @Test
    @DisplayName("上传背景图 - 已删除的节日被拒（selectById 查不到）")
    void upload_deletedFestival() {
        // 已删除的节日查不到 → 拒绝绑定（删除时后端已把相关背景图划归「无节日」）
        when(festivalMapper.selectById("fest-gone")).thenReturn(null);
        ResponseEntity<J> response = backgroundImageService.uploadService("admin-uuid", image, "home", null, "fest-gone");
        assertEquals(1, response.getBody().getCode());
        verify(cosService, never()).uploadFileWithKey(any(), anyString(), any());
    }

    @Test
    @DisplayName("上传背景图 - 已关闭但未删除的节日仍可绑定（关闭只影响用户端判定）")
    void upload_disabledFestivalStillBindable() {
        FestivalModel disabled = new FestivalModel();
        disabled.setId("fest-off");
        disabled.setEnabled(false);
        when(festivalMapper.selectById("fest-off")).thenReturn(disabled);
        when(objectStorage.uploadWithKey(eq("admin-uuid"), eq(image), anyString(), eq(1024L * 1024)))
                .thenAnswer(inv -> inv.getArgument(2));
        when(backgroundImageMapper.insert(any(BackgroundImageModel.class))).thenReturn(1);

        ResponseEntity<J> response = backgroundImageService.uploadService("admin-uuid", image, "home", null, "fest-off");

        assertEquals(0, response.getBody().getCode());
        BackgroundImageModel saved = (BackgroundImageModel) response.getBody().getData();
        assertEquals("fest-off", saved.getFestival());
    }

    @Test
    @DisplayName("更新场景 - 记录不存在返回失败")
    void updateScene_notFound() {
        when(backgroundImageMapper.selectById("id1")).thenReturn(null);
        ResponseEntity<J> response = backgroundImageService.updateSceneService("admin-uuid", "id1", "day", "fest-1");
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
    @DisplayName("更新场景 - 合法节日写入节日 ID")
    void updateScene_validFestival() {
        BackgroundImageModel model = new BackgroundImageModel();
        model.setId("id1");
        when(backgroundImageMapper.selectById("id1")).thenReturn(model);
        when(festivalMapper.selectById("fest-1")).thenReturn(enabledFestival("fest-1"));
        when(backgroundImageMapper.updateScene("id1", null, "fest-1")).thenReturn(1);

        ResponseEntity<J> response = backgroundImageService.updateSceneService("admin-uuid", "id1", null, "fest-1");

        assertEquals(0, response.getBody().getCode());
        verify(backgroundImageMapper).updateScene("id1", null, "fest-1");
        BackgroundImageModel saved = (BackgroundImageModel) response.getBody().getData();
        assertEquals("fest-1", saved.getFestival());
    }

    @Test
    @DisplayName("公开接口 - 携带时段与节日标签")
    void publicAll_carriesScene() {
        BackgroundImageModel home = new BackgroundImageModel();
        home.setModule("home");
        home.setObject_key("client/bg/home/A-1.jpg");
        home.setTime_period("day");
        home.setFestival("fest-1");
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
        assertEquals("fest-1", item.getFestival());
    }

    /** 构造一个「存在且启用」的节日，供背景图绑定校验使用 */
    private FestivalModel enabledFestival(String id) {
        FestivalModel festival = new FestivalModel();
        festival.setId(id);
        festival.setEnabled(true);
        return festival;
    }
}
