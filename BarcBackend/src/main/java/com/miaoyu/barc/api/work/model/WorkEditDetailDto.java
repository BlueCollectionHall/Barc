package com.miaoyu.barc.api.work.model;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 管理端作品编辑详情 DTO
 * 包含作品基本信息、已签名的封面/内容图URL、收录者/归属者信息、关联学校/部团/学生名称
 */
@Setter
@Getter
public class WorkEditDetailDto {
    private WorkModel work;                  // 作品基本信息
    private String cover_image_url;          // 已签名的封面图URL
    private List<String> content_image_urls; // 已签名的内容图URL列表
    private List<ContentImageDto> content_images; // 带图片ID的内容图，用于作者侧安全删除
    private String uploader_nickname;        // 收录者昵称
    private String author_display;           // 归属者显示：已认领=真实昵称，未认领=author_nickname
    private String school_name;              // 关联学园名
    private String club_name;                // 关联部团名
    private String student_name;             // 关联学生名

    @Setter
    @Getter
    public static class ContentImageDto {
        private String id;
        private String url;
        private Integer sort;
    }
}
