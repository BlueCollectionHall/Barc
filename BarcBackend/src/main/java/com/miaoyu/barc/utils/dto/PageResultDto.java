package com.miaoyu.barc.utils.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 分页查询结果 DTO类
 * */

@Data
@AllArgsConstructor
@NoArgsConstructor
public class PageResultDto<T> {
    private Long total; // 总记录数
    private List<T> list; // 数据列表
    private Integer page_num; // 当前页
    private Integer page_size; // 每页条数
    private Integer total_page; // 总页数
}
