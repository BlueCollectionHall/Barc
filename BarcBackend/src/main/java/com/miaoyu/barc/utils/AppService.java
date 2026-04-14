package com.miaoyu.barc.utils;

import com.miaoyu.barc.AppConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class AppService {
    private final AppConfig appConfig;

    @Autowired
    public AppService(AppConfig appConfig) {
        this.appConfig = appConfig;
    }

    public String printAppConfig() {
        System.out.println("App Name: " + appConfig.getPasswordKey());
        return null;
    }

    public String getPwdKey(){
        return appConfig.getPasswordKey();
    }
    public String getVersion() {return appConfig.getVersion();}
}
