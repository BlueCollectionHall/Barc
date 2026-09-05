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

    @Insert("INSERT INTO background_image(id, module, object_key, filename, sort_order, enabled, created_by) " +
            "VALUES(#{id}, #{module}, #{object_key}, #{filename}, #{sort_order}, #{enabled}, #{created_by})")
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

    @Select("SELECT COALESCE(MAX(sort_order), 0) FROM background_image WHERE deleted_at IS NULL AND module = #{module}")
    int selectMaxSortOrder(String module);

    @Update("UPDATE background_image SET sort_order = #{sortOrder} WHERE id = #{id} AND deleted_at IS NULL")
    int updateSortOrder(@Param("id") String id, @Param("sortOrder") int sortOrder);

    @Update("UPDATE background_image SET enabled = #{enabled}, updated_at = CURRENT_TIMESTAMP " +
            "WHERE id = #{id} AND deleted_at IS NULL")
    int updateEnabled(@Param("id") String id, @Param("enabled") boolean enabled);

    @Update("UPDATE background_image SET deleted_at = CURRENT_TIMESTAMP WHERE id = #{id} AND deleted_at IS NULL")
    int softDelete(String id);
}
