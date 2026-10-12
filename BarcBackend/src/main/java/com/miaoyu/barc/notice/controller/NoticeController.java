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
     *
     * 说明：一次查询返回最近一条公告；原来的日期范围查询加回退与此结果相同。
     * day 仅保留请求兼容，首页不会因为公告超过天数而显示“暂无公告”。
     *
     * @param day 兼容旧客户端的日期参数
     * @return 公告列表（0 或 1 条），返回结构与改动前保持一致
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
        // 作者归属由服务层按公告 ID 读取，请求体 author 不参与授权。
        return noticeService.updateNoticeService(request.getAttribute("uuid").toString(), model);
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
