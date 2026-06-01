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

    @Scheduled(fixedDelay = 5 * 60 * 1000)
    public void consume() {
        List<CosGcMapper.CosGcItem> items = cosGcMapper.selectPending(BATCH_SIZE);
        for (CosGcMapper.CosGcItem item : items) {
            try {
                String bucket = item.getBucket();
                String key = item.getObject_key();
                cosClient.cosClient(CosBucketConfigEnum.image)
                        .deleteObject(bucket, key);
                cosGcMapper.markProcessed(item.getId());
                log.info("COS GC succeeded: bucket={}, key={}", bucket, key);
            } catch (Exception e) {
                cosGcMapper.markFailed(item.getId(), e.getMessage());
                log.error("COS GC failed: id={}, key={}, error={}", item.getId(), item.getObject_key(), e.getMessage());
            }
        }
    }
}
