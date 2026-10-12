package com.miaoyu.barc.api.file.service;

import com.miaoyu.barc.api.mapper.StorageObjectMapper;
import com.miaoyu.barc.exception.ApiException;
import com.miaoyu.barc.response.ErrorR;
import com.miaoyu.barc.utils.tencent.cos.*;
import java.util.Objects;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/** 存储业务封装，复用既有 COS client；对象命名、输入校验和补偿在调用层完成。 */
@Service
public class ObjectStorageService {
    @Autowired private ObjectReservationService reservations;
    @Autowired private StorageObjectMapper objects;
    @Autowired private CosService cos;
    @Autowired private CosConfig config;

    public String upload(String uuid, MultipartFile file, String prefix, long maxBytes) {
        String extension = ImageInput.extension(file, maxBytes);
        String key = ObjectKeyPolicy.requireKey(prefix + UUID.randomUUID() + "." + extension);
        return uploadReserved(uuid, file, key);
    }

    /** 为有既定命名规则的背景/友链保留自定义 key，同时统一类型校验与补偿记录。 */
    public String uploadWithKey(String uuid, MultipartFile file, String key, long maxBytes) {
        ImageInput.extension(file, maxBytes);
        return uploadReserved(uuid, file, ObjectKeyPolicy.requireKey(key));
    }

    private String uploadReserved(String uuid, MultipartFile file, String key) {
        reservations.reserve(uuid, key);
        var result = cos.uploadFileWithKey(file, key, CosBucketConfigEnum.image);
        if (result == null || result.getCode() != 0) throw new ApiException(new ErrorR().normal("图片上传失败，请重试"));
        return key;
    }

    /** 必须在保存业务引用的同一事务中调用；行锁阻止与 GC 并发绑定。 */
    @Transactional
    public void bind(String uuid, String key) {
        if (key == null || key.isBlank()) return;
        ObjectKeyPolicy.requireKey(key);
        var object = objects.lock(config.getImage().getBucketName(), key);
        if (object == null || !"ACTIVE".equals(object.getState()) || !Objects.equals(uuid, object.getOwner_uuid())) {
            throw new ApiException(new ErrorR().normal("图片未由当前账号上传或已进入回收，请重新上传"));
        }
    }

    /** 仅对更换的 key 建立新引用；相同历史引用不做破坏性重写。 */
    public String replace(String uuid, String submitted, String existing) {
        String next = ObjectKeyPolicy.replacement(submitted, existing);
        if (!Objects.equals(next, existing)) bind(uuid, next);
        return next;
    }
}
