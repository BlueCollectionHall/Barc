package com.miaoyu.barc.api.controller;

import com.miaoyu.barc.annotation.ApiPath;
import com.miaoyu.barc.annotation.IgnoreAuth;
import com.miaoyu.barc.api.service.ClubService;
import com.miaoyu.barc.utils.J;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/club")
public class ClubController {
    @Autowired
    private ClubService clubService;

    /**获取BA中全部的club实体
     * @return List类型中包含BA中全部的club实体*/
    @IgnoreAuth
    @GetMapping("/all")
    public ResponseEntity<J> getBaAllClubControl() {
        return clubService.getAllClubsService();
    }

    /**根据school_id获取BA中全部符合条件的club实体
     * @param schoolId school的id
     * @return List类型中包含BA中符合school_id条件的全部club实体*/
    @IgnoreAuth
    @GetMapping("/clubs_by_school")
    public ResponseEntity<J> getBaClubsBySchoolControl(
            @RequestParam("school_id") String schoolId
    ) {
        return clubService.getClubsBySchoolService(schoolId);
    }

    /**根据club_id获取符合条件的唯一club实体
     * @param clubId club的id
     * @return 符合条件的唯一club实体*/
    @IgnoreAuth
    @GetMapping("/only")
    public ResponseEntity<J> getBaClubOnlyControl(
            @RequestParam("club_id") String clubId
    ) {
        return clubService.getClubByIdService(clubId);
    }

    /**上传club参数
     * @param token 管理者以上权限者的令牌
     * @param upType 上传方式"upload"/"update"
     * @param request club的实体
     * @return 修改是否正确完成*/
    @PostMapping("/up")
    public Map<String, Object> upBaSchoolClubControl(
            @RequestHeader("Authorization") String token,
            @RequestParam("up_type") String upType,
            @RequestBody J request
    ) {
        return null;
    }
}
