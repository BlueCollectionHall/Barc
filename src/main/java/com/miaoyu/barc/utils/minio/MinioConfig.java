package com.miaoyu.barc.utils.minio;

import io.minio.MinioClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MinioConfig {
    @Bean
    public MinioClient minioClient() {
        return MinioClient.builder()
//                .endpoint("http://82.157.185.26:52015")
                .endpoint("https://file.naigos.cn:52011")
                .credentials("admin", "chajiminio2001")
                .build();
    }
}
