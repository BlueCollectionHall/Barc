package com.miaoyu.barc.api.work.mapper;

import com.miaoyu.barc.api.work.model.WorkViewLogModel;
import org.apache.ibatis.annotations.Insert;

public interface WorkViewLogMapper {
    @Insert("INSERT INTO work_view_log " +
            "(id, work_id, view_date, viewer_type, viewer_user_uuid, viewer_ipv4, viewer_ipv6, viewer_ip_hash, dedupe_key) VALUES " +
            "(#{id}, #{work_id}, #{view_date}, #{viewer_type}, #{viewer_user_uuid}, #{viewer_ipv4}, #{viewer_ipv6}, #{viewer_ip_hash}, #{dedupe_key})")
    int insert(WorkViewLogModel model);
}
