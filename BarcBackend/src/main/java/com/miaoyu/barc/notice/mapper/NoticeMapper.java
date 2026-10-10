package com.miaoyu.barc.notice.mapper;

import com.miaoyu.barc.notice.model.NoticeModel;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

public interface NoticeMapper {
    /**
     * 按天数范围查询最新的一条公告
     *
     * @param day 日期范围（天），只查询 updated_at 在该范围内的公告
     * @return 范围内最新的一条公告，范围内没有公告时返回 null
     */
    NoticeModel latest(@Param("day") Integer day);

    /**
     * 查询全表最新的一条公告（不限时间）
     *
     * 用途：/notice/latest 接口在天数范围内查不到公告时的回退兜底，
     * 保证只要 notice 表中存在公告，前端首页就永远不会显示“暂无公告”。
     *
     * @return 全表按 updated_at 倒序的第一条公告，表中没有公告时返回 null
     */
    NoticeModel latestWithoutDayLimit();

    List<NoticeModel> selectByPage(@Param("offset") Integer offset, @Param("pageSize") Integer pageSize, @Param("condition")Map<String, Object> condition);
    Long countByPage(@Param("condition")Map<String, Object> condition);

    NoticeModel selectById(@Param("id") String noticeId);

    boolean insert(NoticeModel notice);

    boolean update(NoticeModel notice);

    boolean deleteById(@Param("id") String noticeId);
}
