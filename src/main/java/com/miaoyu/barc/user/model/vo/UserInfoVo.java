package com.miaoyu.barc.user.model.vo;

import com.miaoyu.barc.user.enumeration.UserIdentityEnum;
import lombok.Data;

import java.time.LocalDate;

@Data
public class UserInfoVo {
    private String uuid;
    private String username;
    private String nickname;
    private String avatar;
    private Integer age;
    private LocalDate birthday;
    private Integer gender;
    private UserIdentityEnum identity;
    private Integer permission;
}
