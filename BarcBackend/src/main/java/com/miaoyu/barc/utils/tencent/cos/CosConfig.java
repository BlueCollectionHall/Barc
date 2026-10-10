package com.miaoyu.barc.utils.tencent.cos;

import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 腾讯云COS对象存储配置类
 *
 * <p>本类只负责绑定 {@code tencent.cos} 前缀下的配置，不感知运行环境；
 * 开发与生产的差异由 Profile 决定：基础配置（application.yaml）为开发环境，
 * application-prod.yaml 在 prod Profile 下覆写 image / avatar 的
 * {@code bucket-name}（用哪个桶）与 {@code custom_domain}（走哪个域名）。</p>
 *
 * <p>注意：{@code bucket-region}、{@code base-url}、{@code image-prefix}、{@code temp-prefix}
 * 四个历史字段全项目无任何消费点（区域由 {@link CosClient} 统一读取 {@code region}，
 * 域名由 {@code custom_domain} 决定），已随本次环境区分需求一并清理，请勿再加回。</p>
 *
 * <p>！！！ 生产环境中秘密 ID 和密钥必须使用启动变量或环境变量 ！！！</p>
 * */
@Configuration
@ConfigurationProperties(prefix = "tencent.cos")
@Data
public class CosConfig {
    private String secretId;
    private String secretKey;
    private String region;
    /** 顶层兜底桶：仅当调用方传入的桶枚举无法匹配时使用 */
    private String bucketName;
    /** 顶层兜底自定义域名：与兜底桶成对使用，留空则走腾讯云官方端点 */
    private String customDomain;
    private CosBucketPojo avatar;
    private CosBucketPojo test;
    private CosBucketPojo image;
    @Setter
    @Getter
    public static class CosBucketPojo {
        /** 桶名：决定上传 / 删除 / 签名的目标桶 */
        private String bucketName;
        /** 自定义域名：非空时客户端改用该域名作为端点，为空时使用腾讯云官方端点 */
        private String customDomain;
    }
}


