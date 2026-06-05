package com.miaoyu.barc.api.work.controller;

import com.miaoyu.barc.annotation.IgnoreAuth;
import com.miaoyu.barc.api.work.model.WorkLikeToggleDto;
import com.miaoyu.barc.api.work.service.WorkLikeService;
import com.miaoyu.barc.response.ErrorR;
import com.miaoyu.barc.utils.J;
import com.miaoyu.barc.utils.dto.PageRequestDto;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/work/like")
public class WorkLikeController {
    @Autowired
    private WorkLikeService workLikeService;

    @PostMapping("/toggle")
    public ResponseEntity<J> toggleLikeControl(HttpServletRequest request, @RequestBody WorkLikeToggleDto dto) {
        Object uuid = request.getAttribute("uuid");
        if (uuid == null || uuid.toString().trim().isEmpty()) {
            return ResponseEntity.ok(new ErrorR().normal("用户未登录"));
        }
        return workLikeService.toggleLike(uuid.toString(), dto);
    }

    @IgnoreAuth
    @PostMapping("/list_by_username")
    public ResponseEntity<J> listLikedWorksByUsernameControl(
            @RequestParam("username") String username,
            @RequestBody(required = false) PageRequestDto pageRequestDto
    ) {
        return workLikeService.listLikedWorksByUsername(username, pageRequestDto);
    }
}
