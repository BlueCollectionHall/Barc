package com.miaoyu.barc.api.scheduler;

import com.miaoyu.barc.api.mapper.*;
import com.miaoyu.barc.api.service.CosGcGuard;
import com.miaoyu.barc.utils.tencent.cos.*;
import com.qcloud.cos.COSClient;
import java.util.List;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import static org.mockito.Mockito.*;

/** 不重定向历史桶；只有开启开关且引用复核通过的对象才能触达 COS 删除。 */
@ExtendWith(MockitoExtension.class)
class CosGcSchedulerTest {
    @Mock CosGcMapper garbage;
    @Mock StorageObjectMapper objects;
    @Mock CosGcGuard guard;
    @Mock CosClient client;
    @Mock COSClient sdk;
    @InjectMocks CosGcScheduler scheduler;

    @BeforeEach void setup() {
        CosConfig config = new CosConfig(); CosConfig.CosBucketPojo image = new CosConfig.CosBucketPojo();
        image.setBucketName("test-image"); config.setImage(image);
        ReflectionTestUtils.setField(scheduler, "cosConfig", config);
    }

    @Test void disabledByDefault() {
        scheduler.consume(); verifyNoInteractions(garbage, client, guard);
    }

    @Test void mismatchedBucketAndUrlNeverReachCos() {
        enable(); when(garbage.selectPending(50)).thenReturn(List.of(item(1, "other-environment", "key.png"), item(2, "test-image", "https://example.com/key")));
        scheduler.consume(); verifyNoInteractions(client, guard);
        verify(garbage).markFailed(eq(1L), anyString()); verify(garbage).markFailed(eq(2L), anyString());
    }

    @Test void referencedObjectIsSkipped() {
        enable(); when(garbage.selectPending(50)).thenReturn(List.of(item(1, "test-image", "key.png")));
        scheduler.consume(); verify(guard).claim(any()); verifyNoInteractions(client);
    }

    @Test void claimedObjectIsDeletedAndFailureIsRetryable() {
        enable(); when(garbage.selectPending(50)).thenReturn(List.of(item(1, "test-image", "a.png"), item(2, "test-image", "b.png")));
        when(guard.claim(any())).thenReturn(true); when(client.cosClient(CosBucketConfigEnum.image)).thenReturn(sdk);
        doNothing().when(sdk).deleteObject("test-image", "a.png");
        doThrow(new IllegalStateException("模拟存储故障")).when(sdk).deleteObject("test-image", "b.png");
        scheduler.consume();
        verify(sdk).deleteObject("test-image", "a.png"); verify(objects).setState("test-image", "a.png", "DELETED"); verify(garbage).markProcessed(1L);
        verify(garbage).markFailed(2L, "模拟存储故障"); verify(garbage, never()).markProcessed(2L);
    }

    private void enable() { ReflectionTestUtils.setField(scheduler, "enabled", true); }
    private CosGcMapper.CosGcItem item(long id, String bucket, String key) {
        var item = new CosGcMapper.CosGcItem(); item.setId(id); item.setBucket(bucket); item.setObject_key(key); return item;
    }
}
