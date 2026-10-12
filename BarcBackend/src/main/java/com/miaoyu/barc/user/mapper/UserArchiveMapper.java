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

    // 用户分页查询（关联两个表）
    //
    // 说明（2026-10 合并）：原先这里还有一条「无条件分页」selectByPage，与下面这条构成两个分支。
    // 但管理端两个页面都恒定携带 identity 筛选（USER / MANAGER），空条件分支实际不可达；
    // 且两条 SQL 的投影字段不一致（旧分支少 age/birthday/gender/safe_level），
    // 造成同一接口的返回结构随参数变化。现合并为单条 SQL：条件为空时 <where> 不生成 WHERE 子句，
    // 退化为全量分页，与旧的无条件分支等价。
    //
    // 注意：不得写成「库名.表名」形式。开发库与生产库同实例不同库名（ba / ba-prod），
    // 一旦带上库名前缀，生产环境会把 user_basic 指到开发库，导致用户名整列为空（跨库读取）。
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
            "   b.safe_level, " +
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
            // gender / age 是 user_archive 自己的列，筛选必须与上面的投影同源（a.）；
            // 历史写法误挂在 user_basic（b.）上：该表没有这两列，一旦传参即 Unknown column 报错，
            // 即便有也会因「按 b 筛、返回 a」而结果错乱。
            "   <if test='condition.gender != null'>" +
            "       AND a.gender = #{condition.gender}" +
            "   </if>" +
            "   <if test='condition.age != null'>" +
            "       AND a.age = #{condition.age}" +
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
            // 与 selectUserInfoByPage 的筛选条件必须逐条保持一致：计数与取数用同一套条件，
            // 否则分页总数与实际列表对不上（gender/age 同理不能再挂到 user_basic 上）。
            "   <if test='condition.gender != null'>" +
            "       AND a.gender = #{condition.gender}" +
            "   </if>" +
            "   <if test='condition.age != null'>" +
            "       AND a.age = #{condition.age}" +
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

    /** 按关键词搜索用户（UUID/用户名/昵称，OR逻辑），限制返回条数 */
    @Select("SELECT a.uuid, a.nickname, a.avatar, b.username FROM user_archive a LEFT JOIN user_basic b ON a.uuid = b.uuid " +
            "WHERE a.uuid LIKE CONCAT('%', #{keyword}, '%') " +
            "OR b.username LIKE CONCAT('%', #{keyword}, '%') " +
            "OR a.nickname LIKE CONCAT('%', #{keyword}, '%') " +
            "LIMIT 15")
    List<UserInfoVo> searchByKeyword(@Param("keyword") String keyword);
}
