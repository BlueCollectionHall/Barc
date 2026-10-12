package com.miaoyu.barc.trend.controller;

import com.miaoyu.barc.annotation.IgnoreAuth;
import com.miaoyu.barc.trend.model.TrendModel;
import com.miaoyu.barc.trend.service.TrendService;
import com.miaoyu.barc.utils.J;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Arrays;

@RestController
@RequestMapping("/trend")
public class TrendController {
    private static final Logger log = LoggerFactory.getLogger(TrendController.class);
    @Autowired
    private TrendService trendService;

    @IgnoreAuth
    @GetMapping("/all")
    public ResponseEntity<J> getAllTrendControl(@RequestParam(defaultValue = "1") Integer page, @RequestParam(defaultValue = "100") Integer size) {
        return trendService.getList(null, null, page, size);
    }

    @IgnoreAuth
    @GetMapping("/latest")
    public ResponseEntity<J> getLatestTrendControl() {
        return trendService.getLatest();
    }

    @IgnoreAuth
    @GetMapping("/new")
    public ResponseEntity<J> getNewTrendControl(@RequestParam("count") Integer count) {
        return trendService.getNewTrendService(count);
    }

    @IgnoreAuth
    @GetMapping("/trends_by_keyword")
    public ResponseEntity<J> getTrendsByKeywordControl(@RequestParam String keyword,
            @RequestParam(defaultValue = "1") Integer page, @RequestParam(defaultValue = "20") Integer size) {
        return trendService.getList(null, keyword, page, size);
    }

    @IgnoreAuth
    @GetMapping("/trends_by_uuid")
    public ResponseEntity<J> getTrendsByUuidControl(@RequestParam("uuid") String uuid,
            @RequestParam(defaultValue = "1") Integer page, @RequestParam(defaultValue = "20") Integer size) {
        return trendService.getList(uuid, null, page, size);
    }

    @GetMapping("/trends_by_me")
    public ResponseEntity<J> getTrendsByMeControl(HttpServletRequest request,
            @RequestParam(defaultValue = "1") Integer page, @RequestParam(defaultValue = "20") Integer size) {
        return trendService.getList(request.getAttribute("uuid").toString(), null, page, size);
    }

    /** 可翻页的统一动态查询，匿名搜索能力保持不变。 */
    @IgnoreAuth
    @GetMapping("/page")
    public ResponseEntity<J> getPage(@RequestParam(required = false) String author, @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") Integer page, @RequestParam(defaultValue = "20") Integer size) {
        return trendService.getPage(author, keyword, page, size);
    }

    @IgnoreAuth
    @GetMapping("/only")
    public ResponseEntity<J> getTrendOnlyControl(@RequestParam("trend_id") String trendId) {
        return trendService.getTrendOnlyService(trendId);
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<J> uploadTrendControl(
            HttpServletRequest request,
            @RequestPart("form") TrendModel trendModel,
            @RequestPart(value = "files", required = false) MultipartFile[] files
    ) {
        if (files == null) files = new MultipartFile[0];
        return trendService.uploadTrendService(request.getAttribute("uuid").toString(), trendModel, files);
    }

    @DeleteMapping("/delete")
    public ResponseEntity<J> deleteTrendControl(HttpServletRequest request, @RequestParam("trend_id") String trendId) {
        return trendService.deleteTrendService(request.getAttribute("uuid").toString(), trendId);
    }
}
