package com.miaoyu.barc.user.mapper;

import com.miaoyu.barc.user.model.VerificationCodeModel;
import org.apache.ibatis.annotations.*;

import java.util.List;
import java.time.LocalDateTime;

public interface VerificationCodeMapper {
    /** 邮箱+场景锁记录只用于串行签发；同事务中锁定后才作废旧码与生成新码。 */
    @Insert("INSERT INTO verification_issue_guard(email, scenario) VALUES(#{email}, #{scenario}) ON DUPLICATE KEY UPDATE email = email")
    int ensureIssueGuard(@Param("email") String email, @Param("scenario") String scenario);
    /** 频率判断也使用数据库时钟，避免 Java UTC 与数据库本地时区错位。 */
    @Update("UPDATE verification_issue_guard SET issued_at = CURRENT_TIMESTAMP WHERE email = #{email} AND scenario = #{scenario} AND (issued_at IS NULL OR issued_at <= DATE_SUB(CURRENT_TIMESTAMP, INTERVAL 60 SECOND))")
    int tryMarkIssued(@Param("email") String email, @Param("scenario") String scenario);
    @Update("UPDATE verification_code SET used = TRUE WHERE username = #{email} AND scenario = #{scenario} AND used = FALSE")
    int invalidatePrevious(@Param("email") String email, @Param("scenario") String scenario);
    /** 条件消费与账号写入处于同一事务，两个并发请求最多成功一个。 */
    @Update("UPDATE verification_code SET used = TRUE WHERE unique_id = #{id} AND username = #{email} AND scenario = #{scenario} AND code = #{code} AND used = FALSE AND expiration_at > CURRENT_TIMESTAMP")
    int consume(@Param("id") String id, @Param("email") String email, @Param("scenario") String scenario, @Param("code") String code);
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
