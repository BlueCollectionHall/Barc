package com.miaoyu.barc.api.work.enumeration;

import com.miaoyu.barc.utils.dto.ValueLabelDto;

import java.util.Arrays;
import java.util.List;

public enum WorkStatusEnum {
    PUBLIC("公开"),
    PRIVATE("私有"),
    OFF("下架"),
    BAN("封禁");

    private static final List<ValueLabelDto> CACHED_OPTIONS = Arrays.stream(WorkStatusEnum.values())
            .map(e -> new ValueLabelDto(e.name(), e.getName()))
            .toList();
    public static List<ValueLabelDto> getOptions() {
        return CACHED_OPTIONS;
    }

    private final String name;

    WorkStatusEnum(String name) {
        this.name = name;
    }
    public String getName() {
        return name;
    }
}
