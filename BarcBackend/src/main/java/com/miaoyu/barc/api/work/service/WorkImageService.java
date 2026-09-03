package com.miaoyu.barc.api.work.service;

import com.miaoyu.barc.annotation.RequireSelfOrPermissionAnno;
import com.miaoyu.barc.api.work.mapper.WorkImageMapper;
import com.miaoyu.barc.api.work.mapper.WorkMapper;
import com.miaoyu.barc.api.work.enumeration.WorkReviewStatusEnum;
import com.miaoyu.barc.api.work.enumeration.WorkStatusEnum;
import com.miaoyu.barc.api.work.model.WorkImageModel;
import com.miaoyu.barc.api.work.model.WorkModel;
import com.miaoyu.barc.permission.PermissionConst;
import com.miaoyu.barc.response.ChangeR;
import com.miaoyu.barc.response.ErrorR;
import com.miaoyu.barc.response.ResourceR;
import com.miaoyu.barc.user.enumeration.UserIdentityEnum;
import com.miaoyu.barc.response.UserR;
import com.miaoyu.barc.utils.GenerateUUID;
import com.miaoyu.barc.utils.J;
import com.miaoyu.barc.utils.tencent.cos.CosBucketConfigEnum;
import com.miaoyu.barc.utils.tencent.cos.CosService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
public class WorkImageService {
    @Autowired
    private WorkImageMapper workImageMapper;
    @Autowired
    private WorkMapper workMapper;
    @Autowired
    private CosService cosService;
    @Autowired
    private WorkService workService;

    public ResponseEntity<J> getImagesByWorkService(String workId) {
        WorkModel work = workMapper.selectById(workId);
        if (work == null || work.getStatus() != WorkStatusEnum.PUBLIC
                || work.getReview_status() != WorkReviewStatusEnum.APPROVED) {
            return ResponseEntity.ok(new ErrorR().normal("作品不可访问"));
        }
        List<WorkImageModel> images = workImageMapper.selectByWorkId(workId);
        
        // 1. 收集所有 object_key
        List<String> objectKeys = images.stream()
                .map(WorkImageModel::getObject_key)
                .toList();
        
        // 2. 批量生成签名 URL（使用并行流优化）
        Date expiration = new Date(System.currentTimeMillis() + 60 * 1000);
        List<String> signedUrls = cosService.generateBatchSignedUrl(objectKeys, expiration, CosBucketConfigEnum.image);
        
        // 3. 建立 object_key -> signed_url 映射（防止并行流导致的顺序错位）
        Map<String, String> objectKeyToSignedUrl = new HashMap<>();
        for (int i = 0; i < objectKeys.size(); i++) {
            objectKeyToSignedUrl.put(objectKeys.get(i), signedUrls.get(i));
        }
        
        // 4. 按 sort 排序并返回签名后的 URL 列表
        List<String> urls = images.stream()
                .sorted(Comparator.comparing(WorkImageModel::getSort))
                .map(image -> objectKeyToSignedUrl.get(image.getObject_key()))
                .toList();
        
        return ResponseEntity.ok(new ResourceR().resourceSuch(true, urls));
    }

    /** 作者侧追加内容图：只写 work_image，并按现有最大 sort 追加到末尾 */
    @Transactional
    public ResponseEntity<J> uploadWorkImageService(String uuid, String workId, MultipartFile file) {
        WorkModel work = workMapper.selectById(workId);
        if (work == null) return ResponseEntity.ok(new ResourceR().resourceSuch(false, null));
        if (!Objects.equals(work.getAuthor(), uuid) && !Objects.equals(work.getUploader(), uuid)) {
            return ResponseEntity.ok(new UserR().uuidMismatch());
        }

        final String KEY = "/" + uuid + "/work_images/";
        J uploadResult = cosService.uploadFile(file, KEY, CosBucketConfigEnum.image);
        if (uploadResult == null || uploadResult.getCode() != 0) {
            return ResponseEntity.ok(new ErrorR().normal("上传作品图时出现异常：From Server!"));
        }

        List<WorkImageModel> images = workImageMapper.selectByWorkId(workId);
        int nextSort = images.stream()
                .map(WorkImageModel::getSort)
                .filter(Objects::nonNull)
                .max(Integer::compareTo)
                .map(sort -> sort + 1)
                .orElse(0);
        WorkImageModel image = new WorkImageModel();
        image.setId(new GenerateUUID().getUuid36l());
        image.setWork_id(workId);
        image.setSort(nextSort);
        image.setObject_key(uploadResult.getData().toString());
        if (!workImageMapper.insert(image)) {
            cosService.deleteFile(image.getObject_key(), CosBucketConfigEnum.image);
            return ResponseEntity.ok(new ChangeR().udu(false, 1));
        }
        workService.submitForReview(workId);
        return ResponseEntity.ok(new ChangeR().udu(true, 1));
    }

    @Transactional
    @RequireSelfOrPermissionAnno(identity = UserIdentityEnum.MANAGER, targetPermission = PermissionConst.SEC_MAINTAINER, isHasElseUpper = true)
    public ResponseEntity<J> deleteWorkImageService(String uuid, String authorUuid, String workImageId) {
        WorkImageModel workImage = workImageMapper.selectById(workImageId);
        if (workImage == null) return ResponseEntity.ok(new ResourceR().resourceSuch(false, null));
        int sort = workImage.getSort();
        boolean delete = workImageMapper.delete(workImageId);
        if (delete) {
            List<WorkImageModel> workImages = workImageMapper.selectByWorkId(workImage.getWork_id());
            for (WorkImageModel workImageModel : workImages) {
                if (sort < workImageModel.getSort()) {
                    workImageModel.setSort(workImageModel.getSort() - 1);
                    workImageMapper.update(workImageModel);
                }
            }
            workService.submitForReview(workImage.getWork_id());
            return ResponseEntity.ok(new ChangeR().udu(true, 2));
        }
        return ResponseEntity.ok(new ChangeR().udu(false, 2));
    }
}
