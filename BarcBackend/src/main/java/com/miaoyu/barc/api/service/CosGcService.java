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
        // 历史 URL 污染只进入人工核查清单，不作为对象 key 交给自动回收。
        if (!com.miaoyu.barc.api.file.service.ObjectKeyPolicy.isKey(objectKey)) return;
        cosGcMapper.enqueue(bucket, objectKey);
        log.info("Enqueued COS garbage: bucket={}, key={}", bucket, objectKey);
    }
}
