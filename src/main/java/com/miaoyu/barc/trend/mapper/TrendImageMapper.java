package com.miaoyu.barc.trend.mapper;

import com.miaoyu.barc.trend.model.TrendImageModel;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface TrendImageMapper {
    @Select("SELECT * FROM trend_image WHERE trend_id = #{trend_id}")
    List<TrendImageModel> selectByTrendId(@Param("trend_id") String trendId);
    @Select("SELECT * FROM trend_image WHERE id = #{id}")
    TrendImageModel selectById(@Param("id") String id);

    @Insert("INSERT INTO trend_image (id, trend_id, sort, image_url, image_name) VALUES (#{id}, #{trend_id}, #{sort}, #{image_url}, #{image_name})")
    boolean insert(TrendImageModel trendImageModel);
    @Delete("DELETE FROM trend_image WHERE id = #{id}")
    boolean delete(@Param("id") String id);
    @Update("UPDATE trend_image SET sort = #{sort}, image_url = #{image_url}, image_name = #{image_name} WHERE id = #{id}")
    boolean update(TrendImageModel trendImageModel);
}
