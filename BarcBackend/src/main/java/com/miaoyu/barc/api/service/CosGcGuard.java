package com.miaoyu.barc.api.service;

import com.miaoyu.barc.api.mapper.CosGcMapper;
import com.miaoyu.barc.api.mapper.StorageObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/** 先原子关闭 key 的新引用入口，再执行外部删除；COS 故障时保持 DELETING 可重试。 */
@Service
public class CosGcGuard {
    @Autowired private StorageObjectMapper objects;
    @Autowired private CosGcMapper garbage;

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public boolean claim(CosGcMapper.CosGcItem item) {
        objects.reserve(item.getBucket(), item.getObject_key(), null);
        var object = objects.lock(item.getBucket(), item.getObject_key());
        if ("DELETED".equals(object.getState()) || objects.references(item.getObject_key()) > 0) {
            garbage.markProcessed(item.getId());
            return false;
        }
        objects.setState(item.getBucket(), item.getObject_key(), "DELETING");
        return true;
    }
}
