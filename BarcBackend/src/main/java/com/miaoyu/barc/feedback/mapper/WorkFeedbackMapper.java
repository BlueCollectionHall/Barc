package com.miaoyu.barc.feedback.mapper;

import com.miaoyu.barc.feedback.model.WorkFeedbackModel;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

public interface WorkFeedbackMapper {
    @Insert("INSERT INTO feedback_work (id, work_id, ipv4, device_info, author, reason_option, content, email) VALUES (#{id}, #{work_id}, #{ipv4}, #{device_info}, #{author}, #{reason_option}, #{content}, #{email})")
    boolean insert(WorkFeedbackModel request);
    @Update("UPDATE feedback_work SET status = true, note = #{note} WHERE id = #{feedback_id}")
    boolean update(@Param("feedback_id") String feedbackId, @Param("note") String note);
    @Select("SELECT * FROM feedback_work")
    List<WorkFeedbackModel> selectAll();
    @Select("SELECT * FROM feedback_work WHERE work_id = #{work_id}")
    List<WorkFeedbackModel> selectByWorkId(@Param("work_id") String workId);
    @Select("SELECT * FROM feedback_work WHERE reason_option = #{reason_option}")
    List<WorkFeedbackModel> selectByReasonOption(@Param("reason_option") Integer reason_option);
    @Select("SELECT * FROM feedback_work WHERE id = #{feedback_id}")
    WorkFeedbackModel selectById(@Param("feedback_id") String feedbackId);
}
