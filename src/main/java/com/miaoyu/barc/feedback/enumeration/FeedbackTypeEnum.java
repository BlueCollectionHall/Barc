package com.miaoyu.barc.feedback.enumeration;

import com.miaoyu.barc.api.work.enumeration.WorkStatusEnum;
import com.miaoyu.barc.utils.dto.ValueLabelDto;

import java.util.Arrays;
import java.util.List;

public enum FeedbackTypeEnum {
    BUG("BUG"),
    WORK("作品"),
    COMMENT("评论"),
    MESSAGE_BOARD("留言"),
    USER("用户"),
    SUGGESTION("意见反馈"),
    OTHER("其他");

    private final String name;

    FeedbackTypeEnum(String name) {
        this.name = name;
    }

    public String getName() {
        return this.name;
    }

    private static final List<ValueLabelDto> CACHED_OPTIONS = Arrays.stream(FeedbackTypeEnum.values())
            .map(e -> new ValueLabelDto(e.name(), e.getName()))
            .toList();

    public static List<ValueLabelDto> getOptions() {
        return CACHED_OPTIONS;
    }
}
