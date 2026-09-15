package com.miaoyu.barc.festival.mapper;

import com.miaoyu.barc.festival.model.FestivalModel;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * 节日数据访问层
 *
 * 说明：
 * 1、节日区间的起止用「统一索引」start_index/end_index（月 * 32 + 日）表达，便于比较与排序；
 * 2、区间是否重叠重合、某天是否属于某节日，都交由 {@link com.miaoyu.barc.festival.utils.MonthDayRangeUtils}
 *    在内存中判定 —— 因为统一索引在相邻月之间留有空隙，纯 SQL 的端点比较会把
 *    毫无交集的区间误判为重叠；节日数量极少，全量取出后逐日比对既准确又足够快。
 */
@Mapper
public interface FestivalMapper {

    /**
     * 新增节日
     */
    @Insert("INSERT INTO festival(id, name, start_month, start_day, end_month, end_day, start_index, end_index, enabled, created_by) " +
            "VALUES(#{id}, #{name}, #{start_month}, #{start_day}, #{end_month}, #{end_day}, #{start_index}, #{end_index}, #{enabled}, #{created_by})")
    int insert(FestivalModel model);

    /**
     * 修改节日（名称 + 区间 + 派生统一索引）
     */
    @Update("UPDATE festival SET name = #{name}, start_month = #{start_month}, start_day = #{start_day}, " +
            "end_month = #{end_month}, end_day = #{end_day}, start_index = #{start_index}, end_index = #{end_index}, " +
            "updated_at = CURRENT_TIMESTAMP WHERE id = #{id} AND deleted_at IS NULL")
    int update(FestivalModel model);

    /**
     * 关闭 / 启用节日（关闭保留数据与背景图绑定，仅不参与用户端判定）
     */
    @Update("UPDATE festival SET enabled = #{enabled}, updated_at = CURRENT_TIMESTAMP " +
            "WHERE id = #{id} AND deleted_at IS NULL")
    int updateEnabled(@Param("id") String id, @Param("enabled") boolean enabled);

    /**
     * 逻辑删除节日
     */
    @Update("UPDATE festival SET deleted_at = CURRENT_TIMESTAMP WHERE id = #{id} AND deleted_at IS NULL")
    int softDelete(String id);

    @Select("SELECT * FROM festival WHERE id = #{id} AND deleted_at IS NULL")
    FestivalModel selectById(String id);

    /**
     * 查询「存在且启用」的节日（背景图绑定校验用，关闭中的节日不可再被绑定）
     */
    @Select("SELECT * FROM festival WHERE id = #{id} AND deleted_at IS NULL AND enabled = 1")
    FestivalModel selectEnabledById(String id);

    /**
     * 全量节日（管理端列表，含已关闭），按起始统一索引排序
     */
    @Select("SELECT * FROM festival WHERE deleted_at IS NULL ORDER BY start_index ASC, created_at ASC")
    List<FestivalModel> selectAll();

    /**
     * 启用的节日（用户端判定、背景图节日下拉用），按起始统一索引排序
     */
    @Select("SELECT * FROM festival WHERE deleted_at IS NULL AND enabled = 1 ORDER BY start_index ASC, created_at ASC")
    List<FestivalModel> selectAllEnabled();

    /**
     * 名称是否已被占用（excludeId 用于修改场景排除自身）
     */
    @Select("<script>" +
            "SELECT COUNT(1) FROM festival WHERE deleted_at IS NULL AND name = #{name} " +
            "<if test='excludeId != null'> AND id != #{excludeId} </if>" +
            "</script>")
    int countByName(@Param("name") String name, @Param("excludeId") String excludeId);
}
