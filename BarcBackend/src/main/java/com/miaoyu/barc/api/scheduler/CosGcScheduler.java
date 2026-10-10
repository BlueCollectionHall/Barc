package com.miaoyu.barc.api.scheduler;

import com.miaoyu.barc.api.mapper.CosGcMapper;
import com.miaoyu.barc.api.service.CosGcService;
import com.miaoyu.barc.utils.tencent.cos.CosBucketConfigEnum;
import com.miaoyu.barc.utils.tencent.cos.CosClient;
import com.miaoyu.barc.utils.tencent.cos.CosConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@Slf4j
public class CosGcScheduler {
    @Autowired
    private CosGcMapper cosGcMapper;
    @Autowired
    private CosClient cosClient;
    @Autowired
    private CosConfig cosConfig;

    private static final int BATCH_SIZE = 50;

    /**
     * 消费 COS 垃圾对象队列（每 5 分钟一批）。
     *
     * <p>桶名来源：以「当前环境配置的 image 桶」为准，<b>刻意忽略</b>入队时写进
     * {@code cos_garbage_key.bucket} 的历史桶名。原因是开发与生产使用不同的桶
     * （dev-miaoyu-barc-image-* 与 miaoyu-barc-image-*），历史记录里的桶名可能属于
     * 另一个环境，直接拿来删除会指向错误目标。数据库列仍保留，仅作审计线索。</p>
     */
    @Scheduled(fixedDelay = 5 * 60 * 1000)
    public void consume() {
        List<CosGcMapper.CosGcItem> items = cosGcMapper.selectPending(BATCH_SIZE);
        for (CosGcMapper.CosGcItem item : items) {
            try {
                String recordedBucket = item.getBucket();
                // 始终使用当前生效配置的 image 桶名，避免用历史（可能是另一个环境的）桶名删错对象
                String bucket = cosConfig.getImage().getBucketName();
                String key = item.getObject_key();
                cosClient.cosClient(CosBucketConfigEnum.image)
                        .deleteObject(bucket, key);
                cosGcMapper.markProcessed(item.getId());
                // 同时打印记录桶名与实际删除桶名，便于排查历史遗留队列项
                log.info("COS GC succeeded: bucket={}, recordedBucket={}, key={}", bucket, recordedBucket, key);
            } catch (Exception e) {
                cosGcMapper.markFailed(item.getId(), e.getMessage());
                log.error("COS GC failed: id={}, key={}, error={}", item.getId(), item.getObject_key(), e.getMessage());
            }
        }
    }
}
