package com.miaoyu.barc.user.mapper;

import com.miaoyu.barc.user.model.UserArchiveModel;
import com.miaoyu.barc.user.model.vo.UserInfoVo;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;
import java.util.Map;

public interface UserArchiveMapper {
    // 获取全部用户信息
    @Select("SELECT uuid, nickname, permission, identity, updated_at FROM user_archive")
    List<UserArchiveModel> selectAll();

    // 无条件分页查询方法
    @Select("SELECT a.uuid, a.nickname, b.username, a.avatar, a.permission, a.identity, a.updated_at FROM user_archive a LEFT JOIN ba.user_basic b ON a.uuid = b.uuid LIMIT #{offset}, #{pageSize}")
    List<UserInfoVo> selectByPage(@Param("offset") int offset, @Param("pageSize") int pageSize);

    // 分页查询用户信息（关联两个表）
    @Select("<script>" +
            "SELECT " +
            "   a.uuid, " +
            "   b.username, " +
            "   a.nickname, " +
            "   a.avatar, " +
            "   a.age, " +
            "   a.birthday, " +
            "   a.gender, " +
            "   a.identity, " +
            "   a.permission, " +
            "   a.updated_at " +
            "FROM user_archive a " +
            "LEFT JOIN user_basic b ON a.uuid = b.uuid " +
            "<where>" +
            "   <if test='condition.nickname != null and condition.nickname != \"\"'>" +
            "       AND a.nickname LIKE CONCAT('%', #{condition.nickname}, '%')" +
            "   </if>" +
            "   <if test='condition.identity != null'>" +
            "       AND a.identity = #{condition.identity}" +
            "   </if>" +
            "   <if test='condition.permission != null'>" +
            "       AND (a.permission &amp; #{condition.permission}) = #{condition.permission}" +
            "   </if>" +
            "   <if test='condition.username != null and condition.username != \"\"'>" +
            "       AND b.username LIKE CONCAT('%', #{condition.username}, '%')" +
            "   </if>" +
            "   <if test='condition.gender != null'>" +
            "       AND b.gender = #{condition.gender}" +
            "   </if>" +
            "   <if test='condition.age != null'>" +
            "       AND b.age = #{condition.age}" +
            "   </if>" +
            "</where>" +
            "ORDER BY a.updated_at DESC " +
            "LIMIT #{offset}, #{pageSize}" +
            "</script>")
    List<UserInfoVo> selectUserInfoByPage(
            @Param("offset") int offset,
            @Param("pageSize") int pageSize,
            @Param("condition") Map<String, Object> condition);

    // 统计用户信息总数
    @Select("<script>" +
            "SELECT COUNT(*) " +
            "FROM user_archive a " +
            "LEFT JOIN user_basic b ON a.uuid = b.uuid " +
            "<where>" +
            "   <if test='condition.nickname != null and condition.nickname != \"\"'>" +
            "       AND a.nickname LIKE CONCAT('%', #{condition.nickname}, '%')" +
            "   </if>" +
            "   <if test='condition.identity != null'>" +
            "       AND a.identity = #{condition.identity}" +
            "   </if>" +
            "   <if test='condition.permission != null'>" +
            "       AND (a.permission &amp; #{condition.permission}) = #{condition.permission}" +
            "   </if>" +
            "   <if test='condition.username != null and condition.username != \"\"'>" +
            "       AND b.username LIKE CONCAT('%', #{condition.username}, '%')" +
            "   </if>" +
            "   <if test='condition.gender != null'>" +
            "       AND b.gender = #{condition.gender}" +
            "   </if>" +
            "   <if test='condition.age != null'>" +
            "       AND b.age = #{condition.age}" +
            "   </if>" +
            "</where>" +
            "</script>")
    Long countUserInfo(@Param("condition") Map<String, Object> condition);
    // 总数查询方法
    @Select("SELECT COUNT(*) FROM user_archive")
    Long countTotal();

    @Select("SELECT * FROM user_archive WHERE uuid = #{uuid}")
    UserArchiveModel selectByUuid(@Param("uuid") String uuid);
    @Select("SELECT ua.* FROM user_archive ua JOIN user_basic ub ON ua.uuid = ub.uuid WHERE ub.username = #{username}")
    UserArchiveModel selectByUsername(@Param("username") String username);
    @Insert("INSERT INTO user_archive (uuid, nickname, avatar) VALUES (#{uuid}, #{nickname}, #{avatar})")
    boolean insert(UserArchiveModel model);
    @Update("UPDATE user_archive SET nickname = #{nickname}, avatar = #{avatar}, gender = #{gender}, birthday = #{birthday}, age = #{age}, permission = #{permission}, updated_at = #{updated_at} WHERE uuid = #{uuid}")
    boolean update(UserArchiveModel model);

}
