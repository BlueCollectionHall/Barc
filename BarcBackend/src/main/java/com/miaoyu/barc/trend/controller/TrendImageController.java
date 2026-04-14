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
    public ResponseEntity<J> uploadTrendImageControl(HttpServletRequest request, @RequestParam("trend_id") String trendId, @RequestBody MultipartFile file) {
        TrendModel trend = trendSelectMapper.selectById(trendId);
        if (trend == null) {
            return ResponseEntity.ok(new ResourceR().resourceSuch(false, null));
        }
        return trendImageService.uploadTrendImageService(
                request.getAttribute("uuid").toString(),
                trend.getAuthor(),
                trendId,
                file);
    }

    @DeleteMapping("/delete")
    public ResponseEntity<J> deleteTrendImageControl(HttpServletRequest request, @RequestParam("trend_image_id") String trendImageId) {
        TrendImageModel waitDeleteImage = trendImageMapper.selectById(trendImageId);
        if (waitDeleteImage == null) {
            return ResponseEntity.ok(new ErrorR().normal("系统未找到图片索引！"));
        }
        TrendModel trend = trendSelectMapper.selectById(waitDeleteImage.getTrend_id());
        if (trend == null) {
            return ResponseEntity.ok(new ResourceR().resourceSuch(false, null));
        }
        return trendImageService.deleteTrendImageService(
                request.getAttribute("uuid").toString(),
                trend.getAuthor(),
                trend.getId(),
                waitDeleteImage);
    }
}
