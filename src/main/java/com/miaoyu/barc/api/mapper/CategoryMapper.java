package com.miaoyu.barc.api.mapper;

import com.miaoyu.barc.api.pojo.CategoryPojo;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface CategoryMapper {
    @Select("SELECT * FROM category")
    List<CategoryPojo> selectAll();

    @Select("SELECT c.id, c.name, c.sort, c.icon FROM category c WHERE level = 1")
    List<CategoryPojo> selectAllTop();
}
