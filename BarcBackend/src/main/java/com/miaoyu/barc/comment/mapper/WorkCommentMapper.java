package com.miaoyu.barc.comment.mapper;

import com.miaoyu.barc.comment.model.WorkCommentModel;
import com.miaoyu.barc.comment.pojo.WorkCommentPojo;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface WorkCommentMapper {
    @Select("SELECT * FROM work_comment WHERE work_id = #{work_id}")
    List<WorkCommentModel> selectByWorkId(@Param("work_id") String workId);
    @Select("SELECT * FROM work_comment WHERE id = #{comment_id}")
    WorkCommentPojo selectById(@Param("comment_id") String commentId);
    @Insert("INSERT INTO work_comment (id, work_id, author, content) VALUES (#{id}, #{work_id}, #{author}, #{content})")
    boolean insert(WorkCommentModel workComment);
    @Delete("DELETE FROM work_comment WHERE id = #{comment_id}")
    boolean delete(@Param("comment_id") String commentId);
}
