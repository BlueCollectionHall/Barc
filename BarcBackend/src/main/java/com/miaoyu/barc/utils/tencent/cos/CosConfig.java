package com.miaoyu.barc.utils.tencent.cos;

import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 腾讯云COS对象存储配置类
 * 不分开发与生产环境
 * 注意application.yaml中配置信息
 * ！！！ 生产环境中秘密ID和密钥必须使用启动变量或环境变量 ！！！
 * */

@Configuration
@ConfigurationProperties(prefix = "tencent.cos")
@Data
public class CosConfig {
    private String secretId;
    private String secretKey;
    private String region;
    private String bucketName;
    private String bucketRegion;
    private String baseUrl;
    private String imagePrefix;
    private String tempPrefix;
    private String customDomain;
    private CosBucketPojo avatar;
    private CosBucketPojo test;
    private CosBucketPojo image;
    @Setter
    @Getter
    public static class CosBucketPojo {
        private String bucketName;
        private String bucketRegion;
        private String baseUrl;
        private String customDomain;
    }
}


