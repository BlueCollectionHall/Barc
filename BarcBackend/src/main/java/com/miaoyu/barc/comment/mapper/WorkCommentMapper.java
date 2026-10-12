package com.miaoyu.barc.comment.mapper;

import com.miaoyu.barc.comment.model.WorkCommentModel;
import com.miaoyu.barc.comment.pojo.WorkCommentPojo;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface WorkCommentMapper {
    /** 主评论分页一次带出回复总数，按时间与主键稳定排序。 */
    @Select("SELECT c.*, (SELECT COUNT(*) FROM work_comment_reply r WHERE r.parent_id=c.id) AS reply_count "
            + "FROM work_comment c WHERE c.work_id=#{work_id} ORDER BY c.created_at DESC,c.id DESC LIMIT #{offset},#{limit}")
    List<WorkCommentPojo> selectPage(@Param("work_id") String workId, @Param("offset") int offset, @Param("limit") int limit);
    @Select("SELECT COUNT(*) FROM work_comment WHERE work_id=#{work_id}")
    long countParents(@Param("work_id") String workId);
    /** 统计直接聚合，避免为计数拉取正文及逐条读取回复。 */
    @Select("SELECT (SELECT COUNT(*) FROM work_comment WHERE work_id=#{work_id}) + "
            + "(SELECT COUNT(*) FROM work_comment_reply r JOIN work_comment c ON c.id=r.parent_id WHERE c.work_id=#{work_id})")
    long countWithReplies(@Param("work_id") String workId);
    @Select("SELECT * FROM work_comment WHERE id = #{comment_id}")
    WorkCommentPojo selectById(@Param("comment_id") String commentId);
    @Select("SELECT * FROM work_comment WHERE id=#{comment_id} FOR UPDATE")
    WorkCommentPojo selectByIdForUpdate(@Param("comment_id") String commentId);
    @Insert("INSERT INTO work_comment (id, work_id, author, content) VALUES (#{id}, #{work_id}, #{author}, #{content})")
    boolean insert(WorkCommentModel workComment);
    @Delete("DELETE FROM work_comment WHERE id = #{comment_id}")
    boolean delete(@Param("comment_id") String commentId);
}
