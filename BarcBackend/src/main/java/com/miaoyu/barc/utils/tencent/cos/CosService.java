package com.miaoyu.barc.utils.tencent.cos;

import com.miaoyu.barc.annotation.RequireUserAndPermissionAnno;
import com.miaoyu.barc.response.ChangeR;
import com.miaoyu.barc.utils.GenerateUUID;
import com.miaoyu.barc.utils.J;
import com.qcloud.cos.COSClient;
import com.qcloud.cos.http.HttpMethodName;
import com.qcloud.cos.model.CopyObjectRequest;
import com.qcloud.cos.model.GeneratePresignedUrlRequest;
import com.qcloud.cos.model.ObjectMetadata;
import com.qcloud.cos.model.PutObjectRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.net.URL;
import java.util.Date;
import java.util.List;

/**
 * 腾讯云COS对象存储业务层
 * ！！！注意数据读写安全性和配置关系！！！
 * ！！！本业务层独立于其它接口性业务层，使用J.class返回信息，code：0正确、1错误！！！
 * */
@Service
@Slf4j
public class CosService {
    @Autowired
    private CosClient cosClient;
    @Autowired
    private CosConfig cosConfig;

    private String resolveBucket(CosBucketConfigEnum clientName) {
        return switch (clientName) {
            case avatar -> cosConfig.getAvatar().getBucketName();
            case image  -> cosConfig.getImage().getBucketName();
            case test   -> cosConfig.getTest().getBucketName();
            default     -> cosConfig.getBucketName();
        };
    }

    /**
     * 上传文件到COS
     * @param file 文件
     * @param key 文件完整路径（*** 不包括文件本身 ***）
     * @return 文件完整路径（包含文件本身）
     */
    public J uploadFile (MultipartFile file, String key, CosBucketConfigEnum clientName) {
        // 文件原始名
        String fileName = file.getOriginalFilename();
        if (fileName == null || fileName.isEmpty()) {
            fileName = new GenerateUUID().getUuid32u().substring(0, 6);
        }
        // 获取扩展名
        String extension = StringUtils.getFilenameExtension(fileName);
        // 生成唯一文件名 路径/时间戳-文件名.扩展名
        String uniqueKeyFilename =
                key + new Date().getTime() + "-" + fileName + (extension != null? "." + extension: "");
        try {
            // 上传COS
            ObjectMetadata meta = new ObjectMetadata();
            meta.setContentLength(file.getSize());
            meta.setContentType(file.getContentType());
            PutObjectRequest putObjectRequest = new PutObjectRequest(
                    resolveBucket(clientName),
                    uniqueKeyFilename,
                    file.getInputStream(),
                    meta
            );
            cosClient.cosClient(clientName).putObject(putObjectRequest);
            // 构建返回路径 （返回COS key，不是签名URL）
            return new J(0, "文件上传成功", uniqueKeyFilename);
        } catch (IOException e) {
            log.error("文件上传失败", e);
            return new J(1, "文件上传失败: " + e.getMessage(), null);
        } catch (Exception e) {
            log.error("COS上传异常", e);
            return new J(1, "COS上传异常: " + e.getMessage(), null);
        }
    }

    /**
     * 删除COS文件
     * @param key 文件完整路径（包括文件本身）
     * @return 删除信息
     */
    public J deleteFile (String key, CosBucketConfigEnum clientName) {
        if (key == null || key.isEmpty()) {
            return new J(1, "文件地址为空", "文件地址为空");
        }
        try {
            cosClient.cosClient(clientName).deleteObject(resolveBucket(clientName), key);
            return new ChangeR().udu(true, 2);
        } catch (Exception e) {
            log.error("删除COS文件失败: {}", key, e);
            return new ChangeR().udu(false, 2);
        }
    }

    /**
     * 移动文件（或从临时目录移动到正式目录）
     * @param sourceKey 源路径
     * @param targetKey 目标路径
     */
    public void moveFile(String sourceKey, String targetKey, CosBucketConfigEnum clientName) {
        try {
            String bucket = resolveBucket(clientName);
            // 复制文件
            CopyObjectRequest copyObjectRequest = new CopyObjectRequest(
                    bucket, sourceKey,
                    bucket, targetKey
            );
            cosClient.cosClient(clientName).copyObject(copyObjectRequest);

            // 删除源文件
            deleteFile(sourceKey, clientName);

        } catch (Exception e) {
            log.error("移动文件失败: {} -> {}", sourceKey, targetKey, e);
            throw new RuntimeException("文件移动失败");
        }
    }

    /**
     * 生成预签名URL
     * @param key 文件完整路径（包括文件本身）
     * @param expiration 过期时间
     * @return 签名后URL
     * */
    public String generateSignedUrl(String key, Date expiration, CosBucketConfigEnum clientName) {
        try {
            COSClient client = cosClient.cosClient(clientName);
            GeneratePresignedUrlRequest request = new GeneratePresignedUrlRequest(resolveBucket(clientName), key, HttpMethodName.GET);
            request.setExpiration(expiration);
            request.putCustomQueryParameter("response-content-disposition", "inline");
            URL url = client.generatePresignedUrl(request);
            return url.toString();
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 批量生成预签名URL
     * 使用 COS SDK 的 generatePresignedUrl 批量生成签名
     * @param keys 文件路径列表
     * @param expiration 过期时间
     * @return 签名后URL列表
     * */
    public List<String> generateBatchSignedUrl(List<String> keys, Date expiration, CosBucketConfigEnum clientName) {
        return keys.parallelStream()
                .map(key -> generateSignedUrl(key, expiration, clientName))
                .toList();
    }
}
