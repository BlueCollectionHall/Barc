package com.miaoyu.barc.api.mapper;

import com.miaoyu.barc.api.model.SchoolModel;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface SchoolMapper {
    @Select("SELECT * FROM schools")
    List<SchoolModel> selectAll();
    @Select("SELECT * FROM schools WHERE id = #{school_id}")
    SchoolModel selectById(@Param("school_id") String schoolId);
}
