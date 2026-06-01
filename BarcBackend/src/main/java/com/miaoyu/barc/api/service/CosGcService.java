package com.miaoyu.barc.api.service;

import com.miaoyu.barc.api.mapper.CosGcMapper;
import com.miaoyu.barc.utils.tencent.cos.CosService;
import com.qcloud.cos.COSClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class CosGcService {
    @Autowired
    private CosGcMapper cosGcMapper;

    public void enqueue(String bucket, String objectKey) {
        if (objectKey == null || objectKey.isEmpty()) return;
        cosGcMapper.enqueue(bucket, objectKey);
        log.info("Enqueued COS garbage: bucket={}, key={}", bucket, objectKey);
    }
}
