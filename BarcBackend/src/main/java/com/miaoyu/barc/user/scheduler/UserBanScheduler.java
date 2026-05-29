package com.miaoyu.barc.user.scheduler;

import com.miaoyu.barc.user.service.UserBanService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 用户封号定时任务
 * 
 * 每小时检查并解封已到期的临时封号用户
 */
@Component
@Slf4j
public class UserBanScheduler {

    @Autowired
    private UserBanService userBanService;

    /**
     * 每小时执行一次，解封已到期的临时封号用户
     */
    @Scheduled(cron = "0 0 * * * ?")
    public void unbanExpiredUsers() {
        log.info("开始执行定时任务：解封已到期的临时封号用户");
        try {
            int count = userBanService.unbanExpiredUsers();
            log.info("定时任务执行完成，共解封{}个用户", count);
        } catch (Exception e) {
            log.error("定时任务执行异常：解封已到期用户失败", e);
        }
    }
}
