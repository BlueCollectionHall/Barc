package com.miaoyu.barc.api.file.service;

import com.miaoyu.barc.annotation.RequireUserAndPermissionAnno;
import com.miaoyu.barc.api.mapper.StorageObjectMapper;
import com.miaoyu.barc.api.service.CosGcService;
import com.miaoyu.barc.exception.ApiException;
import com.miaoyu.barc.permission.PermissionConst;
import com.miaoyu.barc.permission.PermissionPolicy;
import com.miaoyu.barc.response.ErrorR;
import com.miaoyu.barc.response.UserR;
import com.miaoyu.barc.user.mapper.UserArchiveMapper;
import com.miaoyu.barc.user.enumeration.UserIdentityEnum;
import com.miaoyu.barc.utils.J;
import com.miaoyu.barc.utils.tencent.cos.CosBucketConfigEnum;
import com.miaoyu.barc.utils.tencent.cos.CosConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/** 通用图片上传保留个人能力，管理资料路径由明确角色和固定字段表限定。 */
@Service
public class FileService {
    @Autowired private ObjectStorageService storage;
    @Autowired private UserArchiveMapper users;
    @Autowired private StorageObjectMapper objects;
    @Autowired private CosGcService garbage;
    @Autowired private CosConfig config;

    @RequireUserAndPermissionAnno({@RequireUserAndPermissionAnno.Check()})
    public ResponseEntity<J> uploadFileService(String uuid, MultipartFile file, String path, CosBucketConfigEnum clientName) {
        if (clientName != null && clientName != CosBucketConfigEnum.image) throw invalid("通用上传仅支持图片桶");
        String requested = path == null ? "" : path.replaceAll("^/+|/+$", "");
        String prefix;
        if (requested.isEmpty() || requested.equals(uuid) || requested.equals("uploads/" + uuid)) {
            prefix = "uploads/" + uuid + "/";
        } else {
            if (!PermissionPolicy.matches(users.selectByUuid(uuid), UserIdentityEnum.MANAGER, PermissionConst.ADMINISTRATOR, false)) {
                throw new ApiException(new UserR().insufficientAccountPermission());
            }
            if (!requested.matches("(schools/[a-z][a-z0-9_]{0,79}/(logo|beautify_logo|bg)|clubs/[a-z][a-z0-9_]{0,79}/(logo|bg)|students/[a-z][a-z0-9_]{0,79}/(avatar_square|avatar_rectangle|body_image))")) {
                throw invalid("上传目录不属于允许的资料字段");
            }
            prefix = requested + "/";
        }
        return ResponseEntity.ok(new J(0, "图片上传成功", storage.upload(uuid, file, prefix, 10L * 1024 * 1024)));
    }

    /** 兼容原删除入口：只允许副馆长以上提交未引用对象的延迟回收，不直接删桶。 */
    @Transactional
    @RequireUserAndPermissionAnno({@RequireUserAndPermissionAnno.Check(identity = UserIdentityEnum.MANAGER, targetPermission = PermissionConst.ADMINISTRATOR, isSuchElseRequire = false)})
    public ResponseEntity<J> forceDeleteFileService(String uuid, String path, String filename) {
        if (path == null || filename == null || filename.contains("/") || filename.contains("\\")) throw invalid("对象路径无效");
        String key = ObjectKeyPolicy.requireKey(path + (path.isEmpty() || path.endsWith("/") ? "" : "/") + filename);
        if (objects.references(key) > 0) throw invalid("该图片仍被使用，不能回收");
        garbage.enqueue(config.getImage().getBucketName(), key);
        return ResponseEntity.ok(new J(0, "已登记回收，保留七天后复核引用", null));
    }

    private ApiException invalid(String message) { return new ApiException(new ErrorR().normal(message)); }
}
