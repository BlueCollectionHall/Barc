package com.miaoyu.barc.trend.service;

import com.miaoyu.barc.annotation.RequireSelfOrPermissionAnno;
import com.miaoyu.barc.permission.PermissionConst;
import com.miaoyu.barc.response.ChangeR;
import com.miaoyu.barc.response.ErrorR;
import com.miaoyu.barc.response.ResourceR;
import com.miaoyu.barc.trend.mapper.TrendChangeMapper;
import com.miaoyu.barc.trend.mapper.TrendImageMapper;
import com.miaoyu.barc.trend.mapper.TrendSelectMapper;
import com.miaoyu.barc.trend.model.TrendImageModel;
import com.miaoyu.barc.trend.model.TrendModel;
import com.miaoyu.barc.trend.pojo.TrendPojo;
import com.miaoyu.barc.user.enumeration.UserIdentityEnum;
import com.miaoyu.barc.utils.GenerateUUID;
import com.miaoyu.barc.utils.J;
import com.miaoyu.barc.utils.minio.MinioObjects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Objects;

@Service
public class TrendService {
    private static final Logger log = LoggerFactory.getLogger(TrendService.class);
    @Autowired
    private TrendSelectMapper trendSelectMapper;
    @Autowired
    private TrendChangeMapper trendChangeMapper;
    @Autowired
    private TrendImageMapper trendImageMapper;
    @Autowired
    private MinioObjects minioObjects;

    public ResponseEntity<J> getAllTrendService() {
        return ResponseEntity.ok(new ResourceR().resourceSuch(true, trendSelectMapper.selectAll()));
    }

    public ResponseEntity<J> getNewTrendService(int count) {
        return ResponseEntity.ok(new ResourceR().resourceSuch(true, trendSelectMapper.selectByCount(count)));
    }

    public ResponseEntity<J> getTrendsByUuidService(String uuid) {
        return ResponseEntity.ok(new ResourceR().resourceSuch(true, trendSelectMapper.selectByUuid(uuid)));
    }

    public ResponseEntity<J> getTrendOnlyService(String trendId) {
        TrendModel trend = trendSelectMapper.selectById(trendId);
        TrendPojo trendPojo = new TrendPojo();
        List<TrendImageModel> imageList = trendImageMapper.selectByTrendId(trendId);
        if (!imageList.isEmpty()) {
            imageList.sort(Comparator.comparingInt(TrendImageModel::getSort));
        }
        BeanUtils.copyProperties(trend, trendPojo);
        trendPojo.setImages(imageList);
        return ResponseEntity.ok(new ResourceR().resourceSuch(true, trendPojo));
    }

    @Transactional(rollbackFor = RuntimeException.class)
    public ResponseEntity<J> uploadTrendService(
            String userUuid, TrendModel trendModel, MultipartFile[] files
    ) {
        try {
            String trendUuid = new GenerateUUID().getUuid36l();
            trendModel.setAuthor(userUuid);
            trendModel.setId(trendUuid);
            trendChangeMapper.insert(trendModel);
            if (files != null && files.length > 0) {
                log.info("id[{}] 开始上传图片文件，数量{}", trendUuid, files.length);
                for (int i = 0; i < files.length; i++) {
                    MultipartFile file = files[i];
                    if (file.isEmpty()) {
                        log.warn("id[{}] 第{}个文件是空值，跳过上传", trendUuid, i + 1);
                        continue;
                    }
                    String newFileName = trendUuid + "_" + Objects.requireNonNullElseGet(file.getOriginalFilename(), () -> new Date().getTime());
                    log.info("id[{}] 处理图片文件：{} -> {}", trendUuid, file.getOriginalFilename(), newFileName);
                    String imageUrl = minioObjects.putObject("barctemp", "uploads/" + newFileName, file);
                    if (imageUrl == null) {
                        throw new RuntimeException("文件上传到图床失败");
                    }
                    // 保存图片信息到数据库
                    TrendImageModel trendImageModel = new TrendImageModel();
                    trendImageModel.setId(new GenerateUUID().getUuid36l());
                    trendImageModel.setTrend_id(trendUuid);
                    trendImageModel.setSort(i);
                    trendImageModel.setImage_name(newFileName);
                    trendImageModel.setImage_url(imageUrl);
                    trendImageMapper.insert(trendImageModel);
                }
            }
            return ResponseEntity.ok().body(new ChangeR().udu(true, 1));
        } catch (Exception e) {
            return ResponseEntity.status(401).body(new ChangeR().udu(false, 1));
        }
    }

    @Transactional
    @RequireSelfOrPermissionAnno(authorUuidIndex = 0, targetPermission = PermissionConst.ADMINISTRATOR, identity = UserIdentityEnum.MANAGER)
    public ResponseEntity<J> deleteTrendService(String uuid, String trendId) {
        List<TrendImageModel> images = trendImageMapper.selectByTrendId(trendId);
        for (TrendImageModel image : images) {
            try {
                minioObjects.deleteObject("barctemp", "uploads/" + image.getImage_name());
            } catch (Exception e) {
                throw new RuntimeException("文件从图床删除失败");
            }
        }
        boolean delete = trendChangeMapper.delete(trendId);
        if (delete) {
            return ResponseEntity.ok().body(new ChangeR().udu(true, 2));
        }
        return ResponseEntity.status(401).body(new ChangeR().udu(false, 2));
    }
}
