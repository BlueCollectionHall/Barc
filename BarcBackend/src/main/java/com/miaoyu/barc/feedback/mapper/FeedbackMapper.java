package com.miaoyu.barc.feedback.mapper;

import com.miaoyu.barc.feedback.enumeration.FeedbackStatusEnum;
import com.miaoyu.barc.feedback.enumeration.FeedbackTypeEnum;
import com.miaoyu.barc.feedback.model.FeedbackFormModel;
import org.apache.ibatis.annotations.*;

import java.util.List;

public interface FeedbackMapper {
    // 说明（2026-10 下线）：原先这里还有两个 @Deprecated 方法
    // selectOptionsByParentId / selectAllTypes，分别读已废弃的 feedback_option 与 feedback_type 表。
    // 二者唯一的调用方是 FeedbackTypeOfController 与 WorkFeedbackService，均已随旧链路删除，
    // 故一并移除。反馈分类改由 FeedbackTypeEnum 与前端本地映射驱动。

    /** 列表与计数共享筛选；个人 author 只能由认证身份提供。 */
    String FILTER = " WHERE type=#{type}<if test='status != null'> AND status=#{status}</if><if test='author != null'> AND author=#{author}</if>";
    @Select("<script>SELECT * FROM feedback" + FILTER + " ORDER BY updated_at DESC,id DESC LIMIT #{offset},#{limit}</script>")
    List<FeedbackFormModel> selectPage(@Param("type") FeedbackTypeEnum type, @Param("status") FeedbackStatusEnum status,
            @Param("author") String author, @Param("offset") int offset, @Param("limit") int limit);
    @Select("<script>SELECT COUNT(*) FROM feedback" + FILTER + "</script>")
    long countPage(@Param("type") FeedbackTypeEnum type, @Param("status") FeedbackStatusEnum status, @Param("author") String author);
    @Select("SELECT * FROM feedback WHERE id = #{id}")
    FeedbackFormModel selectById(@Param("id") String id);

    @Insert("INSERT INTO feedback (id, target_id, ipv4, ipv6, author, email, content, echo, type) VALUES (#{id}, #{target_id}, #{ipv4}, #{ipv6}, #{author}, #{email}, #{content}, #{echo}, #{type})")
    boolean insert(FeedbackFormModel requestModel);
    @Update("UPDATE feedback SET echo = #{echo}, status = #{status} WHERE id = #{id}")
    boolean update(FeedbackFormModel requestModel);
    /** 仅修改处理字段，状态条件防止并发详情或旧表单覆盖已经完成的结果。 */
    @Update("UPDATE feedback SET echo=#{echo},status=#{status},updated_at=NOW() WHERE id=#{id} AND status=#{expected}")
    int transition(@Param("id") String id, @Param("expected") FeedbackStatusEnum expected,
                   @Param("status") FeedbackStatusEnum status, @Param("echo") String echo);
    @Delete("DELETE FROM feedback WHERE id = #{id}")
    boolean delete(@Param("id") String id);
}
