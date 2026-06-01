package com.miaoyu.barc.api.controller;

import com.miaoyu.barc.annotation.IgnoreAuth;
import com.miaoyu.barc.api.model.SchoolClubModel;
import com.miaoyu.barc.api.service.ClubService;
import com.miaoyu.barc.utils.J;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/club")
public class ClubController {
    @Autowired
    private ClubService clubService;

    @IgnoreAuth
    @GetMapping("/list")
    public ResponseEntity<J> list(
            @RequestParam(value = "school_id", required = false) String schoolId,
            @RequestParam(value = "keyword", required = false, defaultValue = "") String keyword,
            @RequestParam(value = "page", required = false, defaultValue = "1") int page,
            @RequestParam(value = "size", required = false, defaultValue = "20") int size) {
        return clubService.getClubsByPage(schoolId, keyword, page, size);
    }

    @IgnoreAuth
    @GetMapping("/{id}")
    public ResponseEntity<J> getById(@PathVariable("id") String id) {
        return clubService.getClubByIdService(id);
    }

    @PostMapping("")
    public ResponseEntity<J> create(@RequestBody SchoolClubModel club, @RequestParam("school_id") String schoolId, HttpServletRequest request) {
        String uuid = (String) request.getAttribute("uuid");
        return clubService.createClub(uuid, club, schoolId);
    }

    @PutMapping("/{id}")
    public ResponseEntity<J> update(@PathVariable("id") String id, @RequestBody SchoolClubModel club, HttpServletRequest request) {
        String uuid = (String) request.getAttribute("uuid");
        // 从 request body 中读取 school 字段（前端发送 payload.school）
        String schoolId = club.getSchool();
        // 清除 club 对象中的 school 字段，避免影响后续逻辑
        club.setSchool(null);
        return clubService.updateClub(uuid, id, club, schoolId);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<J> delete(@PathVariable("id") String id, HttpServletRequest request) {
        String uuid = (String) request.getAttribute("uuid");
        return clubService.deleteClub(uuid, id);
    }

    @IgnoreAuth
    @GetMapping("/check_id_available")
    public ResponseEntity<J> checkIdAvailable(@RequestParam("id") String id) {
        return clubService.checkIdAvailable(id);
    }
}
