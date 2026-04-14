package com.miaoyu.barc.trend.mapper;

import com.miaoyu.barc.trend.model.TrendModel;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface TrendSelectMapper {
    @Select("SELECT * FROM trend")
    List<TrendModel> selectAll();
    @Select("SELECT * FROM trend ORDER BY updated_at DESC LIMIT #{count}")
    List<TrendModel> selectByCount(@Param("count") Integer count);
    @Select("SELECT * FROM trend WHERE author = #{uuid}")
    List<TrendModel> selectByUuid(@Param("uuid") String uuid);

    @Select("SELECT * FROM trend WHERE id = #{id}")
    TrendModel selectById(@Param("id") String trendId);
}
