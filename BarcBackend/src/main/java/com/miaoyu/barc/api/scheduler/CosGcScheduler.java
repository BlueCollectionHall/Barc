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
    @Autowired private com.miaoyu.barc.api.service.CosGcGuard gcGuard;
    @Autowired private com.miaoyu.barc.api.mapper.StorageObjectMapper objects;
    // 历史队列需先完成只读核查后由部署配置启用，不随代码发布自动清理已有用户对象。
    @org.springframework.beans.factory.annotation.Value("${barc.storage.gc-enabled:false}")
    private boolean enabled;

    private static final int BATCH_SIZE = 50;

    /** 七天窗口后复核引用；历史桶不匹配的记录只记异常，不跨环境重定向删除。 */
    @Scheduled(fixedDelay = 5 * 60 * 1000)
    public void consume() {
        if (!enabled) return;
        List<CosGcMapper.CosGcItem> items = cosGcMapper.selectPending(BATCH_SIZE);
        for (CosGcMapper.CosGcItem item : items) {
            try {
                String recordedBucket = item.getBucket();
                // 只处理明确属于当前环境的队列记录。
                String bucket = cosConfig.getImage().getBucketName();
                String key = item.getObject_key();
                // 不把其他环境记录重定向到当前桶，同名 key 也可能属于完全不同的对象。
                if (!java.util.Objects.equals(bucket, recordedBucket)
                        || !com.miaoyu.barc.api.file.service.ObjectKeyPolicy.isKey(key)) {
                    cosGcMapper.markFailed(item.getId(), "桶或对象 key 与当前环境不符，需人工核查");
                    continue;
                }
                if (!gcGuard.claim(item)) continue;
                cosClient.cosClient(CosBucketConfigEnum.image)
                        .deleteObject(bucket, key);
                objects.setState(bucket, key, "DELETED");
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
