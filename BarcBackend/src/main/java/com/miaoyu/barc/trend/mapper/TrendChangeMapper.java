package com.miaoyu.barc.trend.mapper;

import com.miaoyu.barc.trend.model.TrendModel;
import org.apache.ibatis.annotations.*;

@Mapper
public interface TrendChangeMapper {
    @Insert("INSERT INTO trend (id, title, content, author) VALUES (#{id}, #{title}, #{content}, #{author})")
    boolean insert(TrendModel trendModel);

    @Update("UPDATE trend SET title = #{title}, content = #{content} WHERE id = #{id}")
    boolean update(TrendModel trendModel);

    @Delete("DELETE FROM trend WHERE id = #{id}")
    boolean delete(@Param("id") String id);
}
