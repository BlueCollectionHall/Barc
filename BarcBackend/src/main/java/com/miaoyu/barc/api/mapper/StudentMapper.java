package com.miaoyu.barc.api.mapper;

import com.miaoyu.barc.api.model.StudentModel;
import org.apache.ibatis.annotations.*;

import java.util.List;

public interface StudentMapper {
    /** 首页最近更新学生先在数据库去重，再限制返回容量，避免逐作品查询学生。 */
    @Select("SELECT s.* FROM student s JOIN (SELECT w.student, MAX(COALESCE(w.content_updated_at,w.created_at)) AS fresh " +
            "FROM work w JOIN work_review wr ON wr.work_id=w.id WHERE w.status='PUBLIC' AND wr.status='APPROVED' " +
            "AND COALESCE(w.content_updated_at,w.created_at) >= DATE_SUB(CURRENT_TIMESTAMP, INTERVAL #{day} DAY) " +
            "GROUP BY w.student) recent ON recent.student=s.id WHERE s.deleted_at IS NULL ORDER BY recent.fresh DESC,s.id ASC LIMIT 100")
    List<StudentModel> selectByRecentWork(@Param("day") int day);
    /** 写事务锁定实体，防止并发迁移、编辑和删除相互覆盖。 */
    @Select("SELECT * FROM student WHERE id=#{id} AND deleted_at IS NULL FOR UPDATE")
    StudentModel selectByIdForUpdate(@Param("id") String id);

    /** 主键占用包含软删除行，禁止把已占用标识误报为可用。 */
    @Select("SELECT COUNT(*) FROM student WHERE id = #{id}")
    int countIncludingDeleted(@Param("id") String id);


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
            " ORDER BY id ASC LIMIT #{offset}, #{size}" +
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
            " ORDER BY id ASC LIMIT #{offset}, #{size}" +
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
