package com.miaoyu.barc.api.work.enumeration;

/**
 * 作品上传审核状态。
 *
 * <p>审核状态与作品可见性状态相互独立：即使作品设置为 PUBLIC 或 PRIVATE，
 * 在审核通过前也只能由作品作者、收录者和具备作品权限的管理员查看。</p>
 */
public enum WorkReviewStatusEnum {
    PENDING("审核中"),
    APPROVED("审核通过"),
    REJECTED("审核未通过");

    private final String name;

    WorkReviewStatusEnum(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }
}
