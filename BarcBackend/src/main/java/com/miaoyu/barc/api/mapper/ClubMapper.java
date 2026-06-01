package com.miaoyu.barc.api.mapper;

import com.miaoyu.barc.api.model.SchoolClubModel;
import org.apache.ibatis.annotations.*;

import java.util.List;

public interface ClubMapper {

    @Select("SELECT * FROM club WHERE deleted_at IS NULL")
    List<SchoolClubModel> selectAll();

    @Select("SELECT c.* FROM club c JOIN school_club sc ON c.id = sc.club_id WHERE sc.school_id = #{school_id} AND c.deleted_at IS NULL")
    List<SchoolClubModel> selectBySchool(@Param("school_id") String schoolId);

    @Select("SELECT c.*, sc.school_id as school FROM club c LEFT JOIN school_club sc ON c.id = sc.club_id WHERE c.id = #{id} AND c.deleted_at IS NULL")
    SchoolClubModel selectById(@Param("id") String id);

    @Select("<script>" +
            "SELECT c.* FROM club c JOIN school_club sc ON c.id = sc.club_id " +
            "WHERE sc.school_id = #{school_id} AND c.deleted_at IS NULL " +
            "<if test='keyword != null and keyword != \"\"'>" +
            "  AND (c.cn_name LIKE CONCAT('%',#{keyword},'%') OR c.jp_name LIKE CONCAT('%',#{keyword},'%') OR c.kr_name LIKE CONCAT('%',#{keyword},'%') OR c.en_name LIKE CONCAT('%',#{keyword},'%'))" +
            "</if>" +
            " LIMIT #{offset}, #{size}" +
            "</script>")
    List<SchoolClubModel> selectByPage(@Param("school_id") String schoolId, @Param("keyword") String keyword, @Param("offset") int offset, @Param("size") int size);

    @Select("<script>" +
            "SELECT COUNT(*) FROM club c JOIN school_club sc ON c.id = sc.club_id " +
            "WHERE sc.school_id = #{school_id} AND c.deleted_at IS NULL " +
            "<if test='keyword != null and keyword != \"\"'>" +
            "  AND (c.cn_name LIKE CONCAT('%',#{keyword},'%') OR c.jp_name LIKE CONCAT('%',#{keyword},'%') OR c.kr_name LIKE CONCAT('%',#{keyword},'%') OR c.en_name LIKE CONCAT('%',#{keyword},'%'))" +
            "</if>" +
            "</script>")
    long countByKeyword(@Param("school_id") String schoolId, @Param("keyword") String keyword);

    @Select("<script>" +
            "SELECT c.*, sc.school_id as school FROM club c " +
            "LEFT JOIN school_club sc ON c.id = sc.club_id " +
            "WHERE c.deleted_at IS NULL " +
            "<if test='keyword != null and keyword != \"\"'>" +
            "  AND (c.cn_name LIKE CONCAT('%',#{keyword},'%') OR c.jp_name LIKE CONCAT('%',#{keyword},'%') OR c.kr_name LIKE CONCAT('%',#{keyword},'%') OR c.en_name LIKE CONCAT('%',#{keyword},'%'))" +
            "</if>" +
            " LIMIT #{offset}, #{size}" +
            "</script>")
    List<SchoolClubModel> selectAllByPage(@Param("keyword") String keyword, @Param("offset") int offset, @Param("size") int size);

    @Select("<script>" +
            "SELECT COUNT(*) FROM club c " +
            "LEFT JOIN school_club sc ON c.id = sc.club_id " +
            "WHERE c.deleted_at IS NULL " +
            "<if test='keyword != null and keyword != \"\"'>" +
            "  AND (c.cn_name LIKE CONCAT('%',#{keyword},'%') OR c.jp_name LIKE CONCAT('%',#{keyword},'%') OR c.kr_name LIKE CONCAT('%',#{keyword},'%') OR c.en_name LIKE CONCAT('%',#{keyword},'%'))" +
            "</if>" +
            "</script>")
    long countAllByKeyword(@Param("keyword") String keyword);

    @Insert("INSERT INTO club(id, cn_name, jp_name, kr_name, en_name, logo, bg) " +
            "VALUES(#{id}, #{cn_name}, #{jp_name}, #{kr_name}, #{en_name}, #{logo}, #{bg})")
    int insert(SchoolClubModel club);

    @Update("<script>" +
            "UPDATE club SET " +
            "  cn_name = #{cn_name}," +
            "  jp_name = #{jp_name}," +
            "  kr_name = #{kr_name}," +
            "  logo = #{logo}," +
            "  bg = #{bg}" +
            " WHERE id = #{id} AND deleted_at IS NULL" +
            "</script>")
    int update(SchoolClubModel club);

    @Update("UPDATE club SET deleted_at = NOW() WHERE id = #{id} AND deleted_at IS NULL")
    int softDelete(@Param("id") String id);

    @Update("UPDATE students SET deleted_at = NOW() WHERE club = #{clubId} AND deleted_at IS NULL")
    int softDeleteStudentsByClub(@Param("clubId") String clubId);

    @Insert("INSERT INTO school_club(school_id, club_id) VALUES(#{school_id}, #{club_id}) " +
            "ON DUPLICATE KEY UPDATE school_id = #{school_id}")
    int upsertSchoolClub(@Param("school_id") String schoolId, @Param("club_id") String clubId);

    @Delete("DELETE FROM school_club WHERE club_id = #{club_id}")
    int deleteSchoolClub(@Param("club_id") String clubId);
}
