package com.miaoyu.barc.api.work.mapper;

import com.miaoyu.barc.api.work.model.WorkCategoryModel;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Select;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

public interface WorkCategoryMapper {
    @Select("SELECT * FROM work_category WHERE id = #{id}")
    WorkCategoryModel selectById(@RequestParam("id") String id);
    /** 取得父作品锁后进行当前读，避免复用普通查询在 MyBatis 一级缓存中的旧关联。 */
    @Select("SELECT * FROM work_category WHERE id = #{id} FOR UPDATE")
    WorkCategoryModel selectByIdForUpdate(@org.apache.ibatis.annotations.Param("id") String id);
    @Select("SELECT * FROM work_category WHERE work_id = #{work_id}")
    List<WorkCategoryModel> selectByWorkId(@RequestParam("work_id") String workId);

    @Select("SELECT u.uuid FROM user_basic u JOIN work w ON w.author = u.uuid JOIN work_category wc ON wc.work_id = w.id WHERE wc.id = #{id}")
    String selectWorkAuthorById(@RequestParam("id") String id);

    @Insert("INSERT INTO work_category (id, work_id, category_id) VALUES (#{id}, #{work_id}, #{category_id})")
    boolean insert(WorkCategoryModel request);
    @Delete("DELETE FROM work_category WHERE id = #{id}")
    boolean delete(@RequestParam("id") String id);
    @Delete("DELETE FROM work_category WHERE work_id = #{work_id} AND category_id = #{category_id}")
    boolean deleteByWorkAndCategoryId(@RequestParam("work_id") String workId, @RequestParam("category_id") String categoryId);
}
