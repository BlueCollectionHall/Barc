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
        if (dto == null) dto = new PageRequestDto();
        this.pageNum = dto.getPage_num() == null || dto.getPage_num() < 1 ? 1 : dto.getPage_num();
        this.pageSize = dto.getPage_size() == null || dto.getPage_size() < 1 ? 10 : Math.min(dto.getPage_size(), 100);
        // 使用 long 计算并限制深分页，防止整数溢出与超大扫描。
        long requestedOffset = (long) (pageNum - 1) * pageSize;
        if (requestedOffset > 1_000_000L) throw com.miaoyu.barc.utils.RequestInput.invalid("页码超出支持范围，请缩小筛选条件");
        this.offset = (int) requestedOffset;
    }

    public PageInitPojo(Integer pageNum, Integer pageSize) {
        this(request(pageNum, pageSize));
    }
    private static PageRequestDto request(Integer pageNum, Integer pageSize) {
        PageRequestDto request = new PageRequestDto(); request.setPage_num(pageNum); request.setPage_size(pageSize); return request;
    }
}
