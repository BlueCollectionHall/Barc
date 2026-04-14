package com.miaoyu.barc.utils.pojo;

import com.miaoyu.barc.utils.dto.PageRequestDto;
import lombok.Data;

/**
 * 初始化接收的PageRequestDto
 * */

@Data
public class PageInitPojo {
    private Integer pageNum;
    private Integer pageSize;
    private Integer offset;

    public PageInitPojo(PageRequestDto dto) {
        this.pageNum = dto.getPage_num() == null || dto.getPage_num() < 1 ? 1 : dto.getPage_num();
        this.pageSize = dto.getPage_size() == null || dto.getPage_size() < 1 ? 10 : dto.getPage_size();
        this.offset = (pageNum - 1) * pageSize;
    }
}
