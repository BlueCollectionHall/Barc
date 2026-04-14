package com.miaoyu.barc.api.file.service;

import com.miaoyu.barc.annotation.RequireUserAndPermissionAnno;
import com.miaoyu.barc.permission.PermissionConst;
import com.miaoyu.barc.user.enumeration.UserIdentityEnum;
import com.miaoyu.barc.utils.J;
import com.miaoyu.barc.utils.tencent.cos.CosBucketConfigEnum;
import com.miaoyu.barc.utils.tencent.cos.CosService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class FileService {
    @Autowired
    private CosService cosService;

    @RequireUserAndPermissionAnno({@RequireUserAndPermissionAnno.Check()})
    public ResponseEntity<J> uploadFileService(String uuid, MultipartFile file, String path, CosBucketConfigEnum clientName) {
        // 桶内文件夹路径为空 或 非法的根目录
        if (path.isEmpty() || path.equals("/")) {
            path = "/" + uuid + "/";
        }
        // 桶内文件夹路径格式检查
        // 如果结尾没有斜线
        if (!path.endsWith("/")) {
            path += "/";
        }
        // 如果开头没有斜线
        if (!path.startsWith("/")) {
            path = "/" + path;
        }
        // 上传文件
        J j = cosService.uploadFile(file, path, clientName);
        return ResponseEntity.ok(j);
    }

    /**
     * 管理员无视数据库绑定强制删除一条文件
     * */
    @RequireUserAndPermissionAnno({@RequireUserAndPermissionAnno.Check(identity = UserIdentityEnum.MANAGER, targetPermission = PermissionConst.FIR_MAINTAINER, isSuchElseRequire = false)})
    public ResponseEntity<J> forceDeleteFileService(String uuid, String path, String filename) {
        return ResponseEntity.ok(cosService.deleteFile(path + filename, null));
    }
}
