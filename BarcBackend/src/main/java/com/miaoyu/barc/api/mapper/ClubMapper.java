package com.miaoyu.barc.api.mapper;

import com.miaoyu.barc.api.model.SchoolClubModel;
import org.apache.ibatis.annotations.*;

import java.util.List;

public interface ClubMapper {
    /** 写事务锁定实体，防止并发迁移、编辑和删除相互覆盖。 */
    @Select("SELECT c.id, c.cn_name, c.jp_name, c.kr_name, c.en_name, c.logo, c.bg, c.deleted_at, sc.school_id AS school FROM club c LEFT JOIN school_club sc ON c.id=sc.club_id WHERE c.id=#{id} AND c.deleted_at IS NULL FOR UPDATE")
    SchoolClubModel selectByIdForUpdate(@Param("id") String id);

    /** 主键占用包含软删除行，禁止把已占用标识误报为可用。 */
    @Select("SELECT COUNT(*) FROM club WHERE id = #{id}")
    int countIncludingDeleted(@Param("id") String id);


    @Select("SELECT c.id, c.cn_name, c.jp_name, c.kr_name, c.en_name, c.logo, c.bg, c.deleted_at, sc.school_id AS school FROM club c LEFT JOIN school_club sc ON c.id=sc.club_id WHERE c.deleted_at IS NULL")
    List<SchoolClubModel> selectAll();

    @Select("SELECT c.id, c.cn_name, c.jp_name, c.kr_name, c.en_name, c.logo, c.bg, c.deleted_at, sc.school_id AS school FROM club c JOIN school_club sc ON c.id = sc.club_id WHERE sc.school_id = #{school_id} AND c.deleted_at IS NULL")
    List<SchoolClubModel> selectBySchool(@Param("school_id") String schoolId);

    @Select("SELECT c.id, c.cn_name, c.jp_name, c.kr_name, c.en_name, c.logo, c.bg, c.deleted_at, sc.school_id as school FROM club c LEFT JOIN school_club sc ON c.id = sc.club_id WHERE c.id = #{id} AND c.deleted_at IS NULL")
    SchoolClubModel selectById(@Param("id") String id);

    @Select("<script>" +
            "SELECT c.id, c.cn_name, c.jp_name, c.kr_name, c.en_name, c.logo, c.bg, c.deleted_at, sc.school_id AS school FROM club c JOIN school_club sc ON c.id = sc.club_id " +
            "WHERE sc.school_id = #{school_id} AND c.deleted_at IS NULL " +
            "<if test='keyword != null and keyword != \"\"'>" +
            "  AND (c.cn_name LIKE CONCAT('%',#{keyword},'%') OR c.jp_name LIKE CONCAT('%',#{keyword},'%') OR c.kr_name LIKE CONCAT('%',#{keyword},'%') OR c.en_name LIKE CONCAT('%',#{keyword},'%'))" +
            "</if>" +
            " ORDER BY c.id ASC LIMIT #{offset}, #{size}" +
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
            "SELECT c.id, c.cn_name, c.jp_name, c.kr_name, c.en_name, c.logo, c.bg, c.deleted_at, sc.school_id as school FROM club c " +
            "LEFT JOIN school_club sc ON c.id = sc.club_id " +
            "WHERE c.deleted_at IS NULL " +
            "<if test='keyword != null and keyword != \"\"'>" +
            "  AND (c.cn_name LIKE CONCAT('%',#{keyword},'%') OR c.jp_name LIKE CONCAT('%',#{keyword},'%') OR c.kr_name LIKE CONCAT('%',#{keyword},'%') OR c.en_name LIKE CONCAT('%',#{keyword},'%'))" +
            "</if>" +
            " ORDER BY c.id ASC LIMIT #{offset}, #{size}" +
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

    @Update("UPDATE student SET deleted_at = NOW() WHERE club = #{clubId} AND deleted_at IS NULL")
    int softDeleteStudentsByClub(@Param("clubId") String clubId);

    @Insert("INSERT INTO school_club(school_id, club_id) VALUES(#{school_id}, #{club_id}) " +
            "ON DUPLICATE KEY UPDATE school_id = #{school_id}")
    int upsertSchoolClub(@Param("school_id") String schoolId, @Param("club_id") String clubId);

    /** 单一学园归属迁移与部团关系更新位于同一事务。 */
    @Update("UPDATE student SET school=#{school} WHERE club=#{club} AND deleted_at IS NULL")
    int moveStudents(@Param("club") String club, @Param("school") String school);

    @Select("SELECT id, school FROM student WHERE club=#{club} AND deleted_at IS NULL ORDER BY id")
    java.util.List<java.util.Map<String, String>> studentsBeforeMove(@Param("club") String club);

    @Insert("INSERT INTO entity_relation_change(id,operator_uuid,club_id,old_school,new_school,previous_students) VALUES(#{id},#{operator},#{club},#{oldSchool},#{newSchool},#{students})")
    int recordMove(@Param("id") String id, @Param("operator") String operator, @Param("club") String club,
                   @Param("oldSchool") String oldSchool, @Param("newSchool") String newSchool, @Param("students") String students);

    /** 级联软删前收集学生图片，后续按引用和保留期回收。 */
    @Select("SELECT avatar_square FROM student WHERE club=#{club} AND deleted_at IS NULL UNION SELECT avatar_rectangle FROM student WHERE club=#{club} AND deleted_at IS NULL UNION SELECT body_image FROM student WHERE club=#{club} AND deleted_at IS NULL")
    java.util.List<String> studentImageKeys(@Param("club") String club);

    @Delete("DELETE FROM school_club WHERE club_id = #{club_id}")
    int deleteSchoolClub(@Param("club_id") String clubId);
}
