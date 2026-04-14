package com.miaoyu.barc.utils.dto;

import lombok.Data;

import java.util.HashMap;
import java.util.Map;

/**
 * 分页查询接收 DTO类
 * */

@Data
public class PageRequestDto {
    private Integer page_num = 1; // 当前页码
    private Integer page_size = 10; // 每页条数
    private String sort_field; // 排序字段
    private String sort_order; // 排序方向
    private Map<String, Object> params = new HashMap<>(); // 查询参数

}
