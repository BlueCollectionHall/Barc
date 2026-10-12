package com.miaoyu.barc.security;

import com.miaoyu.barc.api.file.service.ObjectStorageService;
import com.miaoyu.barc.api.mapper.CatalogMutationMapper;
import com.miaoyu.barc.background.mapper.BackgroundImageMapper;
import com.miaoyu.barc.background.model.BackgroundImageModel;
import com.miaoyu.barc.background.service.BackgroundImageService;
import com.miaoyu.barc.utils.tencent.cos.ImageUrlResolver;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 背景展示只返回可解析的图片；失败写入不得返回成功状态。 */
@ExtendWith(MockitoExtension.class)
class BackgroundReadContractTest {
    @Mock BackgroundImageMapper images;
    @Mock CatalogMutationMapper locks;
    @Mock ImageUrlResolver resolver;
    @Mock ObjectStorageService storage;
    @InjectMocks BackgroundImageService service;
    @Test void publicModuleKeepsSceneMetadataAndSkipsUnavailableSignature() {
        var active = image("a", "key-a"); var broken = image("b", "key-b");
        when(images.selectEnabledByModule("home")).thenReturn(List.of(active, broken)); when(resolver.resolve("key-a")).thenReturn("signed-a");
        var list = (List<?>) service.publicByModuleService("home").getData(); assertEquals(1, list.size());
        var item = (BackgroundImageService.PublicItem) list.get(0); assertEquals("signed-a", item.getUrl()); assertEquals("night", item.getTime_period()); assertEquals("festival", item.getFestival());
        assertNotEquals(0, service.publicByModuleService("unknown").getCode());
        when(images.selectAllEnabled()).thenReturn(List.of(active, broken)); var grouped = (Map<?, ?>) service.publicAllService().getData(); assertEquals(1, ((List<?>) grouped.get("home")).size());
    }
    @Test void managementListSeparatesStoredKeyAndDisplayUrl() {
        var image = image("a", "key-a"); when(images.selectByModule("home")).thenReturn(List.of(image)); when(resolver.resolve("key-a")).thenReturn("signed-a");
        var list = (List<?>) service.listService("manager", "home").getBody().getData(); assertSame(image, list.get(0)); assertEquals("key-a", image.getObject_key()); assertEquals("signed-a", image.getUrl());
        assertNotEquals(0, service.listService("manager", "unknown").getBody().getCode());
    }
    @Test void enablingAndSceneChangesReturnFailureWhenDatabaseChangesNoRow() {
        assertNotEquals(0, service.setEnabledService("manager", "missing", true).getBody().getCode());
        var image = image("a", "key-a"); when(images.selectById("a")).thenReturn(image);
        assertNotEquals(0, service.setEnabledService("manager", "a", true).getBody().getCode());
        assertNotEquals(0, service.updateSceneService("manager", "a", "night", " ").getBody().getCode()); verify(images).updateScene("a", "night", null);
        verify(locks, times(3)).lock("presentation"); verifyNoInteractions(storage);
    }
    private BackgroundImageModel image(String id, String key) { var value = new BackgroundImageModel(); value.setId(id); value.setModule("home"); value.setObject_key(key); value.setTime_period("night"); value.setFestival("festival"); return value; }
}
