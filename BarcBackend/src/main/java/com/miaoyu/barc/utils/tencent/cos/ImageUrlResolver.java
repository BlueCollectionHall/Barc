package com.miaoyu.barc.utils.tencent.cos;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Date;

@Component
public class ImageUrlResolver {
    @Autowired
    private CosService cosService;

    private static final long DEFAULT_EXPIRE_MS = 3600_000; // 1 hour

    public String resolve(String value) {
        if (value == null || value.isEmpty()) return null;
        if (value.startsWith("https://") || value.startsWith("http://")) return value;
        Date expiration = new Date(System.currentTimeMillis() + DEFAULT_EXPIRE_MS);
        return cosService.generateSignedUrl(value, expiration, CosBucketConfigEnum.image);
    }

    public boolean isKey(String value) {
        return value != null && !value.isEmpty() && !value.startsWith("http://") && !value.startsWith("https://");
    }
}
