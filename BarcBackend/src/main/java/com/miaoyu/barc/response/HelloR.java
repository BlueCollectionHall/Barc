package com.miaoyu.barc.response;

import com.miaoyu.barc.utils.J;

import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;

public class HelloR {
    public J Hello() {
        OperatingSystemMXBean osBean = ManagementFactory.getPlatformMXBean(OperatingSystemMXBean.class);
        // CPU 核心数
        int cpuCores = osBean.getAvailableProcessors();
        J j = new J();
        j.setCode(0);
        j.setMsg("HelloWorld!");
        j.setData("操作系统: " + System.getProperty("os.name") + " 系统版本: " + System.getProperty("os.version") + " Java 版本: " + System.getProperty("java.version") + " CPU 核心数: " + cpuCores);
        return j;
    }
}
