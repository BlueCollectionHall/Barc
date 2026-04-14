package com.miaoyu.barc.api.mapper;

import com.miaoyu.barc.api.model.SchoolClubModel;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface ClubMapper {
    @Select("SELECT * FROM club")
    List<SchoolClubModel> selectAll();
    @Select("SELECT c.* FROM club c JOIN school_club sc ON c.id = sc.club_id WHERE sc.school_id = #{school_id}")
    List<SchoolClubModel> selectBySchool(@Param("school_id") String schoolId);
    @Select("SELECT * FROM club WHERE id = #{club_id}")
    SchoolClubModel selectById(@Param("club_id") String clubId);
}
