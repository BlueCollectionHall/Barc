package com.miaoyu.barc.api.work.model;

/** 认领与作者指派选择器只返回标识和标题，不暴露正文、审核信息及存储对象 key。 */
public record WorkSearchView(String id, String title) { }
