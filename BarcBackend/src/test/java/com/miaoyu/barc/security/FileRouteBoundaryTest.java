package com.miaoyu.barc.security;

import com.miaoyu.barc.api.file.service.*;
import com.miaoyu.barc.api.mapper.StorageObjectMapper;
import com.miaoyu.barc.api.service.CosGcService;
import com.miaoyu.barc.exception.ApiException;
import com.miaoyu.barc.user.enumeration.UserIdentityEnum;
import com.miaoyu.barc.user.mapper.UserArchiveMapper;
import com.miaoyu.barc.user.model.UserArchiveModel;
import com.miaoyu.barc.utils.tencent.cos.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 通用上传路径与实体图片目录互相隔离；回收接口只入队且保护仍被引用的对象。 */
@ExtendWith(MockitoExtension.class)
class FileRouteBoundaryTest {
    @Mock ObjectStorageService storage;
    @Mock UserArchiveMapper users;
    @Mock StorageObjectMapper objects;
    @Mock CosGcService garbage;
    @Spy CosConfig config = new CosConfig();
    @InjectMocks FileService service;
    final MockMultipartFile image = new MockMultipartFile("file", "image.png", "image/png", new byte[]{1});

    @BeforeEach void configure() { var bucket = new CosConfig.CosBucketPojo(); bucket.setBucketName("test-image"); config.setImage(bucket); }
    @Test void personalAliasesResolveToOneServerOwnedPrefix() {
        when(storage.upload("u", image, "uploads/u/", 10485760L)).thenReturn("uploads/u/key.png");
        for (String path : new String[]{null, "", "u", "/uploads/u/"}) assertEquals("uploads/u/key.png", service.uploadFileService("u", image, path, null).getBody().getData());
        verifyNoInteractions(users); verify(storage, times(4)).upload("u", image, "uploads/u/", 10485760L);
        assertThrows(ApiException.class, () -> service.uploadFileService("u", image, null, CosBucketConfigEnum.avatar));
    }
    @Test void managementDirectoriesRequireBothIdentityAndSufficientLevel() {
        assertThrows(ApiException.class, () -> service.uploadFileService("u", image, "schools/a/logo", CosBucketConfigEnum.image));
        var manager = new UserArchiveModel(); manager.setIdentity(UserIdentityEnum.MANAGER); manager.setPermission(16); when(users.selectByUuid("u")).thenReturn(manager);
        service.uploadFileService("u", image, "schools/a/logo", CosBucketConfigEnum.image); verify(storage).upload("u", image, "schools/a/logo/", 10485760L);
        for (String path : new String[]{"schools/a/unknown", "uploads/other", "../a", "students/a/avatar_square/child"}) assertThrows(ApiException.class, () -> service.uploadFileService("u", image, path, null));
        manager.setIdentity(UserIdentityEnum.USER); assertThrows(ApiException.class, () -> service.uploadFileService("u", image, "clubs/a/logo", null));
    }
    @Test void deletionProtectsReferencesAndQueuesOnlyCanonicalKeys() {
        assertThrows(ApiException.class, () -> service.forceDeleteFileService("admin", null, "a"));
        assertThrows(ApiException.class, () -> service.forceDeleteFileService("admin", "x", "../a"));
        assertThrows(ApiException.class, () -> service.forceDeleteFileService("admin", "x", "a\\b"));
        when(objects.references("used/a.png")).thenReturn(1L);
        assertThrows(ApiException.class, () -> service.forceDeleteFileService("admin", "used", "a.png")); verifyNoInteractions(garbage);
        assertEquals(0, service.forceDeleteFileService("admin", "unused/", "a.png").getBody().getCode());
        verify(garbage).enqueue("test-image", "unused/a.png");
    }
}
