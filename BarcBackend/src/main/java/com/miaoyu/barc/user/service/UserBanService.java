package com.miaoyu.barc.user.service;

import com.miaoyu.barc.response.ErrorR;
import com.miaoyu.barc.response.ResourceR;
import com.miaoyu.barc.response.SuccessR;
import com.miaoyu.barc.user.enumeration.BanTypeEnum;
import com.miaoyu.barc.user.mapper.UserBanRecordMapper;
import com.miaoyu.barc.user.mapper.UserBasicMapper;
import com.miaoyu.barc.user.model.UserBanRecordModel;
import com.miaoyu.barc.user.model.UserBasicModel;
import com.miaoyu.barc.utils.J;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 用户封号服务
 * 
 * 提供封号、解封、查询封号历史等功能
 */
@Slf4j
@Service
public class UserBanService {
    
    @Autowired
    private UserBanRecordMapper userBanRecordMapper;
    
    @Autowired
    private UserBasicMapper userBasicMapper;
    
    /**
     * 封号用户
     * 
     * @param operatorId 操作人ID
     * @param operatorType 操作人类型（0-系统自动，1-管理员）
     * @param userId 被封号用户ID
     * @param banType 封号类型
     * @param reason 封号原因
     * @param durationDays 封号天数（临时封号必填）
     * @return 封号结果
     */
    @Transactional
    public ResponseEntity<J> banUser(String operatorId, int operatorType, String userId, 
                                     int banType, String reason, Integer durationDays) {
        log.info("封号用户，操作人{}，用户{}，类型{}", operatorId, userId, banType);
        
        // 1. 验证封号类型
        BanTypeEnum banTypeEnum;
        try {
            banTypeEnum = BanTypeEnum.fromCode(banType);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.ok(new ErrorR().normal("无效的封号类型"));
        }
        
        // 2. 验证临时封号必须提供天数
        if (banTypeEnum == BanTypeEnum.TEMPORARY_BAN && (durationDays == null || durationDays <= 0)) {
            return ResponseEntity.ok(new ErrorR().normal("临时封号必须提供有效的封号天数"));
        }
        
        // 3. 查询用户当前状态
        UserBasicModel user = userBasicMapper.selectByUuid(userId);
        if (user == null) {
            return ResponseEntity.ok(new ErrorR().normal("用户不存在"));
        }
        
        // 4. 检查用户是否已被封号
        if (user.getSafe_level() != null && user.getSafe_level() < 0) {
            return ResponseEntity.ok(new ErrorR().normal("用户已被封号，不能重复封号"));
        }
        
        // 5. 记录封号前的safe_level
        int safeLevelBeforeBan = user.getSafe_level() != null ? user.getSafe_level() : 0;
        
        // 6. 更新用户表
        int targetSafeLevel = banTypeEnum.getSafeLevelValue();
        userBasicMapper.updateSafeLevelBeforeBan(userId, safeLevelBeforeBan);
        userBasicMapper.updateSafeLevel(userId, targetSafeLevel);
        
        // 7. 创建封号记录
        UserBanRecordModel record = new UserBanRecordModel();
        record.setUserId(userId);
        record.setBanType(banType);
        record.setBanReason(reason);
        record.setBanDurationDays(banTypeEnum == BanTypeEnum.TEMPORARY_BAN ? durationDays : null);
        record.setBannedAt(LocalDateTime.now());
        record.setOperatorId(operatorId);
        record.setOperatorType(operatorType);
        record.setSafeLevelBeforeBan(safeLevelBeforeBan);
        record.setSafeLevelAfterBan(targetSafeLevel);
        userBanRecordMapper.insert(record);
        
        // 8. 返回结果
        Map<String, Object> result = new HashMap<>();
        result.put("recordId", record.getId());
        if (banTypeEnum == BanTypeEnum.TEMPORARY_BAN) {
            LocalDateTime unbanTime = LocalDateTime.now().plusDays(durationDays);
            result.put("unbanTime", unbanTime.toString());
        }
        
        return ResponseEntity.ok(new SuccessR().normal("封号成功"));
    }
    
    /**
     * 解封用户
     * 
     * @param operatorId 操作人ID
     * @param userId 被解封用户ID
     * @param reason 解封原因
     * @return 解封结果
     */
    @Transactional
    public ResponseEntity<J> unbanUser(String operatorId, String userId, String reason) {
        log.info("解封用户，操作人{}，用户{}", operatorId, userId);
        
        // 1. 查询用户当前状态
        UserBasicModel user = userBasicMapper.selectByUuid(userId);
        if (user == null) {
            return ResponseEntity.ok(new ErrorR().normal("用户不存在"));
        }
        
        // 2. 检查用户是否被封号
        if (user.getSafe_level() == null || user.getSafe_level() >= 0) {
            return ResponseEntity.ok(new ErrorR().normal("用户未被封号"));
        }
        
        // 3. 获取封号前的safe_level
        Integer safeLevelBeforeBan = user.getSafe_level_before_ban();
        if (safeLevelBeforeBan == null) {
            safeLevelBeforeBan = 0; // 默认恢复到0
        }
        
        // 4. 恢复用户safe_level
        userBasicMapper.updateSafeLevel(userId, safeLevelBeforeBan);
        userBasicMapper.updateSafeLevelBeforeBan(userId, null);
        
        // 5. 更新封号记录
        UserBanRecordModel latestRecord = userBanRecordMapper.selectLatestByUserId(userId);
        if (latestRecord != null) {
            userBanRecordMapper.updateUnbannedAt(latestRecord.getId(), LocalDateTime.now(), reason);
        }
        
        // 6. 返回结果
        Map<String, Object> result = new HashMap<>();
        result.put("safeLevel", safeLevelBeforeBan);
        
        return ResponseEntity.ok(new SuccessR().normal("解封成功"));
    }
    
    /**
     * 查询用户封号历史
     * 
     * @param userId 用户ID
     * @param page 页码
     * @param size 每页大小
     * @return 封号历史列表
     */
    public ResponseEntity<J> getBanHistory(String userId, int page, int size) {
        log.info("查询用户封号历史，用户{}，页码{}，大小{}", userId, page, size);
        
        int offset = (page - 1) * size;
        List<UserBanRecordModel> list = userBanRecordMapper.selectByUserId(userId, offset, size);
        long total = userBanRecordMapper.countByUserId(userId);
        
        Map<String, Object> result = new HashMap<>();
        result.put("list", list);
        result.put("total", total);
        
        return ResponseEntity.ok(new ResourceR().resourceSuch(true, result));
    }
    
    /**
     * 查询用户当前状态
     * 
     * @param userId 用户ID
     * @return 用户状态信息
     */
    public ResponseEntity<J> getUserStatus(String userId) {
        log.info("查询用户状态，用户{}", userId);
        
        UserBasicModel user = userBasicMapper.selectByUuid(userId);
        if (user == null) {
            return ResponseEntity.ok(new ErrorR().normal("用户不存在"));
        }
        
        Map<String, Object> result = new HashMap<>();
        result.put("userId", userId);
        result.put("safeLevel", user.getSafe_level());
        result.put("safeLevelBeforeBan", user.getSafe_level_before_ban());
        result.put("banStatus", BanTypeEnum.getStatusBySafeLevel(user.getSafe_level() != null ? user.getSafe_level() : 0));
        
        // 查询最新封号记录
        UserBanRecordModel latestRecord = userBanRecordMapper.selectLatestByUserId(userId);
        if (latestRecord != null) {
            result.put("banReason", latestRecord.getBanReason());
            result.put("banTime", latestRecord.getBannedAt());
            if (latestRecord.getBanDurationDays() != null) {
                LocalDateTime unbanTime = latestRecord.getBannedAt().plusDays(latestRecord.getBanDurationDays());
                result.put("unbanTime", unbanTime);
            }
        }
        
        return ResponseEntity.ok(new ResourceR().resourceSuch(true, result));
    }
    
    /**
     * 自动解封已到期的临时封号用户
     * 
     * @return 解封数量
     */
    @Transactional
    public int unbanExpiredUsers() {
        log.info("自动解封已到期的临时封号用户");
        
        List<UserBanRecordModel> expiredRecords = userBanRecordMapper.selectExpiredRecords();
        int count = 0;
        
        for (UserBanRecordModel record : expiredRecords) {
            // 恢复用户safe_level
            userBasicMapper.updateSafeLevel(record.getUserId(), record.getSafeLevelBeforeBan());
            userBasicMapper.updateSafeLevelBeforeBan(record.getUserId(), null);
            
            // 更新封号记录
            userBanRecordMapper.updateUnbannedAt(record.getId(), LocalDateTime.now(), "系统自动解封");
            
            count++;
            log.info("自动解封用户{}", record.getUserId());
        }
        
        return count;
    }
}
