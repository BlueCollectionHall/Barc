package com.miaoyu.barc.api.work.mapper;

import com.miaoyu.barc.api.work.model.WorkClaimModel;
import com.miaoyu.barc.api.work.model.WorkClaimListDto;
import org.apache.ibatis.annotations.*;
import java.util.List;

/** 认领记录只新增/结案，不再删除；列表一次关联作品和用户，避免逐条查询。 */
public interface WorkClaimMapper {
    @Select("SELECT * FROM work_claim WHERE id = #{id}")
    WorkClaimModel selectById(@Param("id") String id);
    @Select("SELECT * FROM work_claim WHERE id = #{id} FOR UPDATE")
    WorkClaimModel selectByIdForUpdate(@Param("id") String id);
    @Select("SELECT * FROM work_claim WHERE work_id = #{work} AND applicant_uuid = #{applicant} AND status = 'PENDING'")
    WorkClaimModel selectPending(@Param("work") String work, @Param("applicant") String applicant);
    @Insert("INSERT INTO work_claim (id, work_id, applicant_uuid, statement, status) VALUES (#{id}, #{work_id}, #{applicant_uuid}, #{statement}, 'PENDING')")
    boolean insert(WorkClaimModel model);
    @Update("UPDATE work_claim SET status = #{status}, reviewer_uuid = #{reviewer}, processed_at = CURRENT_TIMESTAMP, result_reason = #{reason} WHERE id = #{id} AND status = 'PENDING'")
    boolean resolve(@Param("id") String id, @Param("status") String status, @Param("reviewer") String reviewer, @Param("reason") String reason);
    @Update("UPDATE work_claim SET status = 'SUPERSEDED', reviewer_uuid = #{reviewer}, processed_at = CURRENT_TIMESTAMP, result_reason = '作品归属已确定' WHERE work_id = #{work} AND status = 'PENDING'")
    int closeOtherPending(@Param("work") String work, @Param("reviewer") String reviewer);

    @Select({"<script>", "SELECT c.*, w.title AS work_title, ua.nickname AS applicant_nickname, ub.username AS applicant_username",
        "FROM work_claim c LEFT JOIN work w ON w.id=c.work_id LEFT JOIN user_archive ua ON ua.uuid=c.applicant_uuid LEFT JOIN user_basic ub ON ub.uuid=c.applicant_uuid",
        "<where><if test='status != null'>c.status=#{status}</if><if test='work != null'>AND c.work_id=#{work}</if><if test='applicant != null'>AND c.applicant_uuid=#{applicant}</if></where>",
        "ORDER BY c.created_at DESC, c.id DESC LIMIT #{offset}, #{limit}", "</script>"})
    List<WorkClaimListDto> selectPage(@Param("status") String status, @Param("work") String work, @Param("applicant") String applicant, @Param("offset") int offset, @Param("limit") int limit);
    @Select({"<script>", "SELECT COUNT(*) FROM work_claim c",
        "<where><if test='status != null'>c.status=#{status}</if><if test='work != null'>AND c.work_id=#{work}</if><if test='applicant != null'>AND c.applicant_uuid=#{applicant}</if></where>", "</script>"})
    long countPage(@Param("status") String status, @Param("work") String work, @Param("applicant") String applicant);
}
