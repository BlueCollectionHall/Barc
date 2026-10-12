package com.miaoyu.barc.notice.mapper;

import com.miaoyu.barc.notice.model.NoticeModel;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

public interface NoticeMapper {
    /** 全表最新公告；同时覆盖近期有记录与历史兜底两种情况。 */
    NoticeModel latestWithoutDayLimit();

    List<NoticeModel> selectByPage(@Param("offset") Integer offset, @Param("pageSize") Integer pageSize, @Param("condition")Map<String, Object> condition);
    Long countByPage(@Param("condition")Map<String, Object> condition);

    NoticeModel selectById(@Param("id") String noticeId);

    boolean insert(NoticeModel notice);

    boolean update(NoticeModel notice);

    boolean deleteById(@Param("id") String noticeId);
}
