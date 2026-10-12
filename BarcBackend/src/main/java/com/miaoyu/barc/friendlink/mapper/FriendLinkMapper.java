package com.miaoyu.barc.friendlink.mapper;

import com.miaoyu.barc.friendlink.model.FriendLinkModel;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * 友情链接数据访问层
 *
 * 约定：
 * 1、所有查询都带 deleted_at IS NULL（软删除）；
 * 2、排序统一按 sort_order ASC, created_at ASC，保证序号相同时也有一致的稳定次序；
 * 3、上移 / 下移需要「相邻的那一条」，因此在同一个 Mapper 里提供 up / down 两个方向查询，
 *    语义显式（不用把一个 direction 参数塞进 SQL 里做分支）。
 */
@Mapper
public interface FriendLinkMapper {

    @Insert("INSERT INTO friend_link(id, title, url, logo_key, intro, sort_order, enabled, created_by) " +
            "VALUES(#{id}, #{title}, #{url}, #{logo_key}, #{intro}, #{sort_order}, #{enabled}, #{created_by})")
    int insert(FriendLinkModel model);

    /** 管理端列表：全部未删除的友链 */
    @Select("SELECT * FROM friend_link WHERE deleted_at IS NULL ORDER BY sort_order ASC, created_at ASC, id ASC")
    List<FriendLinkModel> selectAll();

    /** 用户端公开列表（本期不按 enabled 过滤，见 V13 迁移脚本注释） */
    @Select("SELECT * FROM friend_link WHERE deleted_at IS NULL ORDER BY sort_order ASC, created_at ASC, id ASC")
    List<FriendLinkModel> selectAllForPublic();

    @Select("SELECT * FROM friend_link WHERE id = #{id} AND deleted_at IS NULL")
    FriendLinkModel selectById(String id);

    /** 当前最大排序序号（无记录时返回 0，新增时取 +1） */
    @Select("SELECT COALESCE(MAX(sort_order), 0) FROM friend_link WHERE deleted_at IS NULL")
    int selectMaxSortOrder();

    @Update("UPDATE friend_link SET title = #{title}, url = #{url}, logo_key = #{logo_key}, " +
            "intro = #{intro}, updated_at = CURRENT_TIMESTAMP WHERE id = #{id} AND deleted_at IS NULL")
    int update(FriendLinkModel model);

    @Update("UPDATE friend_link SET sort_order = #{sortOrder}, updated_at = CURRENT_TIMESTAMP " +
            "WHERE id = #{id} AND deleted_at IS NULL")
    int updateSortOrder(@Param("id") String id, @Param("sortOrder") int sortOrder);

    @Update("UPDATE friend_link SET deleted_at = CURRENT_TIMESTAMP WHERE id = #{id} AND deleted_at IS NULL")
    int softDelete(String id);
}
