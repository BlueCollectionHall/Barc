package com.miaoyu.barc.user.mapper;

import com.miaoyu.barc.user.model.UserBasicModel;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

public interface UserBasicMapper {
    @Insert("INSERT INTO user_basic (uuid, username, password, password_version, local_password_enabled, email, email_verified, telephone) VALUES (#{uuid}, #{username}, #{password}, COALESCE(#{password_version}, 1), COALESCE(#{local_password_enabled}, TRUE), #{email}, #{email_verified}, #{telephone})")
    boolean insert(UserBasicModel request);
    /** 基础资料更新不再直接改变邮箱或验证状态。 */
    @Update("UPDATE user_basic SET username = #{username}, telephone = #{telephone} WHERE uuid = #{uuid}")
    boolean update(UserBasicModel request);
    /** 改密同时撤销全部旧会话，且开启经过验证的本地登录。 */
    @Update("UPDATE user_basic SET password = #{password}, password_version = 2, local_password_enabled = TRUE, auth_version = auth_version + 1 WHERE uuid = #{uuid}")
    boolean updatePassword(@Param("uuid") String uuid, @Param("password") String password);
    /** 登录升级使用旧哈希作为条件，不能覆盖并发改密；算法升级本身不撤销会话。 */
    @Update("UPDATE user_basic SET password = #{newHash}, password_version = 2 WHERE uuid = #{uuid} AND password = #{oldHash}")
    boolean upgradePassword(@Param("uuid") String uuid, @Param("oldHash") String oldHash, @Param("newHash") String newHash);
    @Update("UPDATE user_basic SET email = #{email}, email_verified = TRUE, auth_version = auth_version + 1 WHERE uuid = #{uuid}")
    boolean updateVerifiedEmail(@Param("uuid") String uuid, @Param("email") String email);
    @Select("SELECT * FROM user_basic WHERE uuid = #{uuid} FOR UPDATE")
    UserBasicModel selectByUuidForUpdate(@Param("uuid") String uuid);
    @Select("SELECT * FROM user_basic WHERE uuid = #{uuid}")
    UserBasicModel selectByUuid(@Param("uuid") String uuid);
    @Select("SELECT * FROM user_basic WHERE username = #{username}")
    UserBasicModel selectByUsername(@Param("username") String username);
    @Select("SELECT * FROM user_basic WHERE email = #{email}")
    UserBasicModel selectByEmail(@Param("email") String email);

    /**
     * 更新用户safe_level
     */
    int updateSafeLevel(@Param("uuid") String uuid, @Param("safeLevel") Integer safeLevel);

    /**
     * 更新用户safe_level_before_ban
     */
    int updateSafeLevelBeforeBan(@Param("uuid") String uuid, @Param("safeLevelBeforeBan") Integer safeLevelBeforeBan);

    /**
     * 查询用户当前safe_level
     */
    Integer selectSafeLevelByUuid(@Param("uuid") String uuid);
}
