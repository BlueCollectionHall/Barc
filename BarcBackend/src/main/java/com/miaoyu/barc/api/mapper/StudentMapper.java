package com.miaoyu.barc.api.mapper;

import com.miaoyu.barc.api.model.StudentModel;
import org.apache.ibatis.annotations.*;

import java.util.List;

public interface StudentMapper {

    @Select("SELECT * FROM student WHERE deleted_at IS NULL")
    List<StudentModel> selectAll();

    @Select("SELECT * FROM student WHERE school = #{school_id} AND deleted_at IS NULL")
    List<StudentModel> selectBySchool(@Param("school_id") String schoolId);

    @Select("SELECT * FROM student WHERE club = #{club_id} AND deleted_at IS NULL")
    List<StudentModel> selectByClub(@Param("club_id") String clubId);

    @Select("SELECT * FROM student WHERE id = #{id} AND deleted_at IS NULL")
    StudentModel selectById(@Param("id") String id);

    @Select("<script>" +
            "SELECT * FROM student WHERE club = #{club_id} AND deleted_at IS NULL " +
            "<if test='keyword != null and keyword != \"\"'>" +
            "  AND (cn_name LIKE CONCAT('%',#{keyword},'%') OR jp_name LIKE CONCAT('%',#{keyword},'%') OR kr_name LIKE CONCAT('%',#{keyword},'%') OR en_name LIKE CONCAT('%',#{keyword},'%'))" +
            "</if>" +
            " LIMIT #{offset}, #{size}" +
            "</script>")
    List<StudentModel> selectByPage(@Param("club_id") String clubId, @Param("keyword") String keyword, @Param("offset") int offset, @Param("size") int size);

    @Select("<script>" +
            "SELECT COUNT(*) FROM student WHERE club = #{club_id} AND deleted_at IS NULL " +
            "<if test='keyword != null and keyword != \"\"'>" +
            "  AND (cn_name LIKE CONCAT('%',#{keyword},'%') OR jp_name LIKE CONCAT('%',#{keyword},'%') OR kr_name LIKE CONCAT('%',#{keyword},'%') OR en_name LIKE CONCAT('%',#{keyword},'%'))" +
            "</if>" +
            "</script>")
    long countByKeyword(@Param("club_id") String clubId, @Param("keyword") String keyword);

    @Select("<script>" +
            "SELECT * FROM student WHERE deleted_at IS NULL " +
            "<if test='keyword != null and keyword != \"\"'>" +
            "  AND (cn_name LIKE CONCAT('%',#{keyword},'%') OR jp_name LIKE CONCAT('%',#{keyword},'%') OR kr_name LIKE CONCAT('%',#{keyword},'%') OR en_name LIKE CONCAT('%',#{keyword},'%'))" +
            "</if>" +
            " LIMIT #{offset}, #{size}" +
            "</script>")
    List<StudentModel> selectAllByPage(@Param("keyword") String keyword, @Param("offset") int offset, @Param("size") int size);

    @Select("<script>" +
            "SELECT COUNT(*) FROM student WHERE deleted_at IS NULL " +
            "<if test='keyword != null and keyword != \"\"'>" +
            "  AND (cn_name LIKE CONCAT('%',#{keyword},'%') OR jp_name LIKE CONCAT('%',#{keyword},'%') OR kr_name LIKE CONCAT('%',#{keyword},'%') OR en_name LIKE CONCAT('%',#{keyword},'%'))" +
            "</if>" +
            "</script>")
    long countAllByKeyword(@Param("keyword") String keyword);

    @Insert("INSERT INTO student(id, cn_name, jp_name, kr_name, en_name, introduce, avatar_square, avatar_rectangle, body_image, school, club) " +
            "VALUES(#{id}, #{cn_name}, #{jp_name}, #{kr_name}, #{en_name}, #{introduce}, #{avatar_square}, #{avatar_rectangle}, #{body_image}, #{school}, #{club})")
    int insert(StudentModel student);

    @Update("<script>" +
            "UPDATE student SET " +
            "  cn_name = #{cn_name}," +
            "  jp_name = #{jp_name}," +
            "  kr_name = #{kr_name}," +
            "  introduce = #{introduce}," +
            "  avatar_square = #{avatar_square}," +
            "  avatar_rectangle = #{avatar_rectangle}," +
            "  body_image = #{body_image}," +
            "  school = #{school}," +
            "  club = #{club}" +
            " WHERE id = #{id} AND deleted_at IS NULL" +
            "</script>")
    int update(StudentModel student);

    @Update("UPDATE student SET deleted_at = NOW() WHERE id = #{id} AND deleted_at IS NULL")
    int softDelete(@Param("id") String id);

    @Update("UPDATE student SET avatar_square = #{value} WHERE id = #{id} AND deleted_at IS NULL")
    int updateAvatarSquare(@Param("id") String id, @Param("value") String value);

    @Update("UPDATE student SET avatar_rectangle = #{value} WHERE id = #{id} AND deleted_at IS NULL")
    int updateAvatarRectangle(@Param("id") String id, @Param("value") String value);

    @Update("UPDATE student SET body_image = #{value} WHERE id = #{id} AND deleted_at IS NULL")
    int updateBodyImage(@Param("id") String id, @Param("value") String value);
}
