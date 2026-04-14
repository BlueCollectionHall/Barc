package com.miaoyu.barc.api.work.mapper;

import com.miaoyu.barc.api.work.model.WorkCoverImageModel;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface WorkCoverImageMapper {
    @Select("SELECT * FROM work_cover_image WHERE work_id = #{work_id}")
    WorkCoverImageModel selectByWorkId(@Param("work_id") String workId);

    @Select("SELECT * FROM work_cover_image WHERE id = #{id}")
    WorkCoverImageModel selectById(@Param("id") String id);

    @Insert("INSERT INTO work_cover_image (id, work_id, object_key) VALUES (#{id}, #{work_id}, #{object_key})")
    boolean insert(WorkCoverImageModel workCoverImageModel);

    @Delete("DELETE FROM work_cover_image WHERE work_id = #{work_id}")
    boolean deleteByWorkId(@Param("work_id") String workId);
}
