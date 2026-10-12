package com.miaoyu.barc.api.file.service;

import com.miaoyu.barc.api.mapper.StorageObjectMapper;
import com.miaoyu.barc.api.mapper.CosGcMapper;
import com.miaoyu.barc.utils.tencent.cos.CosConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** 上传前独立持久化补偿依据：后续业务回滚也不会遗失已上传对象的回收任务。 */
@Service
public class ObjectReservationService {
    @Autowired private StorageObjectMapper objects;
    @Autowired private CosGcMapper garbage;
    @Autowired private CosConfig config;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void reserve(String owner, String key) {
        String bucket = config.getImage().getBucketName();
        if (objects.reserve(bucket, key, owner) != 1) throw new IllegalStateException("上传对象标识冲突");
        garbage.enqueue(bucket, key);
    }
}
