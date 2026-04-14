package com.miaoyu.barc.api.mapper;

import com.miaoyu.barc.api.model.StudentModel;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface StudentMapper {
    @Select("SELECT * FROM students")
    List<StudentModel> selectAll();
    @Select("SELECT * FROM students WHERE school = #{school_id}")
    List<StudentModel> selectBySchool(@Param("school_id") String schoolId);
    @Select("SELECT * FROM students WHERE club = #{club_id}")
    List<StudentModel> selectByClub(@Param("club_id") String clubId);
    @Select("SELECT * FROM students WHERE id = #{student_id}")
    StudentModel selectById(@Param("student_id") String studentId);
}
