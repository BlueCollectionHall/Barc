package com.miaoyu.barc.comment.mapper;

import com.miaoyu.barc.comment.model.WorkCommentReplyModel;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface WorkCommentReplyMapper {
    @Select("SELECT * FROM work_comment_reply WHERE parent_id = #{parent_id}")
    List<WorkCommentReplyModel> selectByParentId(@Param("parent_id") String parentId);
    @Select("SELECT * FROM work_comment_reply WHERE id = #{reply_id}")
    WorkCommentReplyModel selectById(@Param("reply_id") String replyId);
    @Insert("INSERT INTO work_comment_reply (id, parent_id, author, reply_user, content) VALUES (#{id}, #{parent_id}, #{author}, #{reply_user}, #{content})")
    boolean insert(WorkCommentReplyModel workCommentReplyModel);
    @Delete("DELETE FROM work_comment_reply WHERE id = #{reply_id}")
    boolean deleteById(@Param("reply_id") String replyId);
}
