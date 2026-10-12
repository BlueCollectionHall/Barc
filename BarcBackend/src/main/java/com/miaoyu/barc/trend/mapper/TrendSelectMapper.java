package com.miaoyu.barc.trend.mapper;

import com.miaoyu.barc.trend.model.TrendModel;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface TrendSelectMapper {
    /** 所有动态列表共享筛选和固定排序，搜索词仅作为绑定参数。 */
    String FILTER = "<where><if test='author != null'>author=#{author}</if>"
            + "<if test='keyword != null'> AND (title LIKE CONCAT('%',#{keyword},'%') OR content LIKE CONCAT('%',#{keyword},'%'))</if></where>";
    @Select("<script>SELECT * FROM trend " + FILTER + " ORDER BY updated_at DESC,id DESC LIMIT #{offset},#{limit}</script>")
    List<TrendModel> selectPage(@Param("author") String author, @Param("keyword") String keyword, @Param("offset") int offset, @Param("limit") int limit);
    @Select("<script>SELECT COUNT(*) FROM trend " + FILTER + "</script>")
    long countPage(@Param("author") String author, @Param("keyword") String keyword);

    @Select("SELECT * FROM trend WHERE id = #{id}")
    TrendModel selectById(@Param("id") String trendId);

    /** 图片追加、删除和父动态删除共用行锁，稳定归属及排序。 */
    @Select("SELECT * FROM trend WHERE id=#{id} FOR UPDATE")
    TrendModel selectByIdForUpdate(@Param("id") String trendId);
}
