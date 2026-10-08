package com.miaoyu.barc.friendlink.service;

import com.miaoyu.barc.api.service.CosGcService;
import com.miaoyu.barc.friendlink.mapper.FriendLinkMapper;
import com.miaoyu.barc.friendlink.model.FriendLinkModel;
import com.miaoyu.barc.utils.J;
import com.miaoyu.barc.utils.tencent.cos.CosBucketConfigEnum;
import com.miaoyu.barc.utils.tencent.cos.CosConfig;
import com.miaoyu.barc.utils.tencent.cos.CosService;
import com.miaoyu.barc.utils.tencent.cos.ImageUrlResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 友情链接服务单元测试（纯校验与逻辑分支，不依赖真实 COS / DB）
 *
 * 覆盖重点：
 * 1、logo 命名规则 friendlink/logo/{图片名}_{uuid第一段}_{秒级时间戳}.扩展名（本次最容易写错的一处）；
 * 2、链接 / 标题 / 简介 / logo_key 的各项校验；
 * 3、上移下移的事务内序号交换与首末位边界（边界时必须「不写库」）；
 * 4、logo 变更与删除时的 GC 入队时机；
 * 5、公开接口只暴露四项字段。
 */
@ExtendWith(MockitoExtension.class)
class FriendLinkServiceTest {

    @Mock
    private FriendLinkMapper friendLinkMapper;
    @Mock
    private CosService cosService;
    @Mock
    private CosConfig cosConfig;
    @Mock
    private CosGcService cosGcService;
    @Mock
    private ImageUrlResolver imageUrlResolver;

    private FriendLinkService friendLinkService;

    private MockMultipartFile image;

    @BeforeEach
    void setUp() {
        friendLinkService = new FriendLinkService();
        // 字段注入（生产用 @Autowired，测试直接反射注入，避免引入 Spring 上下文）
        ReflectionTestUtils.setField(friendLinkService, "friendLinkMapper", friendLinkMapper);
        ReflectionTestUtils.setField(friendLinkService, "cosService", cosService);
        ReflectionTestUtils.setField(friendLinkService, "cosConfig", cosConfig);
        ReflectionTestUtils.setField(friendLinkService, "cosGcService", cosGcService);
        ReflectionTestUtils.setField(friendLinkService, "imageUrlResolver", imageUrlResolver);

        image = new MockMultipartFile("file", "Photo.png", "image/png", new byte[]{1, 2, 3});
    }

    // ============ 上传 ============

    @Test
    @DisplayName("上传 logo - 空文件被拒")
    void upload_emptyFile() {
        MockMultipartFile empty = new MockMultipartFile("file", "a.png", "image/png", new byte[0]);
        ResponseEntity<J> response = friendLinkService.uploadService("admin-uuid", empty);
        assertEquals(1, response.getBody().getCode());
        verify(cosService, never()).uploadFileWithKey(any(), anyString(), any());
    }

    @Test
    @DisplayName("上传 logo - 超过 1MB 被拒")
    void upload_tooLarge() {
        MockMultipartFile large = new MockMultipartFile(
                "file", "big.png", "image/png", new byte[1024 * 1024 + 1]);
        ResponseEntity<J> response = friendLinkService.uploadService("admin-uuid", large);
        assertEquals(1, response.getBody().getCode());
        verify(cosService, never()).uploadFileWithKey(any(), anyString(), any());
    }

    @Test
    @DisplayName("上传 logo - 非图片类型被拒")
    void upload_notImage() {
        MockMultipartFile txt = new MockMultipartFile(
                "file", "note.txt", "text/plain", "hello".getBytes());
        ResponseEntity<J> response = friendLinkService.uploadService("admin-uuid", txt);
        assertEquals(1, response.getBody().getCode());
        verify(cosService, never()).uploadFileWithKey(any(), anyString(), any());
    }

    @Test
    @DisplayName("上传 logo - 成功，命名符合 friendlink/logo/{图片名}_{uuid第一段}_{秒级}.扩展名")
    void upload_success() {
        when(cosService.uploadFileWithKey(eq(image), anyString(), eq(CosBucketConfigEnum.image)))
                .thenAnswer(inv -> new J(0, "文件上传成功", inv.getArgument(1)));

        ResponseEntity<J> response = friendLinkService.uploadService("admin-uuid", image);

        assertEquals(0, response.getBody().getCode());
        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(cosService).uploadFileWithKey(eq(image), captor.capture(), eq(CosBucketConfigEnum.image));
        String objectKey = captor.getValue();
        assertTrue(objectKey.startsWith("friendlink/logo/"), objectKey);
        assertTrue(objectKey.endsWith(".png"), objectKey);
        // 图片名保留原文、uuid 第一段为 8 位大写十六进制、时间戳为秒级 10 位
        assertTrue(objectKey.matches("friendlink/logo/Photo_[0-9A-F]{8}_\\d{10}\\.png"), objectKey);
    }

    @Test
    @DisplayName("上传 logo - 文件名含路径与非法字符时被清洗")
    void upload_sanitizeFileName() {
        MockMultipartFile messy = new MockMultipartFile(
                "file", "C:\\Users\\me\\My Cover!.png", "image/png", new byte[]{1});
        when(cosService.uploadFileWithKey(eq(messy), anyString(), eq(CosBucketConfigEnum.image)))
                .thenAnswer(inv -> new J(0, "文件上传成功", inv.getArgument(1)));

        friendLinkService.uploadService("admin-uuid", messy);

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(cosService).uploadFileWithKey(eq(messy), captor.capture(), eq(CosBucketConfigEnum.image));
        assertTrue(captor.getValue().matches("friendlink/logo/My_Cover_[0-9A-F]{8}_\\d{10}\\.png"),
                captor.getValue());
    }

    @Test
    @DisplayName("上传 logo - 无扩展名时不拼点号")
    void upload_noExtension() {
        MockMultipartFile noExt = new MockMultipartFile(
                "file", "cover", "image/png", new byte[]{1});
        when(cosService.uploadFileWithKey(eq(noExt), anyString(), eq(CosBucketConfigEnum.image)))
                .thenAnswer(inv -> new J(0, "文件上传成功", inv.getArgument(1)));

        friendLinkService.uploadService("admin-uuid", noExt);

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(cosService).uploadFileWithKey(eq(noExt), captor.capture(), eq(CosBucketConfigEnum.image));
        assertTrue(captor.getValue().matches("friendlink/logo/cover_[0-9A-F]{8}_\\d{10}"), captor.getValue());
    }

    @Test
    @DisplayName("上传 logo - 图片名清洗后为空时回落为 logo")
    void upload_emptyBaseNameFallsBackToLogo() {
        MockMultipartFile weird = new MockMultipartFile(
                "file", "   .png", "image/png", new byte[]{1});
        when(cosService.uploadFileWithKey(eq(weird), anyString(), eq(CosBucketConfigEnum.image)))
                .thenAnswer(inv -> new J(0, "文件上传成功", inv.getArgument(1)));

        friendLinkService.uploadService("admin-uuid", weird);

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(cosService).uploadFileWithKey(eq(weird), captor.capture(), eq(CosBucketConfigEnum.image));
        assertTrue(captor.getValue().matches("friendlink/logo/logo_[0-9A-F]{8}_\\d{10}\\.png"), captor.getValue());
    }

    @Test
    @DisplayName("上传 logo - COS 上传失败时原样透出错误")
    void upload_cosFail() {
        when(cosService.uploadFileWithKey(eq(image), anyString(), eq(CosBucketConfigEnum.image)))
                .thenReturn(new J(1, "文件上传失败", null));
        ResponseEntity<J> response = friendLinkService.uploadService("admin-uuid", image);
        assertEquals(1, response.getBody().getCode());
    }

    // ============ 列表 ============

    @Test
    @DisplayName("管理端列表 - 逐条把 logo_key 签名成 logo_url")
    void list_resolvesLogoUrl() {
        FriendLinkModel withLogo = model("id1", "A 站", "https://a.com", "friendlink/logo/a.png", 1);
        FriendLinkModel withoutLogo = model("id2", "B 站", "https://b.com", null, 2);
        when(friendLinkMapper.selectAll()).thenReturn(List.of(withLogo, withoutLogo));
        when(imageUrlResolver.resolve("friendlink/logo/a.png")).thenReturn("https://signed/a.png");
        when(imageUrlResolver.resolve(null)).thenReturn(null);

        ResponseEntity<J> response = friendLinkService.listService("admin-uuid");

        assertEquals(0, response.getBody().getCode());
        @SuppressWarnings("unchecked")
        List<FriendLinkModel> list = (List<FriendLinkModel>) response.getBody().getData();
        assertEquals(2, list.size());
        assertEquals("https://signed/a.png", list.get(0).getLogo_url());
        assertNull(list.get(1).getLogo_url());
    }

    // ============ 新增 ============

    @Test
    @DisplayName("新增 - 标题缺失被拒")
    void create_blankTitle() {
        ResponseEntity<J> response = friendLinkService.createService(
                "admin-uuid", item("   ", "https://a.com", null, null));
        assertEquals(1, response.getBody().getCode());
        verify(friendLinkMapper, never()).insert(any());
    }

    @Test
    @DisplayName("新增 - 标题超长被拒")
    void create_tooLongTitle() {
        ResponseEntity<J> response = friendLinkService.createService(
                "admin-uuid", item("x".repeat(65), "https://a.com", null, null));
        assertEquals(1, response.getBody().getCode());
    }

    @Test
    @DisplayName("新增 - 链接缺失被拒")
    void create_blankUrl() {
        ResponseEntity<J> response = friendLinkService.createService(
                "admin-uuid", item("A 站", "  ", null, null));
        assertEquals(1, response.getBody().getCode());
    }

    @Test
    @DisplayName("新增 - 链接无 http(s) 前缀被拒")
    void create_urlWithoutScheme() {
        ResponseEntity<J> response = friendLinkService.createService(
                "admin-uuid", item("A 站", "www.a.com", null, null));
        assertEquals(1, response.getBody().getCode());
        verify(friendLinkMapper, never()).insert(any());
    }

    @Test
    @DisplayName("新增 - 链接超长被拒")
    void create_tooLongUrl() {
        ResponseEntity<J> response = friendLinkService.createService(
                "admin-uuid", item("A 站", "https://a.com/" + "x".repeat(512), null, null));
        assertEquals(1, response.getBody().getCode());
    }

    @Test
    @DisplayName("新增 - 简介超长被拒")
    void create_tooLongIntro() {
        ResponseEntity<J> response = friendLinkService.createService(
                "admin-uuid", item("A 站", "https://a.com", null, "x".repeat(256)));
        assertEquals(1, response.getBody().getCode());
    }

    @Test
    @DisplayName("新增 - logo 路径前缀不合法被拒（防越权写其它模块对象）")
    void create_logoKeyPrefixRejected() {
        ResponseEntity<J> response = friendLinkService.createService(
                "admin-uuid", item("A 站", "https://a.com", "client/bg/home/xxx.png", null));
        assertEquals(1, response.getBody().getCode());
        verify(friendLinkMapper, never()).insert(any());
    }

    @Test
    @DisplayName("新增 - 成功：sort_order 取最大值 +1，created_by 为操作者，logo_url 已签名")
    void create_success() {
        when(friendLinkMapper.selectMaxSortOrder()).thenReturn(4);
        when(friendLinkMapper.insert(any(FriendLinkModel.class))).thenReturn(1);
        when(imageUrlResolver.resolve("friendlink/logo/a.png")).thenReturn("https://signed/a.png");

        ResponseEntity<J> response = friendLinkService.createService(
                "admin-uuid", item("A 站", "  https://a.com  ", "friendlink/logo/a.png", "  简介  "));

        assertEquals(0, response.getBody().getCode());
        ArgumentCaptor<FriendLinkModel> captor = ArgumentCaptor.forClass(FriendLinkModel.class);
        verify(friendLinkMapper).insert(captor.capture());
        FriendLinkModel saved = captor.getValue();
        assertEquals(5, saved.getSort_order());
        assertEquals("admin-uuid", saved.getCreated_by());
        assertEquals("A 站", saved.getTitle());
        assertEquals("https://a.com", saved.getUrl());
        assertEquals("简介", saved.getIntro());
        assertTrue(saved.getEnabled());
        assertEquals(32, saved.getId().length());
        assertEquals("https://signed/a.png", saved.getLogo_url());
    }

    @Test
    @DisplayName("新增 - 落库失败返回错误")
    void create_insertFails() {
        when(friendLinkMapper.selectMaxSortOrder()).thenReturn(0);
        when(friendLinkMapper.insert(any(FriendLinkModel.class))).thenReturn(0);

        ResponseEntity<J> response = friendLinkService.createService(
                "admin-uuid", item("A 站", "https://a.com", null, null));

        assertEquals(1, response.getBody().getCode());
    }

    // ============ 修改 ============

    @Test
    @DisplayName("修改 - 记录不存在返回失败")
    void update_notFound() {
        when(friendLinkMapper.selectById("id1")).thenReturn(null);
        ResponseEntity<J> response = friendLinkService.updateService(
                "admin-uuid", "id1", item("A 站", "https://a.com", null, null));
        assertEquals(1, response.getBody().getCode());
        verify(friendLinkMapper, never()).update(any());
    }

    @Test
    @DisplayName("修改 - 校验失败时不写库")
    void update_invalid() {
        when(friendLinkMapper.selectById("id1"))
                .thenReturn(model("id1", "A 站", "https://a.com", null, 1));
        ResponseEntity<J> response = friendLinkService.updateService(
                "admin-uuid", "id1", item("", "https://a.com", null, null));
        assertEquals(1, response.getBody().getCode());
        verify(friendLinkMapper, never()).update(any());
    }

    @Test
    @DisplayName("修改 - logo 变更时旧对象入 GC 队列")
    void update_logoChangedEnqueuesOldKey() {
        // 同一方法内会查询两次：第 1 次拿旧 logo（判断是否需要清理旧对象），第 2 次回显最新记录
        when(friendLinkMapper.selectById("id1"))
                .thenReturn(model("id1", "A 站", "https://a.com", "friendlink/logo/old.png", 1))
                .thenReturn(model("id1", "A 站", "https://a.com", "friendlink/logo/new.png", 1));
        when(cosConfig.getImage()).thenReturn(bucket("image-bucket"));
        when(friendLinkMapper.update(any(FriendLinkModel.class))).thenReturn(1);
        when(imageUrlResolver.resolve("friendlink/logo/new.png")).thenReturn("https://signed/new.png");

        ResponseEntity<J> response = friendLinkService.updateService(
                "admin-uuid", "id1", item("A 站", "https://a.com", "friendlink/logo/new.png", null));

        assertEquals(0, response.getBody().getCode());
        verify(cosGcService).enqueue("image-bucket", "friendlink/logo/old.png");
        ArgumentCaptor<FriendLinkModel> captor = ArgumentCaptor.forClass(FriendLinkModel.class);
        verify(friendLinkMapper).update(captor.capture());
        assertEquals("friendlink/logo/new.png", captor.getValue().getLogo_key());
        FriendLinkModel returned = (FriendLinkModel) response.getBody().getData();
        assertEquals("https://signed/new.png", returned.getLogo_url());
    }

    @Test
    @DisplayName("修改 - logo 未变时不入 GC 队列")
    void update_logoUnchangedNoEnqueue() {
        FriendLinkModel existing = model("id1", "A 站", "https://a.com", "friendlink/logo/a.png", 1);
        when(friendLinkMapper.selectById("id1")).thenReturn(existing);
        when(friendLinkMapper.update(any(FriendLinkModel.class))).thenReturn(1);
        when(imageUrlResolver.resolve("friendlink/logo/a.png")).thenReturn("https://signed/a.png");

        ResponseEntity<J> response = friendLinkService.updateService(
                "admin-uuid", "id1", item("A 站", "https://a.com", "friendlink/logo/a.png", null));

        assertEquals(0, response.getBody().getCode());
        verify(cosGcService, never()).enqueue(anyString(), anyString());
    }

    @Test
    @DisplayName("修改 - 清空 logo 时同样把旧对象入 GC 队列")
    void update_logoClearedEnqueuesOldKey() {
        when(friendLinkMapper.selectById("id1"))
                .thenReturn(model("id1", "A 站", "https://a.com", "friendlink/logo/old.png", 1))
                .thenReturn(model("id1", "A 站", "https://a.com", null, 1));
        when(cosConfig.getImage()).thenReturn(bucket("image-bucket"));
        when(friendLinkMapper.update(any(FriendLinkModel.class))).thenReturn(1);
        when(imageUrlResolver.resolve(null)).thenReturn(null);

        ResponseEntity<J> response = friendLinkService.updateService(
                "admin-uuid", "id1", item("A 站", "https://a.com", "", null));

        assertEquals(0, response.getBody().getCode());
        verify(cosGcService).enqueue("image-bucket", "friendlink/logo/old.png");
    }

    @Test
    @DisplayName("修改 - 更新影响 0 行时返回错误")
    void update_zeroRows() {
        when(friendLinkMapper.selectById("id1"))
                .thenReturn(model("id1", "A 站", "https://a.com", null, 1));
        when(friendLinkMapper.update(any(FriendLinkModel.class))).thenReturn(0);

        ResponseEntity<J> response = friendLinkService.updateService(
                "admin-uuid", "id1", item("A 站", "https://a.com", null, null));

        assertEquals(1, response.getBody().getCode());
    }

    // ============ 上移 / 下移 ============

    @Test
    @DisplayName("上移 - direction 非法被拒")
    void move_invalidDirection() {
        ResponseEntity<J> response = friendLinkService.moveService("admin-uuid", "id2", "left");
        assertEquals(1, response.getBody().getCode());
        verify(friendLinkMapper, never()).selectById(anyString());
    }

    @Test
    @DisplayName("上移 - 记录不存在返回失败")
    void move_notFound() {
        when(friendLinkMapper.selectById("id9")).thenReturn(null);
        ResponseEntity<J> response = friendLinkService.moveService("admin-uuid", "id9", "up");
        assertEquals(1, response.getBody().getCode());
    }

    @Test
    @DisplayName("上移 - 已在最前时返回失败且不写库")
    void move_upAtTop() {
        when(friendLinkMapper.selectById("id1")).thenReturn(model("id1", "A", "https://a.com", null, 1));
        when(friendLinkMapper.selectPrevBySortOrder(1)).thenReturn(null);

        ResponseEntity<J> response = friendLinkService.moveService("admin-uuid", "id1", "up");

        assertEquals(1, response.getBody().getCode());
        // 边界失败用 new J(1, 文案, null) 返回，文案在 msg 上（data 为 null）
        assertTrue(response.getBody().getMsg().contains("最前"), response.getBody().getMsg());
        verify(friendLinkMapper, never()).updateSortOrder(anyString(), anyInt());
    }

    @Test
    @DisplayName("下移 - 已在最后时返回失败且不写库")
    void move_downAtBottom() {
        when(friendLinkMapper.selectById("id2")).thenReturn(model("id2", "B", "https://b.com", null, 2));
        when(friendLinkMapper.selectNextBySortOrder(2)).thenReturn(null);

        ResponseEntity<J> response = friendLinkService.moveService("admin-uuid", "id2", "down");

        assertEquals(1, response.getBody().getCode());
        // 边界失败用 new J(1, 文案, null) 返回，文案在 msg 上（data 为 null）
        assertTrue(response.getBody().getMsg().contains("最后"), response.getBody().getMsg());
        verify(friendLinkMapper, never()).updateSortOrder(anyString(), anyInt());
    }

    @Test
    @DisplayName("上移 - 与相邻一条交换序号并回传完整列表")
    void move_upSwapsOrders() {
        when(friendLinkMapper.selectById("id2")).thenReturn(model("id2", "B", "https://b.com", null, 2));
        when(friendLinkMapper.selectPrevBySortOrder(2)).thenReturn(model("id1", "A", "https://a.com", null, 1));
        when(friendLinkMapper.selectAll()).thenReturn(List.of(
                model("id2", "B", "https://b.com", null, 1),
                model("id1", "A", "https://a.com", null, 2)));
        when(imageUrlResolver.resolve(null)).thenReturn(null);

        ResponseEntity<J> response = friendLinkService.moveService("admin-uuid", "id2", "up");

        assertEquals(0, response.getBody().getCode());
        verify(friendLinkMapper).updateSortOrder("id2", 1);
        verify(friendLinkMapper).updateSortOrder("id1", 2);
        @SuppressWarnings("unchecked")
        List<FriendLinkModel> list = (List<FriendLinkModel>) response.getBody().getData();
        assertEquals(2, list.size());
    }

    @Test
    @DisplayName("下移 - 与相邻一条交换序号（大小写不敏感）")
    void move_downSwapsOrders() {
        when(friendLinkMapper.selectById("id1")).thenReturn(model("id1", "A", "https://a.com", null, 1));
        when(friendLinkMapper.selectNextBySortOrder(1)).thenReturn(model("id2", "B", "https://b.com", null, 2));
        when(friendLinkMapper.selectAll()).thenReturn(List.of(
                model("id2", "B", "https://b.com", null, 1),
                model("id1", "A", "https://a.com", null, 2)));
        when(imageUrlResolver.resolve(null)).thenReturn(null);

        ResponseEntity<J> response = friendLinkService.moveService("admin-uuid", "id1", "DOWN");

        assertEquals(0, response.getBody().getCode());
        verify(friendLinkMapper).updateSortOrder("id1", 2);
        verify(friendLinkMapper).updateSortOrder("id2", 1);
    }

    // ============ 删除 ============

    @Test
    @DisplayName("删除 - 记录不存在返回失败且不入队、不软删")
    void delete_notFound() {
        when(friendLinkMapper.selectById("id1")).thenReturn(null);
        ResponseEntity<J> response = friendLinkService.deleteService("admin-uuid", "id1");
        assertEquals(1, response.getBody().getCode());
        verify(cosGcService, never()).enqueue(anyString(), anyString());
        verify(friendLinkMapper, never()).softDelete(anyString());
    }

    @Test
    @DisplayName("删除 - 有 logo 时入队 GC 并软删")
    void delete_withLogo() {
        when(friendLinkMapper.selectById("id1"))
                .thenReturn(model("id1", "A", "https://a.com", "friendlink/logo/a.png", 1));
        when(cosConfig.getImage()).thenReturn(bucket("image-bucket"));
        when(friendLinkMapper.softDelete("id1")).thenReturn(1);

        ResponseEntity<J> response = friendLinkService.deleteService("admin-uuid", "id1");

        assertEquals(0, response.getBody().getCode());
        verify(cosGcService).enqueue("image-bucket", "friendlink/logo/a.png");
        verify(friendLinkMapper).softDelete("id1");
    }

    @Test
    @DisplayName("删除 - 无 logo 时不入队 GC，仍然软删")
    void delete_withoutLogo() {
        when(friendLinkMapper.selectById("id2")).thenReturn(model("id2", "B", "https://b.com", null, 2));
        when(friendLinkMapper.softDelete("id2")).thenReturn(1);

        ResponseEntity<J> response = friendLinkService.deleteService("admin-uuid", "id2");

        assertEquals(0, response.getBody().getCode());
        verify(cosGcService, never()).enqueue(anyString(), anyString());
        verify(friendLinkMapper).softDelete("id2");
    }

    // ============ 公开接口 ============

    @Test
    @DisplayName("公开接口 - 只返回 title/url/intro/logo_url 四项")
    void publicAll_onlyPublicFields() {
        when(friendLinkMapper.selectAllForPublic()).thenReturn(List.of(
                model("id1", "A 站", "https://a.com", "friendlink/logo/a.png", 1),
                model("id2", "B 站", "https://b.com", null, 2)));
        when(imageUrlResolver.resolve("friendlink/logo/a.png")).thenReturn("https://signed/a.png");
        when(imageUrlResolver.resolve(null)).thenReturn(null);

        J result = friendLinkService.publicAllService();

        assertEquals(0, result.getCode());
        @SuppressWarnings("unchecked")
        List<FriendLinkService.PublicItem> items = (List<FriendLinkService.PublicItem>) result.getData();
        assertEquals(2, items.size());
        assertEquals("A 站", items.get(0).getTitle());
        assertEquals("https://a.com", items.get(0).getUrl());
        assertEquals("https://signed/a.png", items.get(0).getLogo_url());
        assertNull(items.get(1).getLogo_url());
    }

    @Test
    @DisplayName("公开接口 - 无友链时返回空列表而不是 null")
    void publicAll_empty() {
        when(friendLinkMapper.selectAllForPublic()).thenReturn(List.of());
        J result = friendLinkService.publicAllService();
        assertEquals(0, result.getCode());
        @SuppressWarnings("unchecked")
        List<FriendLinkService.PublicItem> items = (List<FriendLinkService.PublicItem>) result.getData();
        assertTrue(items.isEmpty());
    }

    // ============ 测试辅助 ============

    private static FriendLinkModel model(String id, String title, String url, String logoKey, int sortOrder) {
        FriendLinkModel model = new FriendLinkModel();
        model.setId(id);
        model.setTitle(title);
        model.setUrl(url);
        model.setLogo_key(logoKey);
        model.setSort_order(sortOrder);
        model.setEnabled(true);
        return model;
    }

    private static FriendLinkService.FriendLinkItem item(
            String title, String url, String logoKey, String intro) {
        FriendLinkService.FriendLinkItem item = new FriendLinkService.FriendLinkItem();
        item.setTitle(title);
        item.setUrl(url);
        item.setLogo_key(logoKey);
        item.setIntro(intro);
        return item;
    }

    private static CosConfig.CosBucketPojo bucket(String bucketName) {
        CosConfig.CosBucketPojo pojo = new CosConfig.CosBucketPojo();
        pojo.setBucketName(bucketName);
        return pojo;
    }
}
