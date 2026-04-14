package com.miaoyu.barc.trend.service;

import com.miaoyu.barc.annotation.RequireSelfOrPermissionAnno;
import com.miaoyu.barc.permission.PermissionConst;
import com.miaoyu.barc.response.ChangeR;
import com.miaoyu.barc.trend.mapper.TrendImageMapper;
import com.miaoyu.barc.trend.mapper.TrendSelectMapper;
import com.miaoyu.barc.trend.model.TrendImageModel;
import com.miaoyu.barc.user.enumeration.UserIdentityEnum;
import com.miaoyu.barc.utils.GenerateUUID;
import com.miaoyu.barc.utils.J;
import com.miaoyu.barc.utils.minio.MinioObjects;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Service
public class TrendImageService {
    @Autowired
    private TrendImageMapper trendImageMapper;
    @Autowired
    private MinioObjects minioObjects;

    @RequireSelfOrPermissionAnno(identity = UserIdentityEnum.MANAGER, isHasElseUpper = true, targetPermission = PermissionConst.ADVANCED_ADMINISTRATOR)
    public ResponseEntity<J> deleteTrendImageService(String uuid, String authorUuid, String trendId, TrendImageModel waitDeleteImage) {
        List<TrendImageModel> images = trendImageMapper.selectByTrendId(trendId);
        minioObjects.deleteObject("barctemp", "uploads/" + waitDeleteImage.getImage_name());
        trendImageMapper.delete(waitDeleteImage.getId());
        for (TrendImageModel image : images) {
            if (waitDeleteImage.getSort() < image.getSort()) {
                image.setSort(image.getSort() - 1);
                trendImageMapper.update(image);
            }
        }
        return ResponseEntity.ok(new ChangeR().udu(true, 2));
    }

    @RequireSelfOrPermissionAnno(isHasElseUpper = true, targetPermission = PermissionConst.ADVANCED_ADMINISTRATOR, identity = UserIdentityEnum.MANAGER)
    public ResponseEntity<J> uploadTrendImageService(String uuid, String authorUuid, String trendId, MultipartFile file) {
        List<TrendImageModel> images = trendImageMapper.selectByTrendId(trendId);
        String newFileName = trendId + "_" + file.getOriginalFilename();
        String imageUrl = minioObjects.putObject("barctemp", "uploads/" + newFileName, file);
        if (imageUrl == null) {
            return ResponseEntity.ok(new ChangeR().udu(false, 1));
        }
        TrendImageModel waitUploadImage = new TrendImageModel();
        waitUploadImage.setId(new GenerateUUID().getUuid36l());
        waitUploadImage.setTrend_id(trendId);
        waitUploadImage.setImage_name(newFileName);
        waitUploadImage.setImage_url(imageUrl);
        waitUploadImage.setSort(images.get(images.size() - 1).getSort() + 1);
        trendImageMapper.insert(waitUploadImage);
        return ResponseEntity.ok(new ChangeR().udu(true, 1));
    }
}
