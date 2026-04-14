package com.miaoyu.barc.api.work.mapper;

import com.miaoyu.barc.api.work.model.WorkClaimModel;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface WorkClaimMapper {
    @Select("SELECT * FROM work_claim")
    List<WorkClaimModel> selectAll();
//    @Select("SELECT * FROM work_claim WHERE initiator_uuid = #{uuid}")
//    List<WorkClaimModel> selectByInitiatorUuid(String uuid);
//    @Select("SELECT * FROM work_claim WHERE recipient_uuid = #{uuid}")
//    List<WorkClaimModel> selectByRecipientUuid(String uuid);
    @Select("SELECT * FROM work_claim WHERE work_id = #{work_id}")
    List<WorkClaimModel> selectByWorkId(String work_id);
    @Select("SELECT * FROM work_claim WHERE applicant_uuid = #{uuid}")
    List<WorkClaimModel> selectByUuid(@Param("uuid") String uuid);
    @Select("SELECT * FROM work_claim WHERE id = #{id}")
    WorkClaimModel selectById(@Param("id") String id);
    @Insert("INSERT INTO work_claim (id, work_id, applicant_uuid) VALUES (#{id}, #{work_id}, #{applicant_uuid})")
    boolean insert(WorkClaimModel workClaimModel);
    @Delete("DELETE FROM work_claim WHERE id = #{id}")
    boolean deleteById(@Param("id") String id);
    @Delete("DELETE FROM work_claim WHERE work_id = #{work_id}")
    boolean deleteByWorkId(@Param("work_id") String workId);
}
