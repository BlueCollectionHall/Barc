package com.miaoyu.barc;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "jwt")
public class JwtConfig {
  
    private String key;
    private String naigos_key;

    public String getNaigos_key() {
        return naigos_key;
    }

    public void setNaigos_key(String naigos_key) {
        this.naigos_key = naigos_key;
    }

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }
}