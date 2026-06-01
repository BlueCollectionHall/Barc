package com.miaoyu.barc.comment.mapper;

import com.miaoyu.barc.comment.model.MessageBoardModel;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

public interface MessageBoardMapper {
    @Select("SELECT * FROM message_board")
    List<MessageBoardModel> selectAll();
    @Select("SELECT * FROM message_board ORDER BY created_at DESC LIMIT #{limit}")
    List<MessageBoardModel> selectByLimit(int limit);
    @Select("SELECT * FROM message_board WHERE id = #{message_id}")
    MessageBoardModel selectById(@Param("message_id") String messageId);
    @Insert("INSERT INTO message_board (id, author, content) VALUES (#{id}, #{author}, #{content})")
    boolean insert(MessageBoardModel model);
    @Delete("DELETE FROM message_board WHERE id = #{messageId}")
    boolean delete(String messageId);

    List<MessageBoardModel> selectAdminByPage(
        @Param("offset") Integer offset,
        @Param("pageSize") Integer pageSize,
        @Param("condition") Map<String, Object> condition
    );

    Long countAdminByPage(@Param("condition") Map<String, Object> condition);

    boolean updateById(@Param("id") String id, @Param("content") String content);

    boolean batchDelete(@Param("ids") List<String> ids);
}
