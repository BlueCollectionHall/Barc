package com.miaoyu.barc.utils.tencent.cos;

import com.qcloud.cos.endpoint.EndpointBuilder;
import lombok.Getter;

import java.net.MalformedURLException;
import java.net.URL;

/**
 * 腾讯云COS对象存储自定义
 * 自定义EndpointBuilder
 */
public class CustomEndpointBuilder implements EndpointBuilder {
    
    private final String customDomain;
    @Getter
    private final String protocol;
    
    public CustomEndpointBuilder(String customDomain) throws MalformedURLException {
        URL url = new URL(customDomain);
        this.customDomain = url.getHost();
        this.protocol = url.getProtocol();
    }
    
    @Override
    public String buildGeneralApiEndpoint(String bucketName) {
        // 使用自定义域名构建Endpoint
        return customDomain;
    }
    
    @Override
    public String buildGetServiceApiEndpoint() {
        return customDomain;
    }
}