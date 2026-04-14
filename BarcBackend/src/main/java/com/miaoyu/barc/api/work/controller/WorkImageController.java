package com.miaoyu.barc.api.work.controller;

import com.miaoyu.barc.annotation.IgnoreAuth;
import com.miaoyu.barc.api.work.mapper.WorkImageMapper;
import com.miaoyu.barc.api.work.mapper.WorkMapper;
import com.miaoyu.barc.api.work.model.WorkImageModel;
import com.miaoyu.barc.api.work.model.WorkModel;
import com.miaoyu.barc.api.work.service.WorkImageService;
import com.miaoyu.barc.response.ResourceR;
import com.miaoyu.barc.utils.J;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/work/image")
public class WorkImageController {
    @Autowired
    private WorkImageService workImageService;
    @Autowired
    private WorkImageMapper workImageMapper;
    @Autowired
    private WorkMapper workMapper;

    @GetMapping("/images_by_work")
    @IgnoreAuth
    public ResponseEntity<J> getImagesByWorkControl(@RequestParam("work_id") String workId) {
        return workImageService.getImagesByWorkService(workId);
    }
    @PostMapping("/upload")
    public ResponseEntity<J> uploadWorkImageControl(
            HttpServletRequest request,
            @RequestParam("work_id") String workId,
            MultipartFile file
    ) {
        return null;
    }
    @DeleteMapping("/delete")
    public ResponseEntity<J> deleteWorkImageControl(
            HttpServletRequest request,
            @RequestParam("work_image_id") String workImageId
    ) {
        WorkImageModel workImage = workImageMapper.selectById(workImageId);
        if (workImage == null) {
            return ResponseEntity.ok(new ResourceR().resourceSuch(false, null));
        }
        WorkModel work = workMapper.selectById(workImage.getWork_id());
        return workImageService.deleteWorkImageService(request.getAttribute("uuid").toString(), work.getAuthor(), workImageId);
    }
}
