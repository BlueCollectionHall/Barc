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

    @Insert("INSERT INTO trend_image (id, trend_id, sort, image_url, image_name, object_key) VALUES (#{id}, #{trend_id}, #{sort}, '', #{image_name}, #{object_key})")
    boolean insert(TrendImageModel trendImageModel);
    @Delete("DELETE FROM trend_image WHERE id = #{id}")
    boolean delete(@Param("id") String id);
    // 重排只写排序，不把读取时生成的签名 URL 回写数据库。
    @Update("UPDATE trend_image SET sort = #{sort} WHERE id = #{id}")
    boolean update(TrendImageModel trendImageModel);

    @Insert("INSERT INTO legacy_object_retirement(provider,bucket,object_key) VALUES('minio','barctemp',#{key})")
    int retireLegacy(@Param("key") String key);
}
