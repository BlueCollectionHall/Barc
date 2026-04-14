package com.miaoyu.barc.utils;

import com.miaoyu.barc.user.mapper.VerificationCodeMapper;
import com.miaoyu.barc.user.model.VerificationCodeModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class VerifyCodeCleanupService {
    @Autowired
    private VerificationCodeMapper verificationCodeMapper;

    @Scheduled(cron = "0 0 0 ? * MON")
    @Transactional
    public void verifyCodeCleanup() {
        List<VerificationCodeModel> vcs = verificationCodeMapper.selectAll();
        LocalDateTime now = LocalDateTime.now();
        for (VerificationCodeModel vc : vcs) {
            if (vc.getExpiration_at().isBefore(now) || vc.getUsed()) {
                verificationCodeMapper.deleteByUniqueId(vc.getUnique_id());
            }
        }
    }
}
