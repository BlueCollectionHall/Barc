package com.miaoyu.barc.api.scheduler;

import com.miaoyu.barc.api.mapper.CosGcMapper;
import com.miaoyu.barc.utils.tencent.cos.CosBucketConfigEnum;
import com.miaoyu.barc.utils.tencent.cos.CosClient;
import com.miaoyu.barc.utils.tencent.cos.CosConfig;
import com.qcloud.cos.COSClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * COS 垃圾回收调度器单元测试
 *
 * <p>锁定 Q4=A1 的行为：删除时统一使用<b>当前环境配置</b>的 image 桶名，
 * 而不是入队时写进 {@code cos_garbage_key.bucket} 的历史桶名。</p>
 *
 * <p>为什么必须这样：开发与生产使用不同桶（{@code dev-miaoyu-barc-image-*}
 * 与 {@code miaoyu-barc-image-*}）。若沿用历史记录里的桶名，当环境配置变更后，
 * 队列中遗留的旧桶名会让删除操作指向错误的桶。</p>
 */
@ExtendWith(MockitoExtension.class)
class CosGcSchedulerTest {

    /** 入队时记录的旧桶名（模拟环境切换前写入的历史数据）。 */
    private static final String RECORDED_BUCKET = "miaoyu-barc-image-1309572720";
    /** 当前环境配置的桶名。 */
    private static final String CONFIGURED_BUCKET = "dev-miaoyu-barc-image-1309572720";

    @Mock
    private CosGcMapper cosGcMapper;
    @Mock
    private CosClient cosClient;
    @Mock
    private COSClient sdkClient;

    private CosGcScheduler cosGcScheduler;

    /**
     * 构造一条队列记录。
     *
     * @param id 记录主键
     * @param bucket 入队时写入的桶名
     * @param objectKey 对象 key
     * @return 队列记录
     */
    private static CosGcMapper.CosGcItem item(long id, String bucket, String objectKey) {
        CosGcMapper.CosGcItem cosGcItem = new CosGcMapper.CosGcItem();
        cosGcItem.setId(id);
        cosGcItem.setBucket(bucket);
        cosGcItem.setObject_key(objectKey);
        return cosGcItem;
    }

    /**
     * 初始化调度器：配置为「开发环境」的 image 桶。
     */
    @BeforeEach
    void setUp() {
        CosConfig cosConfig = new CosConfig();
        CosConfig.CosBucketPojo image = new CosConfig.CosBucketPojo();
        image.setBucketName(CONFIGURED_BUCKET);
        image.setCustomDomain("");
        cosConfig.setImage(image);

        cosGcScheduler = new CosGcScheduler();
        ReflectionTestUtils.setField(cosGcScheduler, "cosGcMapper", cosGcMapper);
        ReflectionTestUtils.setField(cosGcScheduler, "cosClient", cosClient);
        ReflectionTestUtils.setField(cosGcScheduler, "cosConfig", cosConfig);
    }

    @Test
    @DisplayName("删除使用当前配置桶名，而不是队列里的历史桶名")
    void consumeShouldDeleteFromConfiguredBucketInsteadOfRecordedBucket() {
        when(cosGcMapper.selectPending(50))
                .thenReturn(List.of(item(1L, RECORDED_BUCKET, "client/bg/home/old.png")));
        when(cosClient.cosClient(CosBucketConfigEnum.image)).thenReturn(sdkClient);

        cosGcScheduler.consume();

        // 只允许在当前环境配置的桶上删除
        verify(sdkClient).deleteObject(eq(CONFIGURED_BUCKET), eq("client/bg/home/old.png"));
        // 绝不允许用队列里的历史桶名删除（否则会误删另一个环境的对象）
        verify(sdkClient, never()).deleteObject(eq(RECORDED_BUCKET), eq("client/bg/home/old.png"));
        verify(cosGcMapper).markProcessed(1L);
    }

    @Test
    @DisplayName("批量记录：逐条都使用当前配置桶名，并全部标记为已处理")
    void consumeShouldProcessEveryPendingItem() {
        when(cosGcMapper.selectPending(50)).thenReturn(List.of(
                item(1L, RECORDED_BUCKET, "client/bg/home/a.png"),
                item(2L, "test-default-1309572720", "client/bg/home/b.png")
        ));
        when(cosClient.cosClient(CosBucketConfigEnum.image)).thenReturn(sdkClient);

        cosGcScheduler.consume();

        verify(sdkClient).deleteObject(CONFIGURED_BUCKET, "client/bg/home/a.png");
        verify(sdkClient).deleteObject(CONFIGURED_BUCKET, "client/bg/home/b.png");
        verify(cosGcMapper).markProcessed(1L);
        verify(cosGcMapper).markProcessed(2L);
    }

    @Test
    @DisplayName("删除异常：记录失败原因且不标记已处理")
    void consumeShouldMarkFailedWhenDeleteThrows() {
        when(cosGcMapper.selectPending(50))
                .thenReturn(List.of(item(9L, RECORDED_BUCKET, "client/bg/home/broken.png")));
        when(cosClient.cosClient(CosBucketConfigEnum.image)).thenReturn(sdkClient);
        org.mockito.Mockito.doThrow(new RuntimeException("COS 不可用"))
                .when(sdkClient).deleteObject(CONFIGURED_BUCKET, "client/bg/home/broken.png");

        cosGcScheduler.consume();

        verify(cosGcMapper).markFailed(eq(9L), eq("COS 不可用"));
        verify(cosGcMapper, never()).markProcessed(9L);
    }

    @Test
    @DisplayName("队列为空：不触发任何删除调用")
    void consumeShouldDoNothingWhenQueueIsEmpty() {
        when(cosGcMapper.selectPending(50)).thenReturn(List.of());

        cosGcScheduler.consume();

        verify(cosClient, never()).cosClient(org.mockito.ArgumentMatchers.any());
        verify(cosGcMapper, never()).markProcessed(org.mockito.ArgumentMatchers.anyLong());
    }
}
