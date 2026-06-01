package com.miaoyu.barc.user.mapper;

import com.miaoyu.barc.user.model.UserBasicModel;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

public interface UserBasicMapper {
    @Insert("INSERT INTO user_basic (uuid, username, password, email, email_verified, telephone) VALUES (#{uuid}, #{username}, #{password}, #{email}, #{email_verified}, #{telephone})")
    boolean insert(UserBasicModel request);
    @Update("UPDATE user_basic SET username = #{username}, email = #{email}, telephone = #{telephone} WHERE uuid = #{uuid}")
    boolean update(UserBasicModel request);
    @Update("UPDATE user_basic SET password = #{password} WHERE uuid = #{uuid}")
    boolean updatePassword(@Param("uuid") String uuid, @Param("password") String password);
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
