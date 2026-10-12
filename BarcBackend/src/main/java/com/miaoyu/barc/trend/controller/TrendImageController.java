package com.miaoyu.barc.trend.controller;

import com.miaoyu.barc.response.ErrorR;
import com.miaoyu.barc.response.ResourceR;
import com.miaoyu.barc.trend.mapper.TrendImageMapper;
import com.miaoyu.barc.trend.mapper.TrendSelectMapper;
import com.miaoyu.barc.trend.model.TrendImageModel;
import com.miaoyu.barc.trend.model.TrendModel;
import com.miaoyu.barc.trend.service.TrendImageService;
import com.miaoyu.barc.utils.J;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/trend/image")
public class TrendImageController {
    @Autowired
    private TrendImageService trendImageService;
    @Autowired
    private TrendSelectMapper trendSelectMapper;
    @Autowired
    private TrendImageMapper trendImageMapper;

    @PostMapping("/upload")
    public ResponseEntity<J> uploadTrendImageControl(HttpServletRequest request, @RequestParam("trend_id") String trendId, @RequestParam("file") MultipartFile file) {
        return trendImageService.uploadTrendImageService((String) request.getAttribute("uuid"), trendId, file);
    }

    @DeleteMapping("/delete")
    public ResponseEntity<J> deleteTrendImageControl(HttpServletRequest request, @RequestParam("trend_image_id") String trendImageId) {
        return trendImageService.deleteTrendImageService((String) request.getAttribute("uuid"), trendImageId);
    }
}
