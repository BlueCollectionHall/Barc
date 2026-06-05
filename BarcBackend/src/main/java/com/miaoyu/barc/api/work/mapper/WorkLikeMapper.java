package com.miaoyu.barc.api.work.mapper;

import com.miaoyu.barc.api.work.enumeration.WorkStatusEnum;
import com.miaoyu.barc.api.work.model.WorkLikeModel;
import com.miaoyu.barc.api.work.model.WorkModel;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface WorkLikeMapper {
    @Select("SELECT * FROM work_like WHERE work_id = #{work_id} AND user_uuid = #{user_uuid} LIMIT 1")
    WorkLikeModel selectByWorkIdAndUserUuid(@Param("work_id") String workId, @Param("user_uuid") String userUuid);

    @Insert("INSERT INTO work_like (id, work_id, user_uuid) VALUES (#{id}, #{work_id}, #{user_uuid})")
    int insert(WorkLikeModel model);

    @Delete("DELETE FROM work_like WHERE work_id = #{work_id} AND user_uuid = #{user_uuid}")
    int deleteByWorkIdAndUserUuid(@Param("work_id") String workId, @Param("user_uuid") String userUuid);

    @Select("SELECT w.id, w.title, w.cover_image, w.view_count, w.like_count, w.status, " +
            "w.author, w.author_nickname, w.uploader, w.is_claim, w.student, w.created_at, w.updated_at " +
            "FROM work_like wl " +
            "JOIN work w ON wl.work_id = w.id " +
            "JOIN user_basic ub ON wl.user_uuid = ub.uuid " +
            "WHERE ub.username = #{username} AND w.status = #{status} " +
            "ORDER BY wl.created_at DESC " +
            "LIMIT #{offset}, #{page_size}")
    List<WorkModel> selectPublicLikedWorksByUsername(
            @Param("username") String username,
            @Param("status") WorkStatusEnum status,
            @Param("offset") Integer offset,
            @Param("page_size") Integer pageSize);

    @Select("SELECT COUNT(*) FROM work_like wl " +
            "JOIN work w ON wl.work_id = w.id " +
            "JOIN user_basic ub ON wl.user_uuid = ub.uuid " +
            "WHERE ub.username = #{username} AND w.status = #{status}")
    Long countPublicLikedWorksByUsername(
            @Param("username") String username,
            @Param("status") WorkStatusEnum status);
}
