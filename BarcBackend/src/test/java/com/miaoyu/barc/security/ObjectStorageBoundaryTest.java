package com.miaoyu.barc.security;

import com.miaoyu.barc.api.file.service.*;
import com.miaoyu.barc.api.mapper.*;
import com.miaoyu.barc.api.service.CosGcGuard;
import com.miaoyu.barc.exception.ApiException;
import com.miaoyu.barc.utils.J;
import com.miaoyu.barc.utils.tencent.cos.*;
import java.util.Base64;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 不访问 COS：验证上传前记录补偿、真实格式识别、key 所有权及 GC/绑定互斥。 */
@ExtendWith(MockitoExtension.class)
class ObjectStorageBoundaryTest {
    @Mock StorageObjectMapper objects;
    @Mock CosGcMapper garbage;
    @Mock CosService cos;
    @Mock ObjectReservationService reservations;
    @InjectMocks ObjectStorageService storage;
    @InjectMocks CosGcGuard guard;
    @InjectMocks ObjectReservationService reservationService;

    @BeforeEach void config() {
        CosConfig config = new CosConfig(); var image = new CosConfig.CosBucketPojo(); image.setBucketName("image-test"); config.setImage(image);
        ReflectionTestUtils.setField(storage, "config", config); ReflectionTestUtils.setField(reservationService, "config", config);
    }

    @Test void fileHeaderOverridesUntrustedFilenameAndMime() {
        assertEquals("png", ImageInput.extension(png(), 1024));
        assertThrows(ApiException.class, () -> ImageInput.extension(new MockMultipartFile("file", "x.png", "image/png", "<svg onload=x()>".getBytes()), 1024));
        assertThrows(ApiException.class, () -> ImageInput.extension(png(), 1));
        assertThrows(ApiException.class, () -> ImageInput.extension(null, 1));
    }

    @Test void keyValidationPreventsUrlRoundtripAndTraversal() {
        for (String value : new String[]{"https://cos.example/a?q=signature", "../x.png", "a\\b", "a%2fb.png", "a\u0000b", " "}) {
            assertFalse(ObjectKeyPolicy.isKey(value)); assertThrows(ApiException.class, () -> ObjectKeyPolicy.requireKey(value));
        }
        assertEquals("old.png", ObjectKeyPolicy.replacement(null, "old.png"));
        assertEquals("old.png", ObjectKeyPolicy.replacement("old.png", "old.png"));
        assertNull(ObjectKeyPolicy.replacement("", "old.png"));
    }

    @Test void reservationPrecedesUploadAndFailedUploadKeepsRecoveryRecord() {
        when(cos.uploadFileWithKey(any(), anyString(), eq(CosBucketConfigEnum.image))).thenReturn(new J(1, "失败", null), new J(0, "成功", null));
        assertThrows(ApiException.class, () -> storage.upload("owner", png(), "uploads/owner/", 1024));
        String key = storage.upload("owner", png(), "uploads/owner/", 1024);
        assertTrue(key.startsWith("uploads/owner/")); assertTrue(key.endsWith(".png"));
        InOrder order = inOrder(reservations, cos);
        order.verify(reservations).reserve(eq("owner"), anyString()); order.verify(cos).uploadFileWithKey(any(), anyString(), any());
        order.verify(reservations).reserve(eq("owner"), eq(key)); order.verify(cos).uploadFileWithKey(any(), eq(key), any());
    }

    @Test void bindingRequiresMatchingUploaderAndActiveRegistryEntry() {
        assertThrows(ApiException.class, () -> storage.bind("owner", "x.png"));
        var object = new StorageObjectMapper.StoredObject(); object.setOwner_uuid("someone"); object.setState("ACTIVE");
        when(objects.lock("image-test", "x.png")).thenReturn(object);
        assertThrows(ApiException.class, () -> storage.bind("owner", "x.png"));
        object.setOwner_uuid("owner"); object.setState("DELETING"); assertThrows(ApiException.class, () -> storage.bind("owner", "x.png"));
        object.setState("ACTIVE"); storage.bind("owner", "x.png"); storage.bind("owner", null);
    }

    @Test void sharedReferencePreventsGcAndUnreferencedKeyClosesBeforeDeletion() {
        var item = new CosGcMapper.CosGcItem(); item.setId(1L); item.setBucket("image-test"); item.setObject_key("x.png");
        var object = new StorageObjectMapper.StoredObject(); object.setState("ACTIVE"); when(objects.lock("image-test", "x.png")).thenReturn(object);
        when(objects.references("x.png")).thenReturn(2L, 0L);
        assertFalse(guard.claim(item)); verify(objects, never()).setState(any(), any(), any());
        assertTrue(guard.claim(item)); verify(objects).setState("image-test", "x.png", "DELETING");
        object.setState("DELETED"); assertFalse(guard.claim(item));
    }

    @Test void registersCompensationAtomicallyAndRejectsKeyCollision() {
        when(objects.reserve("image-test", "x.png", "owner")).thenReturn(1, 0);
        reservationService.reserve("owner", "x.png"); verify(garbage).enqueue("image-test", "x.png");
        assertThrows(IllegalStateException.class, () -> reservationService.reserve("owner", "x.png"));
    }

    private MockMultipartFile png() {
        return new MockMultipartFile("file", "ignored.html", "image/png", Base64.getDecoder().decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+aVioAAAAASUVORK5CYII="));
    }
}
