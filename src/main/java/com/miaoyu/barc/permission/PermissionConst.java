package com.miaoyu.barc.permission;

import com.miaoyu.barc.user.enumeration.UserIdentityEnum;
import com.miaoyu.barc.utils.dto.ValueLabelDto;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class PermissionConst {
    public static final int USER = 1; // 会员 0000 0001 浏览、留言、点赞、收藏、反馈
    public static final int UPPER = 1 << 1; // 收录员 0000 0010 发布、收录、处置作品
    public static final int CREATOR = 1 << 2; // 创作者 0000 0100 与收录员相同

    public static final int DISCIPLINARY_COMMITTEE = 1; // 风纪委员 0000 0001 警告、公告、审批认领
    public static final int FIR_MAINTAINER = 1 << 1; // 一级管理员 0000 0010 接收、处理（评论、留言）举报
    public static final int SEC_MAINTAINER = 1 << 2; // 二级管理员 0000 0100 接收、处理（作品）举报
    public static final int THI_MAINTAINER = 1 << 3; // 三级管理员 0000 1000 接收、处理（用户）举报
    public static final int ADMINISTRATOR = 1 << 4; // 副馆长 0001 0000 管理所有非馆长用户
    public static final int ADVANCED_ADMINISTRATOR = 1 << 5; // 馆长 0010 0000 管理所有副馆长

    public List<ValueLabelDto> permissionsByIdentity(UserIdentityEnum identity) {
        List<ValueLabelDto> list = new ArrayList<>();
        switch (identity) {
            case USER: {
                list.add(new ValueLabelDto(USER, toString(identity, USER)));
                list.add(new ValueLabelDto(UPPER, toString(identity, UPPER)));
                list.add(new ValueLabelDto(CREATOR, toString(identity, CREATOR)));
                return list;
            } case MANAGER: {
                list.add(new ValueLabelDto(DISCIPLINARY_COMMITTEE, toString(identity, DISCIPLINARY_COMMITTEE)));
                list.add(new ValueLabelDto(FIR_MAINTAINER, toString(identity, FIR_MAINTAINER)));
                list.add(new ValueLabelDto(SEC_MAINTAINER, toString(identity, SEC_MAINTAINER)));
                list.add(new ValueLabelDto(THI_MAINTAINER, toString(identity, THI_MAINTAINER)));
                list.add(new ValueLabelDto(ADMINISTRATOR, toString(identity, ADMINISTRATOR)));
                list.add(new ValueLabelDto(ADVANCED_ADMINISTRATOR, toString(identity, ADVANCED_ADMINISTRATOR)));
                return list;
            } default: return null;
        }
    }

    public String toString(UserIdentityEnum identity, int permission) {
        return switch (identity) {
            case USER -> switch (permission) {
                case USER -> "会员";
                case UPPER -> "收录员";
                case CREATOR -> "创作者";
                default -> null;
            };
            case MANAGER -> switch (permission) {
                case DISCIPLINARY_COMMITTEE -> "风纪委员";
                case FIR_MAINTAINER -> "一级管理员";
                case SEC_MAINTAINER -> "二级管理员";
                case THI_MAINTAINER -> "三级管理员";
                case ADMINISTRATOR -> "副馆长";
                case ADVANCED_ADMINISTRATOR -> "馆长";
                default -> null;
            };
        };
    }
}
