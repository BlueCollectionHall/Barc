package com.miaoyu.barc.api.mapper;

import com.miaoyu.barc.api.model.SchoolModel;
import org.apache.ibatis.annotations.*;

import java.util.List;

public interface SchoolMapper {
    /** 写事务锁定实体，防止并发迁移、编辑和删除相互覆盖。 */
    @Select("SELECT * FROM school WHERE id=#{id} AND deleted_at IS NULL FOR UPDATE")
    SchoolModel selectByIdForUpdate(@Param("id") String id);

    /** 主键占用包含软删除行，禁止把已占用标识误报为可用。 */
    @Select("SELECT COUNT(*) FROM school WHERE id = #{id}")
    int countIncludingDeleted(@Param("id") String id);


    @Select("SELECT * FROM school WHERE deleted_at IS NULL")
    List<SchoolModel> selectAll();

    @Select("SELECT * FROM school WHERE id = #{id} AND deleted_at IS NULL")
    SchoolModel selectById(@Param("id") String id);

    @Select("<script>" +
            "SELECT * FROM school WHERE deleted_at IS NULL " +
            "<if test='keyword != null and keyword != \"\"'>" +
            "  AND (cn_name LIKE CONCAT('%',#{keyword},'%') OR jp_name LIKE CONCAT('%',#{keyword},'%') OR kr_name LIKE CONCAT('%',#{keyword},'%') OR en_name LIKE CONCAT('%',#{keyword},'%'))" +
            "</if>" +
            " ORDER BY id ASC LIMIT #{offset}, #{size}" +
            "</script>")
    List<SchoolModel> selectByPage(@Param("keyword") String keyword, @Param("offset") int offset, @Param("size") int size);

    @Select("<script>" +
            "SELECT COUNT(*) FROM school WHERE deleted_at IS NULL " +
            "<if test='keyword != null and keyword != \"\"'>" +
            "  AND (cn_name LIKE CONCAT('%',#{keyword},'%') OR jp_name LIKE CONCAT('%',#{keyword},'%') OR kr_name LIKE CONCAT('%',#{keyword},'%') OR en_name LIKE CONCAT('%',#{keyword},'%'))" +
            "</if>" +
            "</script>")
    long countByKeyword(@Param("keyword") String keyword);

    @Insert("INSERT INTO school(id, cn_name, jp_name, kr_name, en_name, introduce, logo, beautify_logo, bg) " +
            "VALUES(#{id}, #{cn_name}, #{jp_name}, #{kr_name}, #{en_name}, #{introduce}, #{logo}, #{beautify_logo}, #{bg})")
    int insert(SchoolModel school);

    @Update("<script>" +
            "UPDATE school SET " +
            "  cn_name = #{cn_name}," +
            "  jp_name = #{jp_name}," +
            "  kr_name = #{kr_name}," +
            "  introduce = #{introduce}," +
            "  logo = #{logo}," +
            "  beautify_logo = #{beautify_logo}," +
            "  bg = #{bg}" +
            " WHERE id = #{id} AND deleted_at IS NULL" +
            "</script>")
    int update(SchoolModel school);

    @Update("UPDATE school SET deleted_at = NOW() WHERE id = #{id} AND deleted_at IS NULL")
    int softDelete(@Param("id") String id);

    @Update("UPDATE club SET deleted_at = NOW() WHERE id IN (SELECT club_id FROM school_club WHERE school_id = #{schoolId}) AND deleted_at IS NULL")
    int softDeleteClubsBySchool(@Param("schoolId") String schoolId);

    @Update("UPDATE student SET deleted_at = NOW() WHERE school = #{schoolId} AND deleted_at IS NULL")
    int softDeleteStudentsBySchool(@Param("schoolId") String schoolId);

    /** 学园级联删除覆盖所有子项图片，不只回收学园自身的三张图。 */
    @Select("SELECT c.logo FROM club c JOIN school_club sc ON c.id=sc.club_id WHERE sc.school_id=#{school} AND c.deleted_at IS NULL UNION SELECT c.bg FROM club c JOIN school_club sc ON c.id=sc.club_id WHERE sc.school_id=#{school} AND c.deleted_at IS NULL UNION SELECT avatar_square FROM student WHERE school=#{school} AND deleted_at IS NULL UNION SELECT avatar_rectangle FROM student WHERE school=#{school} AND deleted_at IS NULL UNION SELECT body_image FROM student WHERE school=#{school} AND deleted_at IS NULL")
    java.util.List<String> childImageKeys(@Param("school") String school);
}
