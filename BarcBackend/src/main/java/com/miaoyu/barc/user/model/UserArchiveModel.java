package com.miaoyu.barc.user.model;

import com.miaoyu.barc.user.enumeration.UserIdentityEnum;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Setter
@Getter
public class UserArchiveModel {
    private String uuid;
    private String nickname;
    private String avatar;
    private Integer gender;
    private LocalDate birthday;
    private Integer age;
    private Integer permission;
    private UserIdentityEnum identity;
    private LocalDateTime updated_at;

}
