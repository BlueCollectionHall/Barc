package com.miaoyu.barc.api.work.mapper;

import com.miaoyu.barc.api.work.enumeration.WorkStatusEnum;
import com.miaoyu.barc.api.work.enumeration.WorkReviewStatusEnum;
import com.miaoyu.barc.api.work.model.WorkModel;
import com.miaoyu.barc.api.work.model.entity.WorkEntity;
import com.miaoyu.barc.utils.pojo.PageInitPojo;
import org.apache.ibatis.annotations.*;

import java.util.List;
import java.util.Map;

public interface WorkMapper {
    @Select("SELECT w.*, wr.status AS review_status, wr.rejection_reason AS review_reason, " +
            "wr.reviewer_uuid, wr.submitted_at AS review_submitted_at, wr.reviewed_at " +
            "FROM work w JOIN work_review wr ON wr.work_id = w.id " +
            "WHERE w.status = #{status} AND wr.status = 'APPROVED'")
    List<WorkEntity> selectAll(@Param("status") WorkStatusEnum statusEnum);

    // 公开新鲜度按内容更新时间计算；历史空值回退到创建时间，避免浏览/点赞等行更新时间影响内容新鲜度。
    @Select("SELECT w.*, wr.status AS review_status, wr.rejection_reason AS review_reason, " +
            "wr.reviewer_uuid, wr.submitted_at AS review_submitted_at, wr.reviewed_at " +
            "FROM work w JOIN work_review wr ON wr.work_id = w.id " +
            "WHERE COALESCE(w.content_updated_at, w.created_at) >= DATE_SUB(now(), INTERVAL #{day} DAY) " +
            "AND w.status = #{status} AND wr.status = 'APPROVED'")
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

    @Select("SELECT w.*, wr.status AS review_status, wr.rejection_reason AS review_reason, " +
            "wr.reviewer_uuid, wr.submitted_at AS review_submitted_at, wr.reviewed_at " +
            "FROM work w JOIN work_review wr ON wr.work_id = w.id JOIN student stu ON w.student = stu.id " +
            "WHERE stu.school = #{school_id} AND w.status = #{status} AND wr.status = 'APPROVED'")
    List<WorkEntity> selectBySchoolId(@Param("school_id") String schoolId, @Param("status") WorkStatusEnum statusEnum);

    @Select("SELECT w.*, wr.status AS review_status, wr.rejection_reason AS review_reason, " +
            "wr.reviewer_uuid, wr.submitted_at AS review_submitted_at, wr.reviewed_at " +
            "FROM work w JOIN work_review wr ON wr.work_id = w.id JOIN student stu ON w.student = stu.club " +
            "WHERE stu.club = #{club_id} AND w.status = #{status} AND wr.status = 'APPROVED'")
    List<WorkEntity> selectByClubId(@Param("club_id") String clubId, @Param("status") WorkStatusEnum statusEnum);

    @Select("SELECT w.*, wr.status AS review_status, wr.rejection_reason AS review_reason, " +
            "wr.reviewer_uuid, wr.submitted_at AS review_submitted_at, wr.reviewed_at " +
            "FROM work w JOIN work_review wr ON wr.work_id = w.id " +
            "WHERE w.student = #{student_id} AND w.status = #{status} AND wr.status = 'APPROVED'")
    List<WorkEntity> selectByStudentId(@Param("student_id") String studentId, @Param("status") WorkStatusEnum statusEnum);

    @Select("SELECT w.*, wr.status AS review_status, wr.rejection_reason AS review_reason, " +
            "wr.reviewer_uuid, wr.submitted_at AS review_submitted_at, wr.reviewed_at " +
            "FROM work w JOIN work_review wr ON wr.work_id = w.id " +
            "WHERE w.author = #{uuid} AND w.status = #{status} AND wr.status = 'APPROVED'")
    List<WorkEntity> selectByUuid(@Param("uuid") String uuid, @Param("status") WorkStatusEnum statusEnum);

    /** 作者本人内容管理查询，可查看待审与驳回作品。 */
    List<WorkEntity> selectByUuidWithFilters(
            @Param("uuid") String uuid,
            @Param("status") WorkStatusEnum statusEnum,
            @Param("review_status") WorkReviewStatusEnum reviewStatus,
            @Param("condition") Map<String, Object> condition);

    /** 对外作者作品查询，SQL 固定要求审核通过，不能由调用方绕过。 */
    List<WorkEntity> selectPublicByUuidWithFilters(
            @Param("uuid") String uuid,
            @Param("status") WorkStatusEnum statusEnum,
            @Param("condition") Map<String, Object> condition);

    @Select("SELECT w.*, wr.status AS review_status, wr.rejection_reason AS review_reason, " +
            "wr.reviewer_uuid, wr.submitted_at AS review_submitted_at, wr.reviewed_at " +
            "FROM work w JOIN work_review wr ON wr.work_id = w.id JOIN user_basic ub ON w.author = ub.uuid " +
            "WHERE ub.username = #{username} AND w.status = #{status} AND wr.status = 'APPROVED'")
    List<WorkEntity> selectByUsername(@Param("username") String username, @Param("status") WorkStatusEnum statusEnum);

    // 获取当前分类ID下属所有分类中的内容
    @Select("WITH RECURSIVE category_tree AS " +
            "( SELECT c1.* FROM category c1 WHERE c1.id = #{id} UNION ALL " +
            "SELECT c2.* FROM category c2 JOIN category_tree ct ON c2.parent_id = ct.id)" +
            "SELECT DISTINCT w.*, wr.status AS review_status, wr.rejection_reason AS review_reason, " +
            "wr.reviewer_uuid, wr.submitted_at AS review_submitted_at, wr.reviewed_at " +
            "FROM work w JOIN work_review wr ON wr.work_id = w.id JOIN work_category wc ON w.id = wc.work_id " +
            "JOIN category_tree ct ON wc.category_id = ct.id WHERE w.status = #{status, typeHandler=org.apache.ibatis.type.EnumTypeHandler} " +
            "AND wr.status = 'APPROVED'")
    List<WorkEntity> selectByCategoryId(@Param("id") String id, @Param("status") WorkStatusEnum statusEnum);

    @Select("SELECT w.*, wr.status AS review_status, wr.rejection_reason AS review_reason, " +
            "wr.reviewer_uuid, wr.submitted_at AS review_submitted_at, wr.reviewed_at " +
            "FROM work w LEFT JOIN work_review wr ON wr.work_id = w.id WHERE w.id = #{work_id}")
    WorkModel selectById(@Param("work_id") String workId);
    @Update("UPDATE work SET view_count = COALESCE(view_count, 0) + 1 WHERE id = #{work_id}")
    int incrementViewCount(@Param("work_id") String workId);
    @Update("UPDATE work SET like_count = COALESCE(like_count, 0) + 1 WHERE id = #{work_id}")
    int incrementLikeCount(@Param("work_id") String workId);
    @Update("UPDATE work SET like_count = CASE WHEN like_count > 0 THEN like_count - 1 ELSE 0 END WHERE id = #{work_id}")
    int decrementLikeCount(@Param("work_id") String workId);
    @Insert("INSERT INTO work " +
            "(id, title, description, content, banner_image, cover_image, author, author_nickname, uploader, is_claim, status, student, created_at, updated_at, content_updated_at) VALUES " +
            "(#{id}, #{title}, #{description}, #{content}, #{banner_image}, #{cover_image}, #{author}, #{author_nickname}, #{uploader}, #{is_claim}, #{status}, #{student}, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)")
    boolean insert(WorkModel workModel);
    // 通用更新兼容旧接口：只有正文类字段真实变化时刷新内容更新时间，状态/认领/封面类变更不刷新。
    @Update("UPDATE work SET content_updated_at = CASE WHEN NOT (title <=> #{title} AND description <=> #{description} AND content <=> #{content} AND author_nickname <=> #{author_nickname} AND student <=> #{student}) THEN CURRENT_TIMESTAMP ELSE content_updated_at END, " +
            "title = #{title}, description = #{description}, content = #{content}, banner_image = #{banner_image}, cover_image = #{cover_image}, author = #{author}, author_nickname = #{author_nickname}, uploader = #{uploader}, is_claim = #{is_claim}, status = #{status}, student = #{student} WHERE id = #{id}")
    boolean update(WorkModel workModel);
    @Delete("DELETE FROM work WHERE id = #{id}")
    boolean delete(@Param("id") String id);

    // ===== 管理端专用查询方法 =====

    /** 管理端分页查询作品（含所有状态，含已删除） */
    List<WorkModel> selectByPageForManage(
            @Param("status") String status,
            @Param("keyword") String keyword,
            @Param("offset") int offset,
            @Param("limit") int limit,
            @Param("sortField") String sortField,
            @Param("sortOrder") String sortOrder);

    /** 管理端统计作品总数 */
    Long countByPageForManage(
            @Param("status") String status,
            @Param("keyword") String keyword);

    /** 管理端审核队列，默认由服务层传 PENDING。 */
    List<WorkModel> selectByPageForReview(
            @Param("review_status") WorkReviewStatusEnum reviewStatus,
            @Param("keyword") String keyword,
            @Param("offset") int offset,
            @Param("limit") int limit);

    Long countByPageForReview(
            @Param("review_status") WorkReviewStatusEnum reviewStatus,
            @Param("keyword") String keyword);

    /** 管理端纯文字更新（不改封面/Banner/作者/收录者，图片在独立表中管理），内容更新时间仅在文字内容变更时刷新。 */
    @Update("UPDATE work SET title = #{title}, description = #{description}, content = #{content}, " +
            "author_nickname = #{author_nickname}, is_claim = #{is_claim}, student = #{student}, " +
            "content_updated_at = CURRENT_TIMESTAMP WHERE id = #{id}")
    boolean updateText(WorkModel workModel);
}
