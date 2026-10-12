package com.miaoyu.barc.comment.mapper;

import com.miaoyu.barc.comment.model.WorkCommentReplyModel;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface WorkCommentReplyMapper {
    /** 每条主评论只取前五条预览，一次 SQL 获取整个页面，不产生逐条查询。 */
    @Select({"<script>SELECT ranked.* FROM (SELECT r.*, ROW_NUMBER() OVER (PARTITION BY parent_id ORDER BY created_at,id) AS rn "
            + "FROM work_comment_reply r WHERE parent_id IN "
            + "<foreach collection='ids' item='id' open='(' separator=',' close=')'>#{id}</foreach>"
            + ") ranked WHERE rn &lt;= 5 ORDER BY parent_id,created_at,id</script>"})
    List<WorkCommentReplyModel> selectPreview(@Param("ids") List<String> ids);
    @Select("SELECT * FROM work_comment_reply WHERE parent_id=#{parent_id} ORDER BY created_at,id LIMIT #{offset},#{limit}")
    List<WorkCommentReplyModel> selectPage(@Param("parent_id") String parentId, @Param("offset") int offset, @Param("limit") int limit);
    @Select("SELECT COUNT(*) FROM work_comment_reply WHERE parent_id=#{parent_id}")
    long countByParentId(@Param("parent_id") String parentId);
    @Select("SELECT COUNT(*) FROM work_comment_reply WHERE parent_id=#{parent_id} AND author=#{author}")
    long countByParentAndAuthor(@Param("parent_id") String parentId, @Param("author") String author);
    @Select("SELECT * FROM work_comment_reply WHERE id = #{reply_id}")
    WorkCommentReplyModel selectById(@Param("reply_id") String replyId);
    @Insert("INSERT INTO work_comment_reply (id, parent_id, author, reply_user, content) VALUES (#{id}, #{parent_id}, #{author}, #{reply_user}, #{content})")
    boolean insert(WorkCommentReplyModel workCommentReplyModel);
    @Delete("DELETE FROM work_comment_reply WHERE id = #{reply_id}")
    boolean deleteById(@Param("reply_id") String replyId);
    @Delete("DELETE FROM work_comment_reply WHERE parent_id=#{parent_id}")
    int deleteByParentId(@Param("parent_id") String parentId);
}
