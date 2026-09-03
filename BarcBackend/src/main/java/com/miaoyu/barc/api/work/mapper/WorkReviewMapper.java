package com.miaoyu.barc.api.work.mapper;

import com.miaoyu.barc.api.work.enumeration.WorkReviewStatusEnum;
import com.miaoyu.barc.api.work.model.WorkReviewModel;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/** 作品审核当前状态持久化。审核历史同时写入 work_operation_log。 */
public interface WorkReviewMapper {

    @Select("SELECT * FROM work_review WHERE work_id = #{work_id}")
    WorkReviewModel selectByWorkId(@Param("work_id") String workId);

    @Insert("INSERT INTO work_review (work_id, status, submitted_at, updated_at) " +
            "VALUES (#{work_id}, 'PENDING', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)")
    boolean insertPending(@Param("work_id") String workId);

    /** 非驳回作品的展示内容变化后进入或刷新审核，旧结论信息同步清空。 */
    @Update("UPDATE work_review SET status = 'PENDING', rejection_reason = NULL, reviewer_uuid = NULL, " +
            "submitted_at = CURRENT_TIMESTAMP, reviewed_at = NULL, updated_at = CURRENT_TIMESTAMP " +
            "WHERE work_id = #{work_id}")
    boolean submitForReview(@Param("work_id") String workId);

    /** 作者显式再次提审；状态条件防止重复点击或并发请求覆盖其他审核结论。 */
    @Update("UPDATE work_review SET status = 'PENDING', rejection_reason = NULL, reviewer_uuid = NULL, " +
            "submitted_at = CURRENT_TIMESTAMP, reviewed_at = NULL, updated_at = CURRENT_TIMESTAMP " +
            "WHERE work_id = #{work_id} AND status = 'REJECTED'")
    boolean resubmitRejected(@Param("work_id") String workId);

    /** 仅允许处理仍处于 PENDING 的记录，防止管理员重复点击覆盖已落库的结论。 */
    @Update("UPDATE work_review SET status = #{status}, rejection_reason = #{reason}, " +
            "reviewer_uuid = #{reviewer_uuid}, reviewed_at = CURRENT_TIMESTAMP, updated_at = CURRENT_TIMESTAMP " +
            "WHERE work_id = #{work_id} AND status = 'PENDING'")
    boolean review(@Param("work_id") String workId,
                   @Param("status") WorkReviewStatusEnum status,
                   @Param("reason") String reason,
                   @Param("reviewer_uuid") String reviewerUuid);
}
