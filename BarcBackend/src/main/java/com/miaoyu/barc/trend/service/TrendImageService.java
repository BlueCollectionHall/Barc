package com.miaoyu.barc.trend.service;

import com.miaoyu.barc.api.file.service.ObjectStorageService;
import com.miaoyu.barc.api.service.CosGcService;
import com.miaoyu.barc.exception.ApiException;
import com.miaoyu.barc.permission.PermissionPolicy;
import com.miaoyu.barc.permission.PermissionConst;
import com.miaoyu.barc.response.*;
import com.miaoyu.barc.trend.mapper.*;
import com.miaoyu.barc.trend.model.*;
import com.miaoyu.barc.user.mapper.UserArchiveMapper;
import com.miaoyu.barc.user.enumeration.UserIdentityEnum;
import com.miaoyu.barc.utils.J;
import com.miaoyu.barc.utils.tencent.cos.CosConfig;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/** 动态图片统一走 COS；新 key 可补偿，旧 MinIO 图片只登记待核查，不在请求中删除。 */
@Service
public class TrendImageService {
    @Autowired private TrendImageMapper images;
    @Autowired private TrendSelectMapper trends;
    @Autowired private UserArchiveMapper users;
    @Autowired private ObjectStorageService storage;
    @Autowired private CosGcService garbage;
    @Autowired private CosConfig config;

    @Transactional
    public ResponseEntity<J> uploadTrendImageService(String uuid, String trendId, MultipartFile file) {
        requireOwner(uuid, trendId);
        List<TrendImageModel> existing = images.selectByTrendId(trendId);
        if (existing.size() >= 20) throw invalid("每条动态最多 20 张图片");
        int next = existing.stream().map(TrendImageModel::getSort).filter(Objects::nonNull).max(Integer::compareTo).orElse(-1) + 1;
        String key = storage.upload(uuid, file, "uploads/" + uuid + "/trend_images/", 10L * 1024 * 1024);
        storage.bind(uuid, key);
        TrendImageModel image = new TrendImageModel(); image.setId(UUID.randomUUID().toString());
        image.setTrend_id(trendId); image.setSort(next); image.setObject_key(key); image.setImage_name(key.substring(key.lastIndexOf('/') + 1));
        if (!images.insert(image)) throw invalid("动态图片记录保存失败");
        return ResponseEntity.ok(new ChangeR().udu(true, 1));
    }

    @Transactional
    public ResponseEntity<J> deleteTrendImageService(String uuid, String imageId) {
        TrendImageModel image = images.selectById(imageId);
        if (image == null) return ResponseEntity.ok(new ResourceR().resourceSuch(false, null));
        requireOwner(uuid, image.getTrend_id());
        retire(image);
        if (!images.delete(imageId)) throw invalid("动态图片删除失败");
        List<TrendImageModel> remaining = images.selectByTrendId(image.getTrend_id());
        remaining.sort(java.util.Comparator.comparing(TrendImageModel::getSort, java.util.Comparator.nullsLast(Integer::compareTo)));
        for (int index = 0; index < remaining.size(); index++) {
            remaining.get(index).setSort(index);
            if (!images.update(remaining.get(index))) throw invalid("动态图片重排失败");
        }
        return ResponseEntity.ok(new ChangeR().udu(true, 2));
    }

    /** 父动态已完成授权及加锁，在相同事务内退役图片引用。 */
    void retireAll(String trendId) {
        for (TrendImageModel image : images.selectByTrendId(trendId)) {
            retire(image);
            if (!images.delete(image.getId())) throw invalid("动态图片删除失败");
        }
    }

    private void retire(TrendImageModel image) {
        if (image.getObject_key() != null) garbage.enqueue(config.getImage().getBucketName(), image.getObject_key());
        else if (image.getImage_name() != null) images.retireLegacy("uploads/" + image.getImage_name());
    }

    private void requireOwner(String uuid, String trendId) {
        TrendModel trend = trends.selectByIdForUpdate(trendId);
        if (trend == null) throw invalid("动态不存在");
        if (uuid == null || (!uuid.equals(trend.getAuthor())
                && !PermissionPolicy.matches(users.selectByUuid(uuid), UserIdentityEnum.MANAGER, PermissionConst.ADMINISTRATOR, false))) {
            throw new ApiException(new UserR().insufficientAccountPermission());
        }
    }

    private ApiException invalid(String message) { return new ApiException(new ErrorR().normal(message)); }
}
