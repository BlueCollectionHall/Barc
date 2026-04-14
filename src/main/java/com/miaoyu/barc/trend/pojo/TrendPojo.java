package com.miaoyu.barc.trend.pojo;

import com.miaoyu.barc.trend.model.TrendImageModel;
import com.miaoyu.barc.trend.model.TrendModel;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
public class TrendPojo extends TrendModel {
    private List<TrendImageModel> images;
}
