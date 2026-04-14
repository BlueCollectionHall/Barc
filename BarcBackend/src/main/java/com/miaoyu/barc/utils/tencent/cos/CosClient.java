package com.miaoyu.barc.utils.tencent.cos;

import com.qcloud.cos.COSClient;
import com.qcloud.cos.ClientConfig;
import com.qcloud.cos.auth.BasicCOSCredentials;
import com.qcloud.cos.auth.COSCredentials;
import com.qcloud.cos.region.Region;
import io.micrometer.common.util.StringUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;

import java.net.MalformedURLException;

/**
 * 手动配置腾讯云COS对象存储客户端（Bean对象）
 * 当前是DEV方式
 * ！！！ 生产环境时需要将秘密ID和密钥通过密文解密 ！！！
 * */

@Slf4j
@Configuration
@Component
public class CosClient {
    @Autowired
    private CosConfig cosConfig;

    public COSClient cosClient(CosBucketConfigEnum clientName) {
        COSCredentials cred = new BasicCOSCredentials(
                cosConfig.getSecretId(),
                cosConfig.getSecretKey()
        );
//        log.info("Cos客户端得到clientName：{}", clientName);
        // 如果客户端名是空值
        if (clientName == null) {
            log.info("clientName被判空");
            clientName = CosBucketConfigEnum.test;
        }
        return switch (clientName) {
            case test -> {
                ClientConfig clientConfig = new ClientConfig(new Region(cosConfig.getRegion()));
                // 如果配置了自定义域名
                if (StringUtils.isNotBlank(cosConfig.getTest().getCustomDomain())) {
                    try {
                        CustomEndpointBuilder endpointBuilder = new CustomEndpointBuilder(cosConfig.getTest().getCustomDomain());
                        clientConfig.setEndpointBuilder(endpointBuilder);
                    } catch (MalformedURLException ignored) {}
                }
                yield new COSClient(cred, clientConfig);
            } case avatar -> {
                ClientConfig clientConfig = new ClientConfig(new Region(cosConfig.getRegion()));
                // 如果配置了自定义域名
                if (StringUtils.isNotBlank(cosConfig.getAvatar().getCustomDomain())) {
                    try {
                        CustomEndpointBuilder endpointBuilder = new CustomEndpointBuilder(cosConfig.getAvatar().getCustomDomain());
                        clientConfig.setEndpointBuilder(endpointBuilder);
                    } catch (MalformedURLException ignored) {}
                }
                yield new COSClient(cred, clientConfig);
            } case image -> {
                ClientConfig clientConfig = new ClientConfig(new Region(cosConfig.getRegion()));
                // 如果配置了自定义域名
                if (StringUtils.isNotBlank(cosConfig.getImage().getCustomDomain())) {
                    try {
                        CustomEndpointBuilder endpointBuilder = new CustomEndpointBuilder(cosConfig.getImage().getCustomDomain());
                        clientConfig.setEndpointBuilder(endpointBuilder);
                    } catch (MalformedURLException ignored) {}
                }
                yield new COSClient(cred, clientConfig);
            }
            default -> {
                ClientConfig clientConfig = new ClientConfig(new Region(cosConfig.getRegion()));
                // 如果配置了自定义域名
                if (StringUtils.isNotBlank(cosConfig.getCustomDomain())) {
                    try {
                        CustomEndpointBuilder endpointBuilder = new CustomEndpointBuilder(cosConfig.getCustomDomain());
                        clientConfig.setEndpointBuilder(endpointBuilder);
                    } catch (MalformedURLException ignored) {}
                }
                yield new COSClient(cred, clientConfig);
            }
        };

    }
}
