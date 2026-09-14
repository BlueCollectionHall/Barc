package com.miaoyu.barc.background.mapper;

import com.miaoyu.barc.background.model.BackgroundImageModel;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface BackgroundImageMapper {

    @Insert("INSERT INTO background_image(id, module, object_key, filename, sort_order, enabled, created_by, time_period, festival) " +
            "VALUES(#{id}, #{module}, #{object_key}, #{filename}, #{sort_order}, #{enabled}, #{created_by}, #{time_period}, #{festival})")
    int insert(BackgroundImageModel model);

    @Select("SELECT * FROM background_image WHERE deleted_at IS NULL AND module = #{module} " +
            "ORDER BY sort_order ASC, created_at ASC")
    List<BackgroundImageModel> selectByModule(String module);

    @Select("SELECT * FROM background_image WHERE deleted_at IS NULL AND module = #{module} AND enabled = 1 " +
            "ORDER BY sort_order ASC, created_at ASC")
    List<BackgroundImageModel> selectEnabledByModule(String module);

    @Select("SELECT * FROM background_image WHERE deleted_at IS NULL AND enabled = 1 " +
            "ORDER BY module ASC, sort_order ASC, created_at ASC")
    List<BackgroundImageModel> selectAllEnabled();

    @Select("SELECT * FROM background_image WHERE id = #{id} AND deleted_at IS NULL")
    BackgroundImageModel selectById(String id);

    /**
     * 统计绑定某节日的背景图数量
     */
    @Select("SELECT COUNT(1) FROM background_image WHERE deleted_at IS NULL AND festival = #{festivalId}")
    int countByFestival(@Param("festivalId") String festivalId);

    @Select("SELECT COALESCE(MAX(sort_order), 0) FROM background_image WHERE deleted_at IS NULL AND module = #{module}")
    int selectMaxSortOrder(String module);

    @Update("UPDATE background_image SET sort_order = #{sortOrder} WHERE id = #{id} AND deleted_at IS NULL")
    int updateSortOrder(@Param("id") String id, @Param("sortOrder") int sortOrder);

    @Update("UPDATE background_image SET enabled = #{enabled}, updated_at = CURRENT_TIMESTAMP " +
            "WHERE id = #{id} AND deleted_at IS NULL")
    int updateEnabled(@Param("id") String id, @Param("enabled") boolean enabled);

    @Update("UPDATE background_image SET time_period = #{timePeriod}, festival = #{festival}, updated_at = CURRENT_TIMESTAMP " +
            "WHERE id = #{id} AND deleted_at IS NULL")
    int updateScene(@Param("id") String id, @Param("timePeriod") String timePeriod, @Param("festival") String festival);

    /**
     * 解绑某个节日的全部背景图（节日被删除时调用）
     *
     * 设计原因：背景图的「节日」存的是节日 ID，一旦节日被删除，
     * 这些背景图会指向一个不存在的节日，导致用户端节日优先逻辑取不到图、程序链路出错。
     * 因此删除节日时统一把这些背景图划归到「无节日」通用图（festival = NULL）。
     */
    @Update("UPDATE background_image SET festival = NULL, updated_at = CURRENT_TIMESTAMP " +
            "WHERE festival = #{festivalId} AND deleted_at IS NULL")
    int clearFestival(@Param("festivalId") String festivalId);

    @Update("UPDATE background_image SET deleted_at = CURRENT_TIMESTAMP WHERE id = #{id} AND deleted_at IS NULL")
    int softDelete(String id);
}
