package com.miaoyu.barc.user.mapper;

import com.miaoyu.barc.user.model.UserBanRecordModel;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 封号记录映射器
 * 
 * 提供封号记录的CRUD操作
 */
@Mapper
public interface UserBanRecordMapper {
    
    /**
     * 插入封号记录
     */
    int insert(UserBanRecordModel record);
    
    /**
     * 根据用户ID查询封号记录（分页）
     */
    List<UserBanRecordModel> selectByUserId(@Param("userId") String userId, 
                                            @Param("offset") int offset, 
                                            @Param("size") int size);
    
    /**
     * 统计用户封号记录总数
     */
    long countByUserId(@Param("userId") String userId);
    
    /**
     * 查询用户最新的封号记录
     */
    UserBanRecordModel selectLatestByUserId(@Param("userId") String userId);
    
    /**
     * 查询已到期的临时封号记录
     */
    List<UserBanRecordModel> selectExpiredRecords();
    
    /**
     * 更新解封时间
     */
    int updateUnbannedAt(@Param("id") Long id, 
                         @Param("unbannedAt") LocalDateTime unbannedAt,
                         @Param("unbanReason") String unbanReason);
}
