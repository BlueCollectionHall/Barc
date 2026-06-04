package com.miaoyu.barc.api.work.controller;

import com.miaoyu.barc.api.work.model.WorkLikeToggleDto;
import com.miaoyu.barc.api.work.service.WorkLikeService;
import com.miaoyu.barc.response.ErrorR;
import com.miaoyu.barc.utils.J;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
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
}
