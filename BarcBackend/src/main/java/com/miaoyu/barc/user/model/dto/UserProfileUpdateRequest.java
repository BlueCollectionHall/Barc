package com.miaoyu.barc.user.model.dto;

import lombok.Getter;
import lombok.Setter;
import java.time.LocalDate;

/** 个人资料写入白名单；身份、权限、年龄和修改时间均不能由客户端指定。 */
@Getter
@Setter
public class UserProfileUpdateRequest {
    private String uuid;
    private String nickname;
    private String avatar;
    private Integer gender;
    private LocalDate birthday;
}
