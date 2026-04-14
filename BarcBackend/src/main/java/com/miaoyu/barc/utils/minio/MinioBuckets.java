package com.miaoyu.barc.utils.minio;

import io.minio.*;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
public class MinioBuckets {
    @Resource
    private MinioClient minioClient;

    public boolean isBucket(String uuid) {
        String lowUuid = uuid.toLowerCase().replace("-", "");
        try{
            return minioClient.bucketExists(BucketExistsArgs.builder()
                    .bucket(lowUuid).build());
        } catch (Exception e){
            return false;
        }
    }
    /**
     * 查询是否存在Bucket，若存在直接返回结果，否则以uuid小写创建一个新Bucket后再返回结果
     * @param uuid 用户UUID，不分大小写
     * @return Map isBucket是否存在，isNew是否是新建*/
    public Map<String, Object> isBucketOrCreate(String uuid) {
        String lowUuid = uuid.toLowerCase().replace("-", "");
        Map<String, Object> result = new HashMap<>();
        try{
            boolean b = minioClient.bucketExists(BucketExistsArgs.builder().bucket(lowUuid).build());
            if (b){
                result.put("isBucket", true);
                result.put("isNew", false);
            } else {
                minioClient.makeBucket(MakeBucketArgs.builder()
                        .bucket(lowUuid)
                        .build());
                minioClient.setBucketPolicy(SetBucketPolicyArgs.builder()
                        .bucket(lowUuid)
                        .config(this.publicPolicyJson(lowUuid)).build());
                result.put("isBucket", true);
                result.put("isNew", true);
            }
            return result;
        } catch (Exception e){

            result.put("isBucket", false);
            result.put("isNew", false);
//            throw new RuntimeException(e);
            return result;
        }
    }
    public boolean removeBucket(String uuid) {
        String lowUuid = uuid.toLowerCase().replace("-", "");
        try {
            minioClient.removeBucket(RemoveBucketArgs.builder().bucket(lowUuid).build());
            return true;
        } catch (Exception e) {
            return false;
        }

    }
    private String publicPolicyJson(String bucketName) {
        return "{\n" +
                "    \"Version\": \"2012-10-17\",\n" +
                "    \"Statement\": [\n" +
                "        {\n" +
                "            \"Effect\": \"Allow\",\n" +
                "            \"Principal\": {\n" +
                "                \"AWS\": [\n" +
                "                    \"*\"\n" +
                "                ]\n" +
                "            },\n" +
                "            \"Action\": [\n" +
                "                \"s3:GetBucketLocation\",\n" +
                "                \"s3:ListBucket\",\n" +
                "                \"s3:ListBucketMultipartUploads\"\n" +
                "            ],\n" +
                "            \"Resource\": [\n" +
                "                \"arn:aws:s3:::" + bucketName + "\"\n" +
                "            ]\n" +
                "        },\n" +
                "        {\n" +
                "            \"Effect\": \"Allow\",\n" +
                "            \"Principal\": {\n" +
                "                \"AWS\": [\n" +
                "                    \"*\"\n" +
                "                ]\n" +
                "            },\n" +
                "            \"Action\": [\n" +
                "                \"s3:AbortMultipartUpload\",\n" +
                "                \"s3:DeleteObject\",\n" +
                "                \"s3:GetObject\",\n" +
                "                \"s3:ListMultipartUploadParts\",\n" +
                "                \"s3:PutObject\"\n" +
                "            ],\n" +
                "            \"Resource\": [\n" +
                "                \"arn:aws:s3:::" + bucketName + "/*\"\n" +
                "            ]\n" +
                "        }\n" +
                "    ]\n" +
                "}";
    }
}
