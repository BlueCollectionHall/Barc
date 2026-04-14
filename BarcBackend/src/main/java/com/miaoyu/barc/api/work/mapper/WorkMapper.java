package com.miaoyu.barc.api.work.mapper;

import com.miaoyu.barc.api.work.enumeration.WorkStatusEnum;
import com.miaoyu.barc.api.work.model.WorkModel;
import com.miaoyu.barc.api.work.model.entity.WorkEntity;
import com.miaoyu.barc.utils.pojo.PageInitPojo;
import org.apache.ibatis.annotations.*;

import java.util.List;
import java.util.Map;

public interface WorkMapper {
    @Select("SELECT * FROM work WHERE status = #{status}")
    List<WorkEntity> selectAll(@Param("status") WorkStatusEnum statusEnum);

    @Select("SELECT * FROM work WHERE updated_at >= DATE_SUB(now(), INTERVAL #{day} DAY) AND status = #{status}")
    List<WorkEntity> selectByDay(@Param("day") Integer day, @Param("status") WorkStatusEnum status);

    List<WorkModel> selectByPage(
            @Param("status") WorkStatusEnum statusEnum,
            @Param("offset") Integer offset,
            @Param("page_size") Integer pageSize,
            @Param("condition") Map<String, Object> condition);

    Long countByPage(
            @Param("status") WorkStatusEnum statusEnum,
            @Param("condition") Map<String, Object> condition);

    List<WorkModel> selectByPageOnCategory(
            @Param("category_id") String categoryId,
            @Param("status") WorkStatusEnum statusEnum,
            @Param("offset") Integer offset,
            @Param("page_size") Integer pageSize,
            @Param("condition") Map<String, Object> condition);

    Long countByPageOnCategory(
            @Param("category_id") String categoryId,
            @Param("status") WorkStatusEnum statusEnum,
            @Param("condition") Map<String, Object> condition);

    @Select("SELECT w.* FROM work w JOIN student stu ON w.student = stu.id WHERE stu.school = #{school_id} AND status = #{status}")
    List<WorkEntity> selectBySchoolId(@Param("school_id") String schoolId, @Param("status") WorkStatusEnum statusEnum);

    @Select("SELECT w.* FROM work w JOIN student stu ON w.student = stu.club WHERE stu.club = #{club_id} AND status = #{status}")
    List<WorkEntity> selectByClubId(@Param("club_id") String clubId, @Param("status") WorkStatusEnum statusEnum);

    @Select("SELECT * FROM work WHERE student = #{student_id} AND status = #{status}")
    List<WorkEntity> selectByStudentId(@Param("student_id") String studentId, @Param("status") WorkStatusEnum statusEnum);

    @Select("SELECT * FROM work WHERE author = #{uuid} AND status = #{status}")
    List<WorkEntity> selectByUuid(@Param("uuid") String uuid, @Param("status") WorkStatusEnum statusEnum);

    @Select("SELECT w.* FROM work w JOIN user_basic ub ON w.author = ub.uuid WHERE ub.username = #{username} AND w.status = #{status}")
    List<WorkEntity> selectByUsername(@Param("username") String username, @Param("status") WorkStatusEnum statusEnum);

    // 获取当前分类ID下属所有分类中的内容
    @Select("WITH RECURSIVE category_tree AS " +
            "( SELECT c1.* FROM category c1 WHERE c1.id = #{id} UNION ALL " +
            "SELECT c2.* FROM category c2 JOIN category_tree ct ON c2.parent_id = ct.id)" +
            "SELECT DISTINCT w.* FROM work w JOIN work_category wc ON w.id = wc.work_id " +
            "JOIN category_tree ct ON wc.category_id = ct.id WHERE w.status = #{status, typeHandler=org.apache.ibatis.type.EnumTypeHandler}")
    List<WorkEntity> selectByCategoryId(@Param("id") String id, @Param("status") WorkStatusEnum statusEnum);

    @Select("SELECT * FROM work WHERE id = #{work_id}")
    WorkModel selectById(@Param("work_id") String workId);
    @Insert("INSERT INTO work " +
            "(id, title, description, content, banner_image, cover_image, author, author_nickname, uploader, is_claim, status, student) VALUES " +
            "(#{id}, #{title}, #{description}, #{content}, #{banner_image}, #{cover_image}, #{author}, #{author_nickname}, #{uploader}, #{is_claim}, 'PUBLIC', #{student})")
    boolean insert(WorkModel workModel);
    @Update("UPDATE work SET title = #{title}, description = #{description}, content = #{content}, banner_image = #{banner_image}, cover_image = #{cover_image}, author = #{author}, author_nickname = #{author_nickname}, uploader = #{uploader}, is_claim = #{is_claim}, student = #{student} WHERE id = #{id}")
    boolean update(WorkModel workModel);
    @Delete("DELETE FROM work WHERE id = #{id}")
    boolean delete(@Param("id") String id);
}
