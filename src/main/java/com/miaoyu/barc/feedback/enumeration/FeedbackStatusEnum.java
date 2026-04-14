package com.miaoyu.barc.feedback.enumeration;

import com.miaoyu.barc.utils.dto.ValueLabelDto;

import java.util.Arrays;
import java.util.List;

public enum FeedbackStatusEnum {
    PENDING("待处理"),
    PROCESSING("处理中"),
    COMPLETED("已完成"),
    REJECTED("已驳回");

    private final String name;

    FeedbackStatusEnum(String name) {
        this.name = name;
    }

    public String getName() {
        return this.name;
    }

    private static final List<ValueLabelDto> CACHED_OPTIONS = Arrays.stream(FeedbackStatusEnum.values())
            .map(e -> new ValueLabelDto(e.name(), e.getName()))
            .toList();

    public static List<ValueLabelDto> getOptions() {
        return CACHED_OPTIONS;
    }
}
