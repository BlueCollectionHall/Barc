package com.miaoyu.barc.user.mapper;

import com.miaoyu.barc.user.model.UserArchiveModel;
import org.apache.ibatis.annotations.Update;

public interface UserPermissionMapper {
    @Update("UPDATE user_archive SET permission = #{permission}, identity = #{identity} WHERE uuid = #{uuid}")
    boolean update(UserArchiveModel userArchive);
}
