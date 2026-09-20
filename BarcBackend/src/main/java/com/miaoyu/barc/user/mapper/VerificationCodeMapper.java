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
    /**
     * 查询指定邮箱在某业务场景下最新的一条验证码记录。
     *
     * <p>用途：同一邮箱重复获取验证码时，用于把旧记录作废，
     * 避免出现「新的 unique_id 搭配旧邮件里的验证码」这种错配组合。</p>
     *
     * @param username 接收验证码的邮箱
     * @param scenario 业务场景（如 Signup、ResetPassword）
     * @return 最新一条记录；从未发送过时返回 null
     */
    @Select("SELECT * FROM verification_code WHERE username = #{username} AND scenario = #{scenario} ORDER BY create_at DESC LIMIT 1")
    VerificationCodeModel selectLatestByUsernameAndScenario(@Param("username") String username, @Param("scenario") String scenario);
    @Update("UPDATE verification_code SET used = true WHERE unique_id = #{unique_id}")
    boolean updateUsed(@Param("unique_id") String uniqueId);
    @Delete("DELETE FROM verification_code WHERE unique_id = #{unique_id}")
    boolean deleteByUniqueId(@Param("unique_id") String uniqueId);
}
