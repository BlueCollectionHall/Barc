package com.miaoyu.barc.api.work.mapper;

import com.miaoyu.barc.api.work.model.WorkLikeModel;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface WorkLikeMapper {
    @Select("SELECT * FROM work_like WHERE work_id = #{work_id} AND user_uuid = #{user_uuid} LIMIT 1")
    WorkLikeModel selectByWorkIdAndUserUuid(@Param("work_id") String workId, @Param("user_uuid") String userUuid);

    @Insert("INSERT INTO work_like (id, work_id, user_uuid) VALUES (#{id}, #{work_id}, #{user_uuid})")
    int insert(WorkLikeModel model);

    @Delete("DELETE FROM work_like WHERE work_id = #{work_id} AND user_uuid = #{user_uuid}")
    int deleteByWorkIdAndUserUuid(@Param("work_id") String workId, @Param("user_uuid") String userUuid);
}
