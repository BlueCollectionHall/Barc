package com.miaoyu.barc.notice.controller;

import com.miaoyu.barc.annotation.IgnoreAuth;
import com.miaoyu.barc.notice.model.NoticeModel;
import com.miaoyu.barc.notice.service.NoticeService;
import com.miaoyu.barc.utils.J;
import com.miaoyu.barc.utils.dto.PageRequestDto;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/notice")
public class NoticeController {
    @Autowired
    private NoticeService noticeService;

    /**
     * 获取最新的公告
     * @param day 日期范围
     * @return 公告内容
     * */
    @IgnoreAuth
    @GetMapping("/latest")
    public ResponseEntity<J> latestNoticeControl(@RequestParam("day") Integer day) {
        return noticeService.latestNoticeService(day);
    }

    /**
     * 分页获取公告
     * @param dto 分页信息+查询条件
     * @return 公告列表
     * */
    @IgnoreAuth
    @PostMapping("/notices_by_page")
    public ResponseEntity<J> noticesByPageControl(@RequestBody PageRequestDto dto) {
        return noticeService.noticesByPageService(dto);
    }

    /**
     * 获取唯一公告
     * */
    @IgnoreAuth
    @GetMapping("/only")
    public ResponseEntity<J> getNoticeOnlyControl(@RequestParam("notice_id") String noticeId) {
        return noticeService.getNoticeOnlyService(noticeId);
    }

    /**
     * 上传新公告
     * @param model 公告实体
     * @return 上传成功/失败
     * */
    @PostMapping("/upload")
    public ResponseEntity<J> uploadNoticeControl(
            HttpServletRequest request,
            @RequestBody NoticeModel model
    ) {
        return noticeService.uploadNoticeService(request.getAttribute("uuid").toString(), model);
    }

    /**
     * 修改公告
     * @param model 公告实体
     * @return 修改成功/失败
     * */
    @PutMapping("/update")
    public ResponseEntity<J> updateNoticeControl(
            HttpServletRequest request,
            @RequestBody NoticeModel model
    ) {
        return noticeService.updateNoticeService(request.getAttribute("uuid").toString(), model.getAuthor(), model);
    }

    /**
     * 删除公告（副馆长）
     * @param noticeId 公告ID
     * @return 删除成功/失败
     * */
    @DeleteMapping("/delete")
    public ResponseEntity<J> deleteNoticeControl(
            HttpServletRequest request,
            @RequestParam("notice_id") String noticeId
    ) {
        return noticeService.deleteNoticeService(request.getAttribute("uuid").toString(), noticeId);
    }
}
