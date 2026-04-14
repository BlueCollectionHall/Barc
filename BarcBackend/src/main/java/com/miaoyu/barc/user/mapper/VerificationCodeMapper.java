package com.miaoyu.barc.user.mapper;

import com.miaoyu.barc.user.model.VerificationCodeModel;
import org.apache.ibatis.annotations.*;

import java.util.List;

public interface VerificationCodeMapper {
    @Select("SELECT * FROM verification_code")
    List<VerificationCodeModel> selectAll();
    @Insert("INSERT INTO verification_code (unique_id, code, username, scenario, create_at, expiration_at, used) VALUES (#{request.unique_id}, #{request.code}, #{request.username}, #{request.scenario}, NOW(), DATE_ADD(NOW(), INTERVAL #{minute} MINUTE), false)")
    boolean insert(@Param("request") VerificationCodeModel request, @Param("minute") int minute);
    @Select("SELECT * FROM verification_code WHERE unique_id = #{unique_id}")
    VerificationCodeModel selectByUniqueId(@Param("unique_id") String uniqueId);
    @Update("UPDATE verification_code SET used = true WHERE unique_id = #{unique_id}")
    boolean updateUsed(@Param("unique_id") String uniqueId);
    @Delete("DELETE FROM verification_code WHERE unique_id = #{unique_id}")
    boolean deleteByUniqueId(@Param("unique_id") String uniqueId);
}
